import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../App';

const navItems = [
  { to: '/', label: 'Dashboard' },
  { to: '/organizations', label: 'Organizations' },
  { to: '/users', label: 'Users' },
  { to: '/endpoints', label: 'Resource Endpoints' },
];

export default function Layout() {
  const { logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      <aside style={sidebarStyle}>
        <div style={{ padding: '24px 20px', borderBottom: '1px solid #334155' }}>
          <h1 style={{ margin: 0, fontSize: '18px', color: '#f8fafc', letterSpacing: '-0.025em' }}>
            Auth Server
          </h1>
          <p style={{ margin: '4px 0 0', fontSize: '12px', color: '#94a3b8' }}>Admin Portal</p>
        </div>
        <nav style={{ padding: '12px 0', flex: 1 }}>
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              style={({ isActive }) => ({
                ...navLinkStyle,
                backgroundColor: isActive ? '#334155' : 'transparent',
                color: isActive ? '#f8fafc' : '#cbd5e1',
              })}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div style={{ padding: '12px 20px', borderTop: '1px solid #334155' }}>
          <button
            onClick={handleLogout}
            style={{
              width: '100%',
              padding: '8px',
              background: 'transparent',
              border: '1px solid #475569',
              borderRadius: '6px',
              color: '#cbd5e1',
              fontSize: '13px',
              cursor: 'pointer',
            }}
          >
            Sign Out
          </button>
        </div>
      </aside>
      <main style={mainStyle}>
        <Outlet />
      </main>
    </div>
  );
}

const sidebarStyle: React.CSSProperties = {
  width: 240,
  background: '#1e293b',
  color: '#f8fafc',
  flexShrink: 0,
  display: 'flex',
  flexDirection: 'column',
};

const navLinkStyle: React.CSSProperties = {
  display: 'block',
  padding: '10px 20px',
  textDecoration: 'none',
  fontSize: '14px',
  transition: 'background 0.15s',
};

const mainStyle: React.CSSProperties = {
  flex: 1,
  padding: '32px',
  backgroundColor: '#f8fafc',
  overflow: 'auto',
};
