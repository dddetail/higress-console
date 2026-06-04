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
package com.alibaba.higress.console.controller.dto;

import com.alibaba.higress.console.repository.entity.Oauth2ProviderEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public view object for OAuth2 providers, exposed on the login page.
 * Only contains fields needed to render the SSO login button.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "OAuth2 Provider (public view)")
public class Oauth2ProviderVO {

    @Schema(description = "Display name, e.g. GitHub")
    private String name;

    @Schema(description = "Provider key, e.g. github, used to build the authorization URL")
    private String providerKey;

    @Schema(description = "Icon URL for the login button")
    private String iconUrl;

    public static Oauth2ProviderVO fromEntity(Oauth2ProviderEntity entity) {
        return Oauth2ProviderVO.builder()
            .name(entity.getName())
            .providerKey(entity.getProviderKey())
            .iconUrl(entity.getIconUrl())
            .build();
    }
}
