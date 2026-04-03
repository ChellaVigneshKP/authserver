import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import PageHeader from '../components/PageHeader';
import Card from '../components/Card';
import DataTable from '../components/DataTable';
import StatusBadge from '../components/StatusBadge';
import type { Application, Credential } from '../types';

interface AppUrls {
  redirectUris: string[];
  postLogoutRedirectUris: string[];
}

export default function ApplicationDetail() {
  const { orgGuid, appGuid } = useParams<{ orgGuid: string; appGuid: string }>();
  const navigate = useNavigate();
  const [app, setApp] = useState<Application | null>(null);
  const [creds, setCreds] = useState<Credential[]>([]);
  const [urls, setUrls] = useState<AppUrls>({ redirectUris: [], postLogoutRedirectUris: [] });
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<'details' | 'creds' | 'urls'>('details');

  useEffect(() => {
    if (!orgGuid || !appGuid) return;
    setLoading(true);
    const base = `/organizations/${orgGuid}/applications/${appGuid}`;
    Promise.allSettled([
      api.get<Application>(base),
      api.get<Credential[]>(`${base}/credentials`),
      api.get<AppUrls>(`${base}/urls`),
    ]).then(([appRes, credsRes, urlsRes]) => {
      if (appRes.status === 'fulfilled') setApp(appRes.value);
      if (credsRes.status === 'fulfilled') setCreds(credsRes.value);
      if (urlsRes.status === 'fulfilled') setUrls(urlsRes.value);
      setLoading(false);
    });
  }, [orgGuid, appGuid]);

  if (loading) return <p style={{ color: '#64748b', padding: 40 }}>Loading...</p>;
  if (!app) return <p style={{ color: '#ef4444' }}>Application not found</p>;

  const credColumns = [
    { key: 'name', header: 'Name' },
    { key: 'authFlow', header: 'Auth Flow' },
    { key: 'credentialStatus', header: 'Status', render: (r: Credential) => <StatusBadge active={r.credentialStatus === 'Active'} label={r.credentialStatus} /> },
    { key: 'tokenAlgorithm', header: 'Algorithm' },
    { key: 'expireOn', header: 'Expires', render: (r: Credential) => r.expireOn ? new Date(r.expireOn).toLocaleDateString() : 'Never' },
  ];

  const detailRows = [
    { label: 'Client ID', value: app.clientId?.trim() },
    { label: 'Application Type', value: app.applicationType },
    { label: 'Auth Flow', value: app.authFlow },
    { label: 'URI', value: app.uri },
    { label: 'Status', value: app.active ? 'Active' : 'Inactive' },
    { label: 'Created', value: app.createdOn ? new Date(app.createdOn).toLocaleString() : '' },
  ];

  const tabs = [
    { key: 'details' as const, label: 'Details' },
    { key: 'creds' as const, label: `Credentials (${creds.length})` },
    { key: 'urls' as const, label: 'Redirect URIs' },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <button onClick={() => navigate(`/organizations/${orgGuid}`)} style={{ background: 'none', border: 'none', color: '#2563eb', cursor: 'pointer', fontSize: 13 }}>
          &larr; Back to Organization
        </button>
      </div>

      <PageHeader
        title={app.name}
        subtitle={app.description || 'No description'}
        action={<StatusBadge active={app.active} />}
      />

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

      {tab === 'details' && (
        <Card>
          <h3 style={{ margin: '0 0 16px', fontSize: 16, color: '#0f172a' }}>Application Details</h3>
          <div style={{ display: 'grid', gridTemplateColumns: '160px 1fr', gap: '10px 16px' }}>
            {detailRows.map((r) => (
              <div key={r.label} style={{ display: 'contents' }}>
                <span style={{ fontSize: 13, color: '#64748b', fontWeight: 500 }}>{r.label}</span>
                <span style={{ fontSize: 14, color: '#1e293b' }}>{r.value}</span>
              </div>
            ))}
          </div>
        </Card>
      )}

      {tab === 'creds' && (
        <Card>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
            <h3 style={{ margin: 0, fontSize: 16, color: '#0f172a' }}>Credentials</h3>
          </div>
          <DataTable columns={credColumns} data={creds} emptyMessage="No credentials configured." />
        </Card>
      )}

      {tab === 'urls' && (
        <Card>
          <h3 style={{ margin: '0 0 16px', fontSize: 16, color: '#0f172a' }}>Redirect URIs</h3>
          <div style={{ marginBottom: 20 }}>
            <p style={{ fontSize: 13, color: '#64748b', fontWeight: 500, marginBottom: 6 }}>Redirect URIs</p>
            {urls.redirectUris?.length > 0 ? (
              <ul style={{ margin: 0, paddingLeft: 20 }}>
                {urls.redirectUris.map((u, i) => <li key={i} style={{ fontSize: 14, color: '#1e293b', marginBottom: 4 }}><code>{u}</code></li>)}
              </ul>
            ) : (
              <p style={{ fontSize: 14, color: '#94a3b8' }}>None configured</p>
            )}
          </div>
          <div>
            <p style={{ fontSize: 13, color: '#64748b', fontWeight: 500, marginBottom: 6 }}>Post-Logout Redirect URIs</p>
            {urls.postLogoutRedirectUris?.length > 0 ? (
              <ul style={{ margin: 0, paddingLeft: 20 }}>
                {urls.postLogoutRedirectUris.map((u, i) => <li key={i} style={{ fontSize: 14, color: '#1e293b', marginBottom: 4 }}><code>{u}</code></li>)}
              </ul>
            ) : (
              <p style={{ fontSize: 14, color: '#94a3b8' }}>None configured</p>
            )}
          </div>
        </Card>
      )}
    </div>
  );
}
