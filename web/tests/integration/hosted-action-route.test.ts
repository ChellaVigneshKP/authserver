import { afterAll, beforeAll, expect, it, vi } from "vitest";
import { createServer, type Server } from "node:http";
import { randomUUID, createHmac } from "node:crypto";
import { createClient } from "redis";
import { GET as action } from "../../src/app/account/action/route";
import { getStore, randomId } from "../../src/server/session-store";
const namespace = "hosted-action-" + randomUUID(),
  prefix = "portal:v1:" + namespace + ":";
const origin = "http://127.0.0.1:3100",
  login = randomUUID(),
  user = randomUUID();
const key = Buffer.alloc(32, 5);
let issuer: string,
  server: Server,
  authorized = false;
const redis = createClient({
  url: "redis://127.0.0.1:16379/0",
  socket: { reconnectStrategy: false },
});
beforeAll(async () => {
  server = createServer((request, response) => {
    if (request.url !== "/services/api/v1/portal/context") {
      response.statusCode = 404;
      response.end();
      return;
    }
    authorized = request.headers.authorization === "Bearer private-test-access";
    const body = JSON.stringify({
      subject: {
        userId: user,
        loginId: login,
        username: "Owned",
        organizationId: user,
      },
      organizations: [{ id: user, name: "Owned" }],
      permissions: [],
      capabilities: {
        roleWrite: false,
        userWrite: false,
        resourceWrite: false,
        providerMode: "unconfigured",
      },
    });
    response.setHeader(
      "x-signature",
      createHmac("sha256", key).update(body).digest("base64"),
    );
    response.end(body);
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  issuer =
    "http://127.0.0.1:" +
    (server.address() as { port: number }).port +
    "/services";
  for (const [name, value] of Object.entries({
    PORTAL_ORIGIN: origin,
    AUTH_ISSUER: issuer,
    AUTH_CLIENT_ID: "portal-public-client",
    PORTAL_REDIS_URL: "redis://127.0.0.1:16379/0",
    PORTAL_REDIS_NAMESPACE: namespace,
    PORTAL_ALLOW_INSECURE_LOOPBACK: "true",
  }))
    vi.stubEnv(name, value);
  await redis.connect();
});
afterAll(async () => {
  for await (const keys of redis.scanIterator({ MATCH: prefix + "*" }))
    if (keys.length) await redis.del(keys);
  await redis.quit();
  await new Promise<void>((resolve) => server.close(() => resolve()));
});
it("fresh Java context supplies stable login GUID rather than JWT/SSO subject name", async () => {
  const now = Date.now();
  const session = await (
    await getStore()
  ).rotateSession({
    id: randomId(),
    subjectId: "jwt-subject-name",
    accessToken: "private-test-access",
    idToken: "private-test-id",
    signingKey: key,
    createdAt: now,
    lastSeenAt: now,
    expiresAt: now + 300000,
    browserContext: {
      userAgent: "OwnedBrowser",
      acceptLanguage: "en-US",
      externalOrigin: origin,
    },
    csrfSecret: randomId(),
  });
  const response = await action(
    new Request(
      origin +
        "/account/action?action=change-password&returnTo=%2Faccount%2Fsecurity",
      {
        headers: {
          "User-Agent": "OwnedBrowser",
          "Accept-Language": "en-US",
          cookie: "portal-session=" + session.id,
        },
      },
    ),
  );
  expect(response.status).toBe(303);
  const target = new URL(response.headers.get("location")!);
  expect(target.origin + target.pathname).toBe(
    issuer + "/portal/account-action",
  );
  expect(target.searchParams.get("expectedLoginId")).toBe(login);
  expect(target.searchParams.get("action")).toBe("password");
  expect(authorized).toBe(true);
  expect(
    [
      session.accessToken,
      session.idToken,
      "jwt-subject-name",
      key.toString("base64"),
    ].some((secret) => target.href.includes(secret)),
  ).toBe(false);
  expect(await response.text()).toBe("");
});
