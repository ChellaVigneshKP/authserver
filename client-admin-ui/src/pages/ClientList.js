import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import clientService from '../services/clientService';

function ClientList() {
  const [clients, setClients] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadClients();
  }, []);

  const loadClients = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await clientService.getAllClients();
      setClients(response.data);
    } catch (err) {
      setError('Failed to load clients. Please try again.');
      console.error('Error loading clients:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id, name) => {
    if (!window.confirm(`Are you sure you want to delete client "${name}"?`)) {
      return;
    }

    try {
      await clientService.deleteClient(id);
      setClients(clients.filter(c => c.id !== id));
    } catch (err) {
      alert('Failed to delete client. Please try again.');
      console.error('Error deleting client:', err);
    }
  };

  if (loading) {
    return (
      <div className="loading">
        <div className="spinner"></div>
        <p>Loading clients...</p>
      </div>
    );
  }

  return (
    <div>
      {error && <div className="alert alert-error">{error}</div>}
      
      <div className="card">
        <div className="card-header">
          <h2>OAuth2 Clients</h2>
          <Link to="/clients/create" className="btn btn-primary">
            Create New Client
          </Link>
        </div>

        {clients.length === 0 ? (
          <p style={{ textAlign: 'center', padding: '2rem', color: '#666' }}>
            No clients found. Create your first client to get started.
          </p>
        ) : (
          <div className="table-container">
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Client ID</th>
                  <th>Type</th>
                  <th>Auth Method</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {clients.map((client) => (
                  <tr key={client.id}>
                    <td>
                      <strong>{client.name}</strong>
                      {client.description && (
                        <div style={{ fontSize: '0.875rem', color: '#666', marginTop: '0.25rem' }}>
                          {client.description}
                        </div>
                      )}
                    </td>
                    <td>
                      <code>{client.clientId}</code>
                    </td>
                    <td>{client.applicationType || 'N/A'}</td>
                    <td>{client.authMethod || 'N/A'}</td>
                    <td>
                      {client.active ? (
                        <span className="badge badge-success">Active</span>
                      ) : (
                        <span className="badge badge-danger">Inactive</span>
                      )}
                    </td>
                    <td>
                      <div className="action-buttons">
                        <Link
                          to={`/clients/${client.id}`}
                          className="btn btn-sm btn-primary"
                        >
                          View
                        </Link>
                        <Link
                          to={`/clients/${client.id}/edit`}
                          className="btn btn-sm btn-secondary"
                        >
                          Edit
                        </Link>
                        <button
                          onClick={() => handleDelete(client.id, client.name)}
                          className="btn btn-sm btn-danger"
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

export default ClientList;
