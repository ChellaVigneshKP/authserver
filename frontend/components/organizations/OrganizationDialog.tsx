"use client";

import { useState } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Box,
  Alert,
} from "@mui/material";
import { createClientApiClient } from "@/lib/api/client";

interface OrganizationDialogProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export default function OrganizationDialog({
  open,
  onClose,
  onSuccess,
}: OrganizationDialogProps) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async () => {
    if (!name) {
      setError("Organization name is required");
      return;
    }

    setLoading(true);
    setError("");

    try {
      const client = createClientApiClient();
      await client.post("/api/v1/organizations", {
        name,
        description: description || undefined,
      });
      onSuccess();
      resetForm();
    } catch (err: any) {
      setError(err.response?.data?.message || "Failed to create organization");
    } finally {
      setLoading(false);
    }
  };

  const resetForm = () => {
    setName("");
    setDescription("");
    setError("");
  };

  const handleClose = () => {
    resetForm();
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>Create New Organization</DialogTitle>
      <DialogContent>
        <Box sx={{ pt: 2, display: "flex", flexDirection: "column", gap: 2 }}>
          {error && <Alert severity="error">{error}</Alert>}

          <TextField
            label="Organization Name"
            fullWidth
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
          />

          <TextField
            label="Description"
            fullWidth
            multiline
            rows={3}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Cancel</Button>
        <Button onClick={handleSubmit} variant="contained" disabled={loading}>
          {loading ? "Creating..." : "Create"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
