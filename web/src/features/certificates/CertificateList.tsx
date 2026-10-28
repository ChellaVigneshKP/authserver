"use client";
import { useEffect, useState } from "react";
import { Alert, Button, Link, Stack, Typography } from "@mui/material";
import type { PortalContext } from "../../contracts/portal";
import {
  certificateExpiry,
  certificateStatusLabel,
  certificateTypeLabel,
  type CertificateSummary,
  type CertificateTypeValue,
} from "../../contracts/certificates";
import { canShow } from "../account/permissions";
import { DataTable } from "../../components/DataTable";
import { EmptyState } from "../../components/EmptyState";
import { actionMessage, requestJson } from "../clients/browser-api";
import { CertificateImportForm } from "./CertificateImportForm";

/**
 * The server has no browser timezone, so the UTC instant is always rendered and
 * the local instant is filled in after hydration to keep SSR deterministic.
 */
export function CertificateExpiry({ value }: { value: string }) {
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  const date = certificateExpiry(value);
  if (!date) return "Unreadable validity date";
  return (
    <>
      <Typography variant="body2">UTC: {date.toISOString()}</Typography>
      <Typography variant="body2">
        Local:{" "}
        {mounted
          ? date.toLocaleString("en-GB", { timeZoneName: "short" })
          : "Available after page load"}
      </Typography>
    </>
  );
}

export function CertificateList({
  initial,
  orgId,
  context,
}: {
  initial: CertificateSummary[];
  orgId: string;
  context: PortalContext;
}) {
  const [items, setItems] = useState(initial);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const endpoint = "/api/admin/organizations/" + orgId + "/certificates";
  async function reload() {
    setPending(true);
    try {
      setItems(
        (await requestJson<{ items: CertificateSummary[] }>(endpoint)).items,
      );
      setError("");
    } catch (e) {
      setError(actionMessage(e));
    } finally {
      setPending(false);
    }
  }
  const importableTypes: CertificateTypeValue[] = [];
  if (canShow(context, "idp-admin-org-cert:create", orgId))
    importableTypes.push("ORGANIZATION");
  if (canShow(context, "idp-admin-cred-cert:create", orgId))
    importableTypes.push("PUBLIC_KEY");
  return (
    <Stack spacing={3}>
      <Typography component="h1" variant="h1">
        Certificates
      </Typography>
      <Typography color="text.secondary">
        Organization signing certificates and client public keys are imported here.
        Only the public certificate can be downloaded; private keys never leave the
        upload and are never displayed again.
      </Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {items.length ? (
        <DataTable
          caption="Certificates registered in the selected organization"
          rows={items}
          rowKey={(item) => item.id}
          columns={[
            {
              key: "name",
              label: "Name",
              render: (item) => (
                <Link
                  href={
                    "/admin/organizations/" +
                    orgId +
                    "/certificates/" +
                    item.id
                  }
                >
                  {item.name}
                </Link>
              ),
            },
            {
              key: "type",
              label: "Type",
              render: (item) => certificateTypeLabel(item.type),
            },
            {
              key: "status",
              label: "Status",
              render: (item) =>
                certificateStatusLabel(item.status, item.isExpired),
            },
            {
              key: "validity",
              label: "Valid until",
              render: (item) => <CertificateExpiry value={item.validTO} />,
            },
          ]}
        />
      ) : (
        <EmptyState
          title="No certificates yet"
          description="Import an organization signing certificate or a client public key to begin."
        />
      )}
      <Stack direction="row" spacing={2}>
        <Button disabled={pending} onClick={() => void reload()}>
          Reload certificates
        </Button>
      </Stack>
      {importableTypes.length > 0 && (
        <CertificateImportForm
          orgId={orgId}
          types={importableTypes}
          onImported={reload}
        />
      )}
    </Stack>
  );
}
