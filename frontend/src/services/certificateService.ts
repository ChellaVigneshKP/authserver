import { apiClient } from './apiClient';
import type { Certificate } from '../types';

export const certificateService = {
  // Get all certificates for an organization
  getAll: async (orgGuid: string): Promise<Certificate[]> => {
    return apiClient.get<Certificate[]>(`/organizations/${orgGuid}/certificates`);
  },

  // Get certificate by ID
  getById: async (orgGuid: string, certId: string): Promise<Certificate> => {
    return apiClient.get<Certificate>(`/organizations/${orgGuid}/certificates/${certId}`);
  },

  // Create certificate
  create: async (orgGuid: string, data: FormData): Promise<Certificate> => {
    return apiClient.post<Certificate>(`/organizations/${orgGuid}/certificates`, data, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  // Download certificate
  download: async (orgGuid: string, certId: string): Promise<string> => {
    return apiClient.get<string>(`/organizations/${orgGuid}/certificates/${certId}/download`);
  },

  // Delete certificate
  delete: async (orgGuid: string, certId: string): Promise<void> => {
    return apiClient.delete(`/organizations/${orgGuid}/certificates/${certId}`);
  },
};
