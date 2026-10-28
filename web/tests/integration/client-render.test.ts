import { expect, it } from "vitest";
import { createElement } from "react";
import { renderToString } from "react-dom/server";
import { CredentialPanel } from "../../src/features/clients/CredentialPanel";
import { ClientDetail } from "../../src/contracts/clients";
import { PermissionMatrix } from "../../src/features/roles/PermissionMatrix";
it("credential SSR renders canonical UTC and defers browser-local expiry to hydration", () => {
  const id = "11111111-1111-4111-8111-111111111111",
    expirationDate = "2030-01-02T03:04:05Z";
  const detail = ClientDetail.parse({
    id,
    orgId: id,
    clientId: "public",
    active: true,
    name: "Client",
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
  const html = renderToString(
    createElement(CredentialPanel, {
      detail,
      api: "/not-fetched-during-ssr",
      editable: false,
      refresh: async () => {},
      credentials: [
        {
          id,
          name: "metadata",
          type: "SHARED_SECRET",
          algorithm: "HS256",
          status: "Active",
          expirationDate,
          isExpired: false,
        },
      ],
    }),
  );
  expect(html.includes("2030-01-02T03:04:05.000Z")).toBe(true);
  expect(html.includes(new Date(expirationDate).toLocaleString())).toBe(false);
});

it("client detail shows only derived grant types and fixed supported protocol scopes", async () => {
  const { ClientDetails } = await import(
    "../../src/features/clients/ClientDetails"
  );
  const id = "11111111-1111-4111-8111-111111111111";
  const detail = ClientDetail.parse({
    id,
    orgId: id,
    clientId: "public",
    active: true,
    name: "Client",
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
  const html = renderToString(
    createElement(ClientDetails, {
      initial: {
        detail,
        urls: {
          applicationUrl: null,
          redirectUrls: [],
          logoutRedirectUrls: [],
        },
        credentials: [],
        assignments: [],
      },
      catalog: [],
      context: {
        subject: {
          userId: id,
          loginId: id,
          username: "Reader",
          organizationId: id,
        },
        organizations: [{ id, name: "Owned" }],
        permissions: ["idp-admin-app:read"],
        capabilities: {
          roleWrite: false,
          userWrite: false,
          resourceWrite: false,
          providerMode: "unconfigured",
        },
      },
    }),
  );
  expect(html.includes("client_credentials")).toBe(true);
  expect(
    html.includes(
      "openid, profile, email, metadata, idp-admin, read, confidential-client",
    ),
  ).toBe(true);
  expect(
    html.includes('name="grantTypes"') || html.includes('name="scopes"'),
  ).toBe(false);
});

function grant(key: string, name: string) {
  return {
    id: "11111111-1111-4111-8111-111111111111",
    key,
    name,
    description: null,
    status: "ACTIVE" as const,
  };
}

it("permission matrix separates ceiling grants from above-ceiling grants without a catalog", () => {
  const html = renderToString(
    createElement(PermissionMatrix, {
      catalog: [],
      grants: [
        grant("idp-admin-org:read", "Organization: Read"),
        grant("idp-admin-user:delete", "User: Delete"),
      ],
      selected: ["idp-admin-org:read", "idp-admin-user:delete"],
      ceiling: ["idp-admin-org:read"],
      legend: "Current permissions",
    }),
  );
  expect(html.split("current grant").length - 1).toBe(1);
  expect(html).toContain("idp-admin-user:delete");
  expect(html).toContain("Organization: Read");
  expect(html).not.toContain("No permissions are available to display.");
  expect(html).toContain("Permission descriptions require platform administration access");
  expect(html).toContain("Permission changes require platform administration access");
});

it("permission matrix keeps catalog descriptions when the catalog is available", () => {
  const html = renderToString(
    createElement(PermissionMatrix, {
      catalog: [
        {
          key: "idp-admin-org:read",
          description: "Read organizations",
          platformOnly: false,
        },
      ],
      selected: ["idp-admin-org:read", "idp-admin-user:delete"],
      ceiling: ["idp-admin-org:read"],
      onChange: () => {},
      legend: "Current permissions",
    }),
  );
  expect(html).toContain("Read organizations");
  expect(html.split("current grant").length - 1).toBe(1);
  expect(html).not.toContain(
    "Permission descriptions require platform administration access",
  );
});
