import React from 'react';

interface Props {
  children: React.ReactNode;
  style?: React.CSSProperties;
}

export default function Card({ children, style }: Props) {
  return (
    <div style={{ background: '#fff', borderRadius: 8, border: '1px solid #e2e8f0', padding: 20, ...style }}>
      {children}
    </div>
  );
}
