import { afterAll, beforeAll, beforeEach, expect, it } from "vitest";
import { createServer, type Server } from "node:http";
import { createHmac } from "node:crypto";
import { z } from "zod";
import { javaRequestWith } from "../../src/server/java-api";
import { parseConfig } from "../../src/server/config";
import { RoleDetail, LegacyRoleList, PermissionCatalog, GroupPermissionList } from "../../src/contracts/roles";
import type { PortalSession } from "../../src/server/types";
let server: Server;
let origin: string;
let calls: { url: string; method: string; valid: boolean; body: string }[] = [];
let responseStatus = 200;
let responseBody = "";
let validResponseSignature = true;
const org = "11111111-1111-4111-8111-111111111111";
const role = "22222222-2222-4222-8222-222222222222";
const user = "33333333-3333-4333-8333-333333333333";
const detail = { id: role, name: "Support", description: null, status: "Active", permissionKeys: ["idp-admin-user:read"], revision: 2, memberCount: 0, readOnly: false };
const s: PortalSession = {
  id: "x".repeat(40),
  subjectId: org,
  accessToken: "synthetic",
  idToken: "synthetic-id",
  signingKey: new Uint8Array(32).fill(7),
  expiresAt: Date.now() + 60000,
  createdAt: Date.now(),
  lastSeenAt: Date.now(),
  browserContext: { userAgent: "RoleTransportTest", acceptLanguage: "en-US", externalOrigin: "http://127.0.0.1:3100" },
  csrfSecret: "x".repeat(40),
};
const config = () => parseConfig({
  PORTAL_ORIGIN: s.browserContext.externalOrigin,
  AUTH_ISSUER: origin + "/services",
  AUTH_CLIENT_ID: "portal",
  PORTAL_REDIS_URL: "redis://127.0.0.1:16379",
  PORTAL_ALLOW_INSECURE_LOOPBACK: "true",
});
const store = { deleteSession: async () => {} } as unknown as Parameters<typeof javaRequestWith>[1];
beforeAll(async () => {
  server = createServer(async (req, res) => {
    let raw = "";
    for await (const chunk of req) raw += chunk;
    calls.push({ url: req.url!, method: req.method!, valid: req.headers["x-signature"] === createHmac("sha256", s.signingKey).update(raw).digest("base64"), body: raw });
    res.writeHead(responseStatus, {
      "x-signature": validResponseSignature ? createHmac("sha256", s.signingKey).update(responseBody).digest("base64") : "unverified",
    });
    res.end(responseStatus === 204 ? "" : responseBody);
  });
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
  origin = "http://127.0.0.1:" + (server.address() as { port: number }).port;
});
afterAll(() => new Promise<void>((r) => server.close(() => r())));
beforeEach(() => { calls = []; responseStatus = 200; responseBody = JSON.stringify(detail); validResponseSignature = true; });
async function fail(operation: Parameters<typeof javaRequestWith>[3], input: unknown = undefined, schema = z.unknown()) {
  try { await javaRequestWith(config(), store, s, operation, input, schema); return null; }
  catch (error) { return error as { status: number; code: string; message: string }; }
}
it("signs every closed role operation on its exact contract route", async () => {
  const base = "/services/api/v1/organizations/" + org + "/groups";
  responseBody = JSON.stringify([]);
  await javaRequestWith(config(), store, s, { kind: "roleList", organizationId: org }, undefined, LegacyRoleList);
  responseBody = JSON.stringify(detail);
  await javaRequestWith(config(), store, s, { kind: "roleGet", organizationId: org, roleId: role }, undefined, RoleDetail);
  responseBody = JSON.stringify([]);
  await javaRequestWith(config(), store, s, { kind: "rolePermissions", organizationId: org, roleId: role }, undefined, GroupPermissionList);
  await javaRequestWith(config(), store, s, { kind: "roleCatalog" }, undefined, PermissionCatalog);
  responseBody = JSON.stringify(detail);
  await javaRequestWith(config(), store, s, { kind: "roleCreate", organizationId: org }, { name: "Support", description: "", permissionKeys: ["idp-admin-user:read"] }, RoleDetail);
  await javaRequestWith(config(), store, s, { kind: "roleUpdate", organizationId: org, roleId: role }, { name: "Support", description: "", permissionKeys: ["idp-admin-user:read"], revision: 2 }, RoleDetail);
  responseStatus = 204; responseBody = "";
  await javaRequestWith(config(), store, s, { kind: "roleDeactivate", organizationId: org, roleId: role }, undefined, z.undefined());
  await javaRequestWith(config(), store, s, { kind: "roleAssign", organizationId: org, userId: user }, { groupId: role }, z.undefined());
  expect(calls.map((c) => [c.method, c.url, c.valid])).toEqual([
    ["GET", base, true],
    ["GET", base + "/" + role, true],
    ["GET", base + "/" + role + "/permissions", true],
    ["GET", "/services/api/v1/portal/permissions", true],
    ["POST", base, true],
    ["PUT", base + "/" + role, true],
    ["DELETE", base + "/" + role, true],
    ["PUT", "/services/api/v1/organizations/" + org + "/users/" + user + "/group", true],
  ]);
  expect(JSON.parse(calls[7].body)).toEqual({ groupId: role });
});
it("surfaces only signature-verified upstream role conflict codes, otherwise stays generic", async () => {
  const cases: [string, RegExp][] = [
    ["ROLE_CONFLICT", /changed since it was opened/],
    ["ROLE_ASSIGNED", /assigned members/],
    ["ROLE_REQUIRES_REVIEW", /protected platform grants/],
    ["MEMBERSHIP_REQUIRES_REVIEW", /legacy or ambiguous memberships/],
    ["LAST_PLATFORM_ADMIN", /final active platform administrator/],
    ["PROFILE_EXISTS", /already exists/],
  ];
  for (const [code, message] of cases) {
    responseStatus = 409;
    responseBody = JSON.stringify({ code, message: "upstream detail", requestId: "synthetic", retryable: false });
    const error = await fail({ kind: "roleUpdate", organizationId: org, roleId: role }, { name: "Support", description: "", permissionKeys: ["idp-admin-user:read"], revision: 2 });
    expect(error).toMatchObject({ status: 409, code });
    expect(error!.message).toMatch(message);
    expect(error!.message).not.toMatch(/upstream detail/);
  }
  responseStatus = 409;
  responseBody = JSON.stringify({ message: "no stable code" });
  expect(await fail({ kind: "roleUpdate", organizationId: org, roleId: role }, { name: "Support", description: "", permissionKeys: ["idp-admin-user:read"], revision: 2 }))
    .toMatchObject({ status: 409, code: "CONFLICT" });
  responseStatus = 409;
  responseBody = JSON.stringify({ code: "ROLE_ASSIGNED", message: "upstream detail" });
  validResponseSignature = false;
  expect(await fail({ kind: "roleDeactivate", organizationId: org, roleId: role }))
    .toMatchObject({ status: 502, code: "INVALID_RESPONSE_SIGNATURE" });
});
it("does not copy an unverified code from non-role operations", async () => {
  responseStatus = 409;
  responseBody = JSON.stringify({ code: "LAST_PLATFORM_ADMIN", message: "forged" });
  expect(await fail({ kind: "organizationUpdate", organizationId: org }, { name: "Org", description: "", status: "Active" }))
    .toMatchObject({ status: 409, code: "CONFLICT" });
  responseStatus = 403;
  responseBody = JSON.stringify({ code: "ROLE_ASSIGNED", message: "forged" });
  expect(await fail({ kind: "roleGet", organizationId: org, roleId: role }))
    .toMatchObject({ status: 403, code: "ACCESS_DENIED" });
});
it("rejects a bodyless assignment or bodied read before any upstream call", async () => {
  expect(await fail({ kind: "roleAssign", organizationId: org, userId: user })).toMatchObject({ status: 400, code: "INVALID_INPUT" });
  expect(await fail({ kind: "roleList", organizationId: org }, { forged: true })).toMatchObject({ status: 400, code: "INVALID_INPUT" });
  expect(calls).toHaveLength(0);
});
