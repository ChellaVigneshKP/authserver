import "server-only";
import { randomUUID } from "node:crypto";
import { z, type ZodType } from "zod";
import { requireSession, boundedBody } from "../../server/auth";
import { requireMutation } from "../../server/csrf";
import {
  javaRequest,
  VerifiedClientCreateRejection,
} from "../../server/java-api";
import { getPortalContext } from "../../server/portal-context";
import { getStore, type SessionStore } from "../../server/session-store";
import {
  PortalFailure,
  errorResponse,
  jsonResponse,
} from "../../server/errors";
import { canShow } from "../account/permissions";
import {
  ClientCreate,
  ClientUpdate,
  ClientSummary,
  ClientDetail,
  ClientUrls,
  UrlInput,
  TokenInput,
  CredentialMetadata,
  CredentialCreate,
  CredentialCreated,
  type ClientCreateInput,
  type ClientSummaryDto,
  type WizardStateDto,
} from "../../contracts/clients";
import {
  AssignmentWire,
  Resource,
  ResourceInput,
  assignmentToBrowser,
} from "../../contracts/resources";
import type { PortalSession } from "../../server/types";
export async function authorize(
  request: Request,
  org: string | undefined,
  permission: string,
  mutation = false,
) {
  const session = await requireSession(request);
  if (mutation) await requireMutation(request, session);
  const context = await getPortalContext(session);
  const organizationId = z.uuid().parse(org ?? context.subject.organizationId);
  if (!canShow(context, permission, organizationId))
    throw new PortalFailure(
      403,
      "ACCESS_DENIED",
      "Your current permissions do not allow this action in this organization.",
    );
  return { session, context, organizationId };
}
export async function body<T>(
  request: Request,
  schema: ZodType<T>,
): Promise<T> {
  try {
    const bytes = await boundedBody(new Response(request.body), 32768);
    return schema.parse(JSON.parse(Buffer.from(bytes).toString()));
  } catch (error) {
    if (error instanceof PortalFailure) throw error;
    throw new PortalFailure(
      400,
      "INVALID_INPUT",
      "Check the supplied fields, URLs and values.",
    );
  }
}
export async function respond(fn: () => Promise<unknown>, status = 200) {
  try {
    return jsonResponse(await fn(), status);
  } catch (error) {
    return errorResponse(error);
  }
}
export async function listClients(
  session: PortalSession,
  organizationId: string,
) {
  return javaRequest(
    session,
    { kind: "clients", organizationId },
    undefined,
    z.array(ClientSummary).max(10000),
  );
}
export async function createClient(
  session: PortalSession,
  organizationId: string,
  input: ClientCreateInput,
) {
  return javaRequest(
    session,
    { kind: "clientCreate", organizationId },
    ClientCreate.parse(input),
    ClientSummary,
  );
}
export async function clientBundle(
  session: PortalSession,
  organizationId: string,
  applicationId: string,
) {
  const scope = {
    organizationId,
    applicationId: z.uuid().parse(applicationId),
  };
  const detail = await javaRequest(
    session,
    { kind: "clientGet", ...scope },
    undefined,
    ClientDetail,
  );
  if (!detail.active)
    return {
      detail,
      urls: { applicationUrl: null, redirectUrls: [], logoutRedirectUrls: [] },
      credentials: [],
      assignments: [],
    };
  const [urls, credentials, assignments] = await Promise.all([
    javaRequest(
      session,
      { kind: "clientUrlsGet", ...scope },
      undefined,
      ClientUrls,
    ),
    javaRequest(
      session,
      { kind: "credentialList", ...scope },
      undefined,
      z.array(CredentialMetadata).max(10000),
    ),
    javaRequest(
      session,
      { kind: "assignmentList", ...scope },
      undefined,
      z.array(AssignmentWire).max(10000),
    ),
  ]);
  return {
    detail,
    urls,
    credentials,
    assignments: assignments.map(assignmentToBrowser),
  };
}
async function active(
  session: PortalSession,
  organizationId: string,
  applicationId: string,
) {
  const detail = await javaRequest(
    session,
    { kind: "clientGet", organizationId, applicationId },
    undefined,
    ClientDetail,
  );
  if (!detail.active)
    throw new PortalFailure(
      409,
      "CLIENT_INACTIVE",
      "This client is inactive. Reactivation is unavailable.",
    );
  return detail;
}
export async function readWizard(
  session: PortalSession,
  organizationId: string,
) {
  const store = await getStore();
  let state = await store.getWizard(session.id, organizationId);
  if (!state) return { state: null, candidates: [] };
  if (state.phase === "creating" && Date.now() - state.startedAt > 20000) {
    await store.replaceWizard(session.id, organizationId, state, {
      ...state,
      phase: "uncertain",
    });
    state = (await store.getWizard(session.id, organizationId)) ?? state;
  }
  if (state.applicationId) {
    const detail = await javaRequest(
      session,
      { kind: "clientGet", organizationId, applicationId: state.applicationId },
      undefined,
      ClientDetail,
    );
    return { state, candidates: [], detail };
  }
  const list = await listClients(session, organizationId);
  const candidates = list.filter(
    (v) =>
      !state.baseline.includes(v.id) &&
      v.name === state.identity.name &&
      v.type === state.identity.type &&
      v.authFlow === state.identity.authMethod,
  );
  return { state, candidates };
}
async function uncertainWizard(
  store: SessionStore,
  owner: string,
  organizationId: string,
  attempt: WizardStateDto,
): Promise<never> {
  // If a known application or a newer attempt already won, this CAS leaves it intact.
  await store.replaceWizard(owner, organizationId, attempt, {
    ...attempt,
    phase: "uncertain",
  });
  throw new PortalFailure(
    503,
    "CLIENT_CREATION_UNCERTAIN",
    "Creation may have completed. Read the current onboarding state before taking another action. No creation request was retried.",
    false,
  );
}
async function rejectedOrUncertainWizard(
  store: SessionStore,
  owner: string,
  organizationId: string,
  attempt: WizardStateDto,
  error: unknown,
  priorUncertain?: WizardStateDto,
): Promise<never> {
  if (!(error instanceof VerifiedClientCreateRejection))
    return uncertainWizard(store, owner, organizationId, attempt);
  if (
    !(await store.settleRejectedWizard(
      owner,
      organizationId,
      attempt.attemptId,
      priorUncertain,
    ))
  )
    throw new PortalFailure(
      409,
      "WIZARD_CHANGED",
      "Onboarding changed. Read the current state before taking another action.",
    );
  const message = priorUncertain
    ? "This retry was rejected. The earlier creation is still uncertain. Read current onboarding state and review matching clients before taking another action."
    : error.status === 409
      ? "Client creation was rejected. A client with that name may already exist; edit the name and try again."
      : error.status === 400
        ? "Client creation was rejected. Edit the name, type or authentication method and try again."
        : error.message;
  throw new PortalFailure(error.status, error.code, message, false);
}
export async function startWizard(request: Request, organizationId: string) {
  const { session } = await authorize(
    request,
    organizationId,
    "idp-admin-app:create",
    true,
  );
  const context = await getPortalContext(session);
  if (!canShow(context, "idp-admin-app:read", organizationId))
    throw new PortalFailure(
      403,
      "ACCESS_DENIED",
      "Client onboarding requires client read access for reconciliation.",
    );
  const identity = await body(request, ClientCreate),
    store = await getStore();
  const baseline = await listClients(session, organizationId);
  const state: WizardStateDto = {
    identity,
    baseline: baseline.map((v) => v.id),
    phase: "creating",
    attemptId: randomUUID(),
    startedAt: Date.now(),
    expiresAt: Date.now() + 1800000,
  };
  if (!(await store.beginWizard(session.id, organizationId, state)))
    throw new PortalFailure(
      409,
      "WIZARD_EXISTS",
      "Resume the existing onboarding request before creating another client.",
    );
  let created: ClientSummaryDto;
  try {
    created = await createClient(session, organizationId, identity);
  } catch (error) {
    return rejectedOrUncertainWizard(
      store,
      session.id,
      organizationId,
      state,
      error,
    );
  }
  // Success has crossed the commit boundary. No subsequent Redis/readback error
  // may retire this identity, even if its HTTP status would reject a create.
  try {
    const next = {
      ...state,
      applicationId: created.id,
      phase: "created" as const,
    };
    if (!(await store.replaceWizard(session.id, organizationId, state, next)))
      throw new PortalFailure(
        409,
        "WIZARD_CHANGED",
        "Onboarding changed. Read its current state.",
      );
    const detail = await javaRequest(
      session,
      { kind: "clientGet", organizationId, applicationId: created.id },
      undefined,
      ClientDetail,
    );
    return { state: next, candidates: [], detail };
  } catch {
    return uncertainWizard(store, session.id, organizationId, state);
  }
}
export async function reconcileWizard(
  request: Request,
  organizationId: string,
) {
  const { session } = await authorize(
    request,
    organizationId,
    "idp-admin-app:create",
    true,
  );
  const input = await body(
    request,
    z.union([
      z.object({ applicationId: z.uuid() }).strict(),
      z.object({ confirmRetry: z.literal(true) }).strict(),
      z.object({ finish: z.literal(true) }).strict(),
    ]),
  );
  const current = await readWizard(session, organizationId),
    store = await getStore();
  if (!current.state)
    throw new PortalFailure(
      404,
      "WIZARD_EXPIRED",
      "Onboarding has expired. Review the client list before creating another client.",
    );
  const state = current.state;
  if ("finish" in input) {
    if (!state.applicationId)
      throw new PortalFailure(
        409,
        "UNRESOLVED_CREATION",
        "Resolve the uncertain creation first.",
      );
    await store.removeWizard(session.id, organizationId, state);
    return { state: null, candidates: [] };
  }
  if ("applicationId" in input) {
    if (!current.candidates.some((v) => v.id === input.applicationId))
      throw new PortalFailure(
        409,
        "CANDIDATE_CHANGED",
        "The candidate list changed. Read the current state again.",
      );
    const next = {
      ...state,
      applicationId: input.applicationId,
      phase: "created" as const,
    };
    if (!(await store.replaceWizard(session.id, organizationId, state, next)))
      throw new PortalFailure(
        409,
        "WIZARD_CHANGED",
        "Onboarding changed. Read the current state again.",
      );
    return readWizard(session, organizationId);
  }
  // A fresh read and an explicit acknowledgement are required even when no candidate exists.
  if (state.applicationId || current.candidates.length)
    throw new PortalFailure(
      409,
      "REVIEW_CANDIDATES",
      "Review the existing client candidates before creating another client.",
    );
  const next = await store.claimWizardRetry(session.id, organizationId, state);
  if (!next)
    throw new PortalFailure(
      409,
      "WIZARD_PENDING",
      "This creation attempt is still pending or changed. Read current state before explicitly reviewing a retry.",
    );
  let created: ClientSummaryDto;
  try {
    created = await createClient(session, organizationId, state.identity);
  } catch (error) {
    // A rejected retry proves nothing about the earlier genuinely uncertain call.
    return rejectedOrUncertainWizard(
      store,
      session.id,
      organizationId,
      next,
      error,
      state,
    );
  }
  try {
    if (
      !(await store.replaceWizard(session.id, organizationId, next, {
        ...next,
        applicationId: created.id,
        phase: "created",
      }))
    )
      throw new PortalFailure(
        409,
        "WIZARD_CHANGED",
        "Onboarding changed. Read its current state.",
      );
    return await readWizard(session, organizationId);
  } catch {
    return uncertainWizard(store, session.id, organizationId, next);
  }
}
export async function clientAction(
  request: Request,
  organizationId: string,
  applicationId: string,
  action:
    | "identity"
    | "urls"
    | "settings"
    | "credentials"
    | "credential"
    | "assignments"
    | "assignment",
  target?: string,
) {
  const permission = action.startsWith("assignment")
    ? "idp-admin-app:assign-resource"
    : request.method === "DELETE" && action === "identity"
      ? "idp-admin-app:delete"
      : "idp-admin-app:update";
  const { session } = await authorize(
    request,
    organizationId,
    permission,
    true,
  );
  const scope = {
    organizationId,
    applicationId: z.uuid().parse(applicationId),
  };
  const detail = await active(session, organizationId, scope.applicationId);
  switch (action) {
    case "identity":
      if (request.method === "DELETE") {
        await javaRequest(
          session,
          { kind: "clientDeactivate", ...scope },
          undefined,
          z.unknown(),
        );
        return {
          detail: await javaRequest(
            session,
            { kind: "clientGet", ...scope },
            undefined,
            ClientDetail,
          ),
        };
      }
      {
        const value = await body(request, ClientUpdate);
        await javaRequest(
          session,
          { kind: "clientUpdate", ...scope },
          {
            ...value,
            usernameType: detail.usernameType,
            allowForgotUsername: detail.allowForgotUsername,
            forgotUsernameSettings: detail.forgotUsernameSettings ?? [],
            pinTimeToLive: detail.pinTimeToLive,
          },
          ClientSummary,
        );
        break;
      }
    case "urls":
      await javaRequest(
        session,
        { kind: "clientUrlsPut", ...scope },
        await body(request, UrlInput),
        z.unknown(),
      );
      break;
    case "settings":
      await javaRequest(
        session,
        { kind: "clientSettingsPut", ...scope },
        await body(request, TokenInput),
        z.unknown(),
      );
      break;
    case "credentials": {
      const input = await body(request, CredentialCreate);
      if (
        detail.authenticationMethod === "PKCE" ||
        (detail.authenticationMethod === "CLIENT_SECRET_JWT") !==
          (input.type === "SHARED_SECRET")
      )
        throw new PortalFailure(
          400,
          "CREDENTIAL_TYPE_MISMATCH",
          "The credential must match this client authentication method.",
        );
      try {
        return await javaRequest(
          session,
          { kind: "credentialCreate", ...scope },
          input,
          CredentialCreated,
        );
      } catch (error) {
        if (
          error instanceof PortalFailure &&
          [400, 403, 404, 409, 401].includes(error.status)
        )
          throw error;
        throw new PortalFailure(
          503,
          "CREDENTIAL_CREATION_UNCERTAIN",
          "Credential creation may have completed. Refresh metadata, disable the new credential and rotate it. A lost secret cannot be recovered by reading it.",
          false,
        );
      }
    }
    case "credential":
      if (request.method === "DELETE")
        await javaRequest(
          session,
          {
            kind: "credentialDelete",
            ...scope,
            credentialId: z.uuid().parse(target),
          },
          undefined,
          z.undefined(),
        );
      else
        await javaRequest(
          session,
          {
            kind: "credentialStatus",
            ...scope,
            credentialId: z.uuid().parse(target),
          },
          await body(
            request,
            z.object({ status: z.enum(["Active", "Disabled"]) }).strict(),
          ),
          z.unknown(),
        );
      break;
    case "assignments":
      await javaRequest(
        session,
        { kind: "assignmentCreate", ...scope },
        await body(request, z.object({ resourceId: z.uuid() }).strict()),
        z.undefined(),
      );
      break;
    case "assignment":
      await javaRequest(
        session,
        {
          kind: "assignmentDelete",
          ...scope,
          resourceId: z.uuid().parse(target),
        },
        undefined,
        z.undefined(),
      );
      break;
  }
  return clientBundle(session, organizationId, scope.applicationId);
}
export async function resourceAction(request: Request, resourceId?: string) {
  const permission =
    "idp-admin-resource:" +
    (request.method === "GET" ? "read" : resourceId ? "update" : "create");
  const { session } = await authorize(
    request,
    undefined,
    permission,
    request.method !== "GET",
  );
  if (request.method === "GET")
    return resourceId
      ? javaRequest(
          session,
          { kind: "resourceGet", resourceId: z.uuid().parse(resourceId) },
          undefined,
          Resource,
        )
      : javaRequest(
          session,
          { kind: "resourceList" },
          undefined,
          z.array(Resource).max(10000),
        );
  const input = await body(request, ResourceInput);
  return javaRequest(
    session,
    resourceId
      ? { kind: "resourceUpdate", resourceId: z.uuid().parse(resourceId) }
      : { kind: "resourceCreate" },
    input,
    Resource,
  );
}
