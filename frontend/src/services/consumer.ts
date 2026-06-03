import request from './request';
import type { ConsumerDetail, ConsumerCreateRequest } from '@/interfaces/consumer';

export const getConsumers = (): Promise<ConsumerDetail[]> => {
  return request.get<any, ConsumerDetail[]>('/v1/consumers');
};

export const getConsumer = (name: string): Promise<ConsumerDetail> => {
  return request.get<any, ConsumerDetail>(`/v1/consumers/${name}`);
};

export const addConsumer = (payload: ConsumerCreateRequest): Promise<any> => {
  return request.post<any, any>('/v1/consumers', payload);
};

export const updateConsumer = (name: string, payload: ConsumerCreateRequest): Promise<any> => {
  return request.put<any, any>(`/v1/consumers/${name}`, payload);
};

export const deleteConsumer = (name: string): Promise<any> => {
  return request.delete<any, any>(`/v1/consumers/${name}`);
};

// === Member management APIs ===

export const listConsumerMembers = (name: string): Promise<string[]> => {
  return request.get<any, string[]>(`/v1/consumers/${name}/members`);
};

export const addConsumerMembers = (name: string, usernames: string[]): Promise<any> => {
  return request.post<any, any>(`/v1/consumers/${name}/members`, usernames);
};

export const removeConsumerMember = (name: string, username: string): Promise<any> => {
  return request.delete<any, any>(`/v1/consumers/${name}/members/${username}`);
};
