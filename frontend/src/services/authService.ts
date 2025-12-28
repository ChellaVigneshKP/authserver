import { apiClient } from './apiClient';
import type { LoginCredentials } from '../types';

export const authService = {
  // Login
  login: async (credentials: LoginCredentials): Promise<{ token: string }> => {
    const response = await apiClient.post<{ token: string }>('/login', credentials);
    if (response.token) {
      localStorage.setItem('authToken', response.token);
    }
    return response;
  },

  // Logout
  logout: async (): Promise<void> => {
    await apiClient.post('/logout');
    localStorage.removeItem('authToken');
  },

  // Check if user is authenticated
  isAuthenticated: (): boolean => {
    return !!localStorage.getItem('authToken');
  },

  // Get current user
  getCurrentUser: async () => {
    // This would need to be implemented based on your backend
    return apiClient.get('/user/me');
  },
};
