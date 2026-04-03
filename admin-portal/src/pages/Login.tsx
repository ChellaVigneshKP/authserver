import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { isAuthenticated, startAuthFlow } from '../api/auth';

export default function Login() {
  const navigate = useNavigate();

  useEffect(() => {
    if (isAuthenticated()) {
      navigate('/', { replace: true });
    }
  }, [navigate]);

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: '#f1f5f9',
    }}>
      <div style={{
        background: 'white',
        borderRadius: '12px',
        padding: '40px',
        width: '100%',
        maxWidth: '400px',
        textAlign: 'center',
        boxShadow: '0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -2px rgba(0,0,0,0.1)',
      }}>
        <h1 style={{
          fontSize: '24px',
          fontWeight: 700,
          marginBottom: '8px',
          color: '#1e293b',
        }}>
          Admin Portal
        </h1>
        <p style={{
          color: '#64748b',
          marginBottom: '32px',
          fontSize: '14px',
        }}>
          Sign in to manage the auth server
        </p>

        <button
          onClick={startAuthFlow}
          style={{
            width: '100%',
            padding: '12px',
            background: '#2563eb',
            color: 'white',
            border: 'none',
            borderRadius: '8px',
            fontSize: '14px',
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          Sign In with Auth Server
        </button>

        <p style={{
          color: '#94a3b8',
          fontSize: '12px',
          marginTop: '24px',
        }}>
          You will be redirected to the auth server login page
        </p>
      </div>
    </div>
  );
}
