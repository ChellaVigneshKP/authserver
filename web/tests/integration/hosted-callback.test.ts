import { afterAll, beforeAll, expect, it, vi } from "vitest";
import { randomUUID } from "node:crypto";
import { createClient } from "redis";
import { GET as callback } from "../../src/app/auth/callback/route";
import { getStore, randomId } from "../../src/server/session-store";
const namespace = "hosted-" + randomUUID(),
  prefix = "portal:v1:" + namespace + ":";
const context = {
  userAgent: "HostedBrowser",
  acceptLanguage: "en-US",
  externalOrigin: "http://127.0.0.1:3100",
};
const redis = createClient({
  url: "redis://127.0.0.1:16379/0",
  socket: { reconnectStrategy: false },
});
beforeAll(async () => {
  for (const [key, value] of Object.entries({
    PORTAL_ORIGIN: context.externalOrigin,
    AUTH_ISSUER: "http://localhost:8080/services",
    AUTH_CLIENT_ID: "portal",
    PORTAL_REDIS_URL: "redis://127.0.0.1:16379/0",
    PORTAL_REDIS_NAMESPACE: namespace,
    PORTAL_ALLOW_INSECURE_LOOPBACK: "true",
  }))
    vi.stubEnv(key, value);
  await redis.connect();
});
afterAll(async () => {
  for await (const keys of redis.scanIterator({ MATCH: prefix + "*" }))
    if (keys.length) await redis.del(keys);
  await redis.quit();
});
async function fresh() {
  const now = Date.now();
  return (await getStore()).rotateSession({
    id: randomId(),
    subjectId: "person",
    accessToken: "test-only-access",
    idToken: "test-only-id",
    signingKey: Buffer.alloc(32, 2),
    createdAt: now,
    lastSeenAt: now,
    expiresAt: now + 300000,
    browserContext: context,
    csrfSecret: randomId(),
  });
}
function request(p: string, id?: string, extra?: Record<string, string>) {
  return new Request(
    context.externalOrigin +
      "/auth/callback?" +
      new URLSearchParams({ p, ...extra }),
    {
      headers: {
        "user-agent": context.userAgent,
        "accept-language": context.acceptLanguage,
        ...(id ? { cookie: "portal-session=" + id } : {}),
      },
    },
  );
}
const encode = (p: string) => Buffer.from(p).toString("base64");
it("valid existing-session continuation returns cleanly without exchanging, rotating or exposing session", async () => {
  const session = await fresh();
  const before = await redis.keys(prefix + "*");
  const response = await callback(
    request(
      encode(
        "surl=http://127.0.0.1:3100/account/security&errors.codes=auths.304.001",
      ),
      session.id,
    ),
  );
  expect(response.headers.get("location")).toBe(
    context.externalOrigin + "/account/security?notice=cancelled",
  );
  expect(response.headers.getSetCookie()).toEqual([]);
  expect(await response.text()).toBe("");
  expect((await redis.keys(prefix + "*")).sort()).toEqual(before.sort());
  expect((await (await getStore()).getSession(session.id))?.subjectId).toBe(
    "person",
  );
});
it("untrusted unsigned outcome never claims a save", async () => {
  const s = await fresh();
  const response = await callback(
    request(
      encode(
        "surl=http://127.0.0.1:3100/account&errors.codes=some-private-content",
      ),
      s.id,
    ),
  );
  expect(response.headers.get("location")).toBe(
    context.externalOrigin + "/account?notice=unverified",
  );
});
it("foreign, malformed, mixed OAuth and guest continuations cannot create or replace a session", async () => {
  const s = await fresh();
  for (const req of [
    request(encode("surl=https://foreign.test/account"), s.id),
    request("!!!", s.id),
    request(encode("surl=http://127.0.0.1:3100/account"), s.id, {
      code: "wrong",
      state: "wrong",
    }),
    request(encode("surl=http://127.0.0.1:3100/account")),
  ]) {
    const r = await callback(req);
    expect(r.headers.get("location")).toBe(
      context.externalOrigin + "/auth/error",
    );
    expect(
      r.headers
        .getSetCookie()
        .some((cookie) => cookie.startsWith("portal-session=")),
    ).toBe(false);
    expect(await r.text()).toBe("");
  }
});
it("expired Redis session cannot use hosted continuation", async () => {
  const s = await fresh();
  await redis.pExpire(prefix + "session:" + s.id, 1);
  await new Promise((r) => setTimeout(r, 10));
  const response = await callback(
    request(encode("surl=http://127.0.0.1:3100/account"), s.id),
  );
  expect(response.headers.get("location")).toBe(
    context.externalOrigin + "/auth/error",
  );
  expect(await redis.exists(prefix + "session:" + s.id)).toBe(0);
});

it("invalid hosted action input returns safe 400 before any Java call", async () => {
  const { GET: action } = await import("../../src/app/account/action/route");
  const s = await fresh();
  for (const query of [
    "action=invalid&returnTo=%2Faccount",
    "action=change-profile&returnTo=%2Faccount&extra=1",
    "action=change-profile&returnTo=https%3A%2F%2Fevil.test",
  ]) {
    const response = await action(
      new Request(context.externalOrigin + "/account/action?" + query, {
        headers: {
          "user-agent": context.userAgent,
          "accept-language": context.acceptLanguage,
          cookie: "portal-session=" + s.id,
        },
      }),
    );
    expect(response.status).toBe(400);
    expect(response.headers.get("cache-control")).toContain("no-store");
    expect(response.headers.get("location")).toBeNull();
  }
});
