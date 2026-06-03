import React from 'react';
import store from '@/store';

interface PermissionGuardProps {
  allowedRoles: string[];
  children: React.ReactNode;
}

const PermissionGuard: React.FC<PermissionGuardProps> = ({ allowedRoles, children }) => {
  const [userState] = store.useModel('user');
  const userRole = userState.currentUser?.role || 'reader';

  if (allowedRoles.includes(userRole)) {
    return <>{children}</>;
  }
  return null;
};

export default PermissionGuard;

export function useUserRole(): string {
  const [userState] = store.useModel('user');
  return userState.currentUser?.role || 'reader';
}

export function useCanWrite(): boolean {
  const role = useUserRole();
  return ['platform_admin', 'owner', 'manager'].includes(role);
}
