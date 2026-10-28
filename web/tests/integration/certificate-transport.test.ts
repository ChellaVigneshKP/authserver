import { afterAll, beforeAll, expect, it } from "vitest";
import { createServer, type Server } from "node:http";
import { createHmac } from "node:crypto";
import { z } from "zod";
import {
  javaDownloadWith,
  javaMultipartWith,
} from "../../src/server/java-api";
import { parseConfig } from "../../src/server/config";
import type { SessionStore } from "../../src/server/session-store";
import type { PortalSession } from "../../src/server/types";
import { buildCertificateMultipart } from "../../src/server/multipart";
import { MAX_CERTIFICATE_DOWNLOAD_BYTES } from "../../src/contracts/certificates";

let server: Server;
let origin: string;
let seen: {
  url: string;
  method: string;
  valid: boolean;
  contentType: string;
  body: Buffer;
}[] = [];
let status = 201;
let responseBody = Buffer.from("{}");
let responseType = "text/plain; charset=utf-8";
let disposition = 'attachment; filename="team cert.pem"';
let validSignature = true;
let deleted: string[] = [];
const id = "11111111-1111-4111-8111-111111111111";
const session: PortalSession = {
  id: "x".repeat(40),
  subjectId: id,
  accessToken: "synthetic",
  idToken: "synthetic-id",
  signingKey: new Uint8Array(32).fill(3),
  expiresAt: Date.now() + 60000,
  createdAt: Date.now(),
  lastSeenAt: Date.now(),
  browserContext: {
    userAgent: "CertificateTransportTest",
    acceptLanguage: "en-US",
    externalOrigin: "http://127.0.0.1:3100",
  },
  csrfSecret: "x".repeat(40),
};
const store = {
  deleteSession: async (value: string) => {
    deleted.push(value);
  },
} as unknown as SessionStore;

beforeAll(async () => {
  server = createServer(async (req, res) => {
    const chunks: Buffer[] = [];
    for await (const chunk of req) chunks.push(Buffer.from(chunk));
    const body = Buffer.concat(chunks);
    seen.push({
      url: req.url!,
      method: req.method!,
      valid:
        req.headers["x-signature"] ===
        createHmac("sha256", session.signingKey).update(body).digest("base64"),
      contentType: String(req.headers["content-type"] ?? ""),
      body,
    });
    res.writeHead(status, {
      "content-type": responseType,
      "content-disposition": disposition,
      "x-signature": validSignature
        ? createHmac("sha256", session.signingKey)
            .update(responseBody)
            .digest("base64")
        : "unverified",
    });
    res.end(responseBody);
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  origin = "http://127.0.0.1:" + (server.address() as { port: number }).port;
});
afterAll(() => new Promise<void>((resolve) => server.close(() => resolve())));

function config() {
  return parseConfig({
    PORTAL_ORIGIN: session.browserContext.externalOrigin,
    AUTH_ISSUER: origin + "/services",
    AUTH_CLIENT_ID: "portal",
    PORTAL_REDIS_URL: "redis://127.0.0.1:16379",
    PORTAL_ALLOW_INSECURE_LOOPBACK: "true",
  });
}
function reset() {
  seen = [];
  deleted = [];
  status = 201;
  responseBody = Buffer.from("{}");
  responseType = "text/plain; charset=utf-8";
  disposition = 'attachment; filename="team cert.pem"';
  validSignature = true;
}
const upload = buildCertificateMultipart({
  name: "Signing",
  type: "ORGANIZATION",
  password: "synthetic-password",
  file: {
    filename: "client.pem",
    contentType: "application/x-pem-file",
    data: Buffer.from("PEM"),
  },
});

it("signs the exact rebuilt multipart body and posts only the canonical parts", async () => {
  reset();
  responseBody = Buffer.from(JSON.stringify({ id }));
  const created = await javaMultipartWith(
    config(),
    store,
    session,
    { kind: "certificateCreate", organizationId: id },
    upload,
    z.object({ id: z.uuid() }),
  );
  expect(seen).toHaveLength(1);
  expect(seen[0].method).toBe("POST");
  expect(seen[0].url).toBe("/services/api/v1/organizations/" + id + "/certificates");
  expect(seen[0].valid).toBe(true);
  expect(seen[0].contentType).toBe(
    "multipart/form-data; boundary=" + upload.boundary,
  );
  expect(seen[0].body.equals(upload.body)).toBe(true);
  expect(seen[0].body.toString()).not.toContain("client.pem");
});

it("returns bounded public certificate bytes with a sanitized filename", async () => {
  reset();
  status = 200;
  responseBody = Buffer.from("-----BEGIN CERTIFICATE-----\npublic\n-----END CERTIFICATE-----\n");
  const download = await javaDownloadWith(config(), store, session, {
    kind: "certificateDownload",
    organizationId: id,
    certificateId: id,
  });
  expect(download.filename).toBe("team_cert.pem");
  expect(Buffer.from(download.bytes).toString()).toBe(responseBody.toString());
  expect(seen[0].url).toBe(
    "/services/api/v1/organizations/" + id + "/certificates/" + id + "/download",
  );
  expect(seen[0].valid).toBe(true);
});

it("rejects unverified, oversized and non-text downloads without exposing bytes", async () => {
  reset();
  status = 200;
  responseBody = Buffer.from("data");
  validSignature = false;
  await expect(
    javaDownloadWith(config(), store, session, {
      kind: "certificateDownload",
      organizationId: id,
      certificateId: id,
    }),
  ).rejects.toMatchObject({ code: "INVALID_RESPONSE_SIGNATURE", status: 502 });

  reset();
  status = 200;
  responseBody = Buffer.alloc(MAX_CERTIFICATE_DOWNLOAD_BYTES + 1);
  await expect(
    javaDownloadWith(config(), store, session, {
      kind: "certificateDownload",
      organizationId: id,
      certificateId: id,
    }),
  ).rejects.toMatchObject({ status: 502 });

  reset();
  status = 200;
  responseBody = Buffer.from("data");
  responseType = "application/octet-stream";
  await expect(
    javaDownloadWith(config(), store, session, {
      kind: "certificateDownload",
      organizationId: id,
      certificateId: id,
    }),
  ).rejects.toMatchObject({ status: 502 });
});

it("invalidates the session on an unverified 401 for multipart and download", async () => {
  reset();
  status = 401;
  responseBody = Buffer.from("");
  await expect(
    javaMultipartWith(
      config(),
      store,
      session,
      { kind: "certificateCreate", organizationId: id },
      upload,
      z.object({ id: z.uuid() }),
    ),
  ).rejects.toMatchObject({ status: 401, code: "SIGN_IN_REQUIRED" });
  expect(deleted).toEqual([session.id]);

  reset();
  status = 401;
  responseBody = Buffer.from("");
  await expect(
    javaDownloadWith(config(), store, session, {
      kind: "certificateDownload",
      organizationId: id,
      certificateId: id,
    }),
  ).rejects.toMatchObject({ status: 401, code: "SIGN_IN_REQUIRED" });
  expect(deleted).toEqual([session.id]);
});
