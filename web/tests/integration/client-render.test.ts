import { expect, it } from "vitest";
import { createElement } from "react";
import { renderToString } from "react-dom/server";
import { CredentialPanel } from "../../src/features/clients/CredentialPanel";
import { ClientDetail } from "../../src/contracts/clients";
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
