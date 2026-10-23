import { afterAll, beforeAll, expect, it, vi } from "vitest";
import { createServer, type Server } from "node:http";
import { randomUUID } from "node:crypto";
import { createClient } from "redis";
import { generateKeyPair, exportJWK, SignJWT } from "jose";
import { GET as callback } from "../../src/app/auth/callback/route";
import { getStore } from "../../src/server/session-store";
let server: Server, issuer: string;
const namespace = "callbacks-" + randomUUID();
const prefix = "portal:v1:" + namespace + ":";
const context = {
  userAgent: "ConcurrentBrowser",
  acceptLanguage: "en-US",
  externalOrigin: "http://127.0.0.1:3100",
};
const codes = new Map<string, { nonce: string; subject: string }>();
const redemptions: string[] = [];
let jwksUnavailable = false;
const redis = createClient({
  url: "redis://127.0.0.1:16379/0",
  socket: { reconnectStrategy: false },
});
beforeAll(async () => {
  const keys = await generateKeyPair("RS256");
  const jwk = {
    ...(await exportJWK(keys.publicKey)),
    kid: "callback",
    alg: "RS256",
  };
  server = createServer(async (req, res) => {
    if (req.url === "/.well-known/openid-configuration") {
      res.end(
        JSON.stringify({
          issuer,
          authorization_endpoint: issuer + "/authorize",
          token_endpoint: issuer + "/token",
          jwks_uri: issuer + "/jwks",
          end_session_endpoint: issuer + "/logout",
        }),
      );
      return;
    }
    if (req.url === "/jwks") {
      if (jwksUnavailable) {
        res.statusCode = 503;
        res.end("{}");
        return;
      }
      res.end(JSON.stringify({ keys: [jwk] }));
      return;
    }
    if (req.url === "/token") {
      let raw = "";
      for await (const chunk of req) raw += chunk;
      const code = new URLSearchParams(raw).get("code")!;
      const identity = codes.get(code);
      if (!identity) {
        res.statusCode = 400;
        res.end("{}");
        return;
      }
      redemptions.push(code);
      const jwt = await new SignJWT({ nonce: identity.nonce })
        .setProtectedHeader({ alg: "RS256", kid: "callback" })
        .setIssuer(issuer)
        .setAudience("portal")
        .setSubject(identity.subject)
        .setIssuedAt()
        .setExpirationTime("5m")
        .sign(keys.privateKey);
      res.end(
        JSON.stringify({
          access_token: "fixture-" + identity.subject,
          id_token: jwt,
          token_type: "Bearer",
          expires_in: 300,
          signing_key: Buffer.alloc(32, 12).toString("base64"),
        }),
      );
      return;
    }
    res.statusCode = 404;
    res.end();
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  issuer = "http://127.0.0.1:" + (server.address() as { port: number }).port;
  for (const [name, value] of Object.entries({
    PORTAL_ORIGIN: context.externalOrigin,
    AUTH_ISSUER: issuer,
    AUTH_CLIENT_ID: "portal",
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
function request(state: string, binding: string, code: string) {
  return new Request(
    context.externalOrigin +
      "/auth/callback?" +
      new URLSearchParams({ state, code }),
    {
      headers: {
        "user-agent": context.userAgent,
        "accept-language": context.acceptLanguage,
        cookie: "portal-login-" + state + "=" + binding,
      },
    },
  );
}
it("simultaneous real callback handlers validate distinct nonces and never swap subjects or return tokens", async () => {
  const store = await getStore();
  const [a, b] = await Promise.all([
    store.createLoginTransaction("/account", context),
    store.createLoginTransaction("/account", context),
  ]);
  codes.set("alice", { nonce: a.nonce, subject: "alice" });
  codes.set("bob", { nonce: b.nonce, subject: "bob" });
  const responses = await Promise.all([
    callback(request(a.state, a.binding, "alice")),
    callback(request(b.state, b.binding, "bob")),
  ]);
  const sessions = [];
  for (const [index, response] of responses.entries()) {
    expect(response.status).toBe(303);
    expect(response.headers.get("location")).toBe(
      context.externalOrigin + "/account",
    );
    expect(response.headers.get("cache-control")).toContain("no-store");
    expect(await response.text()).toBe("");
    const cookie = response.headers
      .getSetCookie()
      .find((v) => v.startsWith("portal-session="));
    expect(!!cookie).toBe(true);
    const id = cookie!.split(";")[0].split("=")[1];
    const session = await store.getSession(id);
    expect(session?.subjectId).toBe(index === 0 ? "alice" : "bob");
    sessions.push(id);
  }
  expect(sessions[0] !== sessions[1]).toBe(true);
  expect(redemptions.slice().sort()).toEqual(["alice", "bob"]);
  const replay = await callback(request(a.state, a.binding, "alice"));
  expect(replay.headers.get("location")).toBe(
    context.externalOrigin + "/auth/error",
  );
  expect(redemptions.length).toBe(2);
});
it("a swapped browser binding cannot redeem an authorization code or consume the rightful transaction", async () => {
  const store = await getStore();
  const a = await store.createLoginTransaction("/account", context),
    b = await store.createLoginTransaction("/account", context);
  codes.set("rightful", { nonce: a.nonce, subject: "rightful" });
  const before = redemptions.length;
  const denied = await callback(request(a.state, b.binding, "rightful"));
  expect(denied.headers.get("location")).toBe(
    context.externalOrigin + "/auth/error",
  );
  expect(redemptions.length).toBe(before);
  const valid = await callback(request(a.state, a.binding, "rightful"));
  expect(valid.headers.get("location")).toBe(
    context.externalOrigin + "/account",
  );
  expect(redemptions.length).toBe(before + 1);
});

it("a JWKS outage redirects to the clean unavailable page and never retries the consumed code", async () => {
  const store = await getStore();
  const tx = await store.createLoginTransaction("/account", context);
  codes.set("jwks-down", { nonce: tx.nonce, subject: "outage-user" });
  const before = redemptions.length;
  vi.resetModules();
  const { GET: freshCallback } = await import(
    "../../src/app/auth/callback/route"
  );
  jwksUnavailable = true;
  try {
    const response = await freshCallback(
      request(tx.state, tx.binding, "jwks-down"),
    );
    expect(response.status).toBe(303);
    expect(response.headers.get("location")).toBe(
      context.externalOrigin + "/auth/error?unavailable=1",
    );
    expect(await response.text()).toBe("");
    expect(redemptions.length - before).toBe(1);
    expect(
      response.headers
        .getSetCookie()
        .some((value) => value.startsWith("portal-session=")),
    ).toBe(false);
    const replay = await freshCallback(
      request(tx.state, tx.binding, "jwks-down"),
    );
    expect(replay.headers.get("location")).toBe(
      context.externalOrigin + "/auth/error",
    );
    expect(redemptions.length - before).toBe(1);
  } finally {
    jwksUnavailable = false;
  }
});
