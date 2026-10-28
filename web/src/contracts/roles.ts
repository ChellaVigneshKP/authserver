import { z } from "zod";

/**
 * Wire status for the legacy group list is ACTIVE|INACTIVE while RoleDto uses Active|Inactive.
 * Normalization is explicit so a wire change can never be mistaken for an editable role.
 */
export const LegacyRoleStatus = z.enum(["ACTIVE", "INACTIVE"]);
export const RoleStatus = z.enum(["Active", "Inactive"]);

const roleName = z
  .string()
  .trim()
  .min(1)
  .max(100)
  .refine((v) => ![...v].some((c) => c.codePointAt(0)! < 32 || c.codePointAt(0)! === 127),
    "Role names cannot contain control characters.");
const roleDescription = z.string().max(1024);

export const PermissionKey = z.string().regex(/^[a-z0-9]+(?:-[a-z0-9]+)*:[a-z0-9]+(?:-[a-z0-9]+)*$/,
  "Permission keys are catalog identifiers.");
const permissionKeys = z.array(PermissionKey).min(1).max(100)
  .refine((keys) => new Set(keys).size === keys.length, "Permission keys must be unique.");

export const LegacyRole = z.object({
  id: z.uuid(),
  name: z.string(),
  description: z.string().nullish(),
  status: LegacyRoleStatus,
  revision: z.number().int().nonnegative(),
  memberCount: z.number().int().nonnegative(),
  readOnly: z.boolean(),
});
export const LegacyRoleList = z.array(LegacyRole);

export const RoleDetail = z.object({
  id: z.uuid(),
  name: z.string(),
  description: z.string().nullable(),
  status: RoleStatus,
  permissionKeys: z.array(z.string()),
  revision: z.number().int().positive(),
  memberCount: z.number().int().nonnegative(),
  readOnly: z.boolean(),
});
export type RoleDto = z.infer<typeof RoleDetail>;

export const RoleInput = z.object({
  name: roleName,
  description: roleDescription.default(""),
  permissionKeys,
}).strict();
export type RoleInputValue = z.infer<typeof RoleInput>;

/** Mutations carry the revision they were rendered from; the server rejects a stale one with 409. */
export const RoleUpdate = RoleInput.extend({ revision: z.number().int().positive() }).strict();

export const RoleAssignmentInput = z.object({ groupId: z.uuid().nullable() }).strict();

export const PermissionCatalogEntry = z.object({
  key: z.string(),
  description: z.string().nullish(),
  platformOnly: z.boolean(),
});
export const PermissionCatalog = z.array(PermissionCatalogEntry);
export type PermissionCatalogEntryDto = z.infer<typeof PermissionCatalogEntry>;

export const GroupPermission = z.object({
  id: z.uuid(),
  name: z.string(),
  description: z.string().nullish(),
  key: z.string(),
  status: LegacyRoleStatus,
});
export const GroupPermissionList = z.array(GroupPermission);
export type GroupPermissionDto = z.infer<typeof GroupPermission>;

export type RoleSummary = { id: string; name: string; description: string | null;
  status: "Active" | "Inactive"; revision: number; memberCount: number; readOnly: boolean };

export function roleSummaryFromLegacy(
  role: z.infer<typeof LegacyRole>,
): RoleSummary {
  return {
    id: role.id,
    name: role.name,
    description: role.description ?? null,
    status: role.status === "ACTIVE" ? "Active" : "Inactive",
    revision: role.revision,
    memberCount: role.memberCount,
    readOnly: role.readOnly,
  };
}

/**
 * Definition protection is not assignment eligibility, and a stale revision is not the same
 * conflict as an assigned role. Both reach the browser as 409, so the upstream code decides.
 */
export type RoleConflict =
  | { kind: "stale"; code: "ROLE_CONFLICT" }
  | { kind: "assigned"; code: "ROLE_ASSIGNED" }
  | { kind: "review"; code: "ROLE_REQUIRES_REVIEW" }
  | { kind: "membership"; code: "MEMBERSHIP_REQUIRES_REVIEW" }
  | { kind: "lastAdministrator"; code: "LAST_PLATFORM_ADMIN" }
  | { kind: "exists"; code: "PROFILE_EXISTS" }
  | { kind: "other"; code: string };

const conflictCodes = {
  ROLE_CONFLICT: "stale",
  ROLE_ASSIGNED: "assigned",
  ROLE_REQUIRES_REVIEW: "review",
  MEMBERSHIP_REQUIRES_REVIEW: "membership",
  LAST_PLATFORM_ADMIN: "lastAdministrator",
  PROFILE_EXISTS: "exists",
} as const;

export function roleConflict(code: string | undefined): RoleConflict {
  const known = code && code in conflictCodes
    ? (code as keyof typeof conflictCodes)
    : undefined;
  if (!known) return { kind: "other", code: code ?? "CONFLICT" };
  return { kind: conflictCodes[known], code: known } as RoleConflict;
}

/** Guidance only: never re-send the mutation automatically. */
export function conflictGuidance(conflict: RoleConflict): string {
  switch (conflict.kind) {
    case "stale":
      return "This role changed since it was opened. Reload it and review your edits before saving again.";
    case "assigned":
      return "This role still has assigned members. Remove those assignments before deactivating it.";
    case "review":
      return "This role contains protected platform grants and needs review before it can be changed.";
    case "membership":
      return "This account has legacy or ambiguous memberships. Reconcile them before assigning a role.";
    case "lastAdministrator":
      return "The final active platform administrator and platform organization must stay active.";
    case "exists":
      return "A role with that name already exists in this organization, including inactive roles.";
    default:
      return "The role request conflicted with the current data. Reload and review it.";
  }
}

/**
 * The assignment write has already committed by the time the caller refreshes. Reporting a
 * refresh failure as a failed assignment would invite a duplicate write for a saved change.
 * A 403 here is the documented rule that administrator targets need `idp-admin-admin` actions,
 * so the tenant keeps the confirmation without pretending the stale details are current.
 */
export function assignmentSaveOutcome(readbackError?: { status?: number }): {
  notice: string;
  warning?: string;
} {
  if (!readbackError) return { notice: "Role assignment saved." };
  if (readbackError.status === 403)
    return {
      notice:
        "Role assignment saved. This account now requires administrator permissions, so its details are no longer shown here.",
    };
  return {
    notice: "Role assignment saved.",
    warning:
      "The account details could not be refreshed. Reload the account to see the current state.",
  };
}
