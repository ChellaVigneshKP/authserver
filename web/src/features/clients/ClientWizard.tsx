"use client";
import { useEffect, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { FormField, validationErrors } from "./FormField";
import { Alert, Box, Button, Link, Stack, Typography } from "@mui/material";
import {
  ClientCreate,
  type ClientCreateInput,
  type WizardStateDto,
  type ClientSummaryDto,
} from "../../contracts/clients";
import { ConfirmAction } from "../../components/ConfirmAction";
import { requestJson, actionMessage } from "./browser-api";
type State = { state: WizardStateDto | null; candidates: ClientSummaryDto[] };
export function ClientWizard({ orgId }: { orgId: string }) {
  const [state, setState] = useState<State | null>(null),
    [pending, setPending] = useState(false),
    [error, setError] = useState("");
  const form = useForm<ClientCreateInput>({
    defaultValues: {
      name: "",
      description: "",
      type: "WEB",
      authMethod: "PKCE",
    },
  });
  const type = useWatch({
    control: form.control,
    name: "type",
    defaultValue: "WEB",
  });
  const api = "/api/admin/organizations/" + orgId + "/clients";
  async function read() {
    setPending(true);
    setError("");
    try {
      setState(await requestJson<State>(api + "/wizard"));
    } catch (e) {
      setError(actionMessage(e));
    } finally {
      setPending(false);
    }
  }
  useEffect(() => {
    void read();
  }, []);
  async function reconcile(input: unknown) {
    setPending(true);
    setError("");
    try {
      setState(await requestJson<State>(api + "/wizard", "POST", input));
    } catch (e) {
      setError(actionMessage(e));
    } finally {
      setPending(false);
    }
  }
  async function create(values: ClientCreateInput) {
    if (pending) return;
    form.clearErrors();
    const input = ClientCreate.safeParse(values);
    if (!input.success) {
      validationErrors(form, input.error);
      setError(input.error.issues[0].message);
      return;
    }
    setPending(true);
    setError("");
    try {
      setState(await requestJson<State>(api, "POST", input.data));
    } catch (e) {
      setError(actionMessage(e));
      try {
        setState(await requestJson<State>(api + "/wizard"));
      } catch {
        /* Keep uncertainty visible; never retry POST. */
      }
    } finally {
      setPending(false);
    }
  }
  return (
    <Stack spacing={3}>
      <Typography component="h1" variant="h1">
        Create client
      </Typography>
      <Typography>
        Identity → URLs → token settings → credentials → resources → readiness
      </Typography>
      <Typography color="text.secondary">
        Each step saves separately. Resume configuration from the client detail
        page. Onboarding progress is kept for 30 minutes in your current
        session.
      </Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {pending && (
        <Typography role="status">Checking current state…</Typography>
      )}
      {state?.state ? (
        <>
          <Typography component="h2" variant="h2">
            {state.state.identity.name}
          </Typography>
          {state.state.applicationId ? (
            <>
              <Alert severity="success">
                Client identity was read back. Continue with its saved
                configuration.
              </Alert>
              <Link
                href={
                  "/admin/organizations/" +
                  orgId +
                  "/clients/" +
                  state.state.applicationId
                }
                data-qa="continue-client"
              >
                Continue configuration
              </Link>
              <Button
                disabled={pending}
                onClick={() => void reconcile({ finish: true })}
              >
                Start another client
              </Button>
            </>
          ) : (
            <>
              <Alert severity="warning">
                Creation is uncertain. No request was automatically repeated.
                Read the current state and review candidate identities before
                choosing a next action.
              </Alert>
              <Button disabled={pending} onClick={() => void read()}>
                Read current onboarding state
              </Button>
              {state.candidates.map((v) => (
                <Box key={v.id}>
                  <Typography>
                    {v.name} — OAuth client ID {v.clientId}
                  </Typography>
                  <ConfirmAction
                    label={"Use this client " + v.clientId}
                    title="Resume this client?"
                    description="Confirm this is the client created by your earlier request. Its actual saved configuration will be read again."
                    onConfirm={() => {
                      if (!pending) void reconcile({ applicationId: v.id });
                    }}
                  />
                </Box>
              ))}
              {!state.candidates.length && !pending && (
                <ConfirmAction
                  label="Review retry"
                  title="Create again after review?"
                  description="No matching active client was found in a fresh complete list. Verify that an operator has not renamed or deactivated the earlier client. This explicitly sends one new creation request."
                  confirmLabel="Create once"
                  onConfirm={() => void reconcile({ confirmRetry: true })}
                />
              )}
            </>
          )}
        </>
      ) : state ? (
        <Box component="form" onSubmit={form.handleSubmit(create)}>
          <Stack spacing={2} sx={{ maxWidth: 640 }}>
            <FormField
              control={form.control}
              label="Client name"
              name="name"
              required
              slotProps={{
                htmlInput: { maxLength: 255, "data-qa": "client-name" },
              }}
            />
            <FormField
              control={form.control}
              label="Description"
              name="description"
              multiline
              slotProps={{
                htmlInput: { maxLength: 1024, "data-qa": "client-description" },
              }}
            />
            <FormField
              control={form.control}
              select
              label="Client type"
              name="type"
              onValueChange={(value) =>
                form.setValue(
                  "authMethod",
                  value === "SERVER" ? "CLIENT_SECRET_JWT" : "PKCE",
                  { shouldDirty: true },
                )
              }
              slotProps={{
                select: { native: true },
                htmlInput: { "data-qa": "client-type" },
              }}
            >
              <option value="WEB">Web</option>
              <option value="MOBILE">Mobile</option>
              <option value="SERVER">Server</option>
            </FormField>
            <FormField
              control={form.control}
              key={type}
              select
              label="Authentication method"
              name="authMethod"
              slotProps={{
                select: { native: true },
                htmlInput: { "data-qa": "client-authentication" },
              }}
            >
              {type === "SERVER" ? (
                <>
                  <option value="CLIENT_SECRET_JWT">Client secret JWT</option>
                  <option value="PRIVATE_KEY_JWT">Private key JWT</option>
                </>
              ) : (
                <option value="PKCE">PKCE</option>
              )}
            </FormField>
            <Button
              type="submit"
              variant="contained"
              disabled={pending}
              data-qa="client-create-identity"
            >
              Create identity
            </Button>
          </Stack>
        </Box>
      ) : (
        !pending && (
          <Button onClick={() => void read()}>
            Read current onboarding state
          </Button>
        )
      )}
    </Stack>
  );
}
