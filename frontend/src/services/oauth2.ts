import type { Oauth2Provider, Oauth2ProviderDetail } from '@/interfaces/user';
import request from './request';

export async function getEnabledProviders(): Promise<Oauth2Provider[]> {
  const data = await request.get('/oauth2/providers');
  return data || [];
}

export async function listProviders(): Promise<Oauth2ProviderDetail[]> {
  return await request.get('/v1/oauth2-providers');
}

export async function addProvider(data: Partial<Oauth2ProviderDetail>): Promise<Oauth2ProviderDetail> {
  return await request.post('/v1/oauth2-providers', data);
}

export async function updateProvider(id: number, data: Partial<Oauth2ProviderDetail>): Promise<Oauth2ProviderDetail> {
  return await request.put(`/v1/oauth2-providers/${id}`, data);
}

export async function deleteProvider(id: number): Promise<any> {
  return await request.delete(`/v1/oauth2-providers/${id}`);
}

export async function getSsoStatus(): Promise<boolean> {
  return await request.get('/v1/oauth2-providers/sso-status');
}

export async function setSsoStatus(enabled: boolean): Promise<any> {
  return await request.put('/v1/oauth2-providers/sso-status', enabled);
}
