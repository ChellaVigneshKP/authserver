"use client";
import { useMemo, useState } from "react";
import {
  Box,
  Button,
  Link,
  Stack,
  TablePagination,
  TextField,
  Typography,
} from "@mui/material";
import { DataTable } from "../../components/DataTable";
import { EmptyState } from "../../components/EmptyState";
import type { ClientSummaryDto } from "../../contracts/clients";
export function ClientList({
  orgId,
  clients,
  canCreate,
}: {
  orgId: string;
  clients: ClientSummaryDto[];
  canCreate: boolean;
}) {
  const [query, setQuery] = useState(""),
    [page, setPage] = useState(0),
    [size, setSize] = useState(25);
  const filtered = useMemo(
    () =>
      clients.filter((v) =>
        (v.name + " " + v.clientId).toLowerCase().includes(query.toLowerCase()),
      ),
    [clients, query],
  );
  const base = "/admin/organizations/" + orgId + "/clients";
  return (
    <Stack spacing={3}>
      <Box>
        <Typography component="h1" variant="h1">
          Clients
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          Configure sign-in applications and confidential services for this
          organization.
        </Typography>
      </Box>
      {canCreate && (
        <Box>
          <Button
            component="a"
            href={base + "/new"}
            variant="contained"
            data-qa="create-client"
          >
            Create client
          </Button>
        </Box>
      )}
      <TextField
        label="Search clients"
        value={query}
        slotProps={{
          htmlInput: { maxLength: 100, "data-qa": "client-search" },
        }}
        onChange={(e) => {
          setQuery(e.target.value);
          setPage(0);
        }}
      />
      <Typography variant="body2" color="text.secondary">
        Search and pagination apply locally to the complete active-client list.
        Inactive clients are absent; their detail links remain readable.
      </Typography>
      {filtered.length ? (
        <>
          <DataTable
            caption="Active clients"
            rows={filtered.slice(page * size, (page + 1) * size)}
            rowKey={(v) => v.id}
            columns={[
              {
                key: "name",
                label: "Client",
                render: (v) => (
                  <Link href={base + "/" + v.id} data-qa="client-detail">
                    {v.name}
                  </Link>
                ),
              },
              { key: "type", label: "Type", render: (v) => v.type },
              {
                key: "auth",
                label: "Authentication",
                render: (v) => v.authFlow,
              },
              {
                key: "clientId",
                label: "OAuth client ID",
                render: (v) => (
                  <Typography variant="body2" sx={{ overflowWrap: "anywhere" }}>
                    {v.clientId}
                  </Typography>
                ),
              },
            ]}
          />
          <TablePagination
            component="div"
            count={filtered.length}
            page={page}
            rowsPerPage={size}
            rowsPerPageOptions={[10, 25, 50, 100]}
            onPageChange={(_, v) => setPage(v)}
            onRowsPerPageChange={(e) => {
              setSize(Number(e.target.value));
              setPage(0);
            }}
          />
        </>
      ) : (
        <EmptyState
          title="No clients found"
          description="Create a client or adjust your search."
        />
      )}
    </Stack>
  );
}
