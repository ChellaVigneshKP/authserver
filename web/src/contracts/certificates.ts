import { z } from "zod";

/** Inclusive server-side file ceiling from the Java certificate contract. */
export const MAX_CERTIFICATE_FILE_BYTES = 1024 * 1024;
/** BFF request budget: 1 MiB certificate plus bounded multipart fields and headers. */
export const MAX_CERTIFICATE_REQUEST_BYTES = 2 * 1024 * 1024;
/** A rebuilt PEM adds a small envelope around the stored DER. */
export const MAX_CERTIFICATE_DOWNLOAD_BYTES = MAX_CERTIFICATE_FILE_BYTES + 16384;
export const MAX_CERTIFICATE_PASSWORD_LENGTH = 1024;

export const CertificateType = z.enum(["ORGANIZATION", "PUBLIC_KEY"]);
export type CertificateTypeValue = z.infer<typeof CertificateType>;

export const CertificateStatus = z.enum([
  "ACTIVE",
  "INACTIVE",
  "EXPIRED",
  "SUSPENDED",
]);
export type CertificateStatusValue = z.infer<typeof CertificateStatus>;

const controlCharacters = (value: string) =>
  [...value].some((character) => {
    const point = character.codePointAt(0)!;
    return point < 32 || point === 127;
  });

/** CertificateName is NVARCHAR(255) in the canonical schema. */
export const CertificateName = z
  .string()
  .trim()
  .min(1, "Enter a certificate name.")
  .max(255, "Certificate names are limited to 255 characters.")
  .refine(
    (value) => !controlCharacters(value),
    "Certificate names cannot contain control characters.",
  );

export const CertificatePassword = z
  .string()
  .max(
    MAX_CERTIFICATE_PASSWORD_LENGTH,
    "Certificates passwords are limited to 1024 characters.",
  )
  .refine(
    (value) => !/[\r\n]/.test(value),
    "Certificate passwords cannot contain line breaks.",
  );

export const CertificateList = z.array(
  z
    .object({
      id: z.uuid(),
      name: z.string(),
      type: CertificateType,
      status: CertificateStatus,
      validTO: z.string(),
      isExpired: z.boolean(),
      /** Java's Lombok boolean getter also emits this alias; isExpired is authoritative. */
      expired: z.boolean().optional(),
    })
    .strict(),
);
export type CertificateSummary = z.infer<typeof CertificateList>[number];

export const CertificateDetail = z
  .object({
    id: z.uuid(),
    name: z.string(),
    type: CertificateType,
    status: CertificateStatus,
    validTO: z.string(),
    isExpired: z.boolean(),
    /** Java's Lombok boolean getter also emits this alias; isExpired is authoritative. */
    expired: z.boolean().optional(),
    fingerprint: z.string(),
    thumbprint: z.string(),
    certificate: z.string(),
  })
  .strict();
export type CertificateDetailDto = z.infer<typeof CertificateDetail>;

export const CertificateCreateResponse = z.object({ id: z.uuid() }).strict();

/**
 * The browser form keeps a real File. The BFF parses the same bounds from the
 * streamed multipart body; this schema is the first, user-facing check.
 */
export const CertificateForm = z.object({
  name: CertificateName,
  type: CertificateType,
  password: CertificatePassword,
  certificate: z
    .instanceof(File, { message: "Choose a certificate file." })
    .refine((file) => file.size > 0, "The certificate file is empty.")
    .refine(
      (file) => file.size <= MAX_CERTIFICATE_FILE_BYTES,
      "Certificate files are limited to 1 MiB.",
    ),
});
export type CertificateFormValue = z.infer<typeof CertificateForm>;

export const CERTIFICATE_READ_PERMISSIONS = [
  "idp-admin-org-cert:read",
  "idp-admin-cred-cert:read",
] as const;

export function certificateCreatePermission(type: CertificateTypeValue) {
  return type === "ORGANIZATION"
    ? "idp-admin-org-cert:create"
    : "idp-admin-cred-cert:create";
}

export function certificateDeletePermission(type: CertificateTypeValue) {
  return type === "ORGANIZATION"
    ? "idp-admin-org-cert:delete"
    : "idp-admin-cred-cert:delete";
}

/**
 * SQL datetime2 and imported naive timestamps represent UTC. Malformed values
 * yield null so callers fail closed instead of guessing a local instant.
 */
export function certificateExpiry(value: string): Date | null {
  if (!value) return null;
  const text = value.trim();
  if (!text) return null;
  const date = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text);
  if (date) return utcDate(date[1], date[2], date[3], "0", "0", "0", "0");
  const naive =
    /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?$/.exec(
      text,
    );
  if (naive)
    return utcDate(
      naive[1],
      naive[2],
      naive[3],
      naive[4],
      naive[5],
      naive[6] ?? "0",
      naive[7] ?? "0",
    );
  const offset =
    /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?(Z|[+-]\d{2}:?\d{2})$/.exec(
      text,
    );
  if (offset) {
    const millis = Date.parse(
      text.replace(" ", "T").replace(/([+-]\d{2})(\d{2})$/, "$1:$2"),
    );
    if (!Number.isNaN(millis)) return new Date(millis);
  }
  return null;
}

function utcDate(
  year: string,
  month: string,
  day: string,
  hour: string,
  minute: string,
  second: string,
  fraction: string,
) {
  const parts = [year, month, day, hour, minute, second].map(Number);
  const [y, mo, d, h, mi, s] = parts;
  if (
    mo < 1 ||
    mo > 12 ||
    d < 1 ||
    d > 31 ||
    h > 23 ||
    mi > 59 ||
    s > 59
  )
    return null;
  const millis = Number(fraction.padEnd(3, "0").slice(0, 3));
  const date = new Date(Date.UTC(y, mo - 1, d, h, mi, s, millis));
  if (
    date.getUTCFullYear() !== y ||
    date.getUTCMonth() !== mo - 1 ||
    date.getUTCDate() !== d ||
    date.getUTCHours() !== h ||
    date.getUTCMinutes() !== mi ||
    date.getUTCSeconds() !== s
  )
    return null;
  return date;
}

/**
 * Download filenames are derived from Content-Disposition but never trusted verbatim:
 * only a flat, safe stem survives and the extension is always `.pem`.
 */
export function certificateDownloadFilename(header: string | null) {
  const match = /(?:^|;)\s*filename=(?:"([^"]*)"|([^;]*))/i.exec(header ?? "");
  const raw = (match?.[1] ?? match?.[2] ?? "").trim();
  const base = raw.split(/[\\/]/).pop() ?? "";
  const safe = base
    .replace(/[^A-Za-z0-9._-]/g, "_")
    .replace(/^[.]+/, "")
    .slice(0, 120);
  const stem = safe.replace(/\.pem$/i, "");
  return (!stem || /^pem$/i.test(stem) ? "certificate" : stem) + ".pem";
}

export function certificateTypeLabel(type: CertificateTypeValue) {
  return type === "ORGANIZATION"
    ? "Organization signing"
    : "Client public key";
}

export function certificateStatusLabel(
  status: CertificateStatusValue,
  isExpired: boolean,
) {
  if (status === "INACTIVE") return "Inactive (deactivated)";
  if (status === "EXPIRED") return "Expired";
  if (status === "SUSPENDED") return "Suspended";
  return isExpired ? "Expired by validity date" : "Active";
}
