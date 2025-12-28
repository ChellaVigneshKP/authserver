import { apiClient } from './apiClient';
import type { Application, ApplicationDetail, TokenSettings, Resource } from '../types';

export const applicationService = {
  // Get all applications for an organization
  getAll: async (orgGuid: string): Promise<Application[]> => {
    return apiClient.get<Application[]>(`/organizations/${orgGuid}/applications`);
  },

  // Get application by ID
  getById: async (orgGuid: string, appGuid: string): Promise<ApplicationDetail> => {
    return apiClient.get<ApplicationDetail>(`/organizations/${orgGuid}/applications/${appGuid}`);
  },

  // Create application
  create: async (orgGuid: string, data: Partial<Application>): Promise<Application> => {
    return apiClient.post<Application>(`/organizations/${orgGuid}/applications`, data);
  },

  // Update application
  update: async (orgGuid: string, appGuid: string, data: Partial<Application>): Promise<Application> => {
    return apiClient.put<Application>(`/organizations/${orgGuid}/applications/${appGuid}`, data);
  },

  // Delete application
  delete: async (orgGuid: string, appGuid: string): Promise<void> => {
    return apiClient.delete(`/organizations/${orgGuid}/applications/${appGuid}`);
  },

  // Get application URLs
  getUrls: async (orgGuid: string, appGuid: string) => {
    return apiClient.get(`/organizations/${orgGuid}/applications/${appGuid}/urls`);
  },

  // Update application URLs
  updateUrls: async (orgGuid: string, appGuid: string, data: { applicationUrl: string; returnUrls: string[]; logoutUrls: string[] }) => {
    return apiClient.put(`/organizations/${orgGuid}/applications/${appGuid}/urls`, data);
  },

  // Update token settings
  updateSettings: async (orgGuid: string, appGuid: string, settings: TokenSettings) => {
    return apiClient.put(`/organizations/${orgGuid}/applications/${appGuid}/settings`, settings);
  },

  // Assign resource to application
  assignResource: async (orgGuid: string, appGuid: string, resourceId: string): Promise<Resource> => {
    return apiClient.post<Resource>(`/organizations/${orgGuid}/applications/${appGuid}/endpoints`, {
      resourceId,
    });
  },

  // Get application resources
  getResources: async (orgGuid: string, appGuid: string): Promise<Resource[]> => {
    return apiClient.get<Resource[]>(`/organizations/${orgGuid}/applications/${appGuid}/endpoints`);
  },

  // Delete application resource
  deleteResource: async (orgGuid: string, appGuid: string, resourceLibraryGuid: string): Promise<void> => {
    return apiClient.delete(`/organizations/${orgGuid}/applications/${appGuid}/endpoints/${resourceLibraryGuid}`);
  },
};
