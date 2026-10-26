"use client";
import { useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import {
  Alert,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
export function SecretOnceDialog({
  secret,
  onErase,
}: {
  secret: string;
  onErase: () => void;
}) {
  const [ack, setAck] = useState(false),
    [message, setMessage] = useState("");
  const input = useRef<HTMLInputElement>(null);
  const pathname = usePathname();
  const erase = useRef(onErase);
  erase.current = onErase;
  useEffect(() => {
    const clear = () => {
      if (input.current) input.current.value = "";
      erase.current();
    };
    const hidden = () => {
      if (document.visibilityState === "hidden") clear();
    };
    window.addEventListener("pagehide", clear);
    document.addEventListener("visibilitychange", hidden);
    return () => {
      window.removeEventListener("pagehide", clear);
      document.removeEventListener("visibilitychange", hidden);
      if (input.current) input.current.value = "";
      erase.current();
    };
  }, [pathname]);
  return (
    <Dialog
      open
      onClose={onErase}
      aria-labelledby="secret-once-title"
      maxWidth="sm"
      fullWidth
    >
      <DialogTitle id="secret-once-title">
        Save your new client secret
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2}>
          <Alert severity="warning">
            This secret is shown once. Save it securely now. Closing this dialog
            or leaving the page removes it from application state; it cannot be
            read again.
          </Alert>
          <TextField
            label="Client secret"
            value={secret}
            inputRef={input}
            slotProps={{
              input: { readOnly: true },
              htmlInput: { autoComplete: "off", "data-qa": "secret-once" },
            }}
          />
          <Button
            onClick={async () => {
              try {
                await navigator.clipboard.writeText(secret);
                setMessage("Copied to your clipboard.");
              } catch {
                setMessage(
                  "Clipboard access is unavailable. Select and copy the secret manually.",
                );
              }
            }}
            data-qa="copy-secret"
          >
            Copy secret
          </Button>
          {message && <Typography role="status">{message}</Typography>}
          <Typography variant="body2">
            Clipboard contents are managed by your browser and operating system.
          </Typography>
          <FormControlLabel
            data-qa="secret-ack"
            control={
              <Checkbox
                checked={ack}
                onChange={(e) => setAck(e.target.checked)}
              />
            }
            label="I saved this secret securely"
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button data-qa="secret-dismiss" onClick={onErase}>
          Dismiss without saving
        </Button>
        <Button
          variant="contained"
          disabled={!ack}
          data-qa="secret-done"
          onClick={onErase}
        >
          Done
        </Button>
      </DialogActions>
    </Dialog>
  );
}
