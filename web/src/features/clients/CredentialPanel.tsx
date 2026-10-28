"use client";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { FormField, validationErrors } from "./FormField";
import { Alert, Box, Button, Stack, Typography } from "@mui/material";
import {
  CredentialCreate,
  credentialInstant,
  type ClientDetailDto,
  type CredentialDto,
} from "../../contracts/clients";
import { DataTable } from "../../components/DataTable";
import { ConfirmAction } from "../../components/ConfirmAction";
import { SecretOnceDialog } from "./SecretOnceDialog";
import { requestJson, actionMessage } from "./browser-api";
export function CredentialPanel({
  detail,
  credentials,
  api,
  editable,
  refresh,
}: {
  detail: ClientDetailDto;
  credentials: CredentialDto[];
  api: string;
  editable: boolean;
  refresh: () => Promise<void>;
}) {
  const [secret, setSecret] = useState<string | null>(null),
    [pending, setPending] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  type Fields = {
    name: string;
    expireOn: string;
    certificateId: string;
    algorithm: "RS256" | "ES256";
  };
  // Creation inputs only. The response secret is intentionally never passed to RHF.
  const form = useForm<Fields>({
    defaultValues: {
      name: "",
      expireOn: "",
      certificateId: "",
      algorithm: "RS256",
    },
  });
  const count = credentials.filter((v) => v.status === "Active").length;
  async function create(values: Fields) {
    if (pending) return;
    form.clearErrors();
    const shared = detail.authenticationMethod === "CLIENT_SECRET_JWT";
    if (
      shared &&
      values.expireOn &&
      !Number.isFinite(Date.parse(values.expireOn))
    ) {
      form.setError("expireOn", { message: "Enter a valid expiry." });
      return;
    }
    const input = CredentialCreate.safeParse({
      name: values.name,
      type: shared ? "SHARED_SECRET" : "PRIVATE_KEY",
      algorithm: shared ? "HS256" : values.algorithm,
      ...(shared
        ? values.expireOn
          ? { expireOn: new Date(values.expireOn).toISOString() }
          : {}
        : { certificateId: values.certificateId }),
    });
    setError("");
    setNotice("");
    if (!input.success) {
      validationErrors(form, input.error);
      setError(
        "Check credential name, algorithm, public certificate ID and expiry.",
      );
      return;
    }
    setPending(true);
    try {
      const value = await requestJson<{ id: string; secretValue?: string }>(
        api + "/credentials",
        "POST",
        input.data,
      );
      if (value.secretValue) setSecret(value.secretValue);
      else setNotice("Credential created. Read current metadata to verify it.");
      await refresh();
    } catch (e) {
      setError(actionMessage(e));
      try {
        await refresh();
      } catch {
        /* Metadata outage remains visible. */
      }
    } finally {
      setPending(false);
    }
  }
  async function action(id: string, method: string, value?: unknown) {
    if (pending) return;
    setPending(true);
    setError("");
    setNotice("");
    try {
      await requestJson(api + "/credentials/" + id, method, value);
      await refresh();
      setNotice("Credential metadata refreshed.");
    } catch (e) {
      setError(actionMessage(e));
    } finally {
      setPending(false);
    }
  }
  return (
    <Stack spacing={2}>
      <Typography component="h2" variant="h2">
        Credentials
      </Typography>
      <Typography variant="body2">
        At most two active credentials. Rotate by creating and testing a second
        credential, then disabling and deleting the old one. Metadata does not
        prove successful token issuance.
      </Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {notice && <Alert severity="success">{notice}</Alert>}
      {detail.authenticationMethod === "PKCE" ? (
        <Typography>
          PKCE clients use authorization codes and a verifier. No client secret
          is required.
        </Typography>
      ) : (
        <>
          <DataTable
            caption="Credential metadata"
            rows={credentials.filter((v) => v.status !== "Inactive")}
            rowKey={(v) => v.id}
            columns={[
              { key: "name", label: "Name", render: (v) => v.name },
              { key: "status", label: "Status", render: (v) => v.status },
              {
                key: "type",
                label: "Type / algorithm",
                render: (v) => v.type + " / " + v.algorithm,
              },
              {
                key: "expiry",
                label: "Expiry",
                render: (v) =>
                  credentialInstant(v.expirationDate) !== null ? (
                    <>
                      <Typography variant="body2">
                        UTC:{" "}
                        {new Date(
                          credentialInstant(v.expirationDate)!,
                        ).toISOString()}
                      </Typography>
                      <Typography variant="body2">
                        Local:{" "}
                        {mounted
                          ? new Date(
                              credentialInstant(v.expirationDate)!,
                            ).toLocaleString()
                          : "Available after page load"}
                      </Typography>
                      {v.isExpired && "Expired"}
                    </>
                  ) : (
                    "Unknown expiry; usability unproven"
                  ),
              },
              {
                key: "actions",
                label: "Actions",
                render: (v) =>
                  editable && !pending ? (
                    <Stack direction="row" spacing={1}>
                      {v.status === "Active" ? (
                        <ConfirmAction
                          label={"Disable " + v.name}
                          title={"Disable " + v.name + "?"}
                          description="New authentication using this credential will be rejected. Test the replacement before disabling the old credential."
                          confirmLabel="Disable credential"
                          onConfirm={() =>
                            void action(v.id, "PUT", { status: "Disabled" })
                          }
                        />
                      ) : (
                        <>
                          <Button
                            disabled={count >= 2 || v.isExpired}
                            onClick={() =>
                              void action(v.id, "PUT", { status: "Active" })
                            }
                          >
                            Enable {v.name}
                          </Button>
                          <ConfirmAction
                            label={"Delete " + v.name}
                            title={"Delete " + v.name + "?"}
                            description="Remove this disabled credential. Its secret cannot be recovered."
                            confirmLabel="Delete credential"
                            onConfirm={() => void action(v.id, "DELETE")}
                          />
                        </>
                      )}
                    </Stack>
                  ) : null,
              },
            ]}
          />
          {editable && (
            <Box component="form" onSubmit={form.handleSubmit(create)}>
              <Stack spacing={2} sx={{ maxWidth: 640 }}>
                {count >= 2 && (
                  <Alert severity="info">
                    Two credentials are active. Disable one before creating
                    another.
                  </Alert>
                )}
                <FormField
                  control={form.control}
                  name="name"
                  label="Credential name"
                  required
                  disabled={pending || count >= 2}
                />
                {detail.authenticationMethod === "CLIENT_SECRET_JWT" ? (
                  <>
                    <Typography>Shared secret / HS256</Typography>
                    <FormField
                      control={form.control}
                      name="expireOn"
                      label="Expiry (local time, optional)"
                      type="datetime-local"
                      disabled={pending || count >= 2}
                      slotProps={{ inputLabel: { shrink: true } }}
                    />
                  </>
                ) : (
                  <>
                    <FormField
                      control={form.control}
                      name="certificateId"
                      label="Public certificate ID"
                      helperText="Use an existing active certificate in this organization."
                      required
                      disabled={pending || count >= 2}
                    />
                    <FormField
                      control={form.control}
                      name="algorithm"
                      select
                      label="Signature algorithm"
                      slotProps={{ select: { native: true } }}
                      disabled={pending || count >= 2}
                    >
                      <option value="RS256">RS256</option>
                      <option value="ES256">ES256</option>
                    </FormField>
                  </>
                )}
                <Box>
                  <Button
                    type="submit"
                    variant="contained"
                    disabled={pending || count >= 2}
                  >
                    Create credential
                  </Button>
                </Box>
              </Stack>
            </Box>
          )}
        </>
      )}
      {secret !== null && (
        <SecretOnceDialog secret={secret} onErase={() => setSecret(null)} />
      )}
    </Stack>
  );
}
