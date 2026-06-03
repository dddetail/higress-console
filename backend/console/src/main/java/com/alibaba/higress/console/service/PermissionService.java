/*
 * Copyright (c) 2022-2023 Alibaba Group Holding Ltd.
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

import org.springframework.stereotype.Service;

/**
 * RBAC permission check service based on role-resource-action matrix.
 */
@Service
public class PermissionService {

    public boolean hasPermission(String role, String resource, String action) {
        if (role == null) {
            return false;
        }

        // Platform admin has full access
        if ("platform_admin".equals(role)) {
            return true;
        }

        // System resources are only accessible by admin
        if (isSystemResource(resource)) {
            return false;
        }

        // User management is only accessible by owner
        if ("user".equals(resource)) {
            return "owner".equals(role);
        }

        // Other resources: owner/manager/reader can read, owner/manager can write
        if ("read".equals(action)) {
            return "owner".equals(role) || "manager".equals(role) || "reader".equals(role);
        }
        if ("write".equals(action)) {
            return "owner".equals(role) || "manager".equals(role);
        }

        return false;
    }

    private boolean isSystemResource(String resource) {
        return "system".equals(resource) || "oauth2_provider".equals(resource);
    }
}
