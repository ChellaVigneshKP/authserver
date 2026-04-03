import React from 'react';

interface Props {
  label: string;
  required?: boolean;
  children: React.ReactNode;
}

export default function FormField({ label, required, children }: Props) {
  return (
    <div style={{ marginBottom: 14 }}>
      <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 500, color: '#475569' }}>
        {label}{required && <span style={{ color: '#ef4444' }}> *</span>}
      </label>
      {children}
    </div>
  );
}

export const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '8px 12px',
  border: '1px solid #d1d5db',
  borderRadius: 6,
  fontSize: 14,
  color: '#1e293b',
  boxSizing: 'border-box',
  outline: 'none',
};

export const selectStyle: React.CSSProperties = {
  ...inputStyle,
  backgroundColor: '#fff',
};
