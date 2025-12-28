import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import { Layout } from './components/common/Layout';
import { Login } from './components/auth/Login';
import { Dashboard } from './pages/Dashboard';
import { Organizations } from './pages/Organizations';
import { Applications } from './pages/Applications';
import { Users } from './pages/Users';
import { Resources } from './pages/Resources';
import { Certificates } from './pages/Certificates';
import { Credentials } from './pages/Credentials';
import { authService } from './services/authService.ts';

const theme = createTheme({
  palette: {
    primary: {
      main: '#1976d2',
    },
    secondary: {
      main: '#dc004e',
    },
  },
  typography: {
    fontFamily: [
      '-apple-system',
      'BlinkMacSystemFont',
      '"Segoe UI"',
      'Roboto',
      '"Helvetica Neue"',
      'Arial',
      'sans-serif',
    ].join(','),
  },
});

interface PrivateRouteProps {
  children: React.ReactNode;
}

const PrivateRoute = ({ children }: PrivateRouteProps) => {
  const isAuthenticated = authService.isAuthenticated();
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" />;
};

function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Router>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route
            path="/*"
            element={
              <PrivateRoute>
                <Layout>
                  <Routes>
                    <Route path="/" element={<Dashboard />} />
                    <Route path="/organizations" element={<Organizations />} />
                    <Route path="/applications" element={<Applications />} />
                    <Route path="/users" element={<Users />} />
                    <Route path="/resources" element={<Resources />} />
                    <Route path="/certificates" element={<Certificates />} />
                    <Route path="/credentials" element={<Credentials />} />
                  </Routes>
                </Layout>
              </PrivateRoute>
            }
          />
        </Routes>
      </Router>
    </ThemeProvider>
  );
}

export default App;
