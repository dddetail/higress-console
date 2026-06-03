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

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Resource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.alibaba.higress.console.controller.exception.AuthException;
import com.alibaba.higress.console.model.User;
import com.alibaba.higress.console.service.PermissionService;
import com.alibaba.higress.console.service.SessionUserHelper;
import com.alibaba.higress.console.service.UserService;

import lombok.extern.slf4j.Slf4j;

/**
 * RBAC permission check aspect. Executes after ApiStandardizationAspect (login check).
 * Checks @RequirePermission annotation and enforces role-based access control.
 */
@Aspect
@Component
@Slf4j
@Order(2)
public class RbacAspect {

    private static final ConcurrentHashMap<String, RequirePermission> METHOD_PERMISSION_CACHE = new ConcurrentHashMap<>();

    @Resource
    private PermissionService permissionService;

    @Resource
    private UserService userService;

    private static final ConcurrentHashMap<String, Boolean> METHOD_ALLOW_ANONYMOUS_CACHE = new ConcurrentHashMap<>();

    @Around("execution(* com.alibaba.higress.console.controller..*Controller.*(..))")
    public Object checkPermission(ProceedingJoinPoint point) throws Throwable {
        if (isAllowAnonymous(point)) {
            return point.proceed();
        }

        RequirePermission permission = getRequirePermission(point);
        if (permission == null) {
            return point.proceed();
        }

        User currentUser = SessionUserHelper.getCurrentUser();
        if (currentUser == null) {
            throw new AuthException("Login required.");
        }

        // Use role from session first (e.g., admin user from K8s Secret)
        String role = currentUser.getRole();
        if (role == null) {
            // Fallback to database lookup for OAuth2 users
            User fullUser = userService.findByUsername(currentUser.getName());
            if (fullUser == null) {
                throw new AuthException("User not found.");
            }
            role = fullUser.getRole();
        }
        if (role == null) {
            role = "reader";
        }

        if (!permissionService.hasPermission(role, permission.resource(), permission.action())) {
            log.warn("Permission denied: user={}, role={}, resource={}, action={}",
                currentUser.getName(), role, permission.resource(), permission.action());
            throw new AuthException("Permission denied.");
        }

        return point.proceed();
    }

    private boolean isAllowAnonymous(ProceedingJoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        String key = signature.getDeclaringTypeName() + "." + signature.getName();

        return METHOD_ALLOW_ANONYMOUS_CACHE.computeIfAbsent(key, k -> {
            try {
                Class<?> targetClass = point.getTarget().getClass();
                if (targetClass.getAnnotation(AllowAnonymous.class) != null) {
                    return true;
                }
                Method method = targetClass.getMethod(signature.getName(), signature.getParameterTypes());
                return method.getAnnotation(AllowAnonymous.class) != null;
            } catch (Exception e) {
                log.error("Failed to check AllowAnonymous annotation: {}", key, e);
                return false;
            }
        });
    }

    private RequirePermission getRequirePermission(ProceedingJoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        String key = signature.getDeclaringTypeName() + "." + signature.getName();

        return METHOD_PERMISSION_CACHE.computeIfAbsent(key, k -> {
            try {
                Class<?> targetClass = point.getTarget().getClass();
                Method method = targetClass.getMethod(signature.getName(), signature.getParameterTypes());
                RequirePermission methodAnnotation = method.getAnnotation(RequirePermission.class);
                if (methodAnnotation != null) {
                    return methodAnnotation;
                }
                return targetClass.getAnnotation(RequirePermission.class);
            } catch (Exception e) {
                log.error("Failed to get RequirePermission annotation: {}", key, e);
                return null;
            }
        });
    }
}
