import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import PageHeader from '../components/PageHeader';
import Card from '../components/Card';
import DataTable from '../components/DataTable';
import Button from '../components/Button';
import Modal from '../components/Modal';
import FormField, { inputStyle, selectStyle } from '../components/FormField';
import StatusBadge from '../components/StatusBadge';
import type { Organization, Application, Certificate, OrganizationGroup } from '../types';

export default function OrganizationDetail() {
  const { orgGuid } = useParams<{ orgGuid: string }>();
  const navigate = useNavigate();
  const [org, setOrg] = useState<Organization | null>(null);
  const [apps, setApps] = useState<Application[]>([]);
  const [certs, setCerts] = useState<Certificate[]>([]);
  const [groups, setGroups] = useState<OrganizationGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [tab, setTab] = useState<'apps' | 'certs' | 'groups'>('apps');

  // Create app modal
  const [showCreateApp, setShowCreateApp] = useState(false);
  const [appForm, setAppForm] = useState({
    name: '', description: '', uri: '', applicationType: 'web', authFlow: 'AuthCode',
  });
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!orgGuid) return;
    setLoading(true);
    Promise.allSettled([
      api.get<Organization>(`/organizations/${orgGuid}`),
      api.get<Application[]>(`/organizations/${orgGuid}/applications`),
      api.get<Certificate[]>(`/organizations/${orgGuid}/certificates`),
      api.get<OrganizationGroup[]>(`/organizations/${orgGuid}/groups`),
    ]).then(([orgRes, appsRes, certsRes, groupsRes]) => {
      if (orgRes.status === 'fulfilled') setOrg(orgRes.value);
      if (appsRes.status === 'fulfilled') setApps(appsRes.value);
      if (certsRes.status === 'fulfilled') setCerts(certsRes.value);
      if (groupsRes.status === 'fulfilled') setGroups(groupsRes.value);
      setLoading(false);
    });
  }, [orgGuid]);

  const handleCreateApp = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.post(`/organizations/${orgGuid}/applications`, appForm);
      setShowCreateApp(false);
      setAppForm({ name: '', description: '', uri: '', applicationType: 'web', authFlow: 'AuthCode' });
      const updated = await api.get<Application[]>(`/organizations/${orgGuid}/applications`);
      setApps(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <p style={{ color: '#64748b', padding: 40 }}>Loading...</p>;
  if (!org) return <p style={{ color: '#ef4444' }}>Organization not found</p>;

  const appColumns = [
    { key: 'name', header: 'Name' },
    { key: 'clientId', header: 'Client ID', render: (r: Application) => <code style={{ fontSize: 12 }}>{r.clientId?.trim()}</code> },
    { key: 'applicationType', header: 'Type' },
    { key: 'authFlow', header: 'Auth Flow' },
    { key: 'active', header: 'Status', render: (r: Application) => <StatusBadge active={r.active} /> },
  ];

  const certColumns = [
    { key: 'certificateName', header: 'Name' },
    { key: 'subject', header: 'Subject' },
    { key: 'issuer', header: 'Issuer' },
    { key: 'validFrom', header: 'Valid From', render: (r: Certificate) => r.validFrom ? new Date(r.validFrom).toLocaleDateString() : '' },
    { key: 'validTo', header: 'Valid To', render: (r: Certificate) => r.validTo ? new Date(r.validTo).toLocaleDateString() : '' },
    { key: 'status', header: 'Status', render: (r: Certificate) => <StatusBadge active={r.status === 1} /> },
  ];

  const groupColumns = [
    { key: 'groupName', header: 'Group Name' },
    { key: 'description', header: 'Description' },
  ];

  const tabs = [
    { key: 'apps' as const, label: `Applications (${apps.length})` },
    { key: 'certs' as const, label: `Certificates (${certs.length})` },
    { key: 'groups' as const, label: `Groups (${groups.length})` },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <button onClick={() => navigate('/organizations')} style={{ background: 'none', border: 'none', color: '#2563eb', cursor: 'pointer', fontSize: 13 }}>
          &larr; Back to Organizations
        </button>
      </div>

      <PageHeader title={org.name} subtitle={org.note || 'No description'} />

      {error && <p style={{ color: '#ef4444', marginBottom: 12 }}>{error}</p>}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 16, marginBottom: 24 }}>
        <Card>
          <p style={{ margin: 0, fontSize: 12, color: '#64748b', fontWeight: 500 }}>Primary Contact</p>
          <p style={{ margin: '4px 0 0', fontSize: 14, color: '#1e293b' }}>{org.primaryContactName || 'Not set'}</p>
          <p style={{ margin: '2px 0 0', fontSize: 13, color: '#64748b' }}>{org.primaryContactEmail || ''}</p>
        </Card>
        <Card>
          <p style={{ margin: 0, fontSize: 12, color: '#64748b', fontWeight: 500 }}>Secondary Contact</p>
          <p style={{ margin: '4px 0 0', fontSize: 14, color: '#1e293b' }}>{org.secondaryContactName || 'Not set'}</p>
          <p style={{ margin: '2px 0 0', fontSize: 13, color: '#64748b' }}>{org.secondaryContactEmail || ''}</p>
        </Card>
        <Card>
          <p style={{ margin: 0, fontSize: 12, color: '#64748b', fontWeight: 500 }}>Status</p>
          <div style={{ marginTop: 6 }}><StatusBadge active={org.status === 1} /></div>
        </Card>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 0, borderBottom: '2px solid #e2e8f0', marginBottom: 16 }}>
        {tabs.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            style={{
              padding: '10px 20px', border: 'none', background: 'none', cursor: 'pointer',
              fontSize: 14, fontWeight: 500,
              color: tab === t.key ? '#2563eb' : '#64748b',
              borderBottom: tab === t.key ? '2px solid #2563eb' : '2px solid transparent',
              marginBottom: -2,
            }}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'apps' && (
        <Card>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
            <h3 style={{ margin: 0, fontSize: 16, color: '#0f172a' }}>Applications</h3>
            <Button onClick={() => setShowCreateApp(true)}>+ New Application</Button>
          </div>
          <DataTable
            columns={appColumns}
            data={apps}
            onRowClick={(row) => navigate(`/organizations/${orgGuid}/applications/${row.rowGuid}`)}
            emptyMessage="No applications yet."
          />
        </Card>
      )}

      {tab === 'certs' && (
        <Card>
          <h3 style={{ margin: '0 0 12px', fontSize: 16, color: '#0f172a' }}>Certificates</h3>
          <DataTable columns={certColumns} data={certs} emptyMessage="No certificates uploaded." />
        </Card>
      )}

      {tab === 'groups' && (
        <Card>
          <h3 style={{ margin: '0 0 12px', fontSize: 16, color: '#0f172a' }}>Organization Groups</h3>
          <DataTable columns={groupColumns} data={groups} emptyMessage="No groups." />
        </Card>
      )}

      <Modal open={showCreateApp} onClose={() => setShowCreateApp(false)} title="Create Application">
        <form onSubmit={handleCreateApp}>
          <FormField label="Application Name" required>
            <input style={inputStyle} value={appForm.name} onChange={(e) => setAppForm({ ...appForm, name: e.target.value })} required />
          </FormField>
          <FormField label="Description">
            <textarea style={{ ...inputStyle, minHeight: 60, resize: 'vertical' }} value={appForm.description} onChange={(e) => setAppForm({ ...appForm, description: e.target.value })} />
          </FormField>
          <FormField label="URI" required>
            <input style={inputStyle} value={appForm.uri} onChange={(e) => setAppForm({ ...appForm, uri: e.target.value })} required placeholder="https://..." />
          </FormField>
          <FormField label="Application Type" required>
            <select style={selectStyle} value={appForm.applicationType} onChange={(e) => setAppForm({ ...appForm, applicationType: e.target.value })}>
              <option value="web">Web</option>
              <option value="mobile">Mobile</option>
              <option value="server">Server</option>
            </select>
          </FormField>
          <FormField label="Auth Flow" required>
            <select style={selectStyle} value={appForm.authFlow} onChange={(e) => setAppForm({ ...appForm, authFlow: e.target.value })}>
              <option value="AuthCode">Authorization Code</option>
              <option value="ClientSecretJWT">Client Secret JWT</option>
              <option value="PrivateKeyJWT">Private Key JWT</option>
            </select>
          </FormField>
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 16 }}>
            <Button variant="secondary" type="button" onClick={() => setShowCreateApp(false)}>Cancel</Button>
            <Button type="submit" disabled={submitting}>{submitting ? 'Creating...' : 'Create'}</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
