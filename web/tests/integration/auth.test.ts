import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { createClient } from "redis";
import { randomUUID, createHmac } from "node:crypto";
import { createServer, type Server } from "node:http";
import { exportJWK, generateKeyPair, SignJWT } from "jose";
import { z } from "zod";
import { SessionStore } from "../../src/server/session-store";
import { parseConfig } from "../../src/server/config";
import {
  IdentityProvider,
  safeReturnTo,
  decodeSigningKey,
} from "../../src/server/auth";
import { requireMutation } from "../../src/server/csrf";
import { javaRequestWith } from "../../src/server/java-api";
import { publicSession, errorResponse } from "../../src/server/errors";

const redis = createClient({
  url: "redis://127.0.0.1:16379/0",
  socket: { reconnectStrategy: false },
});
const prefix = "portal:v1:test:" + randomUUID() + ":";
let now = Date.now();
let store: SessionStore;
const browser = {
  userAgent: "PortalTest/1",
  acceptLanguage: "en-US",
  externalOrigin: "http://127.0.0.1:3100",
};
let server: Server,
  issuer: string,
  provider: IdentityProvider,
  config: ReturnType<typeof parseConfig>,
  key: Awaited<ReturnType<typeof generateKeyPair>>,
  jwk: object;
let tokenClaims: Record<string, unknown> = {},
  apiMode = "good",
  apiCalls = 0;
