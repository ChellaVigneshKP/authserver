"use client";

import { useState } from "react";
import {
  Box,
  Typography,
  Button,
  Paper,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import UsersList from "@/components/users/UsersList";
import UserDialog from "@/components/users/UserDialog";

export default function UsersPage() {
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
        <Typography variant="h4">Users</Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={handleCreate}
        >
          Create User
        </Button>
      </Box>

      <Paper sx={{ p: 3 }}>
        <UsersList key={refreshKey} />
      </Paper>

      <UserDialog
        open={openDialog}
        onClose={handleClose}
        onSuccess={handleSuccess}
      />
    </Box>
  );
}
