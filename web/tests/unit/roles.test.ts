import { describe, expect, it } from "vitest";
import {
  LegacyRole,
  LegacyRoleList,
  RoleDetail,
  RoleInput,
  RoleUpdate,
  RoleAssignmentInput,
  assignmentSaveOutcome,
  conflictGuidance,
  roleConflict,
  roleSummaryFromLegacy,
} from "../../src/contracts/roles";
const orgId = "11111111-1111-4111-8111-111111111111";
const legacy = {
  id: orgId,
  name: "Tenant administrators",
  description: null,
  status: "ACTIVE" as const,
  revision: 3,
  memberCount: 2,
  readOnly: false,
};
describe("role wire contracts", () => {
  it("normalizes the legacy list status explicitly instead of reusing RoleDto status", () => {
    expect(LegacyRoleList.parse([legacy])).toHaveLength(1);
    expect(roleSummaryFromLegacy(legacy)).toEqual({
      id: orgId,
      name: "Tenant administrators",
      description: null,
      status: "Active",
      revision: 3,
      memberCount: 2,
      readOnly: false,
    });
    expect(roleSummaryFromLegacy({ ...legacy, status: "INACTIVE" }).status).toBe("Inactive");
    expect(() => LegacyRole.parse({ ...legacy, status: "Active" })).toThrow();
  });
  it("accepts only an exact RoleDto with a positive revision", () => {
    const detail = {
      id: orgId,
      name: "Support",
      description: null,
      status: "Active",
      permissionKeys: ["idp-admin-user:read"],
      revision: 1,
      memberCount: 0,
      readOnly: false,
    };
    expect(RoleDetail.parse(detail)).toEqual(detail);
    expect(() => RoleDetail.parse({ ...detail, revision: 0 })).toThrow();
    expect(() => RoleDetail.parse({ ...detail, status: "ACTIVE" })).toThrow();
    expect(() => RoleDetail.parse({ ...detail, extra: true })).not.toThrow();
  });
  it("trims names, defaults descriptions and rejects non-catalog permission shapes", () => {
    expect(RoleInput.parse({ name: "  Support  ", permissionKeys: ["idp-admin-user:read"] })).toEqual({
      name: "Support",
      description: "",
      permissionKeys: ["idp-admin-user:read"],
    });
    expect(() => RoleInput.parse({ name: "Bad\u0000name", permissionKeys: ["idp-admin-user:read"] })).toThrow();
    expect(() => RoleInput.parse({ name: "Support", permissionKeys: [] })).toThrow();
    expect(() => RoleInput.parse({ name: "Support", permissionKeys: ["idp-admin-user:read", "idp-admin-user:read"] })).toThrow();
    expect(() => RoleInput.parse({ name: "Support", permissionKeys: ["User Read"] })).toThrow();
    expect(() => RoleInput.parse({ name: "Support", permissionKeys: ["idp-admin-user:read"], forbidden: true })).toThrow();
  });
  it("carries the rendered revision on updates and a nullable same-org group on assignment", () => {
    expect(RoleUpdate.parse({ name: "Support", permissionKeys: ["idp-admin-user:read"], revision: 4 })).toEqual({
      name: "Support",
      description: "",
      permissionKeys: ["idp-admin-user:read"],
      revision: 4,
    });
    expect(() => RoleUpdate.parse({ name: "Support", permissionKeys: ["idp-admin-user:read"] })).toThrow();
    expect(RoleAssignmentInput.parse({ groupId: orgId })).toEqual({ groupId: orgId });
    expect(RoleAssignmentInput.parse({ groupId: null })).toEqual({ groupId: null });
    expect(() => RoleAssignmentInput.parse({ groupId: "not-a-uuid" })).toThrow();
    expect(() => RoleAssignmentInput.parse({ groupId: null, group: "forged" })).toThrow();
  });
});
describe("role conflicts", () => {
  it("maps every stable upstream conflict code to a distinct recovery path", () => {
    expect(roleConflict("ROLE_CONFLICT")).toEqual({ kind: "stale", code: "ROLE_CONFLICT" });
    expect(roleConflict("ROLE_ASSIGNED")).toEqual({ kind: "assigned", code: "ROLE_ASSIGNED" });
    expect(roleConflict("ROLE_REQUIRES_REVIEW")).toEqual({ kind: "review", code: "ROLE_REQUIRES_REVIEW" });
    expect(roleConflict("MEMBERSHIP_REQUIRES_REVIEW")).toEqual({ kind: "membership", code: "MEMBERSHIP_REQUIRES_REVIEW" });
    expect(roleConflict("LAST_PLATFORM_ADMIN")).toEqual({ kind: "lastAdministrator", code: "LAST_PLATFORM_ADMIN" });
    expect(roleConflict("PROFILE_EXISTS")).toEqual({ kind: "exists", code: "PROFILE_EXISTS" });
    expect(roleConflict(undefined)).toEqual({ kind: "other", code: "CONFLICT" });
    expect(roleConflict("SOMETHING_NEW")).toEqual({ kind: "other", code: "SOMETHING_NEW" });
  });
  it("offers reload/review guidance and never automatic retry", () => {
    expect(conflictGuidance(roleConflict("ROLE_CONFLICT"))).toMatch(/changed since it was opened/)
    expect(conflictGuidance(roleConflict("ROLE_CONFLICT"))).toMatch(/Reload/)
    expect(conflictGuidance(roleConflict("ROLE_ASSIGNED"))).toMatch(/assigned members/)
    expect(conflictGuidance(roleConflict("ROLE_REQUIRES_REVIEW"))).toMatch(/protected platform grants/)
    expect(conflictGuidance(roleConflict("MEMBERSHIP_REQUIRES_REVIEW"))).toMatch(/legacy or ambiguous memberships/)
    expect(conflictGuidance(roleConflict("LAST_PLATFORM_ADMIN"))).toMatch(/final active platform administrator/)
    expect(conflictGuidance(roleConflict("PROFILE_EXISTS"))).toMatch(/already exists/)
    expect(conflictGuidance({ kind: "other", code: "CONFLICT" })).toMatch(/Reload and review/)
    for (const code of ["ROLE_CONFLICT","ROLE_ASSIGNED","ROLE_REQUIRES_REVIEW","MEMBERSHIP_REQUIRES_REVIEW","LAST_PLATFORM_ADMIN","PROFILE_EXISTS"] as const)
      expect(conflictGuidance(roleConflict(code))).not.toMatch(/automatically retr/i)
  });
});
describe("assignment read-back", () => {
  it("keeps the committed write a success and explains the administrator permission boundary", () => {
    expect(assignmentSaveOutcome()).toEqual({ notice: "Role assignment saved." });
    const denied = assignmentSaveOutcome({ status: 403 });
    expect(denied.notice).toMatch(/^Role assignment saved\./);
    expect(denied.notice).toMatch(/now requires administrator permissions/);
    expect(denied.warning).toBeUndefined();
  });
  it("never turns a refresh failure into a failed assignment", () => {
    for (const status of [400, 409, 500, 503, undefined]) {
      const outcome = assignmentSaveOutcome({ status });
      expect(outcome.notice).toBe("Role assignment saved.");
      expect(outcome.warning).toMatch(/could not be refreshed/);
      expect(outcome.warning).not.toMatch(/failed|could not be saved/i);
    }
  });
});
