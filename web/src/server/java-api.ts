import "server-only";
import { createHmac, randomUUID, timingSafeEqual } from "node:crypto";
import type { ZodType } from "zod";
import { z } from "zod";
import { getConfig, type Config } from "./config";
import { getStore, type SessionStore } from "./session-store";
import { boundedBody, transportUrl, upstreamFetch } from "./auth";
import { PortalFailure, signin } from "./errors";
import type { PortalSession } from "./types";
export type JavaOperation =
  | { kind: "context" }
  | { kind: "clients"; organizationId: string };
export async function javaRequestWith<T>(
  config: Config,
  store: SessionStore,
  session: PortalSession,
  operation: JavaOperation,
  input: unknown,
  schema: ZodType<T>,
): Promise<T> {
  if (session.expiresAt <= Date.now()) {
    await store.deleteSession(session.id);
    throw signin();
  }
  let path: string;
  switch (operation.kind) {
    case "context":
      path = "/api/v1/portal/context";
      break;
    case "clients":
      path =
        "/api/v1/organizations/" +
        z.uuid().parse(operation.organizationId) +
        "/applications";
      break;
    default:
      throw new PortalFailure(
        400,
        "INVALID_OPERATION",
        "This operation is unavailable.",
      );
  }
  if (input !== undefined)
    throw new PortalFailure(
      400,
      "INVALID_INPUT",
      "This operation does not accept input.",
    );
  const raw = Buffer.alloc(0);
  const signature = createHmac("sha256", session.signingKey)
    .update(raw)
    .digest("base64");
  const response = await upstreamFetch(
    transportUrl(config, config.issuer + path),
    {
      headers: {
        Authorization: "Bearer " + session.accessToken,
        "User-Agent": session.browserContext.userAgent,
        "Accept-Language": session.browserContext.acceptLanguage,
        Referer: session.browserContext.externalOrigin + "/",
        "x-request-datetime": new Date().toISOString(),
        "x-request-id": randomUUID(),
        "x-signature": signature,
      },
    },
  );
  if (response.status === 401) {
    await store.deleteSession(session.id);
    throw signin();
  }
  const bytes = await boundedBody(response, 1024 * 1024);
  const expected = createHmac("sha256", session.signingKey)
    .update(bytes)
    .digest("base64");
  const actual = response.headers.get("x-signature") ?? "";
  if (
    actual.length !== expected.length ||
    !timingSafeEqual(Buffer.from(actual), Buffer.from(expected))
  )
    throw new PortalFailure(
      502,
      "INVALID_RESPONSE_SIGNATURE",
      "The identity service response could not be verified.",
    );
  if (!response.ok) {
    const messages: Record<number, [string, string]> = {
      400: ["INVALID_INPUT", "Check the supplied values."],
      403: ["ACCESS_DENIED", "You do not have access to this action."],
      404: ["NOT_FOUND", "The requested item was not found."],
      409: ["CONFLICT", "This item changed. Refresh and review it."],
      503: [
        "DEPENDENCY_UNAVAILABLE",
        "The identity service is temporarily unavailable.",
      ],
    };
    const [code, message] = messages[response.status] ?? [
      "UPSTREAM_FAILURE",
      "The identity service could not complete the request.",
    ];
    throw new PortalFailure(
      response.status in messages ? response.status : 502,
      code,
      message,
      response.status === 503,
    );
  }
  try {
    return schema.parse(JSON.parse(Buffer.from(bytes).toString()));
  } catch {
    throw new PortalFailure(
      502,
      "UPSTREAM_INVALID",
      "The identity service returned an invalid response.",
    );
  }
}
export async function javaRequest<T>(
  session: PortalSession,
  operation: JavaOperation,
  input: unknown,
  schema: ZodType<T>,
): Promise<T> {
  return javaRequestWith(
    getConfig(),
    await getStore(),
    session,
    operation,
    input,
    schema,
  );
}
