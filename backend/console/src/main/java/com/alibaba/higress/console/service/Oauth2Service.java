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

import com.alibaba.higress.console.model.User;
import com.alibaba.higress.console.repository.entity.Oauth2ProviderEntity;

/**
 * @author Higress
 */
public interface Oauth2Service {

    /**
     * Build the authorization URL to redirect the user to the OAuth2 provider.
     */
    String buildAuthorizationUrl(String providerKey, String redirectUri);

    /**
     * Handle the OAuth2 callback: exchange code for token, fetch user info,
     * create or update local user, and return the authenticated user.
     */
    User handleCallback(String providerKey, String code, String state, String redirectUri);

    /**
     * Get all enabled providers (for login page rendering).
     */
    List<Oauth2ProviderEntity> getEnabledProviders();
}
