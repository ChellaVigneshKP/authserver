"use client";

import { useEffect, useState } from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  IconButton,
  Chip,
  CircularProgress,
  Box,
  Alert,
} from "@mui/material";
import SettingsIcon from "@mui/icons-material/Settings";
import DeleteIcon from "@mui/icons-material/Delete";
import { createClientApiClient } from "@/lib/api/client";
import { Application } from "@/lib/types";

export default function ApplicationsList() {
  const [applications, setApplications] = useState<Application[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedOrg, setSelectedOrg] = useState<string>("");

  useEffect(() => {
    fetchApplications();
  }, []);

  const fetchApplications = async () => {
    try {
      setLoading(true);
      setError("");
      
      const client = createClientApiClient();
      const orgsResponse = await client.get("/api/v1/organizations");
      const orgs = orgsResponse.data;
      
      if (orgs && orgs.length > 0) {
        const orgGuid = orgs[0].rowGuid;
        setSelectedOrg(orgGuid);
        
        const appsResponse = await client.get(
          `/api/v1/organizations/${orgGuid}/applications`
        );
        setApplications(appsResponse.data || []);
      } else {
        setError("No organizations found. Please create an organization first.");
      }
    } catch (err: any) {
      setError(err.response?.data?.message || "Failed to load applications");
      console.error("Error fetching applications:", err);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (appGuid: string) => {
    if (!confirm("Are you sure you want to delete this application?")) {
      return;
    }

    try {
      const client = createClientApiClient();
      await client.delete(
        `/api/v1/organizations/${selectedOrg}/applications/${appGuid}`
      );
      fetchApplications();
    } catch (err: any) {
      alert(err.response?.data?.message || "Failed to delete application");
    }
  };

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" p={4}>
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return <Alert severity="error">{error}</Alert>;
  }

  if (applications.length === 0) {
    return (
      <Box textAlign="center" p={4}>
        <Alert severity="info">
          No applications found. Create your first application to get started.
        </Alert>
      </Box>
    );
  }

  return (
    <TableContainer>
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>Client Name</TableCell>
            <TableCell>Client ID</TableCell>
            <TableCell>Status</TableCell>
            <TableCell>Organization</TableCell>
            <TableCell align="right">Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {applications.map((app) => (
            <TableRow key={app.rowGuid}>
              <TableCell>{app.clientName}</TableCell>
              <TableCell>{app.clientId}</TableCell>
              <TableCell>
                <Chip
                  label={app.status === 1 ? "Active" : "Inactive"}
                  color={app.status === 1 ? "success" : "default"}
                  size="small"
                />
              </TableCell>
              <TableCell>{app.organizationGuid}</TableCell>
              <TableCell align="right">
                <IconButton
                  size="small"
                  onClick={() => {
                    window.location.href = `/applications/${app.rowGuid}`;
                  }}
                >
                  <SettingsIcon />
                </IconButton>
                <IconButton
                  size="small"
                  color="error"
                  onClick={() => handleDelete(app.rowGuid)}
                >
                  <DeleteIcon />
                </IconButton>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
