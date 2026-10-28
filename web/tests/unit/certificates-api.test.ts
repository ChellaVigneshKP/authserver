import { beforeEach, expect, it, vi } from "vitest";

const owned = vi.hoisted(() => ({
  org: "11111111-1111-4111-8111-111111111111",
  csrf: "c".repeat(40),
  origin: "http://127.0.0.1:3100",
  permissions: ["idp-admin-org:switch", "idp-admin-org-cert:create"] as string[],
  calls: [] as { operation: unknown; body: Buffer }[],
}));

vi.mock("../../src/server/auth", () => ({
  requireSession: async () => ({
    id: "s".repeat(40),
    subjectId: owned.org,
    accessToken: "synthetic",
    idToken: "synthetic-id",
    signingKey: new Uint8Array(32),
    expiresAt: Date.now() + 60000,
    createdAt: Date.now(),
    lastSeenAt: Date.now(),
    browserContext: {
      userAgent: "CertificateApiTest",
      acceptLanguage: "en-US",
      externalOrigin: owned.origin,
    },
    csrfSecret: owned.csrf,
  }),
}));

vi.mock("../../src/server/portal-context", () => ({
  getPortalContext: async () => ({
    subject: {
      userId: owned.org,
      loginId: owned.org,
      username: "Synthetic Person",
      organizationId: owned.org,
    },
    organizations: [{ id: owned.org, name: "Owned" }],
    permissions: owned.permissions,
    capabilities: {
      roleWrite: false,
      userWrite: false,
      resourceWrite: false,
      providerMode: "unconfigured",
    },
  }),
}));

vi.mock("../../src/server/java-api", () => ({
  javaMultipart: async (
    _session: unknown,
    operation: unknown,
    body: Buffer,
  ) => {
    owned.calls.push({ operation, body });
    return { id: owned.org };
  },
  javaRequest: async () => {
    throw new Error("unexpected upstream request");
  },
  javaDownload: async () => {
    throw new Error("unexpected upstream download");
  },
}));

import { MAX_CERTIFICATE_REQUEST_BYTES } from "../../src/contracts/certificates";
import { buildCertificateMultipart } from "../../src/server/multipart";
import { importCertificate } from "../../src/features/certificates/api";

beforeEach(() => {
  owned.calls = [];
  owned.permissions = ["idp-admin-org:switch", "idp-admin-org-cert:create"];
});

function streamed(size: number, extra = 0) {
  return new ReadableStream({
    start(controller) {
      controller.enqueue(new Uint8Array(size));
      if (extra) controller.enqueue(new Uint8Array(extra));
      controller.close();
    },
  });
}

function uploadRequest(body: BodyInit, boundary = "synthetic"): Request {
  return new Request(
    owned.origin + "/api/admin/organizations/" + owned.org + "/certificates",
    {
      method: "POST",
      headers: {
        origin: owned.origin,
        "x-csrf-token": owned.csrf,
        "content-type": "multipart/form-data; boundary=" + boundary,
      },
      body,
      duplex: "half",
    } as RequestInit,
  );
}

it("rejects an over-budget upload before any certificate-create forward", async () => {
  await expect(
    importCertificate(
      uploadRequest(streamed(MAX_CERTIFICATE_REQUEST_BYTES, 1)),
      owned.org,
    ),
  ).rejects.toMatchObject({ status: 413, code: "UPLOAD_TOO_LARGE" });
  expect(owned.calls).toHaveLength(0);
});

it("holds the wired budget to exactly the published request ceiling", async () => {
  await expect(
    importCertificate(
      uploadRequest(streamed(MAX_CERTIFICATE_REQUEST_BYTES)),
      owned.org,
    ),
  ).rejects.toMatchObject({ status: 400, code: "INVALID_INPUT" });
  expect(owned.calls).toHaveLength(0);
});

it("forwards one canonical upload to the certificate-create endpoint", async () => {
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
  await expect(
    importCertificate(uploadRequest(upload.body, upload.boundary), owned.org),
  ).resolves.toEqual({ id: owned.org });
  expect(owned.calls).toHaveLength(1);
  expect(owned.calls[0].operation).toEqual({
    kind: "certificateCreate",
    organizationId: owned.org,
  });
  expect(owned.calls[0].body.toString()).not.toContain("client.pem");
});
