import "server-only";
import { timingSafeEqual } from "node:crypto";
import { PortalFailure } from "./errors";
import type { PortalSession } from "./types";
async function assertMutation(
  request: Request,
  session: PortalSession,
  methods: readonly string[],
  contentType: string,
): Promise<void> {
  const token = request.headers.get("x-csrf-token") ?? "";
  const expected = session.csrfSecret;
  const type = request.headers.get("content-type")?.split(";")[0].trim();
  if (
    !methods.includes(request.method) ||
    request.headers.get("origin") !== session.browserContext.externalOrigin ||
    type !== contentType ||
    Buffer.byteLength(token) !== Buffer.byteLength(expected) ||
    !timingSafeEqual(Buffer.from(token), Buffer.from(expected))
  )
    throw new PortalFailure(
      403,
      "CSRF_REJECTED",
      "This request could not be verified. Refresh the page and try again.",
    );
}

export async function requireMutation(
  request: Request,
  session: PortalSession,
): Promise<void> {
  return assertMutation(
    request,
    session,
    ["POST", "PUT", "PATCH", "DELETE"],
    "application/json",
  );
}

export async function requireMultipartMutation(
  request: Request,
  session: PortalSession,
): Promise<void> {
  return assertMutation(request, session, ["POST"], "multipart/form-data");
}
