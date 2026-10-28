import "server-only";
import { createHmac, randomUUID, timingSafeEqual } from "node:crypto";
import type { ZodType } from "zod";
import { z } from "zod";
import { getConfig, type Config } from "./config";
import { getStore, type SessionStore } from "./session-store";
import { boundedBody, transportUrl, upstreamFetch } from "./auth";
import { PortalFailure, signin } from "./errors";
import { UserPageQuery } from "../contracts/users";
import { conflictGuidance, roleConflict } from "../contracts/roles";
import type { PortalSession } from "./types";
// Only a verified noncommitting response from the create call carries this authority.
// Transport/signature errors, early unverified401 and later reads must never use it.
export class VerifiedClientCreateRejection extends PortalFailure {}
type ClientScope = { organizationId: string; applicationId: string };
export type JavaOperation =
  | { kind: "context" }
  | { kind: "organizationList" | "organizationCreate" }
  | { kind: "organizationGet" | "organizationUpdate" | "primaryContact" | "secondaryContact"; organizationId: string }
  | { kind: "userList"; organizationId: string; query: import("../contracts/users").UserQuery }
  | { kind: "userCreate" }
  | { kind: "userGet" | "userDelete" | "userProfile" | "userEmail" | "userUsername" | "userPassword" | "userSecurity" | "userStatus" | "userMetadataGet" | "userMetadataPut"; userId: string; loginId?: string }
  | { kind: "clients" | "clientCreate"; organizationId: string }
  | ({
      kind:
        | "clientGet"
        | "clientUpdate"
        | "clientDeactivate"
        | "clientUrlsGet"
        | "clientUrlsPut"
        | "clientSettingsPut"
        | "credentialList"
        | "credentialCreate"
        | "assignmentList"
        | "assignmentCreate";
    } & ClientScope)
  | ({
      kind: "credentialStatus" | "credentialDelete";
      credentialId: string;
    } & ClientScope)
  | ({ kind: "assignmentDelete"; resourceId: string } & ClientScope)
  | { kind: "resourceList" | "resourceCreate" }
  | { kind: "resourceGet" | "resourceUpdate"; resourceId: string }
  | { kind: "roleList" | "roleCreate"; organizationId: string }
  | {
      kind: "roleGet" | "roleUpdate" | "roleDeactivate" | "rolePermissions";
      organizationId: string;
      roleId: string;
    }
  | { kind: "roleAssign"; organizationId: string; userId: string }
  | { kind: "roleCatalog" };
