import "server-only";
import { createHash, verify, X509Certificate } from "node:crypto";
import { createLocalJWKSet, jwtVerify, type JSONWebKeySet } from "jose";
import { z } from "zod";
import { getConfig, type Config } from "./config";
import { PortalFailure, signin } from "./errors";
import { getStore } from "./session-store";
import type { BrowserContext, PortalSession } from "./types";
const invalid = () =>
  new PortalFailure(
    400,
    "INVALID_AUTH_RESPONSE",
    "Sign-in could not be verified. Please start again.",
  );
export function safeReturnTo(value: string) {
  if (
    ![
      "/",
      "/account",
      "/clients",
      "/organizations",
      "/users",
      "/roles",
      "/certificates",
      "/resources",
    ].includes(value)
  )
    throw new PortalFailure(
      400,
      "INVALID_RETURN_PATH",
      "Choose a portal page to continue.",
    );
  return value === "/" ? "/account" : value;
}
export function browserContext(request: Request): BrowserContext {
  const userAgent = request.headers.get("user-agent") ?? "",
    acceptLanguage = request.headers.get("accept-language") ?? "";
  if (
    !userAgent ||
    !acceptLanguage ||
    userAgent.length > 2048 ||
    acceptLanguage.length > 512
  )
    throw invalid();
  return { userAgent, acceptLanguage, externalOrigin: getConfig().origin };
}
export function cookie(request: Request, name: string) {
  const matches = (request.headers.get("cookie") ?? "")
    .split(";")
    .map((v) => v.trim())
    .filter((v) => v.startsWith(name + "="));
  return matches.length === 1 ? matches[0].slice(name.length + 1) : "";
}
export function sessionCookieName(config = getConfig()) {
  return config.secureCookies ? "__Host-portal-session" : "portal-session";
}
export function transactionCookieName(kind: "login" | "logout", state: string) {
  if (!/^[a-zA-Z0-9_-]{43}$/.test(state)) throw invalid();
  return (
    (getConfig().secureCookies ? "__Host-" : "") +
    "portal-" +
    kind +
    "-" +
    state
  );
}
export function setCookie(
  response: Response,
  name: string,
  value: string,
  maxAge: number,
) {
  response.headers.append(
    "Set-Cookie",
    `${name}=${value}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${maxAge}${getConfig().secureCookies ? "; Secure" : ""}`,
  );
}
export async function requireSession(request: Request): Promise<PortalSession> {
  const store = await getStore();
  const session = await store.getSession(cookie(request, sessionCookieName()));
  if (!session) throw signin();
  const context = browserContext(request);
  if (
    context.userAgent !== session.browserContext.userAgent ||
    context.acceptLanguage !== session.browserContext.acceptLanguage ||
    context.externalOrigin !== session.browserContext.externalOrigin
  )
    throw signin();
  return session;
}
export function decodeSigningKey(value: unknown): Buffer {
  if (
    typeof value !== "string" ||
    !/^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(value)
  )
    throw invalid();
  const bytes = Buffer.from(value, "base64");
  if (
    bytes.length < 32 ||
    bytes.length > 128 ||
    bytes.toString("base64") !== value
  )
    throw invalid();
  return bytes;
}
export function verifyTokenBody(
  raw: Uint8Array,
  signature: string | null,
  certificate: string,
) {
  try {
    if (
      !signature ||
      !/^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(
        signature,
      )
    )
      throw invalid();
    const key = new X509Certificate(certificate).publicKey;
    const options =
      key.asymmetricKeyType === "ec"
        ? { key, dsaEncoding: "ieee-p1363" as const }
        : key;
    if (!verify("sha256", raw, options, Buffer.from(signature, "base64")))
      throw invalid();
  } catch {
    throw invalid();
  }
}
export async function boundedBody(response: Response, maximum = 65536) {
  if (Number(response.headers.get("content-length")) > maximum)
    throw new PortalFailure(
      502,
      "UPSTREAM_INVALID",
      "The identity service returned an invalid response.",
    );
  const reader = response.body?.getReader();
  if (!reader) return new Uint8Array();
  let total = 0;
  const chunks: Uint8Array[] = [];
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.length;
      if (total > maximum) throw Error("Response too large");
      chunks.push(value);
    }
    return Buffer.concat(chunks, total);
  } catch {
    await reader.cancel();
    throw new PortalFailure(
      502,
      "UPSTREAM_INVALID",
      "The identity service returned an invalid response.",
    );
  }
}
export function transportUrl(config: Config, external: string) {
  const u = new URL(external);
  if (u.origin !== new URL(config.issuer).origin)
    throw new PortalFailure(
      502,
      "UPSTREAM_INVALID",
      "The identity service returned an invalid endpoint.",
    );
  return config.transportOrigin + u.pathname + u.search;
}
export async function upstreamFetch(url: string, init: RequestInit = {}) {
  try {
    return await fetch(url, {
      ...init,
      cache: "no-store",
      redirect: "error",
      signal: AbortSignal.timeout(10_000),
    });
  } catch {
    throw new PortalFailure(
      503,
      "IDENTITY_UNAVAILABLE",
      "The identity service is temporarily unavailable. Please try again.",
      true,
    );
  }
}
const discoverySchema = z.object({
  issuer: z.string(),
  authorization_endpoint: z.string().url(),
  token_endpoint: z.string().url(),
  jwks_uri: z.string().url(),
  end_session_endpoint: z.string().url(),
});
export class IdentityProvider {
  private discoveryCache?: {
    at: number;
    value: z.infer<typeof discoverySchema>;
  };
  private keys?: { at: number; getKey: ReturnType<typeof createLocalJWKSet> };
  constructor(private config: Config) {}
  async discovery() {
    if (this.discoveryCache && Date.now() - this.discoveryCache.at < 300_000)
      return this.discoveryCache.value;
    const response = await upstreamFetch(
      transportUrl(
        this.config,
        this.config.issuer + "/.well-known/openid-configuration",
      ),
    );
    if (!response.ok)
      throw new PortalFailure(
        503,
        "IDENTITY_UNAVAILABLE",
        "The identity service is temporarily unavailable.",
        true,
      );
    try {
      const value = discoverySchema.parse(
        JSON.parse(Buffer.from(await boundedBody(response)).toString()),
      );
      if (value.issuer !== this.config.issuer) throw Error();
      for (const field of [
        "authorization_endpoint",
        "token_endpoint",
        "jwks_uri",
        "end_session_endpoint",
      ] as const) {
        const u = new URL(value[field]);
        if (
          u.origin !== new URL(this.config.issuer).origin ||
          u.username ||
          u.password ||
          u.search ||
          u.hash ||
          !u.pathname.startsWith(
            new URL(this.config.issuer).pathname.replace(/\/$/, "") + "/",
          )
        )
          throw Error();
      }
      this.discoveryCache = { at: Date.now(), value };
      return value;
    } catch {
      throw new PortalFailure(
        502,
        "UPSTREAM_INVALID",
        "The identity service configuration could not be verified.",
      );
    }
  }
  async authorize(tx: { state: string; nonce: string; verifier: string }) {
    const d = await this.discovery();
    const url = new URL(d.authorization_endpoint);
    url.search = new URLSearchParams({
      response_type: "code",
      client_id: this.config.clientId,
      redirect_uri: this.config.origin + "/auth/callback",
      scope: "openid profile email metadata idp-admin",
      branding: "idp",
      state: tx.state,
      nonce: tx.nonce,
      code_challenge: createHash("sha256")
        .update(tx.verifier)
        .digest("base64url"),
      code_challenge_method: "S256",
      "x-request-datetime": new Date().toISOString(),
    }).toString();
    return url.href;
  }
  async verifyIdToken(jwt: string, nonce: string) {
    try {
      if (!this.keys || Date.now() - this.keys.at > 300_000) {
        const d = await this.discovery();
        const response = await upstreamFetch(
          transportUrl(this.config, d.jwks_uri),
        );
        if (!response.ok)
          throw response.status >= 500
            ? new PortalFailure(
                503,
                "IDENTITY_UNAVAILABLE",
                "The identity service is temporarily unavailable. Start sign-in again.",
                true,
              )
            : invalid();
        const jwks = JSON.parse(
          Buffer.from(await boundedBody(response)).toString(),
        ) as JSONWebKeySet;
        if (
          !Array.isArray(jwks.keys) ||
          jwks.keys.length < 1 ||
          jwks.keys.length > 16
        )
          throw invalid();
        this.keys = { at: Date.now(), getKey: createLocalJWKSet(jwks) };
      }
      const result = await jwtVerify(jwt, this.keys.getKey, {
        issuer: this.config.issuer,
        audience: this.config.clientId,
        algorithms: ["RS256", "ES256"],
        requiredClaims: ["sub", "exp", "iat", "nonce"],
        clockTolerance: 0,
      });
      if (
        result.payload.nonce !== nonce ||
        !result.payload.sub ||
        ((result.payload.azp !== undefined ||
          (Array.isArray(result.payload.aud) &&
            result.payload.aud.length > 1)) &&
          result.payload.azp !== this.config.clientId)
      )
        throw invalid();
      return result.payload;
    } catch (error) {
      if (error instanceof PortalFailure && error.status === 503) throw error;
      throw invalid();
    }
  }
  async exchange(
    code: string,
    tx: { nonce: string; verifier: string; context?: BrowserContext },
  ) {
    const d = await this.discovery();
    const headers: Record<string, string> = {
      "Content-Type": "application/x-www-form-urlencoded",
      "x-request-datetime": new Date().toISOString(),
    };
    if (tx.context) {
      headers["User-Agent"] = tx.context.userAgent;
      headers["Accept-Language"] = tx.context.acceptLanguage;
      headers.Referer = tx.context.externalOrigin + "/";
    }
    const tokenRequestStartedAt = Date.now();
    const response = await upstreamFetch(
      transportUrl(this.config, d.token_endpoint),
      {
        method: "POST",
        headers,
        body: new URLSearchParams({
          grant_type: "authorization_code",
          client_id: this.config.clientId,
          code,
          redirect_uri: this.config.origin + "/auth/callback",
          code_verifier: tx.verifier,
        }),
      },
    );
    if (!response.ok)
      throw response.status >= 500
        ? new PortalFailure(
            503,
            "IDENTITY_UNAVAILABLE",
            "The identity service is temporarily unavailable. Start sign-in again.",
            true,
          )
        : invalid();
    const raw = await boundedBody(response);
    if (this.config.tokenCertificate)
      verifyTokenBody(
        raw,
        response.headers.get("x-signature"),
        this.config.tokenCertificate,
      );
    try {
      const token = z
        .object({
          access_token: z.string().min(1).max(16384),
          id_token: z.string().min(1).max(32768),
          token_type: z.string().refine((v) => v.toLowerCase() === "bearer"),
          expires_in: z.number().positive().finite(),
          signing_key: z.unknown(),
        })
        .parse(JSON.parse(Buffer.from(raw).toString()));
      const identity = await this.verifyIdToken(token.id_token, tx.nonce);
      return {
        subjectId: identity.sub!,
        accessToken: token.access_token,
        idToken: token.id_token,
        signingKey: decodeSigningKey(token.signing_key),
        expiresAt: tokenRequestStartedAt + Math.floor(token.expires_in * 1000),
      };
    } catch (error) {
      if (error instanceof PortalFailure && error.status === 503) throw error;
      throw invalid();
    }
  }
  async logoutUrl(idToken: string) {
    const d = await this.discovery();
    const url = new URL(d.end_session_endpoint);
    url.search = new URLSearchParams({
      id_token_hint: idToken,
      client_id: this.config.clientId,
      post_logout_redirect_uri: this.config.origin + "/auth/logged-out",
    }).toString();
    return url.href;
  }
}
let provider: IdentityProvider | undefined;
export function getIdentityProvider() {
  return (provider ??= new IdentityProvider(getConfig()));
}
