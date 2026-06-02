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

import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * @author Higress
 */
@Component
public class UserInfoMapper {

    /**
     * Maps provider-specific user info response to a normalized structure.
     *
     * @return an array: [providerUserId, providerUsername, displayName, email]
     */
    public String[] map(String providerKey, Map<String, Object> userInfo) {
        switch (providerKey) {
            case "github":
                return mapGithub(userInfo);
            case "gitlab":
                return mapGitlab(userInfo);
            default:
                return mapGeneric(userInfo);
        }
    }

    private String[] mapGithub(Map<String, Object> info) {
        String id = String.valueOf(info.get("id"));
        String login = (String)info.get("login");
        String name = (String)info.get("name");
        return new String[] {id, login, name != null ? name : login, null};
    }

    private String[] mapGitlab(Map<String, Object> info) {
        String id = String.valueOf(info.get("id"));
        String username = (String)info.get("username");
        String name = (String)info.get("name");
        return new String[] {id, username, name != null ? name : username, null};
    }

    private String[] mapGeneric(Map<String, Object> info) {
        Object id = info.get("id");
        if (id == null) {
            id = info.get("sub");
        }
        String userId = id != null ? String.valueOf(id) : "";
        String username = (String)info.getOrDefault("username", userId);
        String name = (String)info.getOrDefault("name", username);
        return new String[] {userId, username, name, null};
    }
}
