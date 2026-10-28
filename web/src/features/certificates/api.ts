import "server-only";
import { z } from "zod";
import { requireSession } from "../../server/auth";
import { requireMutation, requireMultipartMutation } from "../../server/csrf";
import { getPortalContext } from "../../server/portal-context";
import {
  javaDownload,
  javaMultipart,
  javaRequest,
  type CertificateDownload,
} from "../../server/java-api";
import { PortalFailure } from "../../server/errors";
import {
  buildCertificateMultipart,
  parseCertificateMultipart,
  readBoundedRequest,
} from "../../server/multipart";
import { canShow } from "../account/permissions";
import * as C from "../../contracts/certificates";
import type { PortalContext } from "../../contracts/portal";
import type { PortalSession } from "../../server/types";
export { respond } from "../clients/api";

const READ = [...C.CERTIFICATE_READ_PERMISSIONS];

/** Session and CSRF checks only; the permission depends on the stored certificate type. */
async function accessContext(
  request: Request,
  orgId: string | undefined,
  mutation: MutationKind = "none",
) {
  const session = await requireSession(request);
  if (mutation === "json") await requireMutation(request, session);
  if (mutation === "multipart")
    await requireMultipartMutation(request, session);
  const context = await getPortalContext(session);
  const organizationId = z.uuid().parse(orgId ?? context.subject.organizationId);
  return { session, context, organizationId };
}

type MutationKind = "none" | "json" | "multipart";

/** Presentation checks only: Java reloads the actor's grants on every operation. */
function requirePermission(
  context: PortalContext,
  permissions: string[],
  organizationId: string,
) {
  if (!permissions.some((permission) => canShow(context, permission, organizationId)))
    throw new PortalFailure(
      403,
      "ACCESS_DENIED",
      "Your current permissions do not allow this action.",
    );
}

export async function access(
  request: Request,
  orgId: string | undefined,
  permissions: string[],
  mutation: MutationKind = "none",
) {
  const base = await accessContext(request, orgId, mutation);
  requirePermission(base.context, permissions, base.organizationId);
  return base;
}

export async function listCertificates(
  session: PortalSession,
  organizationId: string,
): Promise<C.CertificateSummary[]> {
  return javaRequest(
    session,
    { kind: "certificateList", organizationId },
    undefined,
    C.CertificateList,
  );
}

export async function certificateDetail(
  session: PortalSession,
  organizationId: string,
  certificateId: string,
): Promise<C.CertificateDetailDto> {
  return javaRequest(
    session,
    {
      kind: "certificateGet",
      organizationId,
      certificateId: z.uuid().parse(certificateId),
    },
    undefined,
    C.CertificateDetail,
  );
}

const UploadFields = z.object({
  name: C.CertificateName,
  type: C.CertificateType,
  password: C.CertificatePassword,
});

/**
 * The client multipart body is bounded and parsed locally, then rebuilt canonically
 * so arbitrary parts, filenames or duplicate fields never reach Java.
 */
export async function importCertificate(
  request: Request,
  orgId: string | undefined,
) {
  const { session, context, organizationId } = await accessContext(
    request,
    orgId,
    "multipart",
  );
  // Reject an actor with no create grant before buffering the multipart body.
  requirePermission(
    context,
    [...C.CERTIFICATE_CREATE_PERMISSIONS],
    organizationId,
  );
  const bytes = await readBoundedRequest(
    request,
    C.MAX_CERTIFICATE_REQUEST_BYTES,
  );
  const upload = parseCertificateMultipart(
    bytes,
    request.headers.get("content-type"),
  );
  const fields = UploadFields.parse({
    name: upload.name,
    type: upload.type,
    password: upload.password,
  });
  requirePermission(
    context,
    [C.certificateCreatePermission(fields.type)],
    organizationId,
  );
  return javaMultipart(
    session,
    { kind: "certificateCreate", organizationId },
    buildCertificateMultipart({ ...upload, ...fields }),
    C.CertificateCreateResponse,
  );
}

export async function deactivateCertificate(
  session: PortalSession,
  organizationId: string,
  certificateId: string,
) {
  await javaRequest(
    session,
    {
      kind: "certificateDelete",
      organizationId,
      certificateId: z.uuid().parse(certificateId),
    },
    undefined,
    z.unknown(),
  );
  return { updated: true };
}

export async function downloadCertificate(
  session: PortalSession,
  organizationId: string,
  certificateId: string,
): Promise<CertificateDownload> {
  return javaDownload(session, {
    kind: "certificateDownload",
    organizationId,
    certificateId: z.uuid().parse(certificateId),
  });
}

export async function certificateListAction(request: Request, orgId: string) {
  if (request.method === "GET") {
    const { session, organizationId } = await access(request, orgId, READ);
    return { items: await listCertificates(session, organizationId) };
  }
  if (request.method === "POST") return importCertificate(request, orgId);
  throw invalidAction();
}

export async function certificateItemAction(
  request: Request,
  orgId: string,
  certificateId: string,
) {
  if (request.method === "GET") {
    const { session, organizationId } = await access(request, orgId, READ);
    return certificateDetail(session, organizationId, certificateId);
  }
  if (request.method !== "DELETE") throw invalidAction();
  const { session, context, organizationId } = await accessContext(
    request,
    orgId,
    "json",
  );
  let stored: C.CertificateDetailDto | undefined;
  try {
    stored = await certificateDetail(session, organizationId, certificateId);
  } catch (error) {
    // A delete-only grant cannot read the record. Java re-derives the stored
    // type and its delete permission, so defer the decision to it.
    if (!(error instanceof PortalFailure) || error.status !== 403) throw error;
  }
  requirePermission(
    context,
    stored
      ? [C.certificateDeletePermission(stored.type)]
      : [...C.CERTIFICATE_DELETE_PERMISSIONS],
    organizationId,
  );
  return deactivateCertificate(session, organizationId, certificateId);
}

export async function certificateDownloadAction(
  request: Request,
  orgId: string,
  certificateId: string,
) {
  if (request.method !== "GET") throw invalidAction();
  const { session, organizationId } = await access(request, orgId, READ);
  return downloadCertificate(session, organizationId, certificateId);
}

function invalidAction() {
  return new PortalFailure(
    400,
    "INVALID_OPERATION",
    "This action is unavailable.",
  );
}
