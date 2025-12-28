import { apiClient } from './apiClient';
import type { Credential } from '../types';

export const credentialService = {
  // Get all credentials for a user
  getAll: async (userId: string): Promise<Credential[]> => {
    return apiClient.get<Credential[]>(`/credentials?userId=${userId}`);
  },

  // Create credential
  create: async (data: Partial<Credential>): Promise<Credential> => {
    return apiClient.post<Credential>('/credentials', data);
  },

  // Update credential
  update: async (credGuid: string, data: Partial<Credential>): Promise<Credential> => {
    return apiClient.put<Credential>(`/credentials/${credGuid}`, data);
  },

  // Delete credential
  delete: async (credGuid: string): Promise<void> => {
    return apiClient.delete(`/credentials/${credGuid}`);
  },
};