let discoveryOverride: Record<string, unknown> = {};
let jwksCount = 1;
const session = () => ({
  id: randomUUID(),
  subjectId: "subject",
  accessToken: "private-access",
  idToken: "private-id",
  signingKey: Buffer.alloc(32, 42),
  expiresAt: now + 3600_000,
  createdAt: now,
  lastSeenAt: now,
  browserContext: browser,
  csrfSecret: "csrf-secret",
});
beforeAll(async () => {
  await redis.connect();
  key = await generateKeyPair("RS256");
  jwk = {
    ...(await exportJWK(key.publicKey)),
    kid: "test",
    alg: "RS256",
    use: "sig",
  };
  server = createServer(async (req, res) => {
    if (req.url === "/.well-known/openid-configuration") {
      res.end(
        JSON.stringify({
          issuer,
          authorization_endpoint: issuer + "/oauth2/authorize",
          token_endpoint: issuer + "/oauth2/token",
          jwks_uri: issuer + "/oauth2/jwks",
          end_session_endpoint: issuer + "/oauth2/connect/logout",
          ...discoveryOverride,
        }),
      );
      return;
    }
    if (req.url === "/oauth2/jwks") {
      res.end(
        JSON.stringify({ keys: Array.from({ length: jwksCount }, () => jwk) }),
      );
      return;
    }
    if (req.url === "/oauth2/token") {
      const jwt = await new SignJWT({ nonce: "nonce", ...tokenClaims })
        .setProtectedHeader({ alg: "RS256", kid: "test" })
        .setIssuer(String(tokenClaims.iss ?? issuer))
        .setAudience(String(tokenClaims.aud ?? "portal"))
        .setSubject("subject")
        .setIssuedAt()
        .setExpirationTime("5m")
        .sign(key.privateKey);
      res.end(
        JSON.stringify({
          access_token: "private-access",
          id_token: jwt,
          token_type: "Bearer",
          expires_in: 300,
          signing_key: Buffer.alloc(32, 42).toString("base64"),
        }),
      );
      return;
    }
    apiCalls++;
    const raw = JSON.stringify({ subject: req.headers.authorization });
    if (apiMode !== "missing")
      res.setHeader(
        "x-signature",
        createHmac("sha256", Buffer.alloc(32, apiMode === "wrong" ? 43 : 42))
          .update(raw)
          .digest("base64"),
      );
    res.statusCode = apiMode === "unauthorized" ? 401 : 200;
    res.end(raw);
  });
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
  issuer = "http://127.0.0.1:" + (server.address() as { port: number }).port;
  config = parseConfig({
    PORTAL_ORIGIN: browser.externalOrigin,
    AUTH_ISSUER: issuer,
    AUTH_CLIENT_ID: "portal",
    PORTAL_REDIS_URL: "redis://127.0.0.1:16379/0",
    PORTAL_ALLOW_INSECURE_LOOPBACK: "true",
  });
  provider = new IdentityProvider(config);
});
afterAll(async () => {
  if (!redis.isOpen) return;
  for await (const keys of redis.scanIterator({ MATCH: prefix + "*" }))
    if (keys.length) await redis.del(keys);
  await redis.quit();
  await new Promise<void>((r) => server.close(() => r()));
});
beforeEach(() => {
  now = Date.now();
  store = new SessionStore(redis, prefix, () => now);
  tokenClaims = {};
  discoveryOverride = {};
  jwksCount = 1;
  apiMode = "good";
  apiCalls = 0;
});
describe("auth session and protocol boundaries", () => {
  it("rejectsWrongReplayedOrExpiredState", async () => {
    const tx = await store.createLoginTransaction("/account", browser);
    await expect(
      store.consumeLoginTransaction(tx.state, "wrong"),
    ).rejects.toMatchObject({ status: 400 });
    expect(
      (await store.consumeLoginTransaction(tx.state, tx.binding)).nonce,
    ).toBe(tx.nonce);
    await expect(
      store.consumeLoginTransaction(tx.state, tx.binding),
    ).rejects.toMatchObject({ status: 400 });
    const expired = await store.createLoginTransaction("/account", browser);
    now += 300_000;
    await expect(
      store.consumeLoginTransaction(expired.state, expired.binding),
    ).rejects.toMatchObject({ status: 400 });
  });
  it("twoTabLoginRemainsIndependent", async () => {
    const [a, b] = await Promise.all([
      store.createLoginTransaction("/account", browser),
      store.createLoginTransaction("/account", browser),
    ]);
    expect(a.state).not.toBe(b.state);
    await expect(
      store.consumeLoginTransaction(a.state, b.binding),
    ).rejects.toMatchObject({ status: 400 });
    const result = await Promise.all([
      store.consumeLoginTransaction(a.state, a.binding),
      store.consumeLoginTransaction(b.state, b.binding),
    ]);
    expect(result.map((t) => t.nonce)).toEqual([a.nonce, b.nonce]);
  });
  it("atomically consumes a callback under concurrency", async () => {
    const tx = await store.createLoginTransaction("/account", browser);
    const out = await Promise.allSettled([
      store.consumeLoginTransaction(tx.state, tx.binding),
      store.consumeLoginTransaction(tx.state, tx.binding),
    ]);
    expect(out.filter((v) => v.status === "fulfilled")).toHaveLength(1);
  });
  it("rejectsExternalReturnTo", () => {
    for (const target of [
      "https://evil.test",
      "//evil.test",
      "/\\evil",
      "/%2f%2fevil",
      "/auth/callback",
      "/account?token=secret",
    ])
      expect(() => safeReturnTo(target)).toThrow();
    expect(safeReturnTo("/account")).toBe("/account");
  });
  it("sessionRotatesAfterLogin", async () => {
    const old = session();
    await store.putSession(old);
    const next = await store.rotateSession(
      { ...old, id: randomUUID() },
      old.id,
    );
    expect(next.id).not.toBe(old.id);
    expect(await store.getSession(old.id)).toBeNull();
    expect(await store.getSession(next.id)).not.toBeNull();
  });
  it("expiredAccessTokenRequiresSignin", async () => {
    const s = session();
    s.expiresAt = now + 1000;
    await store.putSession(s);
    now += 1000;
    expect(await store.getSession(s.id)).toBeNull();
  });
  it("enforces idle and absolute expiry without reviving deleted sessions", async () => {
    const idle = session();
    await store.putSession(idle);
    now += 1800_000;
    expect(await store.getSession(idle.id)).toBeNull();
    const absolute = session();
    absolute.createdAt = now - 8 * 3600_000 + 1000;
    absolute.expiresAt = now + 3600_000;
    await store.putSession(absolute);
    now += 1000;
    expect(await store.getSession(absolute.id)).toBeNull();
    const s = session();
    await store.putSession(s);
    await store.deleteSession(s.id);
    await store.touchSession(s);
    expect(await store.getSession(s.id)).toBeNull();
  });
  it("logout transactions are browser bound, one use and expire", async () => {
    const tx = await store.createLogoutTransaction(session());
    await expect(
      store.consumeLogoutTransaction(tx.state, "wrong"),
    ).rejects.toMatchObject({ status: 400 });
    expect(
      (await store.consumeLogoutTransaction(tx.state, tx.binding)).idToken,
    ).toBe("private-id");
    await expect(
      store.consumeLogoutTransaction(tx.state, tx.binding),
    ).rejects.toMatchObject({ status: 400 });
    const exp = await store.createLogoutTransaction(session());
    now += 300_000;
    await expect(
      store.consumeLogoutTransaction(exp.state, exp.binding),
    ).rejects.toMatchObject({ status: 400 });
  });
  it("validatesIssuerAudienceNonceAndSignature", async () => {
    const tx = { nonce: "nonce", verifier: "verifier" };
    expect((await provider.exchange("code", tx)).subjectId).toBe("subject");
    for (const claim of [
      { nonce: "wrong" },
      { iss: "https://evil.test" },
      { aud: "wrong" },
    ]) {
      tokenClaims = claim;
      await expect(provider.exchange("code", tx)).rejects.toMatchObject({
        status: 400,
      });
    }
    const wrong = await generateKeyPair("RS256");
    const jwt = await new SignJWT({ nonce: "nonce" })
      .setProtectedHeader({ alg: "RS256", kid: "test" })
      .setIssuer(issuer)
      .setAudience("portal")
      .setSubject("subject")
      .setExpirationTime("5m")
      .sign(wrong.privateKey);
    await expect(provider.verifyIdToken(jwt, "nonce")).rejects.toMatchObject({
      status: 400,
    });
  });
  it("decodes actual JSON Base64 signing key and rejects malformed keys", () => {
    expect(decodeSigningKey(Buffer.alloc(32, 42).toString("base64"))).toEqual(
      Buffer.alloc(32, 42),
    );
    for (const bad of ["", [], 42, "%%%", "YWJj"])
      expect(() => decodeSigningKey(bad)).toThrow();
  });
  it("requires exact HMAC response bytes and rejects wrong or missing signatures", async () => {
    for (const mode of ["missing", "wrong"]) {
      apiMode = mode;
      await expect(
        javaRequestWith(
          config,
          store,
          session(),
          { kind: "context" },
          undefined,
          z.object({ subject: z.string() }),
        ),
      ).rejects.toMatchObject({ status: 502 });
    }
    apiMode = "good";
    expect(
      await javaRequestWith(
        config,
        store,
        session(),
        { kind: "context" },
        undefined,
        z.object({ subject: z.string() }),
      ),
    ).toEqual({ subject: "Bearer private-access" });
  });
  it("crossUserResponsesAreNotCached", async () => {
    const a = session(),
      b = { ...session(), accessToken: "other-access" };
    const schema = z.object({ subject: z.string() });
    expect(
      (
        await javaRequestWith(
          config,
          store,
          a,
          { kind: "context" },
          undefined,
          schema,
        )
      ).subject,
    ).toBe("Bearer private-access");
    expect(
      (
        await javaRequestWith(
          config,
          store,
          b,
          { kind: "context" },
          undefined,
          schema,
        )
      ).subject,
    ).toBe("Bearer other-access");
    expect(apiCalls).toBe(2);
  });
  it("invalid upstream bearer deletes the portal session", async () => {
    const s = session();
    await store.putSession(s);
    apiMode = "unauthorized";
    await expect(
      javaRequestWith(
        config,
        store,
        s,
        { kind: "context" },
        undefined,
        z.unknown(),
      ),
    ).rejects.toMatchObject({ status: 401 });
    expect(await store.getSession(s.id)).toBeNull();
  });
  it("sessionTokensAbsentFromBrowserArtifacts", async () => {
    const s = session();
    const response = publicSession(s);
    expect(await response.json()).toEqual({
      authenticated: true,
      expiresAt: now + 1800_000,
      csrfToken: "csrf-secret",
    });
    expect(response.headers.get("cache-control")).toContain("no-store");
    const error = errorResponse(new Error("private-access"));
    expect(await error.text()).not.toContain("private-access");
  });
  it("mutationRequiresOriginAndCsrf", async () => {
    const s = session();
    const valid = {
      origin: browser.externalOrigin,
      "x-csrf-token": s.csrfSecret,
      "content-type": "application/json",
    };
    await expect(
      requireMutation(
        new Request(browser.externalOrigin, { method: "POST", headers: valid }),
        s,
      ),
    ).resolves.toBeUndefined();
    for (const changed of [
      { origin: "https://evil.test" },
      { origin: "" },
      { "x-csrf-token": "" },
      { "content-type": "text/plain" },
    ])
      await expect(
        requireMutation(
          new Request(browser.externalOrigin, {
            method: "POST",
            headers: { ...valid, ...changed },
          }),
          s,
        ),
      ).rejects.toMatchObject({ status: 403 });
  });
  it("rejects unsafe configuration and network destinations", () => {
    for (const values of [
      { AUTH_ISSUER: "http://remote.test" },
      { PORTAL_ORIGIN: "http://remote.test" },
      { AUTH_ISSUER: issuer + "?query=x" },
      { AUTH_CLIENT_ID: "" },
    ])
      expect(() => parseConfig({ ...config.raw, ...values })).toThrow();
  });
  it("mutation guard accepts implemented mutation verbs but rejects multibyte CSRF safely", async () => {
    const s = session();
    for (const method of ["PUT", "PATCH", "DELETE"])
      await expect(
        requireMutation(
          new Request(browser.externalOrigin, {
            method,
            headers: {
              origin: browser.externalOrigin,
              "x-csrf-token": s.csrfSecret,
              "content-type": "application/json",
            },
          }),
          s,
        ),
      ).resolves.toBeUndefined();
    await expect(
      requireMutation(
        new Request(browser.externalOrigin, {
          method: "POST",
          headers: {
            origin: browser.externalOrigin,
            "x-csrf-token": "é".repeat(s.csrfSecret.length),
            "content-type": "application/json",
          },
        }),
        s,
      ),
    ).rejects.toMatchObject({ status: 403 });
  });

  it("rejects a different authorized party even with a matching audience", async () => {
    tokenClaims = { azp: "other-client" };
    expect(
      await provider
        .exchange("code", { nonce: "nonce", verifier: "verifier" })
        .then(
          () => 0,
          (error) => error.status,
        ),
    ).toBe(400);
  });
  it("rejects expired identity tokens independently of access expiry", async () => {
    const jwt = await new SignJWT({ nonce: "nonce" })
      .setProtectedHeader({ alg: "RS256", kid: "test" })
      .setIssuer(issuer)
      .setAudience("portal")
      .setSubject("subject")
      .setIssuedAt()
      .setExpirationTime(Math.floor(Date.now() / 1000) - 1)
      .sign(key.privateKey);
    await expect(provider.verifyIdToken(jwt, "nonce")).rejects.toMatchObject({
      status: 400,
    });
  });
  it("rejects discovered issuer changes and off-origin or credential-bearing endpoints before exchange", async () => {
    for (const override of [
      { issuer: "https://evil.example" },
      { jwks_uri: "https://evil.example/keys" },
      { token_endpoint: "https://evil.example/token" },
      {
        authorization_endpoint:
          issuer.replace("http://", "http://user:pass@") + "/authorize",
      },
    ]) {
      discoveryOverride = override;
      expect(
        await new IdentityProvider(config).discovery().then(
          () => 200,
          (error) => error.status,
        ),
      ).toBe(502);
    }
  });
  it("bounds the accepted JWKS key set", async () => {
    jwksCount = 17;
    expect(
      await new IdentityProvider(config)
        .exchange("code", { nonce: "nonce", verifier: "verifier" })
        .then(
          () => 200,
          (error) => error.status,
        ),
    ).toBe(400);
  });
});
