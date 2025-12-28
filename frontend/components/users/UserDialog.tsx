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
  Alert,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
} from "@mui/material";
import { createClientApiClient } from "@/lib/api/client";

interface UserDialogProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export default function UserDialog({ open, onClose, onSuccess }: UserDialogProps) {
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
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
    if (!loginId || !password || !firstName || !lastName || !email || !selectedOrg) {
      setError("Please fill in all required fields");
      return;
    }

    setLoading(true);
    setError("");

    try {
      const client = createClientApiClient();
      await client.post("/api/v1/users", {
        loginId,
        password,
        firstName,
        lastName,
        email,
        phoneNumber: phoneNumber || undefined,
        orgGuid: selectedOrg,
      });
      onSuccess();
      resetForm();
    } catch (err: any) {
      setError(err.response?.data?.message || "Failed to create user");
    } finally {
      setLoading(false);
    }
  };

  const resetForm = () => {
    setLoginId("");
    setPassword("");
    setFirstName("");
    setLastName("");
    setEmail("");
    setPhoneNumber("");
    setError("");
  };

  const handleClose = () => {
    resetForm();
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>Create New User</DialogTitle>
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
            label="Username"
            fullWidth
            required
            value={loginId}
            onChange={(e) => setLoginId(e.target.value)}
          />

          <TextField
            label="Password"
            type="password"
            fullWidth
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />

          <TextField
            label="First Name"
            fullWidth
            required
            value={firstName}
            onChange={(e) => setFirstName(e.target.value)}
          />

          <TextField
            label="Last Name"
            fullWidth
            required
            value={lastName}
            onChange={(e) => setLastName(e.target.value)}
          />

          <TextField
            label="Email"
            type="email"
            fullWidth
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />

          <TextField
            label="Phone Number"
            fullWidth
            value={phoneNumber}
            onChange={(e) => setPhoneNumber(e.target.value)}
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
