import type { ConsumerGroup, ConsumerGroupApiGrant, ConsumerGroupMember } from '@/interfaces/user';
import request from './request';

export async function listConsumerGroups(): Promise<Array<ConsumerGroup>> {
  return await request.get('/v1/consumer-groups');
}

export async function getConsumerGroup(id: number): Promise<ConsumerGroup> {
  return await request.get(`/v1/consumer-groups/${id}`);
}

export async function createConsumerGroup(data: Partial<ConsumerGroup>): Promise<ConsumerGroup> {
  return await request.post('/v1/consumer-groups', data);
}

export async function updateConsumerGroup(id: number, data: Partial<ConsumerGroup>): Promise<ConsumerGroup> {
  return await request.put(`/v1/consumer-groups/${id}`, data);
}

export async function deleteConsumerGroup(id: number): Promise<any> {
  return await request.delete(`/v1/consumer-groups/${id}`);
}

export async function listGroupMembers(groupId: number): Promise<Array<ConsumerGroupMember>> {
  return await request.get(`/v1/consumer-groups/${groupId}/members`);
}

export async function addGroupMembers(groupId: number, usernames: Array<string>): Promise<any> {
  return await request.post(`/v1/consumer-groups/${groupId}/members`, usernames);
}

export async function removeGroupMember(groupId: number, username: string): Promise<any> {
  return await request.delete(`/v1/consumer-groups/${groupId}/members/${username}`);
}

export async function listGroupGrants(groupId: number): Promise<Array<ConsumerGroupApiGrant>> {
  return await request.get(`/v1/consumer-groups/${groupId}/grants`);
}

export async function updateGroupGrants(
  groupId: number,
  grants: { resourceType: string; resourceName: string }[],
): Promise<any> {
  return await request.put(`/v1/consumer-groups/${groupId}/grants`, grants);
}
