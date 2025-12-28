import { apiClient } from './apiClient';
import type { Organization, Contact } from '../types';

export const organizationService = {
  // Get all organizations
  getAll: async (): Promise<Organization[]> => {
    return apiClient.get<Organization[]>('/organizations');
  },

  // Get organization by ID
  getById: async (id: string): Promise<Organization> => {
    return apiClient.get<Organization>(`/organizations/${id}`);
  },

  // Create organization
  create: async (data: Partial<Organization>): Promise<Organization> => {
    return apiClient.post<Organization>('/organizations', data);
  },

  // Update organization
  update: async (id: string, data: Partial<Organization>): Promise<Organization> => {
    return apiClient.put<Organization>(`/organizations/${id}`, data);
  },

  // Update primary contact
  updatePrimaryContact: async (id: string, contact: Contact): Promise<Organization> => {
    return apiClient.put<Organization>(`/organizations/${id}/primary-contact`, contact);
  },

  // Update secondary contact
  updateSecondaryContact: async (id: string, contact: Contact): Promise<Organization> => {
    return apiClient.put<Organization>(`/organizations/${id}/secondary-contact`, contact);
  },

  // Get organization groups
  getGroups: async (orgId: string) => {
    return apiClient.get(`/organizations/${orgId}/groups`);
  },

  // Get group permissions
  getGroupPermissions: async (orgId: string, groupId: string) => {
    return apiClient.get(`/organizations/${orgId}/groups/${groupId}/permissions`);
  },
};
