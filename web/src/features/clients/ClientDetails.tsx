"use client";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { FormField, validationErrors } from "./FormField";
import {
  Alert,
  Box,
  Button,
  Divider,
  Link,
  Stack,
  Typography,
} from "@mui/material";
import {
  ClientUpdate,
  getClientReadiness,
  getClientProgress,
} from "../../contracts/clients";
import type { ResourceDto } from "../../contracts/resources";
import type { PortalContext } from "../../contracts/portal";
import type { clientBundle } from "./api";
import { canShow } from "../account/permissions";
import { ConfirmAction } from "../../components/ConfirmAction";
import { UrlSettingsForm } from "./UrlSettingsForm";
import { TokenSettingsForm } from "./TokenSettingsForm";
import { CredentialPanel } from "./CredentialPanel";
import { ResourceAssignments } from "./ResourceAssignments";
import { requestJson, actionMessage } from "./browser-api";
type Bundle = Awaited<ReturnType<typeof clientBundle>>;
export function ClientDetails({
  initial,
  context,
  catalog,
}: {
  initial: Bundle;
  context: PortalContext;
  catalog: ResourceDto[];
}) {
  const [data, setData] = useState(initial),
    [pending, setPending] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const { detail, urls, credentials, assignments } = data,
    orgId = detail.orgId,
    api = "/api/admin/organizations/" + orgId + "/clients/" + detail.id;
  const editable =
      detail.active && canShow(context, "idp-admin-app:update", orgId),
    readiness = getClientReadiness(detail, urls, credentials);
  const progress = getClientProgress(
    detail,
    urls,
    credentials,
    assignments.length,
  );
  const savedIdentity = {
    name: detail.name,
    description: detail.description ?? "",
  };
  const identityForm = useForm({
    defaultValues: savedIdentity,
    values: savedIdentity,
    resetOptions: { keepDirtyValues: true },
  });
  const dirtyIdentity =
    Object.keys(identityForm.formState.dirtyFields).length > 0;
  const [reloadKey, setReloadKey] = useState(0);
  async function refresh(discardDrafts = false) {
    const fresh = await requestJson<Bundle>(api);
    setData(fresh);
    if (discardDrafts) {
      identityForm.reset(
        {
          name: fresh.detail.name,
          description: fresh.detail.description ?? "",
        },
        { keepDirtyValues: false, keepDirty: false },
      );
      setReloadKey((v) => v + 1);
    }
  }
  async function save(path: string, value: unknown) {
    setData(await requestJson<Bundle>(api + path, "PUT", value));
  }
  async function identity(values: typeof savedIdentity) {
    if (pending) return;
    identityForm.clearErrors();
    const parsed = ClientUpdate.safeParse(values);
    if (!parsed.success) {
      validationErrors(identityForm, parsed.error);
      setError(
        "Enter a client name and description within the displayed limits.",
      );
      return;
    }
    setPending(true);
    setError("");
    try {
      await save("", parsed.data);
      identityForm.reset(parsed.data, {
        keepDirtyValues: false,
        keepDirty: false,
      });
      setNotice("Client details saved and verified.");
    } catch (e) {
      setError(actionMessage(e));
    } finally {
      setPending(false);
    }
  }
  async function deactivate() {
    if (pending) return;
    setPending(true);
    setError("");
    try {
      const value = await requestJson<{ detail: Bundle["detail"] }>(
        api,
        "DELETE",
      );
      setData({
        ...data,
        detail: value.detail,
        credentials: [],
        assignments: [],
      });
      setNotice("Client deactivated. It is absent from the active list.");
    } catch (e) {
      setError(actionMessage(e));
    } finally {
      setPending(false);
    }
  }
  return (
    <Stack spacing={4}>
      <Box>
        <Link href={"/admin/organizations/" + orgId + "/clients"}>Clients</Link>
        <Typography component="h1" variant="h1" sx={{ mt: 2 }}>
          {detail.name}
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          Application ID: {detail.id}
        </Typography>
        <Typography sx={{ overflowWrap: "anywhere" }}>
          OAuth client ID: {detail.clientId}
        </Typography>
        <Typography>
          {detail.type} / {detail.authenticationMethod} ·{" "}
          {detail.active ? "Active" : "Inactive"}
        </Typography>
        <Typography variant="body2">
          Derived grants:{" "}
          {detail.type === "SERVER"
            ? "client_credentials"
            : "authorization_code, refresh_token"}
        </Typography>
        <Typography variant="body2">
          Supported protocol scopes: openid, profile, email, metadata,
          idp-admin, read, confidential-client
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Scopes are fixed by the service. Requesting a scope does not grant
          management permissions.
        </Typography>
      </Box>
      {error && <Alert severity="error">{error}</Alert>}
      {notice && <Alert severity="success">{notice}</Alert>}
      {!detail.active ? (
        <Alert severity="warning">
          This client is inactive. Existing bearer tokens and new issuance are
          rejected. Reactivation is unavailable.
        </Alert>
      ) : (
        <>
          <Box
            component="nav"
            aria-label="Client configuration"
            sx={{ border: 1, borderColor: "divider", p: 3, borderRadius: 1 }}
          >
            <Typography component="h2" variant="h2">
              Configuration progress
            </Typography>
            <Typography role="status" sx={{ mt: 1 }}>
              Next needed:{" "}
              {progress.steps.find((v) => v.id === progress.nextId)?.label}
            </Typography>
            <Box component="ol" sx={{ pl: 3, mb: 0, "& li": { mb: 1.5 } }}>
              {progress.steps.map((step) => (
                <li key={step.id}>
                  <Link
                    href={"#" + step.id}
                    aria-current={
                      progress.nextId === step.id ? "step" : undefined
                    }
                    data-qa={"configuration-" + step.id}
                  >
                    {step.label}
                  </Link>
                  <Typography component="span" variant="body2" sx={{ ml: 1 }}>
                    {step.status}
                  </Typography>
                  {step.id === "client-resources" && (
                    <Typography
                      component="span"
                      variant="body2"
                      color="text.secondary"
                    >
                      {" "}
                      (optional)
                    </Typography>
                  )}
                </li>
              ))}
            </Box>
          </Box>
          <Box
            id="client-identity"
            tabIndex={-1}
            key={"identity-" + reloadKey}
            component="form"
            onSubmit={identityForm.handleSubmit(identity)}
          >
            <Stack spacing={2}>
              <Typography component="h2" variant="h2">
                Client identity
              </Typography>
              <Typography variant="body2">
                Client type and authentication method are immutable after
                creation.
              </Typography>
              {dirtyIdentity && (
                <Typography variant="body2">
                  Unsaved identity changes.
                </Typography>
              )}
              <FormField
                control={identityForm.control}
                name="name"
                label="Client name"
                required
                disabled={!editable || pending}
                slotProps={{ htmlInput: { maxLength: 255 } }}
              />
              <FormField
                control={identityForm.control}
                name="description"
                label="Description"
                multiline
                disabled={!editable || pending}
                slotProps={{ htmlInput: { maxLength: 1024 } }}
              />
              {editable && (
                <Box>
                  <Button type="submit" variant="contained" disabled={pending}>
                    Save client details
                  </Button>
                </Box>
              )}
            </Stack>
          </Box>
          <Divider />
          <Box
            component="section"
            id="client-urls"
            tabIndex={-1}
            sx={{
              scrollMarginTop: 32,
              "&:focus": { outline: "2px solid #3e6fab", outlineOffset: 8 },
            }}
          >
            <UrlSettingsForm
              key={"urls-" + reloadKey}
              urls={urls}
              disabled={!editable}
              save={(v) => save("/urls", v)}
            />
          </Box>
          <Divider />
          <Box
            component="section"
            id="client-settings"
            tabIndex={-1}
            sx={{
              scrollMarginTop: 32,
              "&:focus": { outline: "2px solid #3e6fab", outlineOffset: 8 },
            }}
          >
            <TokenSettingsForm
              key={"settings-" + reloadKey}
              detail={detail}
              disabled={!editable}
              save={(v) => save("/settings", v)}
            />
          </Box>
          <Divider />
          <Box
            component="section"
            id="client-credentials"
            tabIndex={-1}
            sx={{
              scrollMarginTop: 32,
              "&:focus": { outline: "2px solid #3e6fab", outlineOffset: 8 },
            }}
          >
            <CredentialPanel
              detail={detail}
              credentials={credentials}
              api={api}
              editable={editable}
              refresh={refresh}
            />
          </Box>
          <Divider />
          <Box
            component="section"
            id="client-resources"
            tabIndex={-1}
            sx={{
              scrollMarginTop: 32,
              "&:focus": { outline: "2px solid #3e6fab", outlineOffset: 8 },
            }}
          >
            <ResourceAssignments
              assignments={assignments}
              catalog={catalog}
              editable={
                detail.active &&
                canShow(context, "idp-admin-app:assign-resource", orgId)
              }
              api={api}
              refresh={refresh}
            />
          </Box>
          <Box
            component="section"
            id="client-readiness"
            tabIndex={-1}
            sx={{
              scrollMarginTop: 32,
              "&:focus": { outline: "2px solid #3e6fab", outlineOffset: 8 },
            }}
          >
            <Typography component="h2" variant="h2">
              Configuration readiness
            </Typography>
            {readiness.ready ? (
              <Alert severity="success">
                Required configuration is present in current readback. Test
                sign-in or service authentication with your application before
                use.
              </Alert>
            ) : (
              <Alert severity="info">
                <ul>
                  {readiness.missing.map((v) => (
                    <li key={v}>{v}</li>
                  ))}
                </ul>
              </Alert>
            )}
            <Button
              onClick={() => {
                setError("");
                void refresh(true).catch((e) => setError(actionMessage(e)));
              }}
            >
              Reload saved configuration
            </Button>
            <Typography variant="body2" color="text.secondary">
              Reloading saved configuration discards unsaved edits in these
              forms.
            </Typography>
          </Box>
        </>
      )}
      {detail.active &&
        canShow(context, "idp-admin-app:delete", orgId) &&
        !pending && (
          <Box>
            <ConfirmAction
              label="Deactivate client"
              title={"Deactivate " + detail.name + "?"}
              description={
                "Deactivate " +
                detail.name +
                ". Existing bearer tokens will be rejected and new tokens will not be issued. The client disappears from active lists. Reactivation is unavailable."
              }
              confirmLabel="Deactivate client"
              onConfirm={() => void deactivate()}
            />
          </Box>
        )}
    </Stack>
  );
}
