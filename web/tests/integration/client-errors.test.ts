import { describe, expect, it } from "vitest";
import {
  ClientCreate,
  ClientUpdate,
  UrlInput,
  TokenInput,
  CredentialCreate,
  ClientDetail,
  CredentialMetadata,
  getClientReadiness,
} from "../../src/contracts/clients";
import {
  ResourceInput,
  assignmentToBrowser,
} from "../../src/contracts/resources";
const id = "11111111-1111-4111-8111-111111111111";
describe("client input boundaries", () => {
  it.each([
    ["WEB", "PKCE", true],
    ["MOBILE", "PKCE", true],
    ["SERVER", "CLIENT_SECRET_JWT", true],
    ["SERVER", "PRIVATE_KEY_JWT", true],
    ["SERVER", "PKCE", false],
    ["WEB", "CLIENT_SECRET_JWT", false],
    ["MOBILE", "PRIVATE_KEY_JWT", false],
  ])("type %s auth %s valid=%s", (type, authMethod, valid) =>
    expect(
      ClientCreate.safeParse({
        name: "Service",
        description: "",
        type,
        authMethod,
      }).success,
    ).toBe(valid),
  );
  it("rejects immutable type/auth and invented scopes/grants on update", () => {
    for (const field of ["type", "authMethod", "scopes", "grantTypes"])
      expect(
        ClientUpdate.safeParse({
          name: "Service",
          description: "",
          [field]: "PKCE",
        }).success,
      ).toBe(false);
  });
  it("keeps exact redirects and rejects duplicates, fragments, credentials, whitespace and wildcards", () => {
    const good = {
      applicationUrl: "http://localhost:3100",
      returnUrls: ["http://localhost:3100/auth/callback?fixed=1"],
      logoutUrls: ["http://localhost:3100/bye"],
    };
    expect(UrlInput.parse(good).returnUrls[0]).toBe(
      "http://localhost:3100/auth/callback?fixed=1",
    );
    for (const returnUrls of [
      [good.returnUrls[0], good.returnUrls[0]],
      ["https://example.com/#frag"],
      ["https://u:p@example.com/cb"],
      [" https://example.com/cb"],
      ["https://example.com/*"],
    ])
      expect(UrlInput.safeParse({ ...good, returnUrls }).success).toBe(false);
  });
  it("requires integer seconds and explicit refresh reuse", () => {
    const v = {
      authCodeTimeToLive: 120,
      accessTokenTimeToLive: 600,
      refreshTokenTimeToLive: 3600,
      deviceCodeTimeToLive: 300,
      maxRequestTransitTime: 30,
      reuseRefreshTokens: false,
    };
    expect(TokenInput.safeParse(v).success).toBe(true);
    for (const accessTokenTimeToLive of [0, 1.5, "600", -1])
      expect(
        TokenInput.safeParse({ ...v, accessTokenTimeToLive }).success,
      ).toBe(false);
    expect(
      TokenInput.safeParse({ ...v, reuseRefreshTokens: undefined }).success,
    ).toBe(false);
  });
  it("limits credential inputs to supported algorithms/types and public certificate ids", () => {
    expect(
      CredentialCreate.safeParse({
        name: "rotation",
        type: "SHARED_SECRET",
        algorithm: "HS256",
      }).success,
    ).toBe(true);
    expect(
      CredentialCreate.safeParse({
        name: "rotation",
        type: "PRIVATE_KEY",
        algorithm: "RS256",
        certificateId: id,
      }).success,
    ).toBe(true);
    for (const v of [
      { name: "x", type: "PRIVATE_KEY", algorithm: "RS256" },
      { name: "x", type: "SHARED_SECRET", algorithm: "RS256" },
      { name: "x", type: "SHARED_SECRET", algorithm: "HS256", secretId: 1 },
    ])
      expect(CredentialCreate.safeParse(v).success).toBe(false);
  });
});
it("readiness derives required readback and treats unknown credential expiry as unproven", () => {
  const detail = ClientDetail.parse({
    active: true,
    id,
    orgId: id,
    clientId: "oauth-public-id",
    name: "Service",
    description: "",
    uri: null,
    type: "SERVER",
    authenticationMethod: "CLIENT_SECRET_JWT",
    authCodeTimeToLive: 120,
    accessTokenTimeToLive: 600,
    refreshTokenTimeToLive: 3600,
    deviceCodeTimeToLive: 300,
    maxRequestTransitTime: 30,
    reuseRefreshTokens: false,
  });
  const urls = {
    applicationUrl: "https://example.com",
    redirectUrls: ["https://example.com/cb"],
    logoutRedirectUrls: ["https://example.com/bye"],
  };
  const unknown = CredentialMetadata.parse({
    id,
    name: "key",
    type: "SHARED_SECRET",
    algorithm: "HS256",
    status: "Active",
    expirationDate: "",
    isExpired: false,
  });
  expect(getClientReadiness(detail, urls, [unknown]).ready).toBe(false);
  expect(
    getClientReadiness(detail, urls, [
      { ...unknown, expirationDate: "2099-01-01T00:00:00Z" },
    ]).ready,
  ).toBe(true);
  expect(getClientReadiness({ ...detail, active: false }, urls, []).ready).toBe(
    false,
  );
});
it("browser assignment mapping keeps GUIDs distinct and strips inherited SQL fields", () => {
  const dto = assignmentToBrowser({
    id,
    name: "Read",
    description: "Read API",
    uri: "https://api.example.test/api/data",
    allowedMethod: "GET",
    urn: null,
    resourceId: 18,
    resourceLibraryId: 9,
  } as Parameters<typeof assignmentToBrowser>[0]);
  expect(dto.catalogResourceId).toBe(id);
  expect("id" in dto).toBe(false);
  expect("resourceId" in dto || "resourceLibraryId" in dto).toBe(false);
});
it("resource input offers actual method/URI/URN fields without catalog delete authority", () => {
  expect(
    ResourceInput.safeParse({
      name: "Read",
      description: "Read API",
      uri: "https://api.example.test/api/data",
      allowedMethod: "GET",
      urn: "urn:example:read",
    }).success,
  ).toBe(true);
  expect(
    ResourceInput.safeParse({
      name: "Read",
      description: "Read API",
      uri: "https://api.example.test/api/data",
      allowedMethod: "EXEC",
      urn: "bad",
    }).success,
  ).toBe(false);
});

