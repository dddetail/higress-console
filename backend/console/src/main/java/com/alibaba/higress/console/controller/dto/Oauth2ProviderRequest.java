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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Higress
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "OAuth2 Provider Request")
public class Oauth2ProviderRequest {

    @Schema(description = "Display name")
    private String name;

    @Schema(description = "Provider key, e.g. github, gitlab, or custom key")
    private String providerKey;

    @Schema(description = "Authorization URL (not required for preset providers)")
    private String authorizationUrl;

    @Schema(description = "Token URL (not required for preset providers)")
    private String tokenUrl;

    @Schema(description = "User info URL (not required for preset providers)")
    private String userInfoUrl;

    @Schema(description = "OAuth2 scope")
    private String scope;

    @Schema(description = "Client ID")
    private String clientId;

    @Schema(description = "Client Secret")
    private String clientSecret;

    @Schema(description = "Icon URL")
    private String iconUrl;

    @Schema(description = "Whether the provider is enabled")
    private Boolean enabled;
}
