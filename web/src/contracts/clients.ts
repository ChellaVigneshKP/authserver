import { z } from "zod";
const name = z
  .string()
  .trim()
  .min(1)
  .max(255)
  .refine((v) => !/[\x00-\x1f\x7f]/.test(v));
const identity = {
  name,
  description: z.string().max(1024),
  type: z.enum(["WEB", "MOBILE", "SERVER"]),
  authMethod: z.enum(["PKCE", "CLIENT_SECRET_JWT", "PRIVATE_KEY_JWT"]),
};
export const ClientCreate = z
  .object(identity)
  .strict()
  .refine(
    (v) =>
      v.type === "SERVER" ? v.authMethod !== "PKCE" : v.authMethod === "PKCE",
    {
      message:
        "Web and mobile clients require PKCE; server clients require confidential authentication.",
      path: ["authMethod"],
    },
  );
export type ClientCreateInput = z.infer<typeof ClientCreate>;
export const ClientUpdate = z
  .object({ name, description: z.string().max(1024) })
  .strict();
export const ClientSummary = z.object({
  id: z.uuid(),
  orgId: z.uuid(),
  clientId: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  uri: z.string().nullable(),
  type: identity.type,
  authFlow: identity.authMethod,
  active: z.boolean(),
});
export type ClientSummaryDto = z.infer<typeof ClientSummary>;
const seconds = z.number().int().positive().max(2147483647);
export const TokenInput = z
  .object({
    authCodeTimeToLive: seconds,
    accessTokenTimeToLive: seconds,
    refreshTokenTimeToLive: seconds,
    deviceCodeTimeToLive: seconds,
    maxRequestTransitTime: seconds,
    reuseRefreshTokens: z.boolean(),
  })
  .strict();
export type TokenSettings = z.infer<typeof TokenInput>;
export const ClientDetail = ClientSummary.omit({ authFlow: true }).extend({
  authenticationMethod: identity.authMethod,
  authCodeTimeToLive: seconds.nullable(),
  accessTokenTimeToLive: seconds.nullable(),
  refreshTokenTimeToLive: seconds.nullable(),
  deviceCodeTimeToLive: seconds.nullable(),
  maxRequestTransitTime: seconds.nullable(),
  reuseRefreshTokens: z.boolean().nullable(),
  usernameType: z.enum(["USERNAME", "EMAIL"]).nullable().optional(),
  allowForgotUsername: z.boolean().nullable().optional(),
  forgotUsernameSettings: z
    .array(
      z.object({
        priority: z.number().int(),
        lookupCriteria: z.array(z.string()),
      }),
    )
    .optional(),
  pinTimeToLive: z.number().int().nullable().optional(),
});
export type ClientDetailDto = z.infer<typeof ClientDetail>;
export const exactUrl = z
  .string()
  .min(1)
  .max(2048)
  .refine((v) => {
    try {
      const u = new URL(v);
      return (
        v.trim() === v &&
        !/[\s*]/.test(v) &&
        !u.hash &&
        !u.username &&
        !u.password &&
        ["http:", "https:", "ftp:", "com.ascensus.readysave529.qa4:"].includes(
          u.protocol,
        )
      );
    } catch {
      return false;
    }
  }, "Use an exact registered URL without whitespace, wildcards, fragments or credentials.");
const urlList = z
  .array(exactUrl)
  .min(1)
  .max(100)
  .refine(
    (v) => new Set(v).size === v.length,
    "Duplicate URLs are not allowed.",
  );
export const UrlInput = z
  .object({
    applicationUrl: exactUrl,
    returnUrls: urlList,
    logoutUrls: urlList,
  })
  .strict();
export const ClientUrls = z.object({
  applicationUrl: z.string().nullable(),
  redirectUrls: z.array(z.string()),
  logoutRedirectUrls: z.array(z.string()),
});
export type ClientUrlsDto = z.infer<typeof ClientUrls>;
export const CredentialMetadata = z.object({
  id: z.uuid(),
  name: z.string(),
  type: z.enum(["SHARED_SECRET", "PRIVATE_KEY"]),
  algorithm: z.string(),
  status: z.string(),
  expirationDate: z.string(),
  isExpired: z.boolean(),
});
export type CredentialDto = z.infer<typeof CredentialMetadata>;
export const CredentialCreate = z.discriminatedUnion("type", [
  z
    .object({
      name,
      type: z.literal("SHARED_SECRET"),
      algorithm: z.literal("HS256"),
      expireOn: z.iso.datetime({ offset: true }).optional(),
    })
    .strict(),
  z
    .object({
      name,
      type: z.literal("PRIVATE_KEY"),
      algorithm: z.enum(["RS256", "ES256"]),
      certificateId: z.uuid(),
    })
    .strict(),
]);
export const CredentialCreated = z
  .object({ id: z.uuid(), secretValue: z.string().min(1).optional() })
  .strict();
