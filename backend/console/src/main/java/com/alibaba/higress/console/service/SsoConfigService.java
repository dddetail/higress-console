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

import java.time.LocalDateTime;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.alibaba.higress.console.repository.Oauth2AccountRepository;
import com.alibaba.higress.console.repository.Oauth2ProviderRepository;
import com.alibaba.higress.console.repository.entity.Oauth2ProviderEntity;
import com.alibaba.higress.console.service.oauth2.PresetProviders;
import com.alibaba.higress.sdk.exception.ValidationException;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Higress
 */
@Slf4j
@Service
public class SsoConfigService {

    private static final String SSO_ENABLED_KEY = "sso.enabled";

    @Resource
    private ConfigService configService;

    @Resource
    private Oauth2ProviderRepository providerRepository;

    @Resource
    private Oauth2AccountRepository accountRepository;

    public boolean isSsoEnabled() {
        return configService.getBoolean(SSO_ENABLED_KEY, false);
    }

    public void setSsoEnabled(boolean enabled) {
        if (enabled) {
            List<Oauth2ProviderEntity> enabledProviders = providerRepository.findByEnabledTrue();
            if (enabledProviders.isEmpty()) {
                throw new ValidationException(
                    "Cannot enable SSO: no OAuth2 provider is configured and enabled.");
            }
        }
        configService.setConfig(SSO_ENABLED_KEY, String.valueOf(enabled));
    }

    public List<Oauth2ProviderEntity> listProviders() {
        return providerRepository.findAll();
    }

    public Oauth2ProviderEntity getProvider(Long id) {
        return providerRepository.findById(id).orElse(null);
    }

    /**
     * Add a new OAuth2 provider. If the providerKey matches a preset template, auto-fill URLs.
     */
    public Oauth2ProviderEntity addProvider(Oauth2ProviderEntity entity) {
        Oauth2ProviderEntity preset = PresetProviders.getPreset(entity.getProviderKey());
        if (preset != null) {
            entity.setAuthorizationUrl(preset.getAuthorizationUrl());
            entity.setTokenUrl(preset.getTokenUrl());
            entity.setUserInfoUrl(preset.getUserInfoUrl());
            entity.setScope(preset.getScope());
            entity.setIconUrl(preset.getIconUrl());
            entity.setIsPreset(true);
        } else {
            entity.setIsPreset(false);
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return providerRepository.save(entity);
    }

    public Oauth2ProviderEntity updateProvider(Oauth2ProviderEntity entity) {
        Oauth2ProviderEntity existing = providerRepository.findById(entity.getId())
            .orElseThrow(() -> new ValidationException("Provider not found: " + entity.getId()));
        entity.setProviderKey(existing.getProviderKey());
        entity.setIsPreset(existing.getIsPreset());
        entity.setCreatedAt(existing.getCreatedAt());
        entity.setUpdatedAt(LocalDateTime.now());
        // Preserve existing clientSecret if not provided
        if (StringUtils.isEmpty(entity.getClientSecret())) {
            entity.setClientSecret(existing.getClientSecret());
        }
        return providerRepository.save(entity);
    }

    public void deleteProvider(Long id) {
        Oauth2ProviderEntity provider = providerRepository.findById(id)
            .orElseThrow(() -> new ValidationException("Provider not found: " + id));
        // Delete associated OAuth2 account bindings
        long deletedCount = accountRepository.deleteByProvider(provider.getProviderKey());
        if (deletedCount > 0) {
            log.info("Deleted {} OAuth2 account bindings for provider: {}",
                deletedCount, provider.getProviderKey());
        }
        // Delete the provider
        providerRepository.deleteById(id);
        // Auto-disable SSO if no enabled providers remain
        if (isSsoEnabled() && providerRepository.findByEnabledTrue().isEmpty()) {
            log.warn("No enabled OAuth2 providers remain. Auto-disabling SSO.");
            configService.setConfig(SSO_ENABLED_KEY, String.valueOf(false));
        }
    }
}
