import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import PageHeader from '../components/PageHeader';
import Card from '../components/Card';
import type { Organization, User, ResourceEndpoint } from '../types';

export default function Dashboard() {
  const navigate = useNavigate();
  const [stats, setStats] = useState({ orgs: 0, users: 0, endpoints: 0 });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.allSettled([
      api.get<Organization[]>('/organizations'),
      api.get<{ content: User[] }>('/users'),
      api.get<ResourceEndpoint[]>('/endpoints'),
    ]).then(([orgs, users, endpoints]) => {
      setStats({
        orgs: orgs.status === 'fulfilled' ? orgs.value.length : 0,
        users: users.status === 'fulfilled' ? users.value.content?.length ?? 0 : 0,
        endpoints: endpoints.status === 'fulfilled' ? endpoints.value.length : 0,
      });
      setLoading(false);
    });
  }, []);

  const cards = [
    { label: 'Organizations', count: stats.orgs, path: '/organizations', color: '#2563eb' },
    { label: 'Users', count: stats.users, path: '/users', color: '#7c3aed' },
    { label: 'Resource Endpoints', count: stats.endpoints, path: '/endpoints', color: '#059669' },
  ];

  return (
    <div>
      <PageHeader title="Dashboard" subtitle="Auth Server Admin Portal overview" />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16 }}>
        {cards.map((c) => (
          <Card key={c.label} style={{ cursor: 'pointer' }}>
            <div onClick={() => navigate(c.path)}>
              <p style={{ margin: 0, fontSize: 13, color: '#64748b', fontWeight: 500 }}>{c.label}</p>
              <p style={{ margin: '8px 0 0', fontSize: 32, fontWeight: 700, color: c.color }}>
                {loading ? '...' : c.count}
              </p>
            </div>
          </Card>
        ))}
      </div>

      <Card style={{ marginTop: 32 }}>
        <h3 style={{ margin: '0 0 12px', fontSize: 16, color: '#0f172a' }}>Quick Links</h3>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          {[
            { label: 'Manage Organizations', path: '/organizations' },
            { label: 'Manage Users', path: '/users' },
            { label: 'API Endpoints', path: '/endpoints' },
          ].map((link) => (
            <button
              key={link.path}
              onClick={() => navigate(link.path)}
              style={{
                padding: '8px 16px', borderRadius: 6, border: '1px solid #e2e8f0',
                background: '#fff', cursor: 'pointer', fontSize: 13, color: '#2563eb',
                fontWeight: 500,
              }}
            >
              {link.label}
            </button>
          ))}
        </div>
      </Card>
    </div>
  );
}
