"use client";

import { Box, Typography, Paper, Alert } from "@mui/material";

export default function ResourcesPage() {
  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Resource Library
      </Typography>

      <Paper sx={{ p: 3 }}>
        <Alert severity="info">
          Resource library management - Configure API resources and scopes that applications can access.
        </Alert>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
          This feature allows you to define and manage API resources that can be assigned to applications.
        </Typography>
      </Paper>
    </Box>
  );
}
