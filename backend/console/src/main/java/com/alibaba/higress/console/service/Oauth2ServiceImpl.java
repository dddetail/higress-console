/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.alibaba.higress.console.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.higress.console.model.User;
import com.alibaba.higress.console.repository.Oauth2AccountRepository;
import com.alibaba.higress.console.repository.Oauth2ProviderRepository;
import com.alibaba.higress.console.repository.UserRepository;
import com.alibaba.higress.console.repository.entity.Oauth2AccountEntity;
import com.alibaba.higress.console.repository.entity.Oauth2ProviderEntity;
import com.alibaba.higress.console.repository.entity.UserEntity;
import com.alibaba.higress.console.service.oauth2.Oauth2Client;
import com.alibaba.higress.console.service.oauth2.Oauth2StateService;
import com.alibaba.higress.console.service.oauth2.TokenEncryptor;
import com.alibaba.higress.console.service.oauth2.UserInfoMapper;
import com.alibaba.higress.sdk.exception.BusinessException;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Higress
 */
@Slf4j
@Service
public class Oauth2ServiceImpl implements Oauth2Service {

    @Resource
    private Oauth2ProviderRepository providerRepository;

    @Resource
    private UserRepository userRepository;

    @Resource
    private Oauth2AccountRepository accountRepository;

    @Resource
    private Oauth2Client oauth2Client;

    @Resource
    private Oauth2StateService stateService;

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private TokenEncryptor tokenEncryptor;

    @Override
    public String buildAuthorizationUrl(String providerKey, String redirectUri) {
        Oauth2ProviderEntity provider = providerRepository.findByProviderKey(providerKey)
            .orElseThrow(() -> new BusinessException("OAuth2 provider not found: " + providerKey));

        if (!provider.getEnabled()) {
            throw new BusinessException("OAuth2 provider is disabled: " + providerKey);
        }

        String state = stateService.generateState(providerKey);
        String scope = provider.getScope() != null ? provider.getScope() : "";

        return provider.getAuthorizationUrl()
            + "?client_id=" + urlEncode(provider.getClientId())
            + "&redirect_uri=" + urlEncode(redirectUri)
            + "&response_type=code"
            + "&scope=" + urlEncode(scope)
            + "&state=" + state;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User handleCallback(String providerKey, String code, String state, String redirectUri) {
        if (!stateService.validateAndConsumeState(state)) {
            throw new BusinessException("Invalid or expired OAuth2 state parameter.");
        }

        Oauth2ProviderEntity provider = providerRepository.findByProviderKey(providerKey)
            .orElseThrow(() -> new BusinessException("OAuth2 provider not found: " + providerKey));

        // Exchange code for token
        Oauth2Client.TokenResponse tokenResponse = oauth2Client.exchangeToken(
            provider.getTokenUrl(), provider.getClientId(), provider.getClientSecret(),
            code, redirectUri);

        // Fetch user info
        Map<String, Object> providerUserInfo = oauth2Client.fetchUserInfo(
            provider.getUserInfoUrl(), tokenResponse.getAccessToken());

        // Map provider user info to normalized fields
        UserInfoMapper.MappedUserInfo mapped = userInfoMapper.map(providerKey, providerUserInfo);
        String providerUserId = mapped.getProviderUserId();
        String providerUsername = mapped.getProviderUsername();
        String displayName = mapped.getDisplayName();

        // Generate a local username based on provider
        String localUsername = providerKey + "_" + providerUserId;
        LocalDateTime now = LocalDateTime.now();

        // Find or create local user
        UserEntity userEntity = userRepository.findByUsername(localUsername).orElse(null);
        if (userEntity == null) {
            userEntity = UserEntity.builder()
                .username(localUsername)
                .displayName(displayName != null ? displayName : providerUsername)
                .type("consumer_user")
                .status("active")
                .createdAt(now)
                .updatedAt(now)
                .build();
            userEntity = userRepository.save(userEntity);
            log.info("Created new OAuth2 user: {} via provider: {}", localUsername, providerKey);
        } else {
            if (displayName != null && !displayName.equals(userEntity.getDisplayName())) {
                userEntity.setDisplayName(displayName);
                userEntity.setUpdatedAt(now);
                userRepository.save(userEntity);
            }
            if ("disabled".equals(userEntity.getStatus())) {
                throw new BusinessException("User account is disabled.");
            }
        }

        // Store or update OAuth2 account binding
        boolean isNewAccount = !accountRepository.findByProviderAndProviderUserId(providerKey, providerUserId)
            .isPresent();
        Oauth2AccountEntity accountEntity = accountRepository
            .findByProviderAndProviderUserId(providerKey, providerUserId)
            .orElse(Oauth2AccountEntity.builder()
                .username(localUsername)
                .provider(providerKey)
                .providerUserId(providerUserId)
                .providerUsername(providerUsername)
                .createdAt(now)
                .updatedAt(now)
                .build());
        accountEntity.setAccessToken(tokenEncryptor.encrypt(tokenResponse.getAccessToken()));
        if (tokenResponse.getRefreshToken() != null) {
            accountEntity.setRefreshToken(tokenEncryptor.encrypt(tokenResponse.getRefreshToken()));
        }
        if (tokenResponse.getExpiresIn() != null) {
            accountEntity.setTokenExpiresAt(now.plusSeconds(tokenResponse.getExpiresIn()));
        }
        if (!isNewAccount) {
            accountEntity.setUpdatedAt(now);
        }
        accountRepository.save(accountEntity);

        return User.builder()
            .name(userEntity.getUsername())
            .displayName(userEntity.getDisplayName())
            .employeeId(userEntity.getEmployeeId())
            .type(userEntity.getType())
            .status(userEntity.getStatus())
            .build();
    }

    @Override
    public List<Oauth2ProviderEntity> getEnabledProviders() {
        return providerRepository.findByEnabledTrue();
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new BusinessException("Failed to encode URL", e);
        }
    }
}
