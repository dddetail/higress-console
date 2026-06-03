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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for RBAC permission matrix enforcement.
 * Synced with the matrix defined in docs/superpowers/plans/2026-06-03-rbac-permission-matrix.md
 */
class PermissionServiceTest {

    private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService();
    }

    @Nested
    @DisplayName("Platform Admin (platform_admin)")
    class PlatformAdminTests {

        @Test
        @DisplayName("platform_admin has full access to all resources")
        void platformAdminHasFullAccess() {
            assertTrue(permissionService.hasPermission("platform_admin", "route", "read"));
            assertTrue(permissionService.hasPermission("platform_admin", "route", "write"));
            assertTrue(permissionService.hasPermission("platform_admin", "system", "read"));
            assertTrue(permissionService.hasPermission("platform_admin", "system", "write"));
            assertTrue(permissionService.hasPermission("platform_admin", "user", "read"));
            assertTrue(permissionService.hasPermission("platform_admin", "user", "write"));
            assertTrue(permissionService.hasPermission("platform_admin", "oauth2_provider", "read"));
            assertTrue(permissionService.hasPermission("platform_admin", "oauth2_provider", "write"));
            assertTrue(permissionService.hasPermission("platform_admin", "consumer", "read"));
            assertTrue(permissionService.hasPermission("platform_admin", "consumer", "write"));
        }
    }

    @Nested
    @DisplayName("Owner role")
    class OwnerTests {

        @ParameterizedTest(name = "owner can read resource={0}")
        @DisplayName("owner can read all non-system resources")
        @CsvSource({"route", "domain", "service_source", "consumer",
            "wasm_plugin", "plugin_instance", "dashboard", "tls_certificate",
            "ai_route", "llm_provider", "mcp_server"})
        void ownerCanRead(String resource) {
            assertTrue(permissionService.hasPermission("owner", resource, "read"));
        }

        @ParameterizedTest(name = "owner can write resource={0}")
        @DisplayName("owner can write all non-system resources")
        @CsvSource({"route", "domain", "service_source", "consumer",
            "wasm_plugin", "plugin_instance", "ai_route", "llm_provider", "mcp_server"})
        void ownerCanWrite(String resource) {
            assertTrue(permissionService.hasPermission("owner", resource, "write"));
        }

        @Test
        @DisplayName("owner can manage users")
        void ownerCanManageUsers() {
            assertTrue(permissionService.hasPermission("owner", "user", "read"));
            assertTrue(permissionService.hasPermission("owner", "user", "write"));
        }

        @Test
        @DisplayName("owner cannot access system settings")
        void ownerCannotAccessSystem() {
            assertFalse(permissionService.hasPermission("owner", "system", "read"));
            assertFalse(permissionService.hasPermission("owner", "system", "write"));
        }

        @Test
        @DisplayName("owner cannot access oauth2_provider")
        void ownerCannotAccessOauth2() {
            assertFalse(permissionService.hasPermission("owner", "oauth2_provider", "read"));
            assertFalse(permissionService.hasPermission("owner", "oauth2_provider", "write"));
        }
    }

    @Nested
    @DisplayName("Manager role")
    class ManagerTests {

        @Test
        @DisplayName("manager can read non-system resources")
        void managerCanRead() {
            assertTrue(permissionService.hasPermission("manager", "route", "read"));
            assertTrue(permissionService.hasPermission("manager", "domain", "read"));
            assertTrue(permissionService.hasPermission("manager", "consumer", "read"));
            assertTrue(permissionService.hasPermission("manager", "consumer", "read"));
        }

        @Test
        @DisplayName("manager can write non-system resources")
        void managerCanWrite() {
            assertTrue(permissionService.hasPermission("manager", "route", "write"));
            assertTrue(permissionService.hasPermission("manager", "domain", "write"));
            assertTrue(permissionService.hasPermission("manager", "consumer", "write"));
        }

        @Test
        @DisplayName("manager cannot manage users")
        void managerCannotManageUsers() {
            assertFalse(permissionService.hasPermission("manager", "user", "read"));
            assertFalse(permissionService.hasPermission("manager", "user", "write"));
        }

        @Test
        @DisplayName("manager cannot access system settings")
        void managerCannotAccessSystem() {
            assertFalse(permissionService.hasPermission("manager", "system", "read"));
            assertFalse(permissionService.hasPermission("manager", "system", "write"));
        }
    }

    @Nested
    @DisplayName("Reader role")
    class ReaderTests {

        @Test
        @DisplayName("reader can read non-system resources")
        void readerCanRead() {
            assertTrue(permissionService.hasPermission("reader", "route", "read"));
            assertTrue(permissionService.hasPermission("reader", "domain", "read"));
            assertTrue(permissionService.hasPermission("reader", "consumer", "read"));
            assertTrue(permissionService.hasPermission("reader", "consumer", "read"));
        }

        @Test
        @DisplayName("reader cannot write any resource")
        void readerCannotWrite() {
            assertFalse(permissionService.hasPermission("reader", "route", "write"));
            assertFalse(permissionService.hasPermission("reader", "domain", "write"));
            assertFalse(permissionService.hasPermission("reader", "consumer", "write"));
        }

        @Test
        @DisplayName("reader cannot manage users")
        void readerCannotManageUsers() {
            assertFalse(permissionService.hasPermission("reader", "user", "read"));
            assertFalse(permissionService.hasPermission("reader", "user", "write"));
        }

        @Test
        @DisplayName("reader cannot access system settings")
        void readerCannotAccessSystem() {
            assertFalse(permissionService.hasPermission("reader", "system", "read"));
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("null role denies all access")
        void nullRoleDeniesAll() {
            assertFalse(permissionService.hasPermission(null, "route", "read"));
            assertFalse(permissionService.hasPermission(null, "system", "write"));
        }

        @Test
        @DisplayName("unknown role is treated as reader")
        void unknownRoleTreatedAsReader() {
            assertFalse(permissionService.hasPermission("unknown_role", "route", "write"));
            assertFalse(permissionService.hasPermission("unknown_role", "system", "read"));
            assertFalse(permissionService.hasPermission("unknown_role", "user", "read"));
        }

        @Test
        @DisplayName("unknown action returns false")
        void unknownActionReturnsFalse() {
            assertFalse(permissionService.hasPermission("owner", "route", "delete"));
            assertFalse(permissionService.hasPermission("owner", "route", "execute"));
        }
    }
}
