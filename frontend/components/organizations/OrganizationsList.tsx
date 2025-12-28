"use client";

import { useEffect, useState } from "react";
import {
  Box,
  CircularProgress,
  Alert,
} from "@mui/material";
import { DataGrid, GridColDef } from "@mui/x-data-grid";
import { createClientApiClient } from "@/lib/api/client";
import { Organization } from "@/lib/types";

export default function OrganizationsList() {
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    fetchOrganizations();
  }, []);

  const fetchOrganizations = async () => {
    try {
      setLoading(true);
      setError("");

      const client = createClientApiClient();
      const response = await client.get("/api/v1/organizations");
      setOrganizations(response.data || []);
    } catch (err: any) {
      setError(err.response?.data?.message || "Failed to load organizations");
      console.error("Error fetching organizations:", err);
    } finally {
      setLoading(false);
    }
  };

  const columns: GridColDef[] = [
    { field: "name", headerName: "Name", flex: 1, minWidth: 200 },
    { field: "description", headerName: "Description", flex: 1, minWidth: 250 },
    {
      field: "status",
      headerName: "Status",
      width: 120,
      renderCell: (params) => (params.value === 1 ? "Active" : "Inactive"),
    },
    {
      field: "primaryContactEmail",
      headerName: "Primary Contact",
      flex: 1,
      minWidth: 200,
    },
  ];

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

  return (
    <Box sx={{ height: 600, width: "100%" }}>
      <DataGrid
        rows={organizations}
        columns={columns}
        getRowId={(row) => row.rowGuid}
        pageSizeOptions={[5, 10, 25]}
        initialState={{
          pagination: {
            paginationModel: { pageSize: 10 },
          },
        }}
        disableRowSelectionOnClick
        onRowClick={(params) => {
          window.location.href = `/organizations/${params.row.rowGuid}`;
        }}
      />
    </Box>
  );
}
