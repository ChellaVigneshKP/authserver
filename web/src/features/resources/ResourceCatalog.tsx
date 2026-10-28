"use client";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { FormField, validationErrors } from "../clients/FormField";
import {
  Alert,
  Box,
  Button,
  Link,
  Stack,
  TextField,
  Typography,
  TablePagination,
} from "@mui/material";
import { ResourceInput, type ResourceDto } from "../../contracts/resources";
import { DataTable } from "../../components/DataTable";
import { requestJson, actionMessage } from "../clients/browser-api";
export function ResourceCatalog({
  initial,
  canCreate,
  canUpdate,
}: {
  initial: ResourceDto[];
  canCreate: boolean;
  canUpdate: boolean;
}) {
  const [rows, setRows] = useState(initial),
    [selected, setSelected] = useState<ResourceDto | null>(null),
    [error, setError] = useState(""),
    [pending, setPending] = useState(false),
    [notice, setNotice] = useState(""),
    [query, setQuery] = useState(""),
    [page, setPage] = useState(0),
    [size, setSize] = useState(25);
  type Fields = {
    name: string;
    description: string;
    uri: string;
    allowedMethod: string;
    urn: string;
  };
  const defaults = (resource: ResourceDto | null): Fields => ({
    name: resource?.name ?? "",
    description: resource?.description ?? "",
    uri: resource?.uri ?? "",
    allowedMethod: resource?.allowedMethod ?? "GET",
    urn: resource?.urn ?? "",
  });
  const form = useForm<Fields>({ defaultValues: defaults(null) });
  async function save(values: Fields) {
    if (pending) return;
    form.clearErrors();
    const input = ResourceInput.safeParse({
      ...values,
      urn: values.urn || null,
    });
    if (!input.success) {
      validationErrors(form, input.error);
      setError(
        "Enter a name, description, valid URI, supported method and optional URN.",
      );
      return;
    }
    setPending(true);
    setError("");
    setNotice("");
    try {
      await requestJson(
        "/api/admin/resources" + (selected ? "/" + selected.id : ""),
        selected ? "PUT" : "POST",
        input.data,
      );
      setRows(await requestJson<ResourceDto[]>("/api/admin/resources"));
      setSelected(null);
      form.reset(defaults(null));
      setNotice("Resource saved and read back.");
    } catch (e) {
      setError(actionMessage(e));
    } finally {
      setPending(false);
    }
  }
  const filtered = rows.filter((v) =>
    (v.name + " " + v.uri).toLowerCase().includes(query.toLowerCase()),
  );
  return (
    <Stack spacing={3}>
      <Typography component="h1" variant="h1">
        Resource catalog
      </Typography>
      <Typography color="text.secondary">
        Manage platform API method and URI permissions, then assign them to
        clients. Catalog deletion is unavailable.
      </Typography>
      <TextField
        label="Search resources"
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          setPage(0);
        }}
        slotProps={{ htmlInput: { maxLength: 100 } }}
      />
      <Typography variant="body2">
        Search applies locally to the complete resource catalog.
      </Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {notice && <Alert severity="success">{notice}</Alert>}
      <DataTable
        caption="Platform API resources"
        rows={filtered.slice(page * size, (page + 1) * size)}
        rowKey={(v) => v.id}
        columns={[
          { key: "name", label: "Name", render: (v) => v.name },
          { key: "method", label: "Method", render: (v) => v.allowedMethod },
          { key: "uri", label: "URI", render: (v) => v.uri },
          {
            key: "edit",
            label: "Actions",
            render: (v) =>
              canUpdate ? (
                <Button
                  disabled={pending}
                  onClick={() => {
                    setNotice("");
                    setError("");
                    form.reset(defaults(v));
                    setSelected(v);
                  }}
                >
                  Edit {v.name}
                </Button>
              ) : null,
          },
        ]}
      />
      <TablePagination
        component="div"
        count={filtered.length}
        page={page}
        rowsPerPage={size}
        rowsPerPageOptions={[10, 25, 50, 100]}
        onPageChange={(_, value) => setPage(value)}
        onRowsPerPageChange={(e) => {
          setSize(Number(e.target.value));
          setPage(0);
        }}
      />
      <Box>
        <Button
          disabled={pending}
          onClick={async () => {
            setPending(true);
            setError("");
            try {
              setRows(await requestJson<ResourceDto[]>("/api/admin/resources"));
              setPage(0);
            } catch (e) {
              setError(actionMessage(e));
            } finally {
              setPending(false);
            }
          }}
        >
          Read current catalog
        </Button>
      </Box>
      {(selected ? canUpdate : canCreate) && (
        <Box
          key={selected?.id ?? "new"}
          component="form"
          onSubmit={form.handleSubmit(save)}
        >
          <Stack spacing={2} sx={{ maxWidth: 720 }}>
            <Typography component="h2" variant="h2">
              {selected ? "Edit resource" : "Create resource"}
            </Typography>
            <FormField
              control={form.control}
              label="Resource name"
              name="name"
              required
              disabled={pending}
            />
            <FormField
              control={form.control}
              label="Resource description"
              name="description"
              required
              disabled={pending}
            />
            <FormField
              control={form.control}
              label="Resource URI"
              name="uri"
              required
              disabled={pending}
            />
            <FormField
              control={form.control}
              label="Allowed method"
              name="allowedMethod"
              select
              disabled={pending}
              slotProps={{ select: { native: true } }}
            >
              {["GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"].map(
                (v) => (
                  <option key={v} value={v}>
                    {v}
                  </option>
                ),
              )}
            </FormField>
            <FormField
              control={form.control}
              label="Resource URN (optional)"
              name="urn"
              disabled={pending}
            />
            <Stack direction="row" spacing={2}>
              <Button type="submit" variant="contained" disabled={pending}>
                {selected ? "Save resource" : "Create resource"}
              </Button>
              {selected && (
                <Button
                  onClick={() => {
                    setNotice("");
                    setError("");
                    form.reset(defaults(null));
                    setSelected(null);
                  }}
                  disabled={pending}
                >
                  Cancel edit
                </Button>
              )}
            </Stack>
          </Stack>
        </Box>
      )}
      <Link href="/admin">Administration</Link>
    </Stack>
  );
}
