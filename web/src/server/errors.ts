import "server-only";
import { randomUUID } from "node:crypto";
import type { PortalSession } from "./types";
export type PortalError = {
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
  requestId: string;
  retryable: boolean;
};
export class PortalFailure extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public retryable = false,
  ) {
    super(message);
  }
}
export const noStore = {
  "Cache-Control": "private, no-store, max-age=0",
  Pragma: "no-cache",
  "Referrer-Policy": "no-referrer",
  "X-Content-Type-Options": "nosniff",
};
export function jsonResponse(data: unknown, status = 200) {
  return Response.json(data, { status, headers: noStore });
}
export function errorResponse(error: unknown) {
  const e =
    error instanceof PortalFailure
      ? error
      : new PortalFailure(
          503,
          "SERVICE_UNAVAILABLE",
          "The service is temporarily unavailable. Please try again.",
          true,
        );
  const body: PortalError = {
    code: e.code,
    message: e.message,
    requestId: randomUUID(),
    retryable: e.retryable,
  };
  const response = jsonResponse(body, e.status);
  if (e.status === 503) response.headers.set("Retry-After", "5");
  return response;
}
export function publicSession(session: PortalSession) {
  return jsonResponse({
    authenticated: true,
    expiresAt: Math.min(
      session.expiresAt,
      session.createdAt + 8 * 3600_000,
      session.lastSeenAt + 1800_000,
    ),
    csrfToken: session.csrfSecret,
  });
}
export const signin = () =>
  new PortalFailure(
    401,
    "SIGN_IN_REQUIRED",
    "Your session has ended. Please sign in again.",
  );