export function credentialInstant(value: string): number | null {
  if (!value) return null;
  const normalized = /^\d{4}-\d{2}-\d{2}$/.test(value)
    ? value + "T00:00:00Z"
    : /T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$/.test(value)
      ? value + "Z"
      : value;
  const millis = Date.parse(normalized);
  return Number.isFinite(millis) ? millis : null;
}
export function getClientReadiness(
  detail: ClientDetailDto,
  urls: ClientUrlsDto,
  credentials: CredentialDto[],
) {
  const missing: string[] = [];
  if (!detail.active)
    missing.push("Client is inactive. Reactivation is unavailable.");
  if (
    !urls.applicationUrl ||
    !urls.redirectUrls.length ||
    !urls.logoutRedirectUrls.length
  )
    missing.push("Save application, redirect and logout URLs.");
  if (
    [
      detail.authCodeTimeToLive,
      detail.accessTokenTimeToLive,
      detail.refreshTokenTimeToLive,
      detail.deviceCodeTimeToLive,
      detail.maxRequestTransitTime,
    ].some((v) => v === null) ||
    detail.reuseRefreshTokens === null
  )
    missing.push("Save token settings in seconds.");
  if (
    detail.authenticationMethod !== "PKCE" &&
    !credentials.some(
      (v) =>
        v.status === "Active" &&
        !v.isExpired &&
        v.expirationDate !== "" &&
        (credentialInstant(v.expirationDate) ?? 0) > Date.now() &&
        v.type ===
          (detail.authenticationMethod === "CLIENT_SECRET_JWT"
            ? "SHARED_SECRET"
            : "PRIVATE_KEY"),
    )
  )
    missing.push("Add a current matching credential with a known expiry.");
  return { ready: missing.length === 0, missing };
}
export const WizardState = z
  .object({
    identity: ClientCreate,
    baseline: z.array(z.uuid()).max(10000),
    applicationId: z.uuid().optional(),
    phase: z.enum(["creating", "uncertain", "created"]),
    attemptId: z.uuid(),
    startedAt: z.number().int().positive(),
    expiresAt: z.number().int().positive(),
  })
  .strict();
export type WizardStateDto = z.infer<typeof WizardState>;

export function getClientProgress(
  detail: ClientDetailDto,
  urls: ClientUrlsDto,
  credentials: CredentialDto[],
  resourceCount: number,
) {
  const urlsReady =
    !!urls.applicationUrl &&
    urls.redirectUrls.length > 0 &&
    urls.logoutRedirectUrls.length > 0;
  const settingsReady = TokenInput.safeParse({
    authCodeTimeToLive: detail.authCodeTimeToLive,
    accessTokenTimeToLive: detail.accessTokenTimeToLive,
    refreshTokenTimeToLive: detail.refreshTokenTimeToLive,
    deviceCodeTimeToLive: detail.deviceCodeTimeToLive,
    maxRequestTransitTime: detail.maxRequestTransitTime,
    reuseRefreshTokens: detail.reuseRefreshTokens,
  }).success;
  const pkce = detail.authenticationMethod === "PKCE";
  const credentialReady =
    pkce ||
    credentials.some(
      (v) =>
        v.status === "Active" &&
        !v.isExpired &&
        (credentialInstant(v.expirationDate) ?? 0) > Date.now() &&
        v.type ===
          (detail.authenticationMethod === "CLIENT_SECRET_JWT"
            ? "SHARED_SECRET"
            : "PRIVATE_KEY"),
    );
  const ready = getClientReadiness(detail, urls, credentials).ready;
  const steps = [
    {
      id: "client-identity",
      label: "Identity",
      status: detail.active ? "Saved" : "Inactive",
      required: true,
      complete: detail.active,
    },
    {
      id: "client-urls",
      label: "URLs",
      status: urlsReady ? "Saved" : "Needs configuration",
      required: true,
      complete: urlsReady,
    },
    {
      id: "client-settings",
      label: "Token settings",
      status: settingsReady ? "Saved" : "Needs configuration",
      required: true,
      complete: settingsReady,
    },
    {
      id: "client-credentials",
      label: "Credentials",
      status: pkce
        ? "Not required for PKCE"
        : credentialReady
          ? "Current matching credential"
          : "Needs a current credential",
      required: !pkce,
      complete: credentialReady,
    },
    {
      id: "client-resources",
      label: "Resources",
      status: resourceCount + " assigned",
      required: false,
      complete: resourceCount > 0,
    },
    {
      id: "client-readiness",
      label: "Readiness",
      status: ready
        ? "Required configuration present"
        : "Review missing configuration",
      required: true,
      complete: ready,
    },
  ];
  return {
    steps,
    nextId:
      steps.find((v) => v.required && !v.complete)?.id ?? "client-readiness",
  };
}
