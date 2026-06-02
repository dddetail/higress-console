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
package com.alibaba.higress.console.service.oauth2;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.higress.console.repository.entity.Oauth2ProviderEntity;

/**
 * @author Higress
 */
public final class PresetProviders {

    private static final Map<String, Oauth2ProviderEntity> PRESETS = new HashMap<>(4);

    static {
        Oauth2ProviderEntity github = new Oauth2ProviderEntity();
        github.setName("GitHub");
        github.setProviderKey("github");
        github.setAuthorizationUrl("https://github.com/login/oauth/authorize");
        github.setTokenUrl("https://github.com/login/oauth/access_token");
        github.setUserInfoUrl("https://api.github.com/user");
        github.setScope("read:user,user:email");
        github.setIconUrl("https://github.githubassets.com/favicons/favicon-dark.svg");
        github.setIsPreset(true);
        PRESETS.put("github", github);

        Oauth2ProviderEntity gitlab = new Oauth2ProviderEntity();
        gitlab.setName("GitLab");
        gitlab.setProviderKey("gitlab");
        gitlab.setAuthorizationUrl("https://gitlab.com/oauth/authorize");
        gitlab.setTokenUrl("https://gitlab.com/oauth/token");
        gitlab.setUserInfoUrl("https://gitlab.com/api/v4/user");
        gitlab.setScope("read_user");
        gitlab.setIconUrl("https://gitlab.com/favicon.ico");
        gitlab.setIsPreset(true);
        PRESETS.put("gitlab", gitlab);
    }

    public static Oauth2ProviderEntity getPreset(String providerKey) {
        return PRESETS.get(providerKey);
    }

    public static Map<String, Oauth2ProviderEntity> allPresets() {
        return new HashMap<>(PRESETS);
    }

    private PresetProviders() {}
}