function operationRoute(operation: JavaOperation): {
  path: string;
  method: string;
  body: boolean;
} {
  const org =
    "organizationId" in operation
      ? z.uuid().parse(operation.organizationId)
      : undefined;
  const base = "/api/v1/organizations/" + org + "/applications";
  const app =
    "applicationId" in operation
      ? base + "/" + z.uuid().parse(operation.applicationId)
      : base;
  const roleBase =
    "organizationId" in operation
      ? "/api/v1/organizations/" + org + "/groups"
      : "";
  const role = "roleId" in operation ? z.uuid().parse(operation.roleId) : "";
  switch (operation.kind) {
    case "organizationList": return {path:"/api/v1/organizations",method:"GET",body:false};
    case "organizationCreate": return {path:"/api/v1/organizations",method:"POST",body:true};
    case "organizationGet": return {path:"/api/v1/organizations/"+org,method:"GET",body:false};
    case "organizationUpdate": return {path:"/api/v1/organizations/"+org,method:"PUT",body:true};
    case "primaryContact": return {path:"/api/v1/organizations/"+org+"/primary-contact",method:"PUT",body:true};
    case "secondaryContact": return {path:"/api/v1/organizations/"+org+"/secondary-contact",method:"PUT",body:true};
    case "userList": {
      const q=UserPageQuery.parse(operation.query);
      const params=new URLSearchParams({organizationId:org!,offset:String(q.offset),resultsPerPage:String(q.limit)});
      for(const field of ["search","status","type"] as const) if(q[field])params.set(field,q[field]);
      return {path:"/api/v1/users?"+params,method:"GET",body:false};
    }
    case "userCreate": return {path:"/api/v1/users",method:"POST",body:true};
    case "userGet": return {path:"/api/v1/users/"+z.uuid().parse(operation.userId)+(operation.loginId?"?loginId="+z.uuid().parse(operation.loginId):""),method:"GET",body:false};
    case "userDelete": return {path:"/api/v1/users/"+z.uuid().parse(operation.userId),method:"DELETE",body:false};
    case "userProfile": case "userEmail": case "userUsername": case "userPassword": case "userSecurity": case "userStatus": case "userMetadataGet": case "userMetadataPut": {
      const suffix={userProfile:"profile",userEmail:"email",userUsername:"username",userPassword:"password",userSecurity:"security-settings",userStatus:"status",userMetadataGet:"metadata",userMetadataPut:"metadata"}[operation.kind];
      const read=operation.kind==="userMetadataGet";
      return {path:"/api/v1/users/"+z.uuid().parse(operation.userId)+"/"+suffix+((operation.kind==="userPassword"||operation.kind==="userUsername")&&operation.loginId?"?loginId="+z.uuid().parse(operation.loginId):""),method:read?"GET":"PUT",body:!read};
    }
    case "context":
      return { path: "/api/v1/portal/context", method: "GET", body: false };
    case "clients":
      return { path: base, method: "GET", body: false };
    case "clientCreate":
      return { path: base, method: "POST", body: true };
    case "clientGet":
      return { path: app, method: "GET", body: false };
    case "clientUpdate":
      return { path: app, method: "PUT", body: true };
    case "clientDeactivate":
      return { path: app, method: "DELETE", body: false };
    case "clientUrlsGet":
      return { path: app + "/urls", method: "GET", body: false };
    case "clientUrlsPut":
      return { path: app + "/urls", method: "PUT", body: true };
    case "clientSettingsPut":
      return { path: app + "/settings", method: "PUT", body: true };
    case "credentialList":
      return {
        path: app.replace("/api/v1/", "/api/v2/") + "/credentials",
        method: "GET",
        body: false,
      };
    case "credentialCreate":
      return { path: app + "/credentials", method: "POST", body: true };
    case "credentialStatus":
      return {
        path: app + "/credentials/" + z.uuid().parse(operation.credentialId),
        method: "PUT",
        body: true,
      };
    case "credentialDelete":
      return {
        path: app + "/credentials/" + z.uuid().parse(operation.credentialId),
        method: "DELETE",
        body: false,
      };
    case "assignmentList":
      return { path: app + "/endpoints", method: "GET", body: false };
    case "assignmentCreate":
      return { path: app + "/endpoints", method: "POST", body: true };
    case "assignmentDelete":
      return {
        path: app + "/endpoints/" + z.uuid().parse(operation.resourceId),
        method: "DELETE",
        body: false,
      };
    case "resourceList":
      return { path: "/api/v1/endpoints", method: "GET", body: false };
    case "resourceCreate":
      return { path: "/api/v1/endpoints", method: "POST", body: true };
    case "resourceGet":
      return {
        path: "/api/v1/endpoints/" + z.uuid().parse(operation.resourceId),
        method: "GET",
        body: false,
      };
    case "resourceUpdate":
      return {
        path: "/api/v1/endpoints/" + z.uuid().parse(operation.resourceId),
        method: "PUT",
        body: true,
      };
    case "roleList":
      return { path: roleBase, method: "GET", body: false };
    case "roleCreate":
      return { path: roleBase, method: "POST", body: true };
    case "roleCatalog":
      return { path: "/api/v1/portal/permissions", method: "GET", body: false };
    case "roleGet":
      return {
        path: roleBase + "/" + role,
        method: "GET",
        body: false,
      };
    case "roleUpdate":
      return {
        path: roleBase + "/" + role,
        method: "PUT",
        body: true,
      };
    case "roleDeactivate":
      return {
        path: roleBase + "/" + role,
        method: "DELETE",
        body: false,
      };
    case "rolePermissions":
      return {
        path: roleBase + "/" + role + "/permissions",
        method: "GET",
        body: false,
      };
    case "roleAssign":
      return {
        path:
          "/api/v1/organizations/" +
          org +
          "/users/" +
          z.uuid().parse(operation.userId) +
          "/group",
        method: "PUT",
        body: true,
      };
    default:
      throw new PortalFailure(
        400,
        "INVALID_OPERATION",
        "This operation is unavailable.",
      );
  }
}
export async function javaRequestWith<T>(
  config: Config,
  store: SessionStore,
  session: PortalSession,
  operation: JavaOperation,
  input: unknown,
  schema: ZodType<T>,
): Promise<T> {
  if (session.expiresAt <= Date.now()) {
    await store.deleteSession(session.id);
    throw signin();
  }
  const route = operationRoute(operation);
  if (
    (!route.body && input !== undefined) ||
    (route.body && input === undefined)
  )
    throw new PortalFailure(
      400,
      "INVALID_INPUT",
      "This operation received invalid input.",
    );
  const raw = route.body ? Buffer.from(JSON.stringify(input)) : Buffer.alloc(0);
  const signature = createHmac("sha256", session.signingKey)
    .update(raw)
    .digest("base64");
  const response = await upstreamFetch(
    transportUrl(config, config.issuer + route.path),
    {
      method: route.method,
      body: route.body ? raw : undefined,
      headers: {
        ...(route.body ? { "Content-Type": "application/json" } : {}),
        Authorization: "Bearer " + session.accessToken,
        "User-Agent": session.browserContext.userAgent,
        "Accept-Language": session.browserContext.acceptLanguage,
        Referer: session.browserContext.externalOrigin + "/",
        "x-request-datetime": new Date().toISOString(),
        "x-request-id": randomUUID(),
        "x-signature": signature,
      },
    },
  );
  if (response.status === 401) {
    await store.deleteSession(session.id);
    throw signin();
  }
  const bytes = await boundedBody(response, 1024 * 1024);
  const expected = createHmac("sha256", session.signingKey)
    .update(bytes)
    .digest("base64");
  const actual = response.headers.get("x-signature") ?? "";
  if (
    actual.length !== expected.length ||
    !timingSafeEqual(Buffer.from(actual), Buffer.from(expected))
  )
    throw new PortalFailure(
      502,
      "INVALID_RESPONSE_SIGNATURE",
      "The identity service response could not be verified.",
    );
  if (!response.ok) {
    const messages: Record<number, [string, string]> = {
      400: ["INVALID_INPUT", "Check the supplied values."],
      403: ["ACCESS_DENIED", "You do not have access to this action."],
      404: ["NOT_FOUND", "The requested item was not found."],
      409: ["CONFLICT", "This item changed. Refresh and review it."],
      503: [
        "DEPENDENCY_UNAVAILABLE",
        "The identity service is temporarily unavailable.",
      ],
    };
    const [code, message] = messages[response.status] ?? [
      "UPSTREAM_FAILURE",
      "The identity service could not complete the request.",
    ];
    const detailed =
      response.status === 409 && operation.kind.startsWith("role")
        ? roleConflictFailure(bytes)
        : undefined;
    if (detailed) throw detailed;
    if (
      operation.kind === "clientCreate" &&
      [400, 403, 404, 409].includes(response.status)
    )
      throw new VerifiedClientCreateRejection(response.status, code, message);
    throw new PortalFailure(
      response.status in messages ? response.status : 502,
      code,
      message,
      response.status === 503,
    );
  }
  try {
    return schema.parse(
      bytes.length ? JSON.parse(Buffer.from(bytes).toString()) : undefined,
    );
  } catch {
    throw new PortalFailure(
      502,
      "UPSTREAM_INVALID",
      "The identity service returned an invalid response.",
    );
  }
}
/**
 * Only signature-verified role conflicts may name a specific cause. Unrecognized codes and
 * non-role operations stay on the generic conflict so upstream strings are never reflected.
 */
function roleConflictFailure(bytes: Uint8Array): PortalFailure | undefined {
  let code: unknown;
  try {
    code = JSON.parse(Buffer.from(bytes).toString()).code;
  } catch {
    return undefined;
  }
  if (typeof code !== "string") return undefined;
  const conflict = roleConflict(code);
  if (conflict.kind === "other") return undefined;
  return new PortalFailure(409, conflict.code, conflictGuidance(conflict));
}
export async function javaRequest<T>(
  session: PortalSession,
  operation: JavaOperation,
  input: unknown,
  schema: ZodType<T>,
): Promise<T> {
  return javaRequestWith(
    getConfig(),
    await getStore(),
    session,
    operation,
    input,
    schema,
  );
}
