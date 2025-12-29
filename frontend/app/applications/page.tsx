"use client";

import { useState } from "react";
import {
  Box,
  Typography,
  Button,
  Paper,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import ApplicationsList from "@/components/applications/ApplicationsList";
import ApplicationDialog from "@/components/applications/ApplicationDialog";

export default function ApplicationsPage() {
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
        <Typography variant="h4">Applications</Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={handleCreate}
        >
          Create Application
        </Button>
      </Box>

      <Paper sx={{ p: 3 }}>
        <ApplicationsList key={refreshKey} />
      </Paper>

      <ApplicationDialog
        open={openDialog}
        onClose={handleClose}
        onSuccess={handleSuccess}
      />
    </Box>
  );
}
