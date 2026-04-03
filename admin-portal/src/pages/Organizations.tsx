import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import PageHeader from '../components/PageHeader';
import Card from '../components/Card';
import DataTable from '../components/DataTable';
import Button from '../components/Button';
import Modal from '../components/Modal';
import FormField, { inputStyle } from '../components/FormField';
import StatusBadge from '../components/StatusBadge';
import type { Organization } from '../types';

export default function Organizations() {
  const navigate = useNavigate();
  const [orgs, setOrgs] = useState<Organization[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ name: '', note: '' });
  const [submitting, setSubmitting] = useState(false);

  const fetchOrgs = () => {
    setLoading(true);
    api.get<Organization[]>('/organizations')
      .then(setOrgs)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(fetchOrgs, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.post('/organizations', form);
      setShowCreate(false);
      setForm({ name: '', note: '' });
      fetchOrgs();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create');
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    { key: 'name', header: 'Name' },
    { key: 'note', header: 'Note' },
    { key: 'primaryContactName', header: 'Primary Contact' },
    { key: 'primaryContactEmail', header: 'Email' },
    {
      key: 'status', header: 'Status',
      render: (row: Organization) => <StatusBadge active={row.status === 1} />,
    },
    {
      key: 'createdOn', header: 'Created',
      render: (row: Organization) => new Date(row.createdOn).toLocaleDateString(),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Organizations"
        subtitle="Manage partner organizations"
        action={<Button onClick={() => setShowCreate(true)}>+ New Organization</Button>}
      />

      {error && <p style={{ color: '#ef4444', marginBottom: 12 }}>{error}</p>}

      <Card>
        {loading ? (
          <p style={{ textAlign: 'center', color: '#64748b', padding: 40 }}>Loading...</p>
        ) : (
          <DataTable
            columns={columns}
            data={orgs}
            onRowClick={(row) => navigate(`/organizations/${row.rowGuid}`)}
          />
        )}
      </Card>

      <Modal open={showCreate} onClose={() => setShowCreate(false)} title="Create Organization">
        <form onSubmit={handleCreate}>
          <FormField label="Name" required>
            <input
              style={inputStyle}
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              required
              placeholder="Organization name"
            />
          </FormField>
          <FormField label="Note">
            <textarea
              style={{ ...inputStyle, minHeight: 80, resize: 'vertical' }}
              value={form.note}
              onChange={(e) => setForm({ ...form, note: e.target.value })}
              placeholder="Optional description"
            />
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
