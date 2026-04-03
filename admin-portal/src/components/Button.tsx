import React from 'react';

interface Props extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger';
}

const colors = {
  primary: { bg: '#2563eb', hover: '#1d4ed8', text: '#fff' },
  secondary: { bg: '#f1f5f9', hover: '#e2e8f0', text: '#334155' },
  danger: { bg: '#ef4444', hover: '#dc2626', text: '#fff' },
};

export default function Button({ variant = 'primary', style, ...props }: Props) {
  const c = colors[variant];
  return (
    <button
      {...props}
      style={{
        padding: '8px 16px',
        borderRadius: 6,
        border: 'none',
        cursor: props.disabled ? 'not-allowed' : 'pointer',
        fontSize: 14,
        fontWeight: 500,
        backgroundColor: c.bg,
        color: c.text,
        opacity: props.disabled ? 0.5 : 1,
        transition: 'background 0.15s',
        ...style,
      }}
      onMouseEnter={(e) => { if (!props.disabled) e.currentTarget.style.backgroundColor = c.hover; }}
      onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = c.bg; }}
    />
  );
}
