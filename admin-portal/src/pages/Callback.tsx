import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { exchangeCodeForToken } from '../api/auth';

export default function Callback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState('');

  useEffect(() => {
    const code = searchParams.get('code');
    const errorParam = searchParams.get('error');

    if (errorParam) {
      setError(searchParams.get('error_description') || errorParam);
      return;
    }

    if (!code) {
      setError('No authorization code received');
      return;
    }

    exchangeCodeForToken(code).then((success) => {
      if (success) {
        navigate('/', { replace: true });
      } else {
        setError('Failed to exchange authorization code for token');
      }
    });
  }, [searchParams, navigate]);

  if (error) {
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
          maxWidth: '400px',
          textAlign: 'center',
          boxShadow: '0 4px 6px -1px rgba(0,0,0,0.1)',
        }}>
          <h2 style={{ color: '#dc2626', marginBottom: '16px' }}>Authentication Error</h2>
          <p style={{ color: '#64748b', marginBottom: '24px' }}>{error}</p>
          <button
            onClick={() => navigate('/login')}
            style={{
              padding: '10px 24px',
              background: '#2563eb',
              color: 'white',
              border: 'none',
              borderRadius: '8px',
              cursor: 'pointer',
            }}
          >
            Try Again
          </button>
        </div>
      </div>
    );
  }

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      color: '#64748b',
    }}>
      Completing sign in...
    </div>
  );
}
