import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { createContext, useContext, useCallback, useSyncExternalStore, type ReactNode } from 'react';
import { isAuthenticated, clearToken } from './api/auth';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import Organizations from './pages/Organizations';
import OrganizationDetail from './pages/OrganizationDetail';
import ApplicationDetail from './pages/ApplicationDetail';
import Users from './pages/Users';
import ResourceEndpoints from './pages/ResourceEndpoints';
import Login from './pages/Login';
import Callback from './pages/Callback';

interface AuthState {
  authenticated: boolean;
  logout: () => void;
}

const AuthContext = createContext<AuthState>({
  authenticated: false,
  logout: () => {},
});

export function useAuth() {
  return useContext(AuthContext);
}

// Simple external store for auth state that re-renders on changes
let authListeners: Array<() => void> = [];
function subscribeAuth(cb: () => void) {
  authListeners.push(cb);
  return () => { authListeners = authListeners.filter((l) => l !== cb); };
}
function notifyAuth() {
  authListeners.forEach((cb) => cb());
}

function AuthProvider({ children }: { children: ReactNode }) {
  const authenticated = useSyncExternalStore(subscribeAuth, isAuthenticated);

  const logout = useCallback(() => {
    clearToken();
    notifyAuth();
  }, []);

  return (
    <AuthContext.Provider value={{ authenticated, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { authenticated } = useAuth();
  const location = useLocation();

  if (!authenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/callback" element={<Callback />} />
          <Route element={<RequireAuth><Layout /></RequireAuth>}>
            <Route path="/" element={<Dashboard />} />
            <Route path="/organizations" element={<Organizations />} />
            <Route path="/organizations/:orgGuid" element={<OrganizationDetail />} />
            <Route path="/organizations/:orgGuid/applications/:appGuid" element={<ApplicationDetail />} />
            <Route path="/users" element={<Users />} />
            <Route path="/endpoints" element={<ResourceEndpoints />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
