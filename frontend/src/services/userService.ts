import { apiClient } from './apiClient';
import type { User, PaginatedResponse } from '../types';

export const userService = {
  // Get all users with pagination
  getAll: async (params?: {
    resultsPerPage?: number;
    offset?: number;
    type?: string;
    search?: string;
    organizationId?: string;
    status?: string;
  }): Promise<PaginatedResponse<User>> => {
    return apiClient.get<PaginatedResponse<User>>('/users', { params });
  },

  // Get user by ID
  getById: async (userGuid: string): Promise<User> => {
    return apiClient.get<User>(`/users/${userGuid}`);
  },

  // Create user
  create: async (data: Partial<User> & { password: string }): Promise<User> => {
    // Encode password to base64
    const encodedPassword = btoa(data.password);
    return apiClient.post<User>('/users', {
      ...data,
      password: encodedPassword,
    });
  },

  // Update user profile
  updateProfile: async (userGuid: string, data: Partial<User>): Promise<User> => {
    return apiClient.put<User>(`/users/${userGuid}/profile`, data);
  },

  // Update user email
  updateEmail: async (userGuid: string, email: string, branding: string): Promise<void> => {
    return apiClient.put(`/users/${userGuid}/email`, { email, branding });
  },

  // Update user password
  updatePassword: async (userGuid: string, password: string, branding: string): Promise<void> => {
    const encodedPassword = btoa(password);
    return apiClient.put(`/users/${userGuid}/password`, {
      password: encodedPassword,
      branding,
      intent: { id: 'idp' },
    });
  },

  // Update username
  updateUsername: async (userGuid: string, username: string, branding: string): Promise<void> => {
    return apiClient.put(`/users/${userGuid}/username`, { username, branding });
  },

  // Update user security settings
  updateSecuritySettings: async (userGuid: string, settings: unknown): Promise<void> => {
    return apiClient.put(`/users/${userGuid}/security-settings`, settings);
  },

  // Update user metadata
  updateMetadata: async (userGuid: string, metadata: Record<string, unknown>): Promise<void> => {
    return apiClient.put(`/users/${userGuid}/metadata`, metadata);
  },

  // Get user metadata
  getMetadata: async (userGuid: string): Promise<Record<string, unknown>> => {
    return apiClient.get(`/users/${userGuid}/metadata`);
  },

  // Delete user
  delete: async (userGuid: string): Promise<void> => {
    return apiClient.delete(`/users/${userGuid}`);
  },

  // Update user status
  updateStatus: async (userGuid: string, status: number): Promise<void> => {
    return apiClient.put(`/users/${userGuid}/status`, { status });
  },
};
