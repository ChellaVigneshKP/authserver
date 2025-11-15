import axios from 'axios';

const API_BASE_URL = '/api/clients';
const DEFAULT_ORG_ID = 1;

class ClientService {
  
  getAllClients(orgId = DEFAULT_ORG_ID) {
    return axios.get(`${API_BASE_URL}?orgId=${orgId}`);
  }
  
  getClientById(id) {
    return axios.get(`${API_BASE_URL}/${id}`);
  }
  
  createClient(clientData, orgId = DEFAULT_ORG_ID) {
    return axios.post(`${API_BASE_URL}?orgId=${orgId}`, clientData);
  }
  
  updateClient(id, clientData, orgId = DEFAULT_ORG_ID) {
    return axios.put(`${API_BASE_URL}/${id}?orgId=${orgId}`, clientData);
  }
  
  deleteClient(id, orgId = DEFAULT_ORG_ID) {
    return axios.delete(`${API_BASE_URL}/${id}?orgId=${orgId}`);
  }
  
  rotateSecret(id, orgId = DEFAULT_ORG_ID) {
    return axios.post(`${API_BASE_URL}/${id}/rotate-secret?orgId=${orgId}`);
  }
  
  getRedirectUris(id) {
    return axios.get(`${API_BASE_URL}/${id}/redirect-uris`);
  }
  
  addRedirectUri(id, uri, orgId = DEFAULT_ORG_ID) {
    return axios.post(`${API_BASE_URL}/${id}/redirect-uris?orgId=${orgId}&uri=${encodeURIComponent(uri)}`);
  }
  
  deleteRedirectUri(id, uri, orgId = DEFAULT_ORG_ID) {
    return axios.delete(`${API_BASE_URL}/${id}/redirect-uris?orgId=${orgId}&uri=${encodeURIComponent(uri)}`);
  }
}

export default new ClientService();
