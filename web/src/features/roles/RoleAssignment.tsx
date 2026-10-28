"use client";
import { useCallback, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Alert, Box, Button, MenuItem, Stack, Typography } from "@mui/material";
import { ActionFailure, actionMessage, requestJson } from "../clients/browser-api";
import { assignmentSaveOutcome } from "../../contracts/roles";
import { FormField } from "../clients/FormField";

type Eligible = { id: string; name: string; memberCount: number };

/**
 * Only roles the server judged inside the actor's ceiling are offered. The current role is kept
 * visible even when it is no longer eligible, so the screen never implies an assignment it did
 * not read from the identity service.
 */
export function RoleAssignment({
  orgId,
  userId,
  groupId,
  groupName,
  onChange,
}: {
  orgId: string;
  userId: string;
  groupId: string | null;
  groupName: string | null;
  onChange: () => Promise<void>;
}) {
  const eligibleEndpoint = "/api/admin/organizations/" + orgId + "/roles/eligible";
  const assignEndpoint =
    "/api/admin/organizations/" + orgId + "/users/" + userId + "/group";
  const [eligible, setEligible] = useState<Eligible[] | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [stale, setStale] = useState("");
  const [uncertain, setUncertain] = useState(false);
  const form = useForm<{ groupId: string }>({
    defaultValues: { groupId: groupId ?? "" },
    values: { groupId: groupId ?? "" },
    resetOptions: { keepDirtyValues: true },
  });
  const dirty = Object.keys(form.formState.dirtyFields).length > 0;
  const loadEligible = useCallback(async () => {
    setEligible((await requestJson<{ items: Eligible[] }>(eligibleEndpoint)).items);
  }, [eligibleEndpoint]);
  useEffect(() => {
    loadEligible().catch((e) => setError(actionMessage(e)));
  }, [loadEligible]);
  const list = eligible ?? [];
  const currentMissing = Boolean(groupId) && !list.some((role) => role.id === groupId);
  return (
    <Box component="section" aria-label="Role assignment" sx={{ py: 3, borderBottom: 1, borderColor: "divider" }}>
      <Typography component="h2" variant="h2" gutterBottom>Role assignment</Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        Only active roles whose permissions fit your own grant ceiling are offered. The identity
        service checks the ceiling and membership again on every change.
      </Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {notice && <Alert severity="success" role="status">{notice}</Alert>}
      {stale && <Alert severity="warning">{stale}</Alert>}
      {uncertain && (
        <Alert severity="warning">
          The result is uncertain. Reload the account before assigning a role again.
        </Alert>
      )}
      <Box
        component="form"
        noValidate
        onSubmit={form.handleSubmit(async (values) => {
          if (pending || uncertain) return;
          setPending(true);
          setError("");
          setNotice("");
          setStale("");
          try {
            await requestJson(assignEndpoint, "PUT", {
              groupId: values.groupId || null,
            });
          } catch (e) {
            setError(actionMessage(e));
            setUncertain(!(e instanceof ActionFailure) || e.status >= 500);
            setPending(false);
            return;
          }
          window.dispatchEvent(new Event("focus"));
          let readbackError: { status?: number } | undefined;
          try {
            await onChange();
          } catch (e) {
            readbackError = { status: e instanceof ActionFailure ? e.status : undefined };
          }
          const outcome = assignmentSaveOutcome(readbackError);
          setNotice(outcome.notice);
          setStale(outcome.warning ?? "");
          form.reset({ groupId: values.groupId }, { keepDirtyValues: false, keepDirty: false });
          loadEligible().catch(() => undefined);
          setPending(false);
        })}
      >
        <Stack spacing={2} sx={{ maxWidth: 480 }}>
          <FormField
            control={form.control}
            name="groupId"
            label="Assigned role"
            select
            helperText={
              eligible === null ? "Loading eligible roles…" : "No role removes the current assignment"
            }
            disabled={pending}
          >
            <MenuItem value="">No role</MenuItem>
            {list.map((role) => (
              <MenuItem key={role.id} value={role.id}>
                {role.name + " (" + role.memberCount + " members)"}
              </MenuItem>
            ))}
            {currentMissing && (
              <MenuItem value={groupId ?? ""}>
                {(groupName ?? "Current role") + " (current, not eligible)"}
              </MenuItem>
            )}
          </FormField>
          <Stack direction="row" spacing={2}>
            <Button type="submit" variant="contained" disabled={pending || uncertain || !dirty}>
              Save assignment
            </Button>
            <Button
              disabled={pending}
              onClick={() => {
                form.reset({ groupId: groupId ?? "" }, { keepDirtyValues: false, keepDirty: false });
                setError("");
                setNotice("Assignment draft discarded.");
                setStale("");
              }}
            >
              Discard
            </Button>
          </Stack>
        </Stack>
      </Box>
    </Box>
  );
}
