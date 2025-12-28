"use client";

import { useEffect, useState } from "react";
import {
  Grid,
  Card,
  CardContent,
  Typography,
  Box,
  CircularProgress,
} from "@mui/material";
import PeopleIcon from "@mui/icons-material/People";
import AppsIcon from "@mui/icons-material/Apps";
import BusinessIcon from "@mui/icons-material/Business";

interface DashboardStats {
  totalUsers: number;
  activeUsers: number;
  totalApplications: number;
  activeApplications: number;
  totalOrganizations: number;
  activeOrganizations: number;
}

export default function DashboardPage() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Mock data - in production, fetch from API
    setTimeout(() => {
      setStats({
        totalUsers: 150,
        activeUsers: 142,
        totalApplications: 25,
        activeApplications: 23,
        totalOrganizations: 12,
        activeOrganizations: 11,
      });
      setLoading(false);
    }, 500);
  }, []);

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  const cards = [
    {
      title: "Total Users",
      value: stats?.totalUsers || 0,
      subtitle: `${stats?.activeUsers || 0} active`,
      icon: <PeopleIcon sx={{ fontSize: 40 }} />,
      color: "#1976d2",
    },
    {
      title: "Applications",
      value: stats?.totalApplications || 0,
      subtitle: `${stats?.activeApplications || 0} active`,
      icon: <AppsIcon sx={{ fontSize: 40 }} />,
      color: "#2e7d32",
    },
    {
      title: "Organizations",
      value: stats?.totalOrganizations || 0,
      subtitle: `${stats?.activeOrganizations || 0} active`,
      icon: <BusinessIcon sx={{ fontSize: 40 }} />,
      color: "#ed6c02",
    },
  ];

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Dashboard
      </Typography>
      <Typography variant="body1" color="text.secondary" paragraph>
        Welcome to the AuthServer Admin Portal
      </Typography>

      <Grid container spacing={3} sx={{ mt: 2 }}>
        {cards.map((card, index) => (
          <Grid item xs={12} sm={6} md={4} key={index}>
            <Card>
              <CardContent>
                <Box display="flex" alignItems="center" justifyContent="space-between">
                  <Box>
                    <Typography color="text.secondary" gutterBottom>
                      {card.title}
                    </Typography>
                    <Typography variant="h4">{card.value}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {card.subtitle}
                    </Typography>
                  </Box>
                  <Box sx={{ color: card.color }}>{card.icon}</Box>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Card sx={{ mt: 4 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Quick Actions
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Use the navigation menu to manage applications, users, organizations, resources, and credentials.
          </Typography>
        </CardContent>
      </Card>
    </Box>
  );
}
