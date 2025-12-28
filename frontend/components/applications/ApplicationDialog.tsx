"use client";

import { useState, useEffect } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Box,
  FormControlLabel,
  Checkbox,
  Alert,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
} from "@mui/material";
import { createClientApiClient } from "@/lib/api/client";

interface ApplicationDialogProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export default function ApplicationDialog({
  open,
  onClose,
  onSuccess,
}: ApplicationDialogProps) {
  const [clientName, setClientName] = useState("");
  const [applicationUrl, setApplicationUrl] = useState("");
  const [requireConsent, setRequireConsent] = useState(false);
  const [requireProofKey, setRequireProofKey] = useState(false);
  const [organizations, setOrganizations] = useState<any[]>([]);
  const [selectedOrg, setSelectedOrg] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (open) {
      fetchOrganizations();
    }
  }, [open]);

  const fetchOrganizations = async () => {
    try {
      const client = createClientApiClient();
      const response = await client.get("/api/v1/organizations");
      setOrganizations(response.data || []);
      if (response.data && response.data.length > 0) {
        setSelectedOrg(response.data[0].rowGuid);
      }
    } catch (err: any) {
      setError("Failed to load organizations");
    }
  };

  const handleSubmit = async () => {
    if (!clientName || !applicationUrl || !selectedOrg) {
      setError("Please fill in all required fields");
      return;
    }

    setLoading(true);
    setError("");

    try {
      const client = createClientApiClient();
      await client.post(`/api/v1/organizations/${selectedOrg}/applications`, {
        clientName,
        applicationUrl,
        requireAuthorizationConsent: requireConsent,
        requireProofKey: requireProofKey,
      });
      onSuccess();
      resetForm();
    } catch (err: any) {
      setError(err.response?.data?.message || "Failed to create application");
    } finally {
      setLoading(false);
    }
  };

  const resetForm = () => {
    setClientName("");
    setApplicationUrl("");
    setRequireConsent(false);
    setRequireProofKey(false);
    setError("");
  };

  const handleClose = () => {
    resetForm();
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>Create New Application</DialogTitle>
      <DialogContent>
        <Box sx={{ pt: 2, display: "flex", flexDirection: "column", gap: 2 }}>
          {error && <Alert severity="error">{error}</Alert>}

          <FormControl fullWidth>
            <InputLabel>Organization</InputLabel>
            <Select
              value={selectedOrg}
              onChange={(e) => setSelectedOrg(e.target.value)}
              label="Organization"
            >
              {organizations.map((org) => (
                <MenuItem key={org.rowGuid} value={org.rowGuid}>
                  {org.name}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <TextField
            label="Client Name"
            fullWidth
            required
            value={clientName}
            onChange={(e) => setClientName(e.target.value)}
          />

          <TextField
            label="Application URL"
            fullWidth
            required
            value={applicationUrl}
            onChange={(e) => setApplicationUrl(e.target.value)}
            placeholder="https://example.com"
          />

          <FormControlLabel
            control={
              <Checkbox
                checked={requireConsent}
                onChange={(e) => setRequireConsent(e.target.checked)}
              />
            }
            label="Require Authorization Consent"
          />

          <FormControlLabel
            control={
              <Checkbox
                checked={requireProofKey}
                onChange={(e) => setRequireProofKey(e.target.checked)}
              />
            }
            label="Require Proof Key (PKCE)"
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Cancel</Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          disabled={loading}
        >
          {loading ? "Creating..." : "Create"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