it("normalizes inherited naive credential expiry as UTC and rejects malformed timestamps without echoing", async () => {
  const { credentialInstant } = await import("../../src/contracts/clients");
  expect(credentialInstant("2030-01-02T03:04:05")).toBe(
    Date.UTC(2030, 0, 2, 3, 4, 5),
  );
  expect(credentialInstant("2030-01-02")).toBe(Date.UTC(2030, 0, 2));
  expect(credentialInstant("2030-01-02T03:04:05+05:30")).toBe(
    Date.UTC(2030, 0, 1, 21, 34, 5),
  );
  expect(credentialInstant("invalid")).toBeNull();
});

it("malformed public GUID validation returns a permanent safe400, never dependency retry guidance", async () => {
  const { respond } = await import("../../src/features/clients/api");
  const { z } = await import("zod");
  const response = await respond(async () => z.uuid().parse("invalid"));
  expect(response.status).toBe(400);
  const data = await response.json();
  expect(data.retryable).toBe(false);
  expect(data.code).toBe("INVALID_INPUT");
  expect(JSON.stringify(data).includes("invalid")).toBe(false);
});

it("guided progress links the next missing readback step, treats PKCE credentials as unnecessary and shows actual optional resource count", async () => {
  const { getClientProgress } = await import("../../src/contracts/clients");
  const detail = ClientDetail.parse({
    id,
    orgId: id,
    clientId: "public",
    active: true,
    name: "Client",
    description: "",
    uri: null,
    type: "WEB",
    authenticationMethod: "PKCE",
    authCodeTimeToLive: 120,
    accessTokenTimeToLive: 600,
    refreshTokenTimeToLive: 3600,
    deviceCodeTimeToLive: 300,
    maxRequestTransitTime: 30,
    reuseRefreshTokens: false,
  });
  const empty = {
    applicationUrl: null,
    redirectUrls: [],
    logoutRedirectUrls: [],
  };
  const urls = {
    applicationUrl: "https://example.test",
    redirectUrls: ["https://example.test/callback"],
    logoutRedirectUrls: ["https://example.test/bye"],
  };
  expect(getClientProgress(detail, empty, [], 0).nextId).toBe("client-urls");
  const ready = getClientProgress(detail, urls, [], 0);
  expect(ready.nextId).toBe("client-readiness");
  expect(ready.steps.find((v) => v.id === "client-credentials")?.status).toBe(
    "Not required for PKCE",
  );
  expect(ready.steps.find((v) => v.id === "client-resources")).toMatchObject({
    required: false,
    status: "0 assigned",
  });
  expect(
    getClientProgress({ ...detail, reuseRefreshTokens: null }, urls, [], 2)
      .nextId,
  ).toBe("client-settings");
  const confidential = getClientProgress(
    { ...detail, type: "SERVER", authenticationMethod: "CLIENT_SECRET_JWT" },
    urls,
    [],
    3,
  );
  expect(confidential.nextId).toBe("client-credentials");
  expect(
    confidential.steps.find((v) => v.id === "client-resources")?.status,
  ).toBe("3 assigned");
});
