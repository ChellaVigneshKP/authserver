import React, { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import clientService from '../services/clientService';

function ClientDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [client, setClient] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadClient();
  }, [id]);

  const loadClient = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await clientService.getClientById(id);
      setClient(response.data);
    } catch (err) {
      setError('Failed to load client details.');
      console.error('Error loading client:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!window.confirm(`Are you sure you want to delete client "${client.name}"?`)) {
      return;
    }

    try {
      await clientService.deleteClient(id);
      navigate('/clients');
    } catch (err) {
      alert('Failed to delete client. Please try again.');
      console.error('Error deleting client:', err);
    }
  };

  const handleRotateSecret = async () => {
    if (!window.confirm('Are you sure you want to rotate the client secret?')) {
      return;
    }

    try {
      const response = await clientService.rotateSecret(id);
      alert(`Secret rotated: ${response.data.message}`);
      if (response.data.newSecret) {
        alert(`New secret: ${response.data.newSecret}\n\nPlease save this secret - you won't be able to see it again!`);
      }
    } catch (err) {
      alert('Failed to rotate secret. Please try again.');
      console.error('Error rotating secret:', err);
    }
  };

  if (loading) {
    return (
      <div className="loading">
        <div className="spinner"></div>
        <p>Loading client details...</p>
      </div>
    );
  }

  if (error || !client) {
    return (
      <div className="card">
        <div className="alert alert-error">{error || 'Client not found'}</div>
        <Link to="/clients" className="btn btn-secondary">
          Back to Clients
        </Link>
      </div>
    );
  }

  return (
    <div>
      <div className="card">
        <div className="card-header">
          <h2>Client Details</h2>
          <div className="action-buttons">
            <Link to={`/clients/${id}/edit`} className="btn btn-primary">
              Edit Client
            </Link>
            <button onClick={handleRotateSecret} className="btn btn-secondary">
              Rotate Secret
            </button>
            <button onClick={handleDelete} className="btn btn-danger">
              Delete Client
            </button>
            <Link to="/clients" className="btn btn-secondary">
              Back to List
            </Link>
          </div>
        </div>

        <div className="detail-row">
          <div className="detail-label">Name:</div>
          <div className="detail-value"><strong>{client.name}</strong></div>
        </div>

        <div className="detail-row">
          <div className="detail-label">Description:</div>
          <div className="detail-value">{client.description || 'N/A'}</div>
        </div>

        <div className="detail-row">
          <div className="detail-label">Client ID:</div>
          <div className="detail-value"><code>{client.clientId}</code></div>
        </div>

        <div className="detail-row">
          <div className="detail-label">Application Type:</div>
          <div className="detail-value">{client.applicationType?.name || 'N/A'}</div>
        </div>

        <div className="detail-row">
          <div className="detail-label">Auth Method:</div>
          <div className="detail-value">{client.authMethod?.name || 'N/A'}</div>
        </div>

        <div className="detail-row">
          <div className="detail-label">Status:</div>
          <div className="detail-value">
            {client.active ? (
              <span className="badge badge-success">Active</span>
            ) : (
              <span className="badge badge-danger">Inactive</span>
            )}
          </div>
        </div>

        {client.uri && (
          <div className="detail-row">
            <div className="detail-label">URI:</div>
            <div className="detail-value"><code>{client.uri}</code></div>
          </div>
        )}

        <div className="detail-row">
          <div className="detail-label">Redirect URIs:</div>
          <div className="detail-value">
            {client.redirectUris && client.redirectUris.length > 0 ? (
              <ul className="uri-list">
                {client.redirectUris.map((uri, index) => (
                  <li key={index}>
                    <code>{uri}</code>
                  </li>
                ))}
              </ul>
            ) : (
              <span>None configured</span>
            )}
          </div>
        </div>

        <div className="detail-row">
          <div className="detail-label">Post Logout Redirect URIs:</div>
          <div className="detail-value">
            {client.postLogoutRedirectUris && client.postLogoutRedirectUris.length > 0 ? (
              <ul className="uri-list">
                {client.postLogoutRedirectUris.map((uri, index) => (
                  <li key={index}>
                    <code>{uri}</code>
                  </li>
                ))}
              </ul>
            ) : (
              <span>None configured</span>
            )}
          </div>
        </div>

        {client.tokenSettings && (
          <>
            <h3 style={{ marginTop: '2rem', marginBottom: '1rem' }}>Token Settings</h3>
            
            <div className="detail-row">
              <div className="detail-label">Access Token TTL:</div>
              <div className="detail-value">{client.tokenSettings.accessTokenTtl} seconds</div>
            </div>

            <div className="detail-row">
              <div className="detail-label">Refresh Token TTL:</div>
              <div className="detail-value">{client.tokenSettings.refreshTokenTtl} seconds</div>
            </div>

            <div className="detail-row">
              <div className="detail-label">Auth Code TTL:</div>
              <div className="detail-value">{client.tokenSettings.authCodeTtl} seconds</div>
            </div>

            <div className="detail-row">
              <div className="detail-label">Device Code TTL:</div>
              <div className="detail-value">{client.tokenSettings.deviceCodeTtl} seconds</div>
            </div>

            <div className="detail-row">
              <div className="detail-label">Reuse Refresh Tokens:</div>
              <div className="detail-value">
                {client.tokenSettings.reuseRefreshTokens ? 'Yes' : 'No'}
              </div>
            </div>

            <div className="detail-row">
              <div className="detail-label">Max Request Transit Time:</div>
              <div className="detail-value">{client.tokenSettings.maxRequestTransitTime} seconds</div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

export default ClientDetail;
