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
 * RBAC 权限校验切面。在 ApiStandardizationAspect（登录校验）之后执行。
 * 检查 @RequirePermission 注解并做角色校验。
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

    @Around("execution(* com.alibaba.higress.console.controller..*Controller.*(..))")
    public Object checkPermission(ProceedingJoinPoint point) throws Throwable {
        RequirePermission permission = getRequirePermission(point);
        if (permission == null) {
            return point.proceed();
        }

        User currentUser = SessionUserHelper.getCurrentUser();
        if (currentUser == null) {
            throw new AuthException("Login required.");
        }

        User fullUser = userService.findByUsername(currentUser.getName());
        if (fullUser == null) {
            throw new AuthException("User not found.");
        }

        String role = fullUser.getRole();
        if (role == null) {
            role = "reader";
        }

        if (!permissionService.hasPermission(role, permission.resource(), permission.action())) {
            log.warn("权限不足：user={}, role={}, resource={}, action={}",
                currentUser.getName(), role, permission.resource(), permission.action());
            throw new AuthException("Permission denied.");
        }

        return point.proceed();
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
                log.error("获取 RequirePermission 注解失败：{}", key, e);
                return null;
            }
        });
    }
}
