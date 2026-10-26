import { afterAll, beforeAll, expect, it } from "vitest";
import { createServer, type Server } from "node:http";
import { createHmac } from "node:crypto";
import { z } from "zod";
import {
  javaRequestWith,
  VerifiedClientCreateRejection,
} from "../../src/server/java-api";
import { parseConfig } from "../../src/server/config";
import type { PortalSession } from "../../src/server/types";
let server: Server;
let origin: string;
let calls: { url: string; method: string; valid: boolean }[] = [];
let responseStatus = 200,
  validResponseSignature = true;
const id = "11111111-1111-4111-8111-111111111111";
const s: PortalSession = {
  id: "x".repeat(40),
  subjectId: id,
  accessToken: "synthetic",
  idToken: "synthetic-id",
  signingKey: new Uint8Array(32).fill(3),
  expiresAt: Date.now() + 60000,
  createdAt: Date.now(),
  lastSeenAt: Date.now(),
  browserContext: {
    userAgent: "TransportTest",
    acceptLanguage: "en-US",
    externalOrigin: "http://127.0.0.1:3100",
  },
  csrfSecret: "x".repeat(40),
};
beforeAll(async () => {
  server = createServer(async (req, res) => {
    let raw = "";
    for await (const chunk of req) raw += chunk;
    calls.push({
      url: req.url!,
      method: req.method!,
      valid:
        req.headers["x-signature"] ===
        createHmac("sha256", s.signingKey).update(raw).digest("base64"),
    });
    const body = req.method === "DELETE" ? "" : JSON.stringify({ id });
    res.writeHead(
      responseStatus === 200
        ? req.method === "DELETE"
          ? 204
          : 200
        : responseStatus,
      {
        "x-signature": validResponseSignature
          ? createHmac("sha256", s.signingKey).update(body).digest("base64")
          : "unverified",
      },
    );
    res.end(body);
  });
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
  origin = "http://127.0.0.1:" + (server.address() as { port: number }).port;
});
afterAll(() => new Promise<void>((r) => server.close(() => r())));
it("closed client write operations sign exact JSON and preserve empty204 responses", async () => {
  const config = parseConfig({
    PORTAL_ORIGIN: s.browserContext.externalOrigin,
    AUTH_ISSUER: origin + "/services",
    AUTH_CLIENT_ID: "portal",
    PORTAL_REDIS_URL: "redis://127.0.0.1:16379",
    PORTAL_ALLOW_INSECURE_LOOPBACK: "true",
  });
  const store = { deleteSession: async () => {} } as unknown as Parameters<
    typeof javaRequestWith
  >[1];
  await javaRequestWith(
    config,
    store,
    s,
    { kind: "clientCreate", organizationId: id },
    { name: "Sample" },
    z.object({ id: z.uuid() }),
  );
  await javaRequestWith(
    config,
    store,
    s,
    {
      kind: "credentialDelete",
      organizationId: id,
      applicationId: id,
      credentialId: id,
    },
    undefined,
    z.undefined(),
  );
  expect(calls).toEqual([
    {
      url: "/services/api/v1/organizations/" + id + "/applications",
      method: "POST",
      valid: true,
    },
    {
      url:
        "/services/api/v1/organizations/" +
        id +
        "/applications/" +
        id +
        "/credentials/" +
        id,
      method: "DELETE",
      valid: true,
    },
  ]);
  await expect(
    javaRequestWith(
      config,
      store,
      s,
      { kind: "context" },
      { unexpected: true },
      z.unknown(),
    ),
  ).rejects.toMatchObject({ status: 400 });
  expect(calls.length).toBe(2);
});

it("only signature-verified create validation/conflict/access rejections carry definitive noncommit authority", async () => {
  const config = parseConfig({
    PORTAL_ORIGIN: s.browserContext.externalOrigin,
    AUTH_ISSUER: origin + "/services",
    AUTH_CLIENT_ID: "portal",
    PORTAL_REDIS_URL: "redis://127.0.0.1:16379",
    PORTAL_ALLOW_INSECURE_LOOPBACK: "true",
  });
  const store = { deleteSession: async () => {} } as unknown as Parameters<
    typeof javaRequestWith
  >[1];
  async function failure(kind: "clientCreate" | "clientGet" = "clientCreate") {
    try {
      await javaRequestWith(
        config,
        store,
        s,
        kind === "clientCreate"
          ? { kind, organizationId: id }
          : { kind, organizationId: id, applicationId: id },
        kind === "clientCreate" ? { name: "Sample" } : undefined,
        z.unknown(),
      );
      return null;
    } catch (error) {
      return error;
    }
  }
  try {
    for (const status of [400, 403, 404, 409]) {
      responseStatus = status;
      validResponseSignature = true;
      expect(await failure()).toBeInstanceOf(VerifiedClientCreateRejection);
      expect(await failure("clientGet")).not.toBeInstanceOf(
        VerifiedClientCreateRejection,
      );
    }
    responseStatus = 409;
    validResponseSignature = false;
    expect(await failure()).not.toBeInstanceOf(VerifiedClientCreateRejection);
    responseStatus = 401;
    validResponseSignature = true;
    expect(await failure()).not.toBeInstanceOf(VerifiedClientCreateRejection);
    responseStatus = 503;
    expect(await failure()).not.toBeInstanceOf(VerifiedClientCreateRejection);
  } finally {
    responseStatus = 200;
    validResponseSignature = true;
  }
});
