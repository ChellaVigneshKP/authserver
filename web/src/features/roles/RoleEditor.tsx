"use client";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { Alert, Box, Button, Chip, Link, Stack, Typography } from "@mui/material";
import type { PortalContext } from "../../contracts/portal";
import {
  RoleInput,
  type GroupPermissionDto,
  type PermissionCatalogEntryDto,
  type RoleDto,
  type RoleInputValue,
} from "../../contracts/roles";
import { canShow } from "../account/permissions";
import { ConfirmAction } from "../../components/ConfirmAction";
import { ActionFailure, actionMessage, requestJson } from "../clients/browser-api";
import { FormField, validationErrors } from "../clients/FormField";
import { PermissionMatrix } from "./PermissionMatrix";

function roleValues(role: RoleDto): RoleInputValue {
  return {
    name: role.name,
    description: role.description ?? "",
    permissionKeys: role.permissionKeys,
  };
}

export function RoleEditor({
  initial,
  grants,
  catalog,
  context,
  orgId,
}: {
  initial: RoleDto;
  grants: GroupPermissionDto[];
  catalog: PermissionCatalogEntryDto[];
  context: PortalContext;
  orgId: string;
}) {
  const [role, setRole] = useState(initial);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [conflict, setConflict] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const form = useForm<RoleInputValue>({
    defaultValues: roleValues(role),
    values: roleValues(role),
    resetOptions: { keepDirtyValues: true },
  });
  const dirty = Object.keys(form.formState.dirtyFields).length > 0;
  const endpoint = "/api/admin/organizations/" + orgId + "/groups/" + role.id;
  const editable = !role.readOnly && canShow(context, "idp-admin-group:update", orgId);
  const deletable = !role.readOnly && canShow(context, "idp-admin-group:delete", orgId);
  async function read() {
    const fresh = await requestJson<RoleDto>(endpoint);
    setRole(fresh);
    return roleValues(fresh);
  }
  async function reload() {
    setPending(true);
    try {
      const fresh = await read();
      form.reset(fresh, { keepDirtyValues: false, keepDirty: false });
      setConflict(false);
      setUncertain(false);
      setError("");
      setNotice("Current role loaded.");
    } catch (e) {
      setError(actionMessage(e));
    } finally {
      setPending(false);
    }
  }
  return (
    <Stack spacing={2}>
      <Link href={"/admin/organizations/" + orgId + "/roles"}>Roles</Link>
      <Typography component="h1" variant="h1">{role.name}</Typography>
      <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
        <Chip label={role.status} />
        <Chip label={role.readOnly ? "Built-in role" : "Custom role"} />
        <Chip label={role.memberCount + (role.memberCount === 1 ? " member" : " members")} />
        <Chip label={"Revision " + role.revision} />
      </Stack>
      <Typography color="text.secondary">
        Every save carries the revision shown here. A conflicting change is never overwritten:
        reload the role, review the current grant set and submit again deliberately.
      </Typography>
      {role.readOnly && (
        <Alert severity="info">
          This built-in role is read-only. Its grants are fixed by the identity service.
        </Alert>
      )}
      {error && <Alert severity="error">{error}</Alert>}
      {notice && <Alert severity="success" role="status">{notice}</Alert>}
      {conflict && (
        <Alert severity="warning" action={<Button onClick={() => void reload()}>Reload role</Button>}>
          The role changed or is protected by a backend rule. No update was re-sent.
        </Alert>
      )}
      {uncertain && (
        <Alert severity="warning">
          The result is uncertain. Read the current role before submitting again.
        </Alert>
      )}
      <Box
        component="form"
        noValidate
        autoComplete="off"
        onSubmit={form.handleSubmit(async (values) => {
          if (pending || uncertain) return;
          const parsed = RoleInput.safeParse(values);
          if (!parsed.success) {
            validationErrors(form, parsed.error);
            return;
          }
          setPending(true);
          setError("");
          setNotice("");
          setConflict(false);
          try {
            const fresh = await requestJson<RoleDto>(endpoint, "PUT", {
              ...parsed.data,
              revision: role.revision,
            });
            setRole(fresh);
            form.reset(roleValues(fresh), { keepDirtyValues: false, keepDirty: false });
            setNotice("Role changes saved.");
          } catch (e) {
            setError(actionMessage(e));
            setConflict(e instanceof ActionFailure && e.status === 409);
            setUncertain(!(e instanceof ActionFailure) || e.status >= 500);
          } finally {
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
            disabled={!editable || pending}
          />
          <FormField
            control={form.control}
            name="description"
            label="Description"
            helperText="Optional, up to 1024 characters"
            multiline
            minRows={2}
            disabled={!editable || pending}
          />
          <Controller
            control={form.control}
            name="permissionKeys"
            render={({ field, fieldState }) => (
              <Stack spacing={1}>
                <PermissionMatrix
                  catalog={catalog}
                  grants={grants}
                  selected={field.value}
                  ceiling={context.permissions}
                  onChange={editable ? field.onChange : undefined}
                  disabled={pending}
                  legend="Current permissions"
                />
                {fieldState.error && (
                  <Typography color="error" variant="body2">
                    {fieldState.error.message}
                  </Typography>
                )}
              </Stack>
            )}
          />
          <Stack direction="row" spacing={2} sx={{ flexWrap: "wrap" }}>
            {editable && (
              <>
                <Button type="submit" variant="contained" disabled={pending || uncertain || !dirty}>
                  Save role
                </Button>
                <Button
                  disabled={pending}
                  onClick={() => {
                    form.reset(roleValues(role), { keepDirtyValues: false, keepDirty: false });
                    setConflict(false);
                    setError("");
                    setNotice("Unsaved changes discarded.");
                  }}
                >
                  Discard changes
                </Button>
                <Button disabled={pending} onClick={() => void reload()}>
                  Read current role
                </Button>
              </>
            )}
          </Stack>
        </Stack>
      </Box>
      <Typography component="h2" variant="h2" sx={{ pt: 2 }}>Role lifecycle</Typography>
      <Typography>
        Deactivation keeps the role record but removes it from new assignments. Roles that still
        have members cannot be deactivated.
      </Typography>
      <Stack direction="row" spacing={2}>
        {editable && deletable && role.status === "Active" && (
          <ConfirmAction
            label="Deactivate role"
            title="Deactivate role?"
            description="Assigned members are checked again by the identity service. Records are retained."
            confirmLabel="Deactivate role"
            onConfirm={() => {
              if (pending) return;
              setPending(true);
              setError("");
              requestJson(endpoint, "DELETE")
                .then(() => window.location.assign("/admin/organizations/" + orgId + "/roles"))
                .catch((e) => {
                  setError(actionMessage(e));
                  setConflict(e instanceof ActionFailure && e.status === 409);
                })
                .finally(() => setPending(false));
            }}
          />
        )}
      </Stack>
    </Stack>
  );
}
