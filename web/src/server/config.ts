import "server-only";
import { z } from "zod";
import { X509Certificate } from "node:crypto";
const shape = z.object({
  PORTAL_ORIGIN: z.string().url(),
  AUTH_ISSUER: z.string().url(),
  AUTH_CLIENT_ID: z.string().min(1).max(256),
  PORTAL_REDIS_URL: z.string().url(),
  PORTAL_ALLOW_INSECURE_LOOPBACK: z.enum(["true", "false"]).default("false"),
  AUTH_TRANSPORT_ORIGIN: z.string().url().optional(),
  AUTH_TOKEN_RESPONSE_CERT_PEM: z.string().optional(),
  PORTAL_REDIS_NAMESPACE: z
    .string()
    .regex(/^[a-zA-Z0-9_-]{1,80}$/)
    .default("default"),
});
export function parseConfig(env: Record<string, string | undefined>) {
  const raw = shape.parse(env);
  const loopback = (u: URL) =>
    ["localhost", "127.0.0.1", "[::1]"].includes(u.hostname);
  function url(value: string, originOnly = false) {
    const u = new URL(value);
    if (
      u.username ||
      u.password ||
      u.search ||
      u.hash ||
      (originOnly && u.pathname !== "/")
    )
      throw Error("Invalid portal endpoint configuration");
    if (
      u.protocol !== "https:" &&
      !(
        raw.PORTAL_ALLOW_INSECURE_LOOPBACK === "true" &&
        u.protocol === "http:" &&
        loopback(u)
      )
    )
      throw Error("HTTPS required");
    return u;
  }
  const origin = url(raw.PORTAL_ORIGIN, true).origin,
    issuer = url(raw.AUTH_ISSUER).href.replace(/\/$/, "");
  const redis = new URL(raw.PORTAL_REDIS_URL);
  if (
    !["redis:", "rediss:"].includes(redis.protocol) ||
    redis.hash ||
    redis.search
  )
    throw Error("Invalid Redis configuration");
  const transport = raw.AUTH_TRANSPORT_ORIGIN
    ? url(raw.AUTH_TRANSPORT_ORIGIN, true).origin
    : new URL(issuer).origin;
  if (raw.AUTH_TOKEN_RESPONSE_CERT_PEM) {
    const key = new X509Certificate(raw.AUTH_TOKEN_RESPONSE_CERT_PEM).publicKey;
    if (!["rsa", "ec"].includes(key.asymmetricKeyType ?? ""))
      throw Error("Unsupported token certificate");
  }
  return {
    raw,
    origin,
    issuer,
    clientId: raw.AUTH_CLIENT_ID,
    redisUrl: raw.PORTAL_REDIS_URL,
    redisPrefix: "portal:v1:" + raw.PORTAL_REDIS_NAMESPACE + ":",
    secureCookies: origin.startsWith("https:"),
    transportOrigin: transport,
    tokenCertificate: raw.AUTH_TOKEN_RESPONSE_CERT_PEM,
  };
}
export type Config = ReturnType<typeof parseConfig>;
let cached: Config | undefined;
export function getConfig() {
  return (cached ??= parseConfig(process.env));
}
