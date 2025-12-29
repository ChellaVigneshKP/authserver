"use client";

import { Box, Typography, Paper, Alert } from "@mui/material";

export default function CredentialsPage() {
  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Certificates & Credentials
      </Typography>

      <Paper sx={{ p: 3 }}>
        <Alert severity="info">
          Certificate and credential management - Manage signing keys, certificates, and client secrets.
        </Alert>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
          This feature allows you to manage certificates for token signing, client authentication credentials, and other security-related keys.
        </Typography>
      </Paper>
    </Box>
  );
}
