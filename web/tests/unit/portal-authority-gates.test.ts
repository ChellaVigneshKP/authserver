import { expect, it } from "vitest";
import { createElement } from "react";
import { renderToString } from "react-dom/server";
import type { PortalContext } from "../../src/contracts/portal";
import type { OrganizationDto, UserDto } from "../../src/contracts/users";
import { UserDetails } from "../../src/features/users/UserDetails";
import { OrganizationForm } from "../../src/features/organizations/OrganizationForm";
const self = "00000000-0000-4000-8000-000000000001",
  other = "00000000-0000-4000-8000-000000000002";
function context(permissions: string[], organizationId = self): PortalContext {
  return {
    subject: {
      userId: self,
      loginId: self,
      username: "Synthetic Person",
      organizationId,
    },
    organizations: [{ id: self, name: "Self" }, { id: other, name: "Other" }],
    permissions,
    capabilities: {
      roleWrite: false,
      userWrite: false,
      resourceWrite: false,
      providerMode: "unconfigured",
    },
  };
}
function user(overrides: Partial<UserDto> = {}): UserDto {
  return {
    id: self,
    memberId: self,
    loginId: self,
    organizationId: self,
    groupId: null,
    userName: "synthetic",
    firstName: "Synthetic",
    lastName: "Person",
    email: "synthetic@example.test",
    branding: "idp",
    status: "Active",
    twoFactorEnabled: false,
    syncMode: "local",
    ...overrides,
  };
}
function organization(id: string): OrganizationDto {
  return {
    id,
    name: "Synthetic Organization",
    status: "ACTIVE",
    description: "",
    primaryContact: null,
    secondaryContact: null,
  };
}
function details(permissions: string[]) {
  return renderToString(
    createElement(UserDetails, {
      initial: user(),
      initialMetadata: {},
      context: context(permissions),
    }),
  );
}
function organizationPage(permissions: string[], id: string, subjectOrg = self) {
  return renderToString(
    createElement(OrganizationForm, {
      initial: organization(id),
      context: context(permissions, subjectOrg),
    }),
  );
}
it("hides account status and delete actions from update-only authority", () => {
  const html = details(["idp-admin-user:read", "idp-admin-user:update"]);
  expect(html).not.toContain("Deactivate account");
  expect(html).not.toContain("Delete account access");
});
it("shows account status actions only with delete authority", () => {
  const html = details([
    "idp-admin-user:read",
    "idp-admin-user:update",
    "idp-admin-user:delete",
  ]);
  expect(html).toContain("Deactivate account");
  expect(html).toContain("Delete account access");
});
it("hides organization status control for a tenant administrator without platform scope", () => {
  const html = organizationPage(
    ["idp-admin-org:update", "idp-admin-org:deactivate"],
    self,
  );
  expect(html).not.toContain("Revoke organization");
});
it("hides organization status control for the platform administrator's own organization", () => {
  const html = organizationPage(
    [
      "idp-admin-org:update",
      "idp-admin-org:switch",
      "idp-admin-org:deactivate",
    ],
    self,
  );
  expect(html).not.toContain("Revoke organization");
});
it("shows organization status control for another organization with platform authority", () => {
  const html = organizationPage(
    [
      "idp-admin-org:update",
      "idp-admin-org:switch",
      "idp-admin-org:deactivate",
    ],
    other,
  );
  expect(html).toContain("Revoke organization");
});
