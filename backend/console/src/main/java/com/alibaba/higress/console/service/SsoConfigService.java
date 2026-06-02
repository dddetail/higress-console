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

import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.alibaba.higress.console.repository.Oauth2ProviderRepository;
import com.alibaba.higress.console.repository.entity.Oauth2ProviderEntity;
import com.alibaba.higress.console.service.oauth2.PresetProviders;
import com.alibaba.higress.sdk.exception.ValidationException;

/**
 * @author Higress
 */
@Service
public class SsoConfigService {

    private static final String SSO_ENABLED_KEY = "sso.enabled";

    @Resource
    private ConfigService configService;

    @Resource
    private Oauth2ProviderRepository providerRepository;

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
        return providerRepository.save(entity);
    }

    public Oauth2ProviderEntity updateProvider(Oauth2ProviderEntity entity) {
        Oauth2ProviderEntity existing = providerRepository.findById(entity.getId())
            .orElseThrow(() -> new ValidationException("Provider not found: " + entity.getId()));
        entity.setProviderKey(existing.getProviderKey());
        entity.setIsPreset(existing.getIsPreset());
        return providerRepository.save(entity);
    }

    public void deleteProvider(Long id) {
        providerRepository.deleteById(id);
    }
}
