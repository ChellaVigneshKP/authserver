import { useEffect, useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  Grid,
  Typography,
  Paper,
} from '@mui/material';
import {
  Business as BusinessIcon,
  Apps as AppsIcon,
  People as PeopleIcon,
  VpnKey as VpnKeyIcon,
} from '@mui/icons-material';
import { organizationService } from '../../services/organizationService.ts';
import { userService } from '../../services/userService.ts';
import { Loading } from '../common/Loading.tsx';
import { ErrorDisplay } from '../common/ErrorDisplay.tsx';

interface StatsCard {
  title: string;
  value: number;
  icon: React.ReactNode;
  color: string;
}

export const Dashboard = () => {
  const [stats, setStats] = useState<StatsCard[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>('');

  useEffect(() => {
    loadDashboardData();
  }, []);

  const loadDashboardData = async () => {
    try {
      setLoading(true);
      
      // Load statistics
      const [orgs, users] = await Promise.all([
        organizationService.getAll().catch(() => []),
        userService.getAll({ resultsPerPage: 1, offset: 0 }).catch(() => ({ data: [], totalResults: 0, offset: 0, resultsPerPage: 0 })),
      ]);

      const dashboardStats: StatsCard[] = [
        {
          title: 'Organizations',
          value: orgs.length,
          icon: <BusinessIcon sx={{ fontSize: 40 }} />,
          color: '#1976d2',
        },
        {
          title: 'Applications',
          value: 0, // Will be updated when we fetch applications
          icon: <AppsIcon sx={{ fontSize: 40 }} />,
          color: '#2e7d32',
        },
        {
          title: 'Users',
          value: users.totalResults || 0,
          icon: <PeopleIcon sx={{ fontSize: 40 }} />,
          color: '#ed6c02',
        },
        {
          title: 'Active Sessions',
          value: 0, // Placeholder
          icon: <VpnKeyIcon sx={{ fontSize: 40 }} />,
          color: '#9c27b0',
        },
      ];

      setStats(dashboardStats);
    } catch (err) {
      setError('Failed to load dashboard data');
      console.error('Dashboard error:', err);
    } finally {
      setLoading(false);
    }
  };

  if (loading) return <Loading message="Loading dashboard..." />;
  if (error) return <ErrorDisplay error={error} />;

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Dashboard
      </Typography>
      <Typography variant="body1" color="text.secondary" paragraph>
        Welcome to the Auth Server Management Console
      </Typography>

      <Grid container spacing={3} sx={{ mt: 2 }}>
        {stats.map((stat) => (
          <Grid item xs={12} sm={6} md={3} key={stat.title}>
            <Card>
              <CardContent>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Box>
                    <Typography color="text.secondary" gutterBottom variant="body2">
                      {stat.title}
                    </Typography>
                    <Typography variant="h3" component="div">
                      {stat.value}
                    </Typography>
                  </Box>
                  <Box sx={{ color: stat.color }}>
                    {stat.icon}
                  </Box>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Paper sx={{ mt: 4, p: 3 }}>
        <Typography variant="h6" gutterBottom>
          Quick Actions
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Use the navigation menu on the left to manage organizations, applications, users, and more.
        </Typography>
      </Paper>
    </Box>
  );
};
