package com.alibaba.higress.console.service;

import org.springframework.stereotype.Service;

/**
 * 根据 RBAC 权限矩阵判断用户角色是否有权执行指定操作。
 */
@Service
public class PermissionService {

    public boolean hasPermission(String role, String resource, String action) {
        if (role == null) {
            return false;
        }

        // 管理员拥有全部权限
        if ("platform_admin".equals(role)) {
            return true;
        }

        // 系统管理类资源仅管理员可访问
        if (isSystemResource(resource)) {
            return false;
        }

        // 用户管理仅 Owner 可访问
        if ("user".equals(resource)) {
            return "owner".equals(role);
        }

        // 其他资源：owner/manager/reader 可读，owner/manager 可写
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
