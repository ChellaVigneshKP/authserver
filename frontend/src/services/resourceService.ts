import { apiClient } from './apiClient';
import type { ResourceLibrary } from '../types';

export const resourceService = {
  // Get all resources
  getAll: async (): Promise<ResourceLibrary[]> => {
    return apiClient.get<ResourceLibrary[]>('/resources');
  },

  // Get resource by ID
  getById: async (resourceGuid: string): Promise<ResourceLibrary> => {
    return apiClient.get<ResourceLibrary>(`/resources/${resourceGuid}`);
  },

  // Create resource
  create: async (data: Partial<ResourceLibrary>): Promise<ResourceLibrary> => {
    return apiClient.post<ResourceLibrary>('/resources', data);
  },

  // Update resource
  update: async (resourceGuid: string, data: Partial<ResourceLibrary>): Promise<ResourceLibrary> => {
    return apiClient.put<ResourceLibrary>(`/resources/${resourceGuid}`, data);
  },
};
