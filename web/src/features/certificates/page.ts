import "server-only";
import { headers } from "next/headers";
import { randomUUID } from "node:crypto";
import { getConfig } from "../../server/config";
import { access } from "./api";
import { PortalFailure } from "../../server/errors";
import type { PortalSession } from "../../server/types";
import type { PortalContext } from "../../contracts/portal";
import { CERTIFICATE_READ_PERMISSIONS } from "../../contracts/certificates";

/** Either certificate-read permission may load the feature; Java re-checks each call. */
export async function loadCertificateFeature<T>(
  path: string,
  orgId: string | undefined,
  load: (
    session: PortalSession,
    org: string,
    context: PortalContext,
  ) => Promise<T>,
) {
  try {
    const { session, context, organizationId } = await access(
      new Request(getConfig().origin + path, { headers: await headers() }),
      orgId,
      [...CERTIFICATE_READ_PERMISSIONS],
    );
    return {
      ok: true as const,
      context,
      data: await load(session, organizationId, context),
    };
  } catch (error) {
    const e =
      error instanceof PortalFailure
        ? error
        : new PortalFailure(
            503,
            "SERVICE_UNAVAILABLE",
            "The current configuration could not be read. Refresh to try again.",
            true,
          );
    return {
      ok: false as const,
      canSignOut: e.status !== 401,
      status: e.status,
      message: e.message,
      requestId: randomUUID(),
    };
  }
}
