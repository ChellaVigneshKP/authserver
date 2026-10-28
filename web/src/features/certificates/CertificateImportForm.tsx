"use client";
import { useRef, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import {
  Alert,
  Box,
  Button,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import {
  CertificateForm,
  certificateTypeLabel,
  type CertificateFormValue,
  type CertificateTypeValue,
} from "../../contracts/certificates";
import { FormField, validationErrors } from "../clients/FormField";
import { actionMessage, ActionFailure, requestForm } from "./browser-api";

type Draft = {
  name: string;
  type: "" | CertificateTypeValue;
  password: string;
  certificate: File | undefined;
};

const BLANK: Draft = {
  name: "",
  type: "",
  password: "",
  certificate: undefined,
};

export function CertificateImportForm({
  orgId,
  onImported,
}: {
  orgId: string;
  onImported: () => Promise<void>;
}) {
  const form = useForm<Draft>({ defaultValues: BLANK });
  const fileInput = useRef<HTMLInputElement>(null);
  const active = useRef(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [uncertain, setUncertain] = useState(false);
  function clearTransient() {
    form.reset(BLANK, { keepDirtyValues: false, keepDirty: false });
    if (fileInput.current) fileInput.current.value = "";
  }
  return (
    <Box component="section" aria-label="Import certificate" sx={{ py: 2 }}>
      <Typography component="h2" variant="h2">
        Import certificate
      </Typography>
      <Typography color="text.secondary" sx={{ my: 2 }}>
        Choose the certificate type explicitly. Organization signing certificates
        need their matching private key and password; client public keys need only
        the public certificate. The file is sent once and is not retained in this
        page.
      </Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {uncertain && (
        <Alert severity="warning">
          The import may have completed. Reload the certificate list before
          importing again.
        </Alert>
      )}
      <Box
        component="form"
        noValidate
        autoComplete="off"
        onSubmit={form.handleSubmit(async (values) => {
          if (active.current || uncertain) return;
          const parsed = CertificateForm.safeParse(values);
          if (!parsed.success) {
            validationErrors(form, parsed.error);
            return;
          }
          active.current = true;
          setPending(true);
          setError("");
          try {
            const body = new FormData();
            body.set("name", parsed.data.name);
            body.set("type", parsed.data.type);
            body.set("password", parsed.data.password);
            body.set("certificate", parsed.data.certificate);
            const created = await requestForm<{ id: string }>(
              "/api/admin/organizations/" + orgId + "/certificates",
              body,
            );
            clearTransient();
            onImported().catch(() => {});
            window.location.assign(
              "/admin/organizations/" + orgId + "/certificates/" + created.id,
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
            label="Certificate name"
            helperText="1–255 characters, no control characters"
            disabled={pending || uncertain}
          />
          <Controller
            control={form.control}
            name="type"
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                select
                label="Certificate type"
                value={field.value}
                error={!!fieldState.error}
                helperText={
                  fieldState.error?.message ??
                  "Required; it decides which permission the import needs."
                }
                disabled={pending || uncertain}
              >
                <MenuItem value="">
                  <em>Select certificate type</em>
                </MenuItem>
                <MenuItem value="ORGANIZATION">
                  {certificateTypeLabel("ORGANIZATION")}
                </MenuItem>
                <MenuItem value="PUBLIC_KEY">
                  {certificateTypeLabel("PUBLIC_KEY")}
                </MenuItem>
              </TextField>
            )}
          />
          <FormField
            control={form.control}
            name="password"
            label="Private key password"
            type="password"
            autoComplete="new-password"
            helperText="Required for encrypted organization signing keys; leave blank when the key has no password. Never displayed or stored after import."
            disabled={pending || uncertain}
          />
          <Controller
            control={form.control}
            name="certificate"
            render={({ field, fieldState }) => (
              <Stack spacing={1}>
                <Button
                  component="label"
                  variant="outlined"
                  disabled={pending || uncertain}
                  sx={{ alignSelf: "flex-start" }}
                >
                  Choose certificate file
                  <input
                    hidden
                    type="file"
                    accept=".pem,.crt,.cer,.key"
                    ref={(element) => {
                      fileInput.current = element;
                      field.ref(element);
                    }}
                    onChange={(event) =>
                      field.onChange(event.target.files?.[0] ?? undefined)
                    }
                  />
                </Button>
                <Typography variant="body2" color="text.secondary">
                  {field.value?.name ?? "No file selected"}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  PEM file up to 1 MiB.
                </Typography>
                {fieldState.error && (
                  <Typography color="error" variant="body2" role="alert">
                    {fieldState.error.message}
                  </Typography>
                )}
              </Stack>
            )}
          />
          <Button type="submit" variant="contained" disabled={pending || uncertain}>
            Import certificate
          </Button>
        </Stack>
      </Box>
    </Box>
  );
}
