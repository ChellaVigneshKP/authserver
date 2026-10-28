"use client";
import { useState } from "react";
import { Alert, Box, Button, Stack, Typography } from "@mui/material";
import type { PortalContext } from "../../contracts/portal";
import {
  certificateDeletePermission,
  certificateStatusLabel,
  certificateTypeLabel,
  type CertificateDetailDto,
} from "../../contracts/certificates";
import { canShow } from "../account/permissions";
import { ConfirmAction } from "../../components/ConfirmAction";
import { actionMessage, requestJson } from "../clients/browser-api";
import { CertificateExpiry } from "./CertificateList";

export function CertificateDetails({
  initial,
  orgId,
  context,
}: {
  initial: CertificateDetailDto;
  orgId: string;
  context: PortalContext;
}) {
  const [detail, setDetail] = useState(initial);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const base =
    "/api/admin/organizations/" + orgId + "/certificates/" + detail.id;
  const deletable =
    detail.status !== "INACTIVE" &&
    canShow(context, certificateDeletePermission(detail.type), orgId);
  async function deactivate() {
    if (pending) return;
    setPending(true);
    setError("");
    try {
      await requestJson<{ updated: true }>(base, "DELETE");
      setDetail({ ...detail, status: "INACTIVE" });
      setNotice(
        "Certificate deactivated. It can no longer be used for new signatures.",
      );
    } catch (e) {
      setError(actionMessage(e));
    } finally {
      setPending(false);
    }
  }
  return (
    <Stack spacing={3}>
      <Typography component="h1" variant="h1">
        {detail.name}
      </Typography>
      <Typography color="text.secondary">
        {certificateTypeLabel(detail.type)}
      </Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {notice && <Alert severity="success">{notice}</Alert>}
      <Box
        component="dl"
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "1fr", sm: "220px 1fr" },
          gap: 1.5,
          m: 0,
        }}
      >
        <Typography component="dt" sx={{ fontWeight: 600 }}>
          Status
        </Typography>
        <Typography component="dd" sx={{ m: 0 }}>
          {certificateStatusLabel(detail.status, detail.isExpired)}
        </Typography>
        <Typography component="dt" sx={{ fontWeight: 600 }}>
          Valid until
        </Typography>
        <Typography component="dd" sx={{ m: 0 }}>
          <CertificateExpiry value={detail.validTO} />
        </Typography>
        <Typography component="dt" sx={{ fontWeight: 600 }}>
          Fingerprint (SHA-256)
        </Typography>
        <Typography
          component="dd"
          sx={{
            m: 0,
            fontFamily: "monospace",
            overflowWrap: "anywhere",
          }}
        >
          {detail.fingerprint}
        </Typography>
        <Typography component="dt" sx={{ fontWeight: 600 }}>
          Thumbprint
        </Typography>
        <Typography
          component="dd"
          sx={{
            m: 0,
            fontFamily: "monospace",
            overflowWrap: "anywhere",
          }}
        >
          {detail.thumbprint}
        </Typography>
      </Box>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
        <Button
          component="a"
          href={base + "/download"}
          variant="contained"
        >
          Download public certificate (.pem)
        </Button>
        {deletable && (
          <ConfirmAction
            label="Deactivate certificate"
            title="Deactivate this certificate?"
            description="Deactivation takes effect immediately. Credentials that rely on this certificate will no longer validate new signatures. This page does not estimate how many credentials are affected."
            confirmLabel="Deactivate certificate"
            onConfirm={() => void deactivate()}
          />
        )}
      </Stack>
      <Typography color="text.secondary" variant="body2">
        Only the public certificate is available for download. Private keys are
        never exported or displayed by this portal.
      </Typography>
    </Stack>
  );
}
