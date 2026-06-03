export interface UserInfo {
  username: string;
  displayName: string;
  type?: 'user' | 'admin' | 'guest';
  avatarUrl?: string;
  employeeId?: string;
  role?: string; // platform_admin / owner / manager / reader
}

export interface LoginParams {
  username: string;
  password: string;
}

export interface ChangePasswordParams {
  oldPassword: string;
  newPassword: string;
}

export interface Oauth2Provider {
  id: number;
  name: string;
  providerKey: string;
  authorizationUrl: string;
  tokenUrl: string;
  userInfoUrl: string;
  scope: string;
  clientId: string;
  clientSecret: string;
  iconUrl: string;
  enabled: boolean;
  isPreset: boolean;
}

export interface UserListItem {
  name: string;
  displayName: string;
  employeeId?: string;
  type: string;
  status: string;
  role?: string;
}

export interface ConsumerGroup {
  id: number;
  nameCn: string;
  nameEn: string;
  shortName: string;
  description?: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface ConsumerGroupMember {
  id: number;
  groupId: number;
  username: string;
}

export interface ConsumerGroupApiGrant {
  id: number;
  groupId: number;
  resourceType: string;
  resourceName: string;
}
