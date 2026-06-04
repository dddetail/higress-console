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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.annotation.PreDestroy;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.higress.sdk.exception.BusinessException;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Higress
 */
@Slf4j
@Component
public class Oauth2Client {

    private static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";
    private static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";

    private final CloseableHttpClient httpClient;

    public Oauth2Client() {
        this.httpClient = HttpClients.custom()
            .setMaxConnTotal(20)
            .setMaxConnPerRoute(5)
            .build();
    }

    @PreDestroy
    public void destroy() throws IOException {
        httpClient.close();
    }

    public TokenResponse exchangeToken(String tokenUrl, String clientId, String clientSecret,
        String code, String redirectUri) {
        JSONObject body = new JSONObject();
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        body.put("code", code);
        body.put("redirect_uri", redirectUri);
        body.put("grant_type", GRANT_TYPE_AUTHORIZATION_CODE);
        return doPostTokenRequest(tokenUrl, body);
    }

    public TokenResponse refreshToken(String tokenUrl, String clientId, String clientSecret,
        String refreshToken) {
        JSONObject body = new JSONObject();
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        body.put("refresh_token", refreshToken);
        body.put("grant_type", GRANT_TYPE_REFRESH_TOKEN);
        return doPostTokenRequest(tokenUrl, body);
    }

    private TokenResponse doPostTokenRequest(String tokenUrl, JSONObject body) {
        HttpPost post = new HttpPost(tokenUrl);
        post.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        post.setHeader(HttpHeaders.ACCEPT, "application/json");
        post.setEntity(new StringEntity(body.toJSONString(), StandardCharsets.UTF_8));

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new BusinessException("Token request failed: " + responseBody);
            }
            JSONObject json = JSON.parseObject(responseBody);
            return new TokenResponse(
                json.getString("access_token"),
                json.getString("refresh_token"),
                json.getInteger("expires_in")
            );
        } catch (IOException e) {
            throw new BusinessException("Failed to perform token request", e);
        }
    }

    /**
     * Fetch user info from OAuth2 provider.
     */
    public Map<String, Object> fetchUserInfo(String userInfoUrl, String accessToken) {
        HttpGet get = new HttpGet(userInfoUrl);
        get.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        get.setHeader(HttpHeaders.ACCEPT, "application/json");

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new BusinessException("Failed to fetch user info: " + responseBody);
            }
            return JSON.parseObject(responseBody, Map.class);
        } catch (IOException e) {
            throw new BusinessException("Failed to fetch user info", e);
        }
    }

    public static class TokenResponse {
        private final String accessToken;
        private final String refreshToken;
        private final Integer expiresIn;

        public TokenResponse(String accessToken, String refreshToken, Integer expiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }

        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public Integer getExpiresIn() { return expiresIn; }
    }
}
