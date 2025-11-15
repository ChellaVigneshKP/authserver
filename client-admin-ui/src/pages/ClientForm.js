import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import clientService from '../services/clientService';

function ClientForm() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEditMode = Boolean(id);

  const [formData, setFormData] = useState({
    name: '',
    description: '',
    applicationType: 'WEB',
    authMethod: 'CLIENT_SECRET_JWT',
    jwkSetUrl: '',
    redirectUris: [],
    postLogoutRedirectUris: [],
    accessTokenTtl: 3600,
    refreshTokenTtl: 86400,
    authCodeTtl: 300,
    deviceCodeTtl: 600,
    reuseRefreshTokens: false,
    maxRequestTransitTime: 300
  });

  const [redirectUri, setRedirectUri] = useState('');
  const [postLogoutUri, setPostLogoutUri] = useState('');
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (isEditMode) {
      loadClient();
    }
  }, [id]);

  const loadClient = async () => {
    try {
      setLoading(true);
      const response = await clientService.getClientById(id);
      const client = response.data;
      
      setFormData({
        name: client.name || '',
        description: client.description || '',
        applicationType: client.applicationType || 'WEB',
        authMethod: client.authMethod || 'CLIENT_SECRET_JWT',
        jwkSetUrl: client.jwkSetUrl || '',
        redirectUris: client.redirectUris || [],
        postLogoutRedirectUris: client.postLogoutRedirectUris || [],
        accessTokenTtl: client.tokenSettings?.accessTokenTtl || 3600,
        refreshTokenTtl: client.tokenSettings?.refreshTokenTtl || 86400,
        authCodeTtl: client.tokenSettings?.authCodeTtl || 300,
        deviceCodeTtl: client.tokenSettings?.deviceCodeTtl || 600,
        reuseRefreshTokens: client.tokenSettings?.reuseRefreshTokens || false,
        maxRequestTransitTime: client.tokenSettings?.maxRequestTransitTime || 300
      });
    } catch (err) {
      alert('Failed to load client details.');
      console.error('Error loading client:', err);
      navigate('/clients');
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
    // Clear error for this field
    if (errors[name]) {
      setErrors(prev => ({ ...prev, [name]: null }));
    }
  };

  const addRedirectUri = () => {
    if (!redirectUri.trim()) return;
    if (!redirectUri.startsWith('http://') && !redirectUri.startsWith('https://')) {
      alert('Redirect URI must start with http:// or https://');
      return;
    }
    setFormData(prev => ({
      ...prev,
      redirectUris: [...prev.redirectUris, redirectUri.trim()]
    }));
    setRedirectUri('');
  };

  const removeRedirectUri = (index) => {
    setFormData(prev => ({
      ...prev,
      redirectUris: prev.redirectUris.filter((_, i) => i !== index)
    }));
  };

  const addPostLogoutUri = () => {
    if (!postLogoutUri.trim()) return;
    if (!postLogoutUri.startsWith('http://') && !postLogoutUri.startsWith('https://')) {
      alert('Post logout URI must start with http:// or https://');
      return;
    }
    setFormData(prev => ({
      ...prev,
      postLogoutRedirectUris: [...prev.postLogoutRedirectUris, postLogoutUri.trim()]
    }));
    setPostLogoutUri('');
  };

  const removePostLogoutUri = (index) => {
    setFormData(prev => ({
      ...prev,
      postLogoutRedirectUris: prev.postLogoutRedirectUris.filter((_, i) => i !== index)
    }));
  };

  const validate = () => {
    const newErrors = {};
    
    if (!formData.name.trim()) {
      newErrors.name = 'Name is required';
    }
    
    if (!formData.applicationType) {
      newErrors.applicationType = 'Application type is required';
    }
    
    if (!formData.authMethod) {
      newErrors.authMethod = 'Auth method is required';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (!validate()) {
      return;
    }

    try {
      setLoading(true);
      
      if (isEditMode) {
        await clientService.updateClient(id, formData);
        alert('Client updated successfully!');
        navigate(`/clients/${id}`);
      } else {
        const response = await clientService.createClient(formData);
        alert('Client created successfully!');
        navigate(`/clients/${response.data.id}`);
      }
    } catch (err) {
      alert(`Failed to ${isEditMode ? 'update' : 'create'} client. Please try again.`);
      console.error('Error saving client:', err);
    } finally {
      setLoading(false);
    }
  };

  if (loading && isEditMode) {
    return (
      <div className="loading">
        <div className="spinner"></div>
        <p>Loading client details...</p>
      </div>
    );
  }

  return (
    <div className="card">
      <div className="card-header">
        <h2>{isEditMode ? 'Edit Client' : 'Create New Client'}</h2>
      </div>

      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label htmlFor="name">Client Name *</label>
          <input
            type="text"
            id="name"
            name="name"
            value={formData.name}
            onChange={handleChange}
            placeholder="Enter client name"
            required
          />
          {errors.name && <div className="form-error">{errors.name}</div>}
        </div>

        <div className="form-group">
          <label htmlFor="description">Description</label>
          <textarea
            id="description"
            name="description"
            value={formData.description}
            onChange={handleChange}
            placeholder="Enter client description"
          />
        </div>

        <div className="form-group">
          <label htmlFor="applicationType">Application Type *</label>
          <select
            id="applicationType"
            name="applicationType"
            value={formData.applicationType}
            onChange={handleChange}
            required
          >
            <option value="WEB">Web Application</option>
            <option value="MOBILE">Mobile Application</option>
            <option value="SERVER">Server Application</option>
          </select>
          {errors.applicationType && <div className="form-error">{errors.applicationType}</div>}
        </div>

        <div className="form-group">
          <label htmlFor="authMethod">Authentication Method *</label>
          <select
            id="authMethod"
            name="authMethod"
            value={formData.authMethod}
            onChange={handleChange}
            required
          >
            <option value="CLIENT_SECRET_JWT">Client Secret JWT</option>
            <option value="PRIVATE_KEY_JWT">Private Key JWT</option>
            <option value="PKCE">PKCE (Public Client)</option>
          </select>
          {errors.authMethod && <div className="form-error">{errors.authMethod}</div>}
        </div>

        <div className="form-group">
          <label htmlFor="jwkSetUrl">JWK Set URL</label>
          <input
            type="text"
            id="jwkSetUrl"
            name="jwkSetUrl"
            value={formData.jwkSetUrl}
            onChange={handleChange}
            placeholder="https://example.com/.well-known/jwks.json"
          />
        </div>

        <div className="form-group">
          <label>Redirect URIs</label>
          <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.5rem' }}>
            <input
              type="text"
              value={redirectUri}
              onChange={(e) => setRedirectUri(e.target.value)}
              placeholder="https://example.com/callback"
              style={{ flex: 1 }}
            />
            <button type="button" onClick={addRedirectUri} className="btn btn-secondary">
              Add
            </button>
          </div>
          {formData.redirectUris.length > 0 && (
            <ul className="uri-list">
              {formData.redirectUris.map((uri, index) => (
                <li key={index}>
                  <code>{uri}</code>
                  <button
                    type="button"
                    onClick={() => removeRedirectUri(index)}
                    className="btn btn-sm btn-danger"
                  >
                    Remove
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="form-group">
          <label>Post Logout Redirect URIs</label>
          <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.5rem' }}>
            <input
              type="text"
              value={postLogoutUri}
              onChange={(e) => setPostLogoutUri(e.target.value)}
              placeholder="https://example.com/logout"
              style={{ flex: 1 }}
            />
            <button type="button" onClick={addPostLogoutUri} className="btn btn-secondary">
              Add
            </button>
          </div>
          {formData.postLogoutRedirectUris.length > 0 && (
            <ul className="uri-list">
              {formData.postLogoutRedirectUris.map((uri, index) => (
                <li key={index}>
                  <code>{uri}</code>
                  <button
                    type="button"
                    onClick={() => removePostLogoutUri(index)}
                    className="btn btn-sm btn-danger"
                  >
                    Remove
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <h3 style={{ marginTop: '2rem', marginBottom: '1rem' }}>Token Settings</h3>

        <div className="form-group">
          <label htmlFor="accessTokenTtl">Access Token TTL (seconds)</label>
          <input
            type="number"
            id="accessTokenTtl"
            name="accessTokenTtl"
            value={formData.accessTokenTtl}
            onChange={handleChange}
            min="1"
          />
        </div>

        <div className="form-group">
          <label htmlFor="refreshTokenTtl">Refresh Token TTL (seconds)</label>
          <input
            type="number"
            id="refreshTokenTtl"
            name="refreshTokenTtl"
            value={formData.refreshTokenTtl}
            onChange={handleChange}
            min="1"
          />
        </div>

        <div className="form-group">
          <label htmlFor="authCodeTtl">Authorization Code TTL (seconds)</label>
          <input
            type="number"
            id="authCodeTtl"
            name="authCodeTtl"
            value={formData.authCodeTtl}
            onChange={handleChange}
            min="1"
          />
        </div>

        <div className="form-group">
          <label htmlFor="deviceCodeTtl">Device Code TTL (seconds)</label>
          <input
            type="number"
            id="deviceCodeTtl"
            name="deviceCodeTtl"
            value={formData.deviceCodeTtl}
            onChange={handleChange}
            min="1"
          />
        </div>

        <div className="form-group">
          <label htmlFor="maxRequestTransitTime">Max Request Transit Time (seconds)</label>
          <input
            type="number"
            id="maxRequestTransitTime"
            name="maxRequestTransitTime"
            value={formData.maxRequestTransitTime}
            onChange={handleChange}
            min="1"
          />
        </div>

        <div className="form-group">
          <label>
            <input
              type="checkbox"
              name="reuseRefreshTokens"
              checked={formData.reuseRefreshTokens}
              onChange={handleChange}
              style={{ width: 'auto', marginRight: '0.5rem' }}
            />
            Reuse Refresh Tokens
          </label>
        </div>

        <div className="form-actions">
          <button type="submit" className="btn btn-primary" disabled={loading}>
            {loading ? 'Saving...' : (isEditMode ? 'Update Client' : 'Create Client')}
          </button>
          <Link to={isEditMode ? `/clients/${id}` : '/clients'} className="btn btn-secondary">
            Cancel
          </Link>
        </div>
      </form>
    </div>
  );
}

export default ClientForm;
