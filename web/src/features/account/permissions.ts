import type { PortalContext } from "../../contracts/portal";
// Presentation only. Java reloads grants independently on every protected request.
const actions = new Set([
  "idp-admin-org:read",
  "idp-admin-org:list",
  "idp-admin-org:switch",
  "idp-admin-org:create",
  "idp-admin-org:update",
  "idp-admin-org:deactivate",
  "idp-admin-app:read",
  "idp-admin-app:create",
  "idp-admin-app:update",
  "idp-admin-app:delete",
  "idp-admin-app:assign-resource",
  "idp-admin-user:read",
  "idp-admin-admin:read",
  "idp-admin-admin:update",
  "idp-admin-admin:delete",
  "idp-admin-user:create",
  "idp-admin-user:update",
  "idp-admin-user:delete",
  "idp-admin-group:read",
  "idp-admin-group:assign",
  "idp-admin-group:create",
  "idp-admin-group:update",
  "idp-admin-group:delete",
  "idp-admin-resource:read",
  "idp-admin-resource:create",
  "idp-admin-resource:update",
  "idp-admin-resource:delete",
  "idp-admin-permission:read",
  "idp-admin-org-cert:read",
  "idp-admin-org-cert:create",
  "idp-admin-org-cert:update",
  "idp-admin-org-cert:delete",
  "idp-admin-cred-cert:read",
  "idp-admin-cred-cert:create",
  "idp-admin-cred-cert:update",
  "idp-admin-cred-cert:delete",
]);
const platformActions = new Set([
  "idp-admin-org:create",
  "idp-admin-org:list",
  "idp-admin-org:switch",
  "idp-admin-org:deactivate",
  "idp-admin-group:create",
  "idp-admin-group:update",
  "idp-admin-group:delete",
  "idp-admin-permission:read",
  "idp-admin-resource:read",
  "idp-admin-resource:create",
  "idp-admin-resource:update",
  "idp-admin-resource:delete",
  "idp-admin-app:assign-resource",
  "idp-admin-org-cert:create",
  "idp-admin-org-cert:update",
  "idp-admin-org-cert:delete",
]);
export function selectableOrganizations(context: PortalContext) {
  return context.organizations.filter(
    (org) =>
      org.id === context.subject.organizationId ||
      context.permissions.includes("idp-admin-org:switch"),
  );
}
export function canShow(
  context: PortalContext,
  action: string,
  orgId: string,
): boolean {
  return (
    actions.has(action) &&
    context.permissions.includes(action) &&
    selectableOrganizations(context).some((org) => org.id === orgId) &&
    (!platformActions.has(action) ||
      context.permissions.includes("idp-admin-org:switch"))
  );
}
export function canAdminister(
  context: PortalContext,
  orgId = context.subject.organizationId,
) {
  return [...actions].some(
    (action) =>
      action !== "idp-admin-org:switch" && canShow(context, action, orgId),
  );
}

/** Server-rendered administration content must follow the latest authority and scope. */
export function requiresAdministrationRefresh(
  previous: PortalContext,
  current: PortalContext,
): boolean {
  return JSON.stringify(previous) !== JSON.stringify(current);
}
