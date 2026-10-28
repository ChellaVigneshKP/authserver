import { expect, it } from "vitest";
import { z } from "zod";
import { PortalFailure, errorResponse } from "../../src/server/errors";

it("maps malformed input to an actionable 400 that is never retryable", async () => {
  const malformed = z.uuid().safeParse("not-a-uuid");
  if (malformed.success) throw new Error("expected a malformed identifier");
  const response = errorResponse(malformed.error);
  expect(response.status).toBe(400);
  expect(response.headers.get("cache-control")).toContain("no-store");
  expect(response.headers.get("retry-after")).toBeNull();
  await expect(response.json()).resolves.toMatchObject({
    code: "INVALID_INPUT",
    retryable: false,
  });
});

it("keeps known failures and sanitizes unexpected ones", async () => {
  const denied = errorResponse(
    new PortalFailure(403, "ACCESS_DENIED", "Not allowed."),
  );
  expect(denied.status).toBe(403);
  await expect(denied.json()).resolves.toMatchObject({
    code: "ACCESS_DENIED",
    retryable: false,
  });
  const unknown = errorResponse(new Error("SQL error near secret table"));
  expect(unknown.status).toBe(503);
  expect(unknown.headers.get("retry-after")).toBe("5");
  const body = await unknown.json();
  expect(body.retryable).toBe(true);
  expect(JSON.stringify(body)).not.toContain("secret table");
  expect(body.requestId).toMatch(/^[0-9a-f-]{36}$/);
});

it("never advertises a retry for an uncertain non-retryable 503", async () => {
  const uncertain = errorResponse(
    new PortalFailure(
      503,
      "CLIENT_CREATION_UNCERTAIN",
      "The creation may have completed. Reload before trying again.",
      false,
    ),
  );
  expect(uncertain.status).toBe(503);
  expect(uncertain.headers.get("retry-after")).toBeNull();
  await expect(uncertain.json()).resolves.toMatchObject({
    code: "CLIENT_CREATION_UNCERTAIN",
    retryable: false,
  });
});
