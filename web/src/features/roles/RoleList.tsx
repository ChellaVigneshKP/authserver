"use client";
import { useRef, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { Alert, Box, Button, Link, Stack, Typography } from "@mui/material";
import type { PortalContext } from "../../contracts/portal";
import {
  RoleInput,
  type PermissionCatalogEntryDto,
  type RoleInputValue,
  type RoleSummary,
} from "../../contracts/roles";
import { canShow } from "../account/permissions";
import { DataTable } from "../../components/DataTable";
import { ActionFailure, actionMessage, requestJson } from "../clients/browser-api";
import { FormField, validationErrors } from "../clients/FormField";
import { PermissionMatrix } from "./PermissionMatrix";

export function RoleList({
  initial,
  orgId,
  context,
  catalog,
}: {
  initial: RoleSummary[];
  orgId: string;
  context: PortalContext;
  catalog: PermissionCatalogEntryDto[];
}) {
  const [roles, setRoles] = useState(initial);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const endpoint = "/api/admin/organizations/" + orgId + "/groups";
  async function reload() {
    setPending(true);
    try {
      setRoles((await requestJson<{ items: RoleSummary[] }>(endpoint)).items);
      setError("");
    } catch (e) {
      setError(actionMessage(e));
      throw e;
    } finally {
      setPending(false);
    }
  }
  const create = canShow(context, "idp-admin-group:create", orgId);
  return (
    <Stack spacing={3}>
      <Typography component="h1" variant="h1">Roles</Typography>
      <Typography color="text.secondary">
        Role definitions are managed here. Assignment eligibility is checked separately and
        includes only roles whose grants fit the assigning administrator&apos;s ceiling.
      </Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {roles.length ? (
        <DataTable
          caption="Roles defined in the selected organization"
          rows={roles}
          rowKey={(role) => role.id}
          columns={[
            {
              key: "name",
              label: "Name",
              render: (role) => (
                <Link href={"/admin/organizations/" + orgId + "/roles/" + role.id}>
                  {role.name}
                </Link>
              ),
            },
            {
              key: "description",
              label: "Description",
              render: (role) => role.description ?? "—",
            },
            { key: "status", label: "Status", render: (role) => role.status },
            {
              key: "members",
              label: "Members",
              render: (role) => role.memberCount,
            },
            {
              key: "type",
              label: "Type",
              render: (role) => (role.readOnly ? "Built-in" : "Custom"),
            },
          ]}
        />
      ) : (
        <Typography>No roles are defined in this organization.</Typography>
      )}
      <Stack direction="row" spacing={2}>
        <Button disabled={pending} onClick={() => void reload().catch(() => {})}>
          Reload roles
        </Button>
      </Stack>
      {create && (
        <CreateRoleForm
          orgId={orgId}
          catalog={catalog}
          ceiling={context.permissions}
        />
      )}
    </Stack>
  );
}

function CreateRoleForm({
  orgId,
  catalog,
  ceiling,
}: {
  orgId: string;
  catalog: PermissionCatalogEntryDto[];
  ceiling: readonly string[];
}) {
  const form = useForm<RoleInputValue>({
    defaultValues: { name: "", description: "", permissionKeys: [] },
  });
  const active = useRef(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [uncertain, setUncertain] = useState(false);
  return (
    <Box component="section" aria-label="Create role" sx={{ py: 2 }}>
      <Typography component="h2" variant="h2">Create custom role</Typography>
      <Typography color="text.secondary" sx={{ my: 2 }}>
        A custom role needs at least one permission. Built-in roles cannot be recreated.
      </Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {uncertain && (
        <Alert severity="warning">
          Creation may have completed. Reload the role list before creating another role.
        </Alert>
      )}
      <Box
        component="form"
        noValidate
        autoComplete="off"
        onSubmit={form.handleSubmit(async (values) => {
          if (active.current || uncertain) return;
          const parsed = RoleInput.safeParse(values);
          if (!parsed.success) {
            validationErrors(form, parsed.error);
            return;
          }
          active.current = true;
          setPending(true);
          setError("");
          try {
            const created = await requestJson<{ id: string }>(
              "/api/admin/organizations/" + orgId + "/groups",
              "POST",
              parsed.data,
            );
            window.location.assign(
              "/admin/organizations/" + orgId + "/roles/" + created.id,
            );
          } catch (e) {
            setError(actionMessage(e));
            setUncertain(!(e instanceof ActionFailure) || e.status >= 500);
          } finally {
            active.current = false;
            setPending(false);
          }
        })}
      >
        <Stack spacing={2} sx={{ maxWidth: 720 }}>
          <FormField
            control={form.control}
            name="name"
            label="Role name"
            helperText="1–100 characters, no control characters"
            disabled={pending}
          />
          <FormField
            control={form.control}
            name="description"
            label="Description"
            helperText="Optional, up to 1024 characters"
            multiline
            minRows={2}
            disabled={pending}
          />
          <Controller
            control={form.control}
            name="permissionKeys"
            render={({ field, fieldState }) => (
              <Stack spacing={1}>
                <PermissionMatrix
                  catalog={catalog}
                  selected={field.value}
                  ceiling={ceiling}
                  onChange={field.onChange}
                  disabled={pending}
                  legend="New role permissions"
                />
                {fieldState.error && (
                  <Typography color="error" variant="body2">
                    {fieldState.error.message}
                  </Typography>
                )}
              </Stack>
            )}
          />
          <Button type="submit" variant="contained" disabled={pending || uncertain}>
            Create role
          </Button>
        </Stack>
      </Box>
    </Box>
  );
}
