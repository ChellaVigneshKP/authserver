import React from 'react';

interface Props {
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
}

export default function PageHeader({ title, subtitle, action }: Props) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 24 }}>
      <div>
        <h2 style={{ margin: 0, fontSize: 24, color: '#0f172a', fontWeight: 700 }}>{title}</h2>
        {subtitle && <p style={{ margin: '4px 0 0', color: '#64748b', fontSize: 14 }}>{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}
