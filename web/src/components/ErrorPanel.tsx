import { Alert, Button, Stack, Typography } from "@mui/material";
export function ErrorPanel({
  title,
  message,
  requestId,
  retry = false,
}: {
  title: string;
  message: string;
  requestId?: string;
  retry?: boolean;
}) {
  return (
    <Stack spacing={2}>
      <Typography component="h1" variant="h1">
        {title}
      </Typography>
      <Alert severity="error">{message}</Alert>
      {requestId && (
        <Typography variant="body2">Reference: {requestId}</Typography>
      )}
      <Stack direction="row" spacing={2}>
        {retry && (
          <Button href="/account" variant="outlined">
            Try again
          </Button>
        )}
        <Button href="/account">Return to account</Button>
      </Stack>
    </Stack>
  );
}
