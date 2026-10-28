"use client";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Alert, Box, Button, Stack, Typography } from "@mui/material";
import { UrlInput, type ClientUrlsDto } from "../../contracts/clients";
import { FormField, validationErrors } from "./FormField";
import { actionMessage } from "./browser-api";
type Fields = {
  applicationUrl: string;
  returnUrls: string;
  logoutUrls: string;
};
export function UrlSettingsForm({
  urls,
  save,
  disabled,
}: {
  urls: ClientUrlsDto;
  save: (value: unknown) => Promise<void>;
  disabled: boolean;
}) {
  const saved = {
    applicationUrl: urls.applicationUrl ?? "",
    returnUrls: urls.redirectUrls.join("\n"),
    logoutUrls: urls.logoutRedirectUrls.join("\n"),
  };
  const form = useForm<Fields>({
    defaultValues: saved,
    values: saved,
    resetOptions: { keepDirtyValues: true },
  });
  const dirty = Object.keys(form.formState.dirtyFields).length > 0;
  const [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [pending, setPending] = useState(false);
  async function submit(values: Fields) {
    if (pending) return;
    form.clearErrors();
    const parsed = UrlInput.safeParse({
      applicationUrl: values.applicationUrl,
      returnUrls: values.returnUrls.split(/\r?\n/),
      logoutUrls: values.logoutUrls.split(/\r?\n/),
    });
    setError("");
    setNotice("");
    if (!parsed.success) {
      validationErrors(form, parsed.error);
      setError(parsed.error.issues[0].message);
      return;
    }
    setPending(true);
    try {
      await save(parsed.data);
      form.reset(values, { keepDirtyValues: false, keepDirty: false });
      setNotice("URLs saved and verified.");
    } catch (e) {
      setError(actionMessage(e));
    } finally {
      setPending(false);
    }
  }
  return (
    <Box component="form" onSubmit={form.handleSubmit(submit)}>
      <Stack spacing={2}>
        <Typography component="h2" variant="h2">
          Application and exact URLs
        </Typography>
        <Typography variant="body2">
          One URL per line. Register exact redirects and logout destinations; no
          wildcards or fragments.
        </Typography>
        {dirty && <Typography variant="body2">Unsaved URL changes.</Typography>}
        {error && <Alert severity="error">{error}</Alert>}
        {notice && <Alert severity="success">{notice}</Alert>}
        <FormField
          control={form.control}
          label="Application URL"
          name="applicationUrl"
          required
          disabled={disabled || pending}
        />
        <FormField
          control={form.control}
          label="Redirect URLs"
          name="returnUrls"
          multiline
          minRows={2}
          required
          disabled={disabled || pending}
        />
        <FormField
          control={form.control}
          label="Logout URLs"
          name="logoutUrls"
          multiline
          minRows={2}
          required
          disabled={disabled || pending}
        />
        <Box>
          <Button
            type="submit"
            variant="contained"
            disabled={disabled || pending}
          >
            Save URLs
          </Button>
        </Box>
      </Stack>
    </Box>
  );
}
