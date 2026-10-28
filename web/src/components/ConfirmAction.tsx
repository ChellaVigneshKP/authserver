"use client";
import { useId, useRef, useState } from "react";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
} from "@mui/material";
export function ConfirmAction({
  label,
  title,
  description,
  onConfirm,
  confirmLabel = "Continue",
}: {
  label: string;
  title: string;
  description: string;
  onConfirm: () => void;
  confirmLabel?: string;
}) {
  const [open, setOpen] = useState(false);
  const id = useId();
  const trigger = useRef<HTMLButtonElement>(null);
  function close() {
    setOpen(false);
  }
  return (
    <>
      <Button ref={trigger} variant="outlined" onClick={() => setOpen(true)}>
        {label}
      </Button>
      <Dialog
        open={open}
        onClose={close}
        aria-labelledby={id + "-title"}
        aria-describedby={id + "-description"}
        slotProps={{ transition: { onExited: () => trigger.current?.focus() } }}
      >
        <DialogTitle id={id + "-title"}>{title}</DialogTitle>
        <DialogContent>
          <DialogContentText id={id + "-description"}>
            {description}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button autoFocus onClick={close}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={() => {
              close();
              onConfirm();
            }}
          >
            {confirmLabel}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
