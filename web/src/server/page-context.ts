import "server-only";
import { headers } from "next/headers";
import { randomUUID } from "node:crypto";
import { requireSession } from "./auth";
import { getConfig } from "./config";
import { getPortalContext } from "./portal-context";
import { PortalFailure } from "./errors";
export async function loadPageContext(path: string) {
  let canSignOut = false;
  try {
    const session = await requireSession(
      new Request(getConfig().origin + path, { headers: await headers() }),
    );
    canSignOut = true;
    const context = await getPortalContext(session);
    return { ok: true as const, context };
  } catch (error) {
    const e =
      error instanceof PortalFailure
        ? error
        : new PortalFailure(
            503,
            "SERVICE_UNAVAILABLE",
            "The service is temporarily unavailable. Please try again.",
            true,
          );
    return {
      ok: false as const,
      canSignOut: canSignOut && e.status !== 401,
      status: e.status,
      message: e.message,
      requestId: randomUUID(),
    };
  }
}
