import { useEffect, useState } from 'react';
import { api } from '../api/client';
import PageHeader from '../components/PageHeader';
import Card from '../components/Card';
import DataTable from '../components/DataTable';
import Button from '../components/Button';
import Modal from '../components/Modal';
import FormField, { inputStyle, selectStyle } from '../components/FormField';
import StatusBadge from '../components/StatusBadge';
import type { ResourceEndpoint } from '../types';

export default function ResourceEndpoints() {
  const [endpoints, setEndpoints] = useState<ResourceEndpoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({
    name: '', description: '', uri: '', allowedMethod: 'GET', urn: '',
  });
  const [submitting, setSubmitting] = useState(false);

  const fetchEndpoints = () => {
    setLoading(true);
    api.get<ResourceEndpoint[]>('/endpoints')
      .then(setEndpoints)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(fetchEndpoints, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.post('/endpoints', form);
      setShowCreate(false);
      setForm({ name: '', description: '', uri: '', allowedMethod: 'GET', urn: '' });
      fetchEndpoints();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    { key: 'name', header: 'Name' },
    { key: 'uri', header: 'URI' },
    { key: 'allowedMethod', header: 'Method', render: (r: ResourceEndpoint) => (
      <span style={{ padding: '2px 8px', borderRadius: 4, fontSize: 12, fontWeight: 600,
        backgroundColor: methodColor(r.allowedMethod), color: '#fff' }}>
        {r.allowedMethod}
      </span>
    )},
    { key: 'urn', header: 'URN' },
    { key: 'description', header: 'Description' },
    { key: 'status', header: 'Status', render: (r: ResourceEndpoint) => <StatusBadge active={r.status === 1} /> },
  ];

  return (
    <div>
      <PageHeader
        title="Resource Endpoints"
        subtitle="API resource endpoint library"
        action={<Button onClick={() => setShowCreate(true)}>+ New Endpoint</Button>}
      />

      {error && <p style={{ color: '#ef4444', marginBottom: 12 }}>{error}</p>}

      <Card>
        {loading ? (
          <p style={{ textAlign: 'center', color: '#64748b', padding: 40 }}>Loading...</p>
        ) : (
          <DataTable columns={columns} data={endpoints} emptyMessage="No endpoints defined." />
        )}
      </Card>

      <Modal open={showCreate} onClose={() => setShowCreate(false)} title="Create Resource Endpoint">
        <form onSubmit={handleCreate}>
          <FormField label="Name" required>
            <input style={inputStyle} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          </FormField>
          <FormField label="Description">
            <textarea style={{ ...inputStyle, minHeight: 60, resize: 'vertical' }} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </FormField>
          <FormField label="URI" required>
            <input style={inputStyle} value={form.uri} onChange={(e) => setForm({ ...form, uri: e.target.value })} required placeholder="/api/v1/resource" />
          </FormField>
          <FormField label="Allowed Method" required>
            <select style={selectStyle} value={form.allowedMethod} onChange={(e) => setForm({ ...form, allowedMethod: e.target.value })}>
              <option value="GET">GET</option>
              <option value="POST">POST</option>
              <option value="PUT">PUT</option>
              <option value="DELETE">DELETE</option>
              <option value="PATCH">PATCH</option>
            </select>
          </FormField>
          <FormField label="URN">
            <input style={inputStyle} value={form.urn} onChange={(e) => setForm({ ...form, urn: e.target.value })} placeholder="urn:namespace:resource" />
          </FormField>
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 16 }}>
            <Button variant="secondary" type="button" onClick={() => setShowCreate(false)}>Cancel</Button>
            <Button type="submit" disabled={submitting}>{submitting ? 'Creating...' : 'Create'}</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

function methodColor(method: string): string {
  switch (method?.toUpperCase()) {
    case 'GET': return '#22c55e';
    case 'POST': return '#3b82f6';
    case 'PUT': return '#f59e0b';
    case 'DELETE': return '#ef4444';
    case 'PATCH': return '#8b5cf6';
    default: return '#64748b';
  }
}
