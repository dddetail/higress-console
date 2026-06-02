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
package com.alibaba.higress.console.controller;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.higress.console.controller.dto.Oauth2ProviderRequest;
import com.alibaba.higress.console.controller.dto.Response;
import com.alibaba.higress.console.controller.util.ControllerUtil;
import com.alibaba.higress.console.repository.entity.Oauth2ProviderEntity;
import com.alibaba.higress.console.service.SsoConfigService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Higress
 */
@RestController
@RequestMapping("/v1/oauth2-providers")
@Tag(name = "OAuth2 Provider APIs")
public class Oauth2ProviderController {

    @Resource
    private SsoConfigService ssoConfigService;

    @GetMapping
    @Operation(summary = "List all OAuth2 providers")
    public ResponseEntity<Response<List<Oauth2ProviderEntity>>> list() {
        return ResponseEntity.ok(Response.success(ssoConfigService.listProviders()));
    }

    @PostMapping
    @Operation(summary = "Add an OAuth2 provider")
    public ResponseEntity<Response<Oauth2ProviderEntity>> add(
        @RequestBody Oauth2ProviderRequest request) {
        Oauth2ProviderEntity entity = toEntity(request);
        Oauth2ProviderEntity created = ssoConfigService.addProvider(entity);
        return ControllerUtil.buildResponseEntity(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an OAuth2 provider")
    public ResponseEntity<Response<Oauth2ProviderEntity>> update(
        @PathVariable Long id, @RequestBody Oauth2ProviderRequest request) {
        Oauth2ProviderEntity entity = toEntity(request);
        entity.setId(id);
        Oauth2ProviderEntity updated = ssoConfigService.updateProvider(entity);
        return ControllerUtil.buildResponseEntity(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an OAuth2 provider")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        ssoConfigService.deleteProvider(id);
        return ControllerUtil.buildSuccessResponseEntity();
    }

    @GetMapping("/sso-status")
    @Operation(summary = "Get SSO enabled status")
    public ResponseEntity<Response<Boolean>> getSsoStatus() {
        return ResponseEntity.ok(Response.success(ssoConfigService.isSsoEnabled()));
    }

    @PutMapping("/sso-status")
    @Operation(summary = "Set SSO enabled status")
    public ResponseEntity<?> setSsoStatus(@RequestBody Boolean enabled) {
        ssoConfigService.setSsoEnabled(enabled);
        return ControllerUtil.buildSuccessResponseEntity();
    }

    private Oauth2ProviderEntity toEntity(Oauth2ProviderRequest request) {
        return Oauth2ProviderEntity.builder()
            .name(request.getName())
            .providerKey(request.getProviderKey())
            .authorizationUrl(request.getAuthorizationUrl())
            .tokenUrl(request.getTokenUrl())
            .userInfoUrl(request.getUserInfoUrl())
            .scope(request.getScope())
            .clientId(request.getClientId())
            .clientSecret(request.getClientSecret())
            .iconUrl(request.getIconUrl())
            .enabled(request.getEnabled() != null ? request.getEnabled() : Boolean.TRUE)
            .build();
    }
}
