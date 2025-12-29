"use client";

import { useState } from "react";
import {
  Box,
  Typography,
  Button,
  Paper,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import OrganizationsList from "@/components/organizations/OrganizationsList";
import OrganizationDialog from "@/components/organizations/OrganizationDialog";

export default function OrganizationsPage() {
  const [openDialog, setOpenDialog] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  const handleCreate = () => {
    setOpenDialog(true);
  };

  const handleClose = () => {
    setOpenDialog(false);
  };

  const handleSuccess = () => {
    setOpenDialog(false);
    setRefreshKey((prev) => prev + 1);
  };

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4">Organizations</Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={handleCreate}
        >
          Create Organization
        </Button>
      </Box>

      <Paper sx={{ p: 3 }}>
        <OrganizationsList key={refreshKey} />
      </Paper>

      <OrganizationDialog
        open={openDialog}
        onClose={handleClose}
        onSuccess={handleSuccess}
      />
    </Box>
  );
}
