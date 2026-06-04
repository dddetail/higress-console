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
  name: string;
  providerKey: string;
  iconUrl: string;
}

export interface Oauth2ProviderDetail {
  id: number;
  name: string;
  providerKey: string;
  authorizationUrl: string;
  tokenUrl: string;
  userInfoUrl: string;
  scope: string;
  clientId: string;
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
