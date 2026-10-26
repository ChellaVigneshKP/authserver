"use client";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { FormField } from "./FormField";
import { Alert, Box, Button, Stack, Typography } from "@mui/material";
import type { AssignmentDto, ResourceDto } from "../../contracts/resources";
import { DataTable } from "../../components/DataTable";
import { ConfirmAction } from "../../components/ConfirmAction";
import { requestJson, actionMessage } from "./browser-api";
export function ResourceAssignments({
  assignments,
  catalog,
  editable,
  api,
  refresh,
}: {
  assignments: AssignmentDto[];
  catalog: ResourceDto[];
  editable: boolean;
  api: string;
  refresh: () => Promise<void>;
}) {
  const [pending, setPending] = useState(false),
    [error, setError] = useState("");
  const form = useForm({ defaultValues: { resourceId: "" } });
  async function mutate(method: string, id: string) {
    if (pending) return;
    setPending(true);
    setError("");
    try {
      await requestJson(
        api + "/resources" + (method === "DELETE" ? "/" + id : ""),
        method,
        method === "POST" ? { resourceId: id } : undefined,
      );
      await refresh();
      return true;
    } catch (e) {
      setError(actionMessage(e));
      return false;
    } finally {
      setPending(false);
    }
  }
  return (
    <Stack spacing={2}>
      <Typography component="h2" variant="h2">
        Assigned resources
      </Typography>
      <Typography variant="body2">
        These method and URI permissions authorize API resources. They are not
        arbitrary OAuth scopes.
      </Typography>
      {error && <Alert severity="error">{error}</Alert>}
      <DataTable
        caption="Assigned API resources"
        rows={assignments}
        rowKey={(v) => v.catalogResourceId}
        columns={[
          { key: "name", label: "Resource", render: (v) => v.name },
          { key: "method", label: "Method", render: (v) => v.allowedMethod },
          { key: "uri", label: "URI", render: (v) => v.uri },
          {
            key: "actions",
            label: "Actions",
            render: (v) =>
              editable && !pending ? (
                <ConfirmAction
                  label={"Remove " + v.name}
                  title={"Remove " + v.name + "?"}
                  description="This client will no longer be assigned this API resource."
                  confirmLabel="Remove assignment"
                  onConfirm={() => void mutate("DELETE", v.catalogResourceId)}
                />
              ) : null,
          },
        ]}
      />
      {!assignments.length && (
        <Typography>No resources are assigned.</Typography>
      )}
      {editable && (
        <Box
          component="form"
          onSubmit={form.handleSubmit(async ({ resourceId }) => {
            if (!/^[0-9a-f-]{36}$/i.test(resourceId)) {
              form.setError("resourceId", {
                message: "Select a public catalog resource ID.",
              });
              return;
            }
            if (await mutate("POST", resourceId))
              form.reset({ resourceId: "" });
          })}
        >
          <Stack spacing={2}>
            {catalog.length ? (
              <FormField
                control={form.control}
                name="resourceId"
                label="Catalog resource"
                select
                required
                disabled={pending}
                slotProps={{ select: { native: true } }}
              >
                <option value="">Select resource</option>
                {catalog
                  .filter(
                    (v) =>
                      !assignments.some((a) => a.catalogResourceId === v.id),
                  )
                  .map((v) => (
                    <option key={v.id} value={v.id}>
                      {v.name} ({v.allowedMethod} {v.uri})
                    </option>
                  ))}
              </FormField>
            ) : (
              <FormField
                control={form.control}
                name="resourceId"
                label="Catalog resource ID"
                helperText="A public catalog GUID is required. Catalog browsing needs platform resource read access."
                required
                disabled={pending}
              />
            )}
            <Box>
              <Button type="submit" variant="contained" disabled={pending}>
                Assign resource
              </Button>
            </Box>
          </Stack>
        </Box>
      )}
    </Stack>
  );
}
