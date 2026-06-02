import type { ChangePasswordParams, LoginParams, UserInfo } from '@/interfaces/user';
import request from './request';

export async function login(data: LoginParams): Promise<UserInfo> {
  return await request.post('/session/login', data);
}

export async function logout() {
  return await request.get('/session/logout');
}

export async function fetchUserInfo(): Promise<UserInfo> {
  return await request.get('/user/info');
}

export async function changePassword(data: ChangePasswordParams): Promise<any> {
  return await request.post('/user/changePassword', data);
}

export async function listUsers(): Promise<any> {
  return await request.get('/user/list');
}

export async function getUserDetail(username: string): Promise<any> {
  return await request.get(`/user/${username}`);
}

export async function updateUserStatus(username: string, status: string): Promise<any> {
  return await request.put(`/user/${username}/status`, { status });
}

export async function deleteUser(username: string): Promise<any> {
  return await request.delete(`/user/${username}`);
}
