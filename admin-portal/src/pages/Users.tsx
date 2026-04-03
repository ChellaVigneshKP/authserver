import { useEffect, useState } from 'react';
import { api } from '../api/client';
import PageHeader from '../components/PageHeader';
import Card from '../components/Card';
import DataTable from '../components/DataTable';
import Button from '../components/Button';
import Modal from '../components/Modal';
import FormField, { inputStyle } from '../components/FormField';
import StatusBadge from '../components/StatusBadge';
import type { User } from '../types';

export default function Users() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({
    userName: '', firstName: '', lastName: '', email: '', phoneNumber: '', password: '',
  });
  const [submitting, setSubmitting] = useState(false);

  const fetchUsers = () => {
    setLoading(true);
    api.get<{ content: User[] }>('/users')
      .then((res) => setUsers(res.content || []))
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(fetchUsers, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.post('/users', form);
      setShowCreate(false);
      setForm({ userName: '', firstName: '', lastName: '', email: '', phoneNumber: '', password: '' });
      fetchUsers();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    { key: 'userName', header: 'Username' },
    { key: 'firstName', header: 'First Name' },
    { key: 'lastName', header: 'Last Name' },
    { key: 'email', header: 'Email' },
    { key: 'phoneNumber', header: 'Phone' },
    {
      key: 'status', header: 'Status',
      render: (row: User) => <StatusBadge active={row.status === 1} />,
    },
    {
      key: 'createdOn', header: 'Created',
      render: (row: User) => row.createdOn ? new Date(row.createdOn).toLocaleDateString() : '',
    },
  ];

  return (
    <div>
      <PageHeader
        title="Users"
        subtitle="Manage user accounts"
        action={<Button onClick={() => setShowCreate(true)}>+ New User</Button>}
      />

      {error && <p style={{ color: '#ef4444', marginBottom: 12 }}>{error}</p>}

      <Card>
        {loading ? (
          <p style={{ textAlign: 'center', color: '#64748b', padding: 40 }}>Loading...</p>
        ) : (
          <DataTable columns={columns} data={users} emptyMessage="No users found." />
        )}
      </Card>

      <Modal open={showCreate} onClose={() => setShowCreate(false)} title="Create User">
        <form onSubmit={handleCreate}>
          <FormField label="Username" required>
            <input style={inputStyle} value={form.userName} onChange={(e) => setForm({ ...form, userName: e.target.value })} required />
          </FormField>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <FormField label="First Name" required>
              <input style={inputStyle} value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} required />
            </FormField>
            <FormField label="Last Name" required>
              <input style={inputStyle} value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} required />
            </FormField>
          </div>
          <FormField label="Email" required>
            <input style={inputStyle} type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
          </FormField>
          <FormField label="Phone Number" required>
            <input style={inputStyle} value={form.phoneNumber} onChange={(e) => setForm({ ...form, phoneNumber: e.target.value })} required placeholder="+1234567890" />
          </FormField>
          <FormField label="Password" required>
            <input style={inputStyle} type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required
              placeholder="Min 12 chars, upper, lower, digit, special" />
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
