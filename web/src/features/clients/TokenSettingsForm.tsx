"use client";
import { useState } from "react";
import { useForm, Controller } from "react-hook-form";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  FormControlLabel,
  Stack,
  Typography,
} from "@mui/material";
import { TokenInput, type ClientDetailDto } from "../../contracts/clients";
import { FormField, validationErrors } from "./FormField";
import { actionMessage } from "./browser-api";
const fields = [
  ["authCodeTimeToLive", "Authorization code lifetime (seconds)"],
  ["accessTokenTimeToLive", "Access token lifetime (seconds)"],
  ["refreshTokenTimeToLive", "Refresh token lifetime (seconds)"],
  ["deviceCodeTimeToLive", "Device code lifetime (seconds)"],
  ["maxRequestTransitTime", "Maximum request transit (seconds)"],
] as const;
type Fields = Record<(typeof fields)[number][0], string | number> & {
  reuseRefreshTokens: boolean;
};
export function TokenSettingsForm({
  detail,
  save,
  disabled,
}: {
  detail: ClientDetailDto;
  save: (value: unknown) => Promise<void>;
  disabled: boolean;
}) {
  const saved: Fields = {
    authCodeTimeToLive: detail.authCodeTimeToLive ?? "",
    accessTokenTimeToLive: detail.accessTokenTimeToLive ?? "",
    refreshTokenTimeToLive: detail.refreshTokenTimeToLive ?? "",
    deviceCodeTimeToLive: detail.deviceCodeTimeToLive ?? "",
    maxRequestTransitTime: detail.maxRequestTransitTime ?? "",
    reuseRefreshTokens: detail.reuseRefreshTokens === true,
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
    const input = TokenInput.safeParse({
      ...Object.fromEntries(fields.map(([key]) => [key, Number(values[key])])),
      reuseRefreshTokens: values.reuseRefreshTokens,
    });
    setError("");
    setNotice("");
    if (!input.success) {
      validationErrors(form, input.error);
      setError("Enter positive whole seconds for every token setting.");
      return;
    }
    setPending(true);
    try {
      await save(input.data);
      form.reset(values, { keepDirtyValues: false, keepDirty: false });
      setNotice("Token settings saved and verified.");
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
          Token settings
        </Typography>
        <Typography variant="body2">
          All lifetimes use seconds. The identity service enforces its
          configured bounds.
        </Typography>
        {dirty && (
          <Typography variant="body2">Unsaved token settings.</Typography>
        )}
        {error && <Alert severity="error">{error}</Alert>}
        {notice && <Alert severity="success">{notice}</Alert>}
        {fields.map(([key, label]) => (
          <FormField
            key={key}
            control={form.control}
            name={key}
            label={label}
            type="number"
            required
            disabled={disabled || pending}
            slotProps={{ htmlInput: { min: 1, step: 1, "data-qa": key } }}
          />
        ))}
        <Controller
          control={form.control}
          name="reuseRefreshTokens"
          render={({ field }) => (
            <FormControlLabel
              control={
                <Checkbox
                  checked={field.value}
                  onChange={(e) => field.onChange(e.target.checked)}
                  slotProps={{
                    input: {
                      name: field.name,
                      ref: field.ref,
                      onBlur: field.onBlur,
                    },
                  }}
                  disabled={disabled || pending}
                />
              }
              label="Reuse refresh tokens"
            />
          )}
        />
        <Box>
          <Button
            type="submit"
            variant="contained"
            disabled={disabled || pending}
          >
            Save token settings
          </Button>
        </Box>
      </Stack>
    </Box>
  );
}
