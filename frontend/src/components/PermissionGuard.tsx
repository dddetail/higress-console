import React from 'react';
import store from '@/store';

/**
 * Role constants synced with backend PermissionService.
 */
export const ROLES = {
  PLATFORM_ADMIN: 'platform_admin',
  OWNER: 'owner',
  MANAGER: 'manager',
  READER: 'reader',
} as const;

export type Role = (typeof ROLES)[keyof typeof ROLES];

/**
 * Roles that have write (create/update/delete) permission on general resources.
 * Synced with backend PermissionService.hasPermission() logic:
 * - platform_admin: full access
 * - owner/manager: write access
 * - reader: read-only
 */
const WRITE_ROLES: Role[] = [ROLES.PLATFORM_ADMIN, ROLES.OWNER, ROLES.MANAGER];

/**
 * Roles that can access system-level resources.
 */
const SYSTEM_ADMIN_ROLES: Role[] = [ROLES.PLATFORM_ADMIN];

/**
 * Roles that can manage users.
 */
const USER_ADMIN_ROLES: Role[] = [ROLES.PLATFORM_ADMIN, ROLES.OWNER];

interface PermissionGuardProps {
  allowedRoles: string[];
  children: React.ReactNode;
}

const PermissionGuard: React.FC<PermissionGuardProps> = ({ allowedRoles, children }) => {
  const [userState] = store.useModel('user');
  const userRole = userState.currentUser?.role || ROLES.READER;

  if (allowedRoles.includes(userRole)) {
    return <>{children}</>;
  }
  return null;
};

export default PermissionGuard;

export function useUserRole(): Role {
  const [userState] = store.useModel('user');
  return (userState.currentUser?.role as Role) || ROLES.READER;
}

export function useCanWrite(): boolean {
  const role = useUserRole();
  return WRITE_ROLES.includes(role);
}

export function useCanAccessSystem(): boolean {
  const role = useUserRole();
  return SYSTEM_ADMIN_ROLES.includes(role);
}

export function useCanManageUsers(): boolean {
  const role = useUserRole();
  return USER_ADMIN_ROLES.includes(role);
}

export function useHasPermission(resource: string, action: 'read' | 'write'): boolean {
  const role = useUserRole();

  // Synced with backend PermissionService logic
  if (role === ROLES.PLATFORM_ADMIN) return true;
  if (resource === 'system' || resource === 'oauth2_provider') return false;
  if (resource === 'user') return role === ROLES.OWNER;
  if (action === 'read') {
    return [ROLES.OWNER, ROLES.MANAGER, ROLES.READER].includes(role);
  }
  if (action === 'write') {
    return [ROLES.OWNER, ROLES.MANAGER].includes(role);
  }
  return false;
}
