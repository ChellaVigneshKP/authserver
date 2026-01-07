import axios from 'axios';

const apiClient = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || '/api/proxy',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add request interceptor to include auth token
apiClient.interceptors.request.use(
  async (config) => {
    // Token will be added by the server-side or client-side session
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Add response interceptor for error handling
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Handle unauthorized - redirect to login
      window.location.href = '/auth/signin';
    }
    return Promise.reject(error);
  }
);

export default apiClient;

// API Helper functions
export const authServerApi = {
  // Organizations
  getOrganizations: () => apiClient.get('/v1/organizations'),
  getOrganization: (guid: string) => apiClient.get(`/v1/organizations/${guid}`),
  createOrganization: (data: any) => apiClient.post('/v1/organizations', data),
  updateOrganization: (guid: string, data: any) => apiClient.put(`/v1/organizations/${guid}`, data),
  
  // Applications (Clients)
  getApplications: (orgId?: number) => 
    apiClient.get('/v1/applications', { params: { orgId } }),
  getApplication: (guid: string) => apiClient.get(`/v1/applications/${guid}`),
  createApplication: (data: any) => apiClient.post('/v1/applications', data),
  updateApplication: (guid: string, data: any) => apiClient.put(`/v1/applications/${guid}`, data),
  deleteApplication: (guid: string) => apiClient.delete(`/v1/applications/${guid}`),
  
  // Users
  getUsers: (params?: any) => apiClient.get('/v1/users', { params }),
  getUser: (guid: string) => apiClient.get(`/v1/users/${guid}`),
  createUser: (data: any) => apiClient.post('/v1/users', data),
  updateUser: (guid: string, data: any) => apiClient.put(`/v1/users/${guid}`, data),
  
  // Credentials
  createCredential: (appGuid: string, data: any) => 
    apiClient.post(`/v1/applications/${appGuid}/secrets`, data),
  getCredentials: (appGuid: string) => 
    apiClient.get(`/v1/applications/${appGuid}/secrets`),
};
