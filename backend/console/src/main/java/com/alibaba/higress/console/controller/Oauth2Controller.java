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

import java.net.URLEncoder;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.higress.console.aop.AllowAnonymous;
import com.alibaba.higress.console.controller.dto.Response;
import com.alibaba.higress.console.model.User;
import com.alibaba.higress.console.service.Oauth2Service;
import com.alibaba.higress.console.service.SessionService;
import com.alibaba.higress.console.service.SsoConfigService;
import com.alibaba.higress.sdk.exception.BusinessException;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Higress
 */
@Slf4j
@RestController
@RequestMapping("/oauth2")
@AllowAnonymous
@Tag(name = "OAuth2 APIs")
public class Oauth2Controller {

    @Resource
    private Oauth2Service oauth2Service;

    @Resource
    private SsoConfigService ssoConfigService;

    @Resource
    private SessionService sessionService;

    @Value("${higress-console.oauth2.redirect-base-url:}")
    private String redirectBaseUrl;

    @GetMapping("/providers")
    public ResponseEntity<Response<?>> listEnabledProviders() {
        if (!ssoConfigService.isSsoEnabled()) {
            return ResponseEntity.ok(Response.success(null));
        }
        return ResponseEntity.ok(Response.success(oauth2Service.getEnabledProviders()));
    }

    @GetMapping("/authorization/{provider}")
    public void authorize(@PathVariable("provider") String providerKey,
        HttpServletRequest request, HttpServletResponse response) {
        if (!ssoConfigService.isSsoEnabled()) {
            throw new BusinessException("SSO is not enabled.");
        }
        String redirectUri = buildRedirectUri(request, providerKey);
        String authUrl = oauth2Service.buildAuthorizationUrl(providerKey, redirectUri);
        try {
            response.sendRedirect(authUrl);
        } catch (Exception e) {
            throw new BusinessException("Failed to redirect to OAuth2 provider", e);
        }
    }

    @GetMapping("/callback/{provider}")
    public void callback(@PathVariable("provider") String providerKey,
        @RequestParam(value = "code", required = false) String code,
        @RequestParam(value = "state", required = false) String state,
        @RequestParam(value = "error", required = false) String error,
        HttpServletRequest request, HttpServletResponse response) {
        if (!ssoConfigService.isSsoEnabled()) {
            redirectToLoginWithError(response, "SSO is not enabled.");
            return;
        }
        if (StringUtils.isNotEmpty(error)) {
            log.warn("OAuth2 authorization failed for provider {}: {}", providerKey, error);
            redirectToLoginWithError(response, error);
            return;
        }
        if (StringUtils.isEmpty(code) || StringUtils.isEmpty(state)) {
            redirectToLoginWithError(response, "Missing code or state parameter.");
            return;
        }
        try {
            String redirectUri = buildRedirectUri(request, providerKey);
            User user = oauth2Service.handleCallback(providerKey, code, state, redirectUri);
            sessionService.saveOauth2Session(response, user);
            sendSuccessRedirect(response);
        } catch (Exception e) {
            log.error("OAuth2 callback failed for provider {}", providerKey, e);
            redirectToLoginWithError(response, e.getMessage());
        }
    }

    private String buildRedirectUri(HttpServletRequest request, String providerKey) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int port = request.getServerPort();
        StringBuilder sb = new StringBuilder(scheme).append("://").append(serverName);
        if ("http".equals(scheme) && port != 80 || "https".equals(scheme) && port != 443) {
            sb.append(":").append(port);
        }
        sb.append("/oauth2/callback/").append(providerKey);
        return sb.toString();
    }

    private void sendSuccessRedirect(HttpServletResponse response) {
        try {
            String target = StringUtils.isNotEmpty(redirectBaseUrl) ? redirectBaseUrl : "/";
            response.sendRedirect(target);
        } catch (Exception e) {
            throw new BusinessException("Failed to redirect after login", e);
        }
    }

    private void redirectToLoginWithError(HttpServletResponse response, String error) {
        try {
            String encodedError = URLEncoder.encode(error, "UTF-8");
            String loginPath = "/login?oauth_error=" + encodedError;
            String target = StringUtils.isNotEmpty(redirectBaseUrl)
                ? redirectBaseUrl + loginPath : loginPath;
            response.sendRedirect(target);
        } catch (Exception e) {
            throw new BusinessException("Failed to redirect", e);
        }
    }
}
