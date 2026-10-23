import "server-only";
import { timingSafeEqual } from "node:crypto";
import { PortalFailure } from "./errors";
import type { PortalSession } from "./types";
export async function requireMutation(
  request: Request,
  session: PortalSession,
): Promise<void> {
  const token = request.headers.get("x-csrf-token") ?? "";
  const expected = session.csrfSecret;
  const type = request.headers.get("content-type")?.split(";")[0].trim();
  if (
    !["POST", "PUT", "PATCH", "DELETE"].includes(request.method) ||
    request.headers.get("origin") !== session.browserContext.externalOrigin ||
    type !== "application/json" ||
    Buffer.byteLength(token) !== Buffer.byteLength(expected) ||
    !timingSafeEqual(Buffer.from(token), Buffer.from(expected))
  )
    throw new PortalFailure(
      403,
      "CSRF_REJECTED",
      "This request could not be verified. Refresh the page and try again.",
    );
}
