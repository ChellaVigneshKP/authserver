import { describe, expect, it } from "vitest";
import type { PortalContext } from "../../src/contracts/portal";
import {
  canShow,
  canAdminister,
  selectableOrganizations,
} from "../../src/features/account/permissions";
const own = "00000000-0000-4000-8000-000000000001",
  other = "00000000-0000-4000-8000-000000000002";
function context(permissions: string[] = []): PortalContext {
  return {
    subject: {
      userId: own,
      loginId: own,
      username: "Person",
      organizationId: own,
    },
    organizations: [
      { id: own, name: "Own" },
      { id: other, name: "Other" },
    ],
    permissions,
    capabilities: {
      roleWrite: false,
      userWrite: false,
      resourceWrite: false,
      providerMode: "unconfigured",
    },
  };
}
describe("current permission navigation", () => {
  it("keeps account-only users out of administration", () =>
    expect(canAdminister(context())).toBe(false));
  it("requires the exact read permission without granting viewer mutations", () => {
    const c = context(["idp-admin-app:read"]);
    expect(canAdminister(c)).toBe(true);
    expect(canShow(c, "idp-admin-app:read", own)).toBe(true);
    expect(canShow(c, "idp-admin-app:create", own)).toBe(false);
  });
  it("limits tenant organization navigation to its own current membership", () => {
    const c = context(["idp-admin-app:read"]);
    expect(selectableOrganizations(c)).toEqual([c.organizations[0]]);
    expect(canShow(c, "idp-admin-app:read", other)).toBe(false);
  });
  it("allows explicit platform scope only for listed organizations", () => {
    const c = context(["idp-admin-org:switch", "idp-admin-app:read"]);
    expect(selectableOrganizations(c)).toHaveLength(2);
    expect(canShow(c, "idp-admin-app:read", other)).toBe(true);
    expect(canShow(c, "idp-admin-app:read", "foreign")).toBe(false);
  });
  it("does not infer platform mutation authority from capability hints", () => {
    const c = context(["idp-admin-group:create"]);
    c.capabilities.roleWrite = true;
    expect(canShow(c, "idp-admin-group:create", own)).toBe(false);
  });
  it("removes navigation when fresh context revokes permissions", () => {
    const c = context(["idp-admin-app:read"]);
    expect(canAdminister(c)).toBe(true);
    c.permissions = [];
    expect(canAdminister(c)).toBe(false);
    expect(canShow(c, "idp-admin-app:read", own)).toBe(false);
  });
  it("never accepts unknown action keys", () =>
    expect(canShow(context(["unknown:write"]), "unknown:write", own)).toBe(
      false,
    ));
});
