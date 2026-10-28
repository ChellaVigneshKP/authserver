import "server-only";
import { z, type ZodType } from "zod";
import { requireSession, boundedBody } from "../../server/auth";
import { requireMutation } from "../../server/csrf";
import { getPortalContext } from "../../server/portal-context";
import { javaRequest } from "../../server/java-api";
import { PortalFailure } from "../../server/errors";
import { canShow } from "../account/permissions";
import * as R from "../../contracts/roles";
import type { PortalSession } from "../../server/types";
export { respond } from "../clients/api";

const READ = "idp-admin-group:read";

export async function input<T>(request: Request, schema: ZodType<T>) {
  try {
    const bytes = await boundedBody(new Response(request.body), 16384);
    return schema.parse(JSON.parse(Buffer.from(bytes).toString("utf8")));
  } catch {
    throw new PortalFailure(400, "INVALID_INPUT", "Check the supplied role fields.");
  }
}

/** Presentation checks only: Java reloads the actor's grants on every operation. */
export async function access(
  request: Request,
  orgId: string | undefined,
  permissions: string[],
  mutation = false,
) {
  const session = await requireSession(request);
  if (mutation) await requireMutation(request, session);
  const context = await getPortalContext(session);
  const organizationId = z.uuid().parse(orgId ?? context.subject.organizationId);
  if (!permissions.some((p) => canShow(context, p, organizationId)))
    throw new PortalFailure(
      403,
      "ACCESS_DENIED",
      "Your current permissions do not allow this action.",
    );
  return { session, context, organizationId };
}

export async function listRoles(
  session: PortalSession,
  organizationId: string,
): Promise<R.RoleSummary[]> {
  const roles = await javaRequest(
    session,
    { kind: "roleList", organizationId },
    undefined,
    R.LegacyRoleList,
  );
  return roles.map(R.roleSummaryFromLegacy);
}

export async function roleDetail(
  session: PortalSession,
  organizationId: string,
  roleId: string,
): Promise<R.RoleDto> {
  return javaRequest(
    session,
    { kind: "roleGet", organizationId, roleId: z.uuid().parse(roleId) },
    undefined,
    R.RoleDetail,
  );
}

export async function rolePermissions(
  session: PortalSession,
  organizationId: string,
  roleId: string,
) {
  return javaRequest(
    session,
    { kind: "rolePermissions", organizationId, roleId: z.uuid().parse(roleId) },
    undefined,
    R.GroupPermissionList,
  );
}

export async function permissionCatalog(
  session: PortalSession,
): Promise<R.PermissionCatalogEntryDto[]> {
  return javaRequest(session, { kind: "roleCatalog" }, undefined, R.PermissionCatalog);
}

/**
 * Assignment eligibility is a ceiling question, not definition protection. The legacy list
 * omits permission keys, so each active role is read before it can be offered.
 */
export async function assignableRoles(
  session: PortalSession,
  organizationId: string,
  ceiling: readonly string[],
) {
  const active = (await listRoles(session, organizationId)).filter(
    (role) => role.status === "Active",
  );
  const details: R.RoleDto[] = [];
  for (let start = 0; start < active.length; start += 4)
    details.push(
      ...(await Promise.all(
        active
          .slice(start, start + 4)
          .map((role) => roleDetail(session, organizationId, role.id)),
      )),
    );
  return details
    .filter((role) => role.permissionKeys.every((key) => ceiling.includes(key)))
    .map((role) => ({
      id: role.id,
      name: role.name,
      memberCount: role.memberCount,
    }));
}

export async function roleListAction(request: Request, orgId: string) {
  const mutation = request.method === "POST";
  const { session, organizationId } = await access(
    request,
    orgId,
    mutation ? ["idp-admin-group:create"] : [READ],
    mutation,
  );
  if (!mutation) return { items: await listRoles(session, organizationId) };
  const value = await input(request, R.RoleInput);
  return javaRequest(
    session,
    { kind: "roleCreate", organizationId },
    value,
    R.RoleDetail,
  );
}

export async function roleAction(
  request: Request,
  orgId: string,
  roleId: string,
  action?: "permissions",
) {
  const mutation = request.method !== "GET";
  const permission =
    request.method === "DELETE" ? "idp-admin-group:delete" : "idp-admin-group:update";
  const { session, organizationId } = await access(
    request,
    orgId,
    mutation ? [permission] : [READ],
    mutation,
  );
  if (request.method === "GET")
    return action === "permissions"
      ? { items: await rolePermissions(session, organizationId, roleId) }
      : roleDetail(session, organizationId, roleId);
  if (request.method === "DELETE") {
    await javaRequest(
      session,
      { kind: "roleDeactivate", organizationId, roleId },
      undefined,
      z.unknown(),
    );
    return { updated: true };
  }
  if (request.method !== "PUT")
    throw new PortalFailure(400, "INVALID_OPERATION", "This action is unavailable.");
  const value = await input(request, R.RoleUpdate);
  return javaRequest(
    session,
    { kind: "roleUpdate", organizationId, roleId },
    value,
    R.RoleDetail,
  );
}

export async function roleAssignmentAction(
  request: Request,
  orgId: string,
  userId: string,
) {
  const { session, organizationId } = await access(
    request,
    orgId,
    ["idp-admin-group:assign"],
    true,
  );
  const value = await input(request, R.RoleAssignmentInput);
  await javaRequest(
    session,
    { kind: "roleAssign", organizationId, userId },
    value,
    z.unknown(),
  );
  return { updated: true };
}

/** Only roles whose every grant is inside the actor's ceiling may be offered for assignment. */
export async function eligibleRolesAction(request: Request, orgId: string) {
  const { session, context, organizationId } = await access(request, orgId, [
    "idp-admin-group:assign",
  ]);
  return { items: await assignableRoles(session, organizationId, context.permissions) };
}

export async function roleCatalogAction(request: Request) {
  const { session } = await access(request, undefined, ["idp-admin-permission:read"]);
  return { items: await permissionCatalog(session) };
}
