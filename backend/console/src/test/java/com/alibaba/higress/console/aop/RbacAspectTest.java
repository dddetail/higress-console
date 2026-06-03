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
package com.alibaba.higress.console.aop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.alibaba.higress.console.controller.ConsumersController;
import com.alibaba.higress.console.controller.SessionController;
import com.alibaba.higress.console.controller.UserController;
import com.alibaba.higress.console.controller.exception.AuthException;
import com.alibaba.higress.console.model.User;
import com.alibaba.higress.console.service.PermissionService;
import com.alibaba.higress.console.service.SessionUserHelper;
import com.alibaba.higress.console.service.UserService;

/**
 * Tests for RbacAspect by using real controller classes for annotation resolution.
 */
@DisplayName("RbacAspect permission enforcement")
class RbacAspectTest {

    @Mock
    private PermissionService permissionService;

    @Mock
    private UserService userService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @InjectMocks
    private RbacAspect rbacAspect;

    @BeforeEach
    void setUp() throws Throwable {
        MockitoAnnotations.openMocks(this);
        SessionUserHelper.clearCurrentUser();
        when(joinPoint.proceed()).thenReturn("ok");
    }

    @AfterEach
    void tearDown() {
        SessionUserHelper.clearCurrentUser();
    }

    private void setupJoinPointForController(Class<?> controllerClass, String methodName, Class<?>... paramTypes)
        throws NoSuchMethodException {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getDeclaringTypeName()).thenReturn(controllerClass.getName());
        when(methodSignature.getName()).thenReturn(methodName);
        when(methodSignature.getParameterTypes()).thenReturn(paramTypes);
        // Return the real class for annotation resolution
        Object realController;
        try {
            realController = controllerClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            // For classes that can't be instantiated easily, use mock but set class
            realController = mock(controllerClass);
        }
        when(joinPoint.getTarget()).thenReturn(realController);
    }

    @Nested
    @DisplayName("AllowAnonymous skip")
    class AllowAnonymousTests {

        @Test
        @DisplayName("methods on @AllowAnonymous controller proceed without check")
        void allowAnonymousControllerSkipped() throws Throwable {
            // SessionController has @AllowAnonymous at class level
            setupJoinPointForController(SessionController.class, "login");

            rbacAspect.checkPermission(joinPoint);

            verify(joinPoint).proceed();
            verify(permissionService, never()).hasPermission(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("No @RequirePermission annotation")
    class NoAnnotationTests {

        @Test
        @DisplayName("UserController info() has no annotation and proceeds")
        void noAnnotationProceeds() throws Throwable {
            // UserController.info() has no @RequirePermission
            setupJoinPointForController(UserController.class, "info");

            Object result = rbacAspect.checkPermission(joinPoint);

            verify(joinPoint).proceed();
            assertEquals("ok", result);
        }
    }

    @Nested
    @DisplayName("RequirePermission with session user")
    class RequirePermissionTests {

        @Test
        @DisplayName("ConsumersController.list() has @RequirePermission and checks permission")
        void checksPermission() throws Throwable {
            User adminUser = User.builder().name("admin").role("platform_admin").build();
            SessionUserHelper.setCurrentUser(adminUser);

            setupJoinPointForController(ConsumersController.class, "list");
            when(permissionService.hasPermission("platform_admin", "consumer", "read")).thenReturn(true);

            rbacAspect.checkPermission(joinPoint);

            verify(permissionService).hasPermission("platform_admin", "consumer", "read");
            verify(joinPoint).proceed();
        }

        @Test
        @DisplayName("permission denied throws AuthException")
        void permissionDeniedThrows() throws Throwable {
            User readerUser = User.builder().name("reader1").role("reader").build();
            SessionUserHelper.setCurrentUser(readerUser);

            setupJoinPointForController(ConsumersController.class, "list");
            when(permissionService.hasPermission("reader", "consumer", "read")).thenReturn(true);
            // add method requires write permission
            setupJoinPointForController(ConsumersController.class, "add",
                com.alibaba.higress.console.controller.dto.ConsumerCreateRequest.class);
            when(permissionService.hasPermission("reader", "consumer", "write")).thenReturn(false);

            assertThrows(AuthException.class, () -> rbacAspect.checkPermission(joinPoint));
        }
    }

    @Nested
    @DisplayName("Role fallback")
    class RoleFallbackTests {

        @Test
        @DisplayName("session user with role uses session role directly")
        void sessionRoleUsed() throws Throwable {
            User sessionUser = User.builder().name("admin").role("platform_admin").build();
            SessionUserHelper.setCurrentUser(sessionUser);

            setupJoinPointForController(ConsumersController.class, "list");
            when(permissionService.hasPermission("platform_admin", "consumer", "read")).thenReturn(true);

            rbacAspect.checkPermission(joinPoint);

            // Should NOT query database since role is in session
            verify(userService, never()).findByUsername(anyString());
            verify(permissionService).hasPermission("platform_admin", "consumer", "read");
        }

        @Test
        @DisplayName("session user without role falls back to database")
        void fallsBackToDatabase() throws Throwable {
            User sessionUser = User.builder().name("oauth_user").type("consumer_user").build();
            SessionUserHelper.setCurrentUser(sessionUser);

            User dbUser = User.builder().name("oauth_user").role("manager").build();
            when(userService.findByUsername("oauth_user")).thenReturn(dbUser);

            setupJoinPointForController(ConsumersController.class, "list");
            when(permissionService.hasPermission("manager", "consumer", "read")).thenReturn(true);

            rbacAspect.checkPermission(joinPoint);

            verify(userService).findByUsername("oauth_user");
            verify(permissionService).hasPermission("manager", "consumer", "read");
        }

        @Test
        @DisplayName("null role defaults to reader")
        void nullRoleDefaultsToReader() throws Throwable {
            User sessionUser = User.builder().name("norole_user").build();
            SessionUserHelper.setCurrentUser(sessionUser);

            User dbUser = User.builder().name("norole_user").build(); // role is null
            when(userService.findByUsername("norole_user")).thenReturn(dbUser);

            setupJoinPointForController(ConsumersController.class, "list");
            when(permissionService.hasPermission("reader", "consumer", "read")).thenReturn(true);

            rbacAspect.checkPermission(joinPoint);

            verify(permissionService).hasPermission("reader", "consumer", "read");
        }
    }
}
