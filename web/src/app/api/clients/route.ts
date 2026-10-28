import { z } from "zod";
import { requireSession } from "../../../server/auth";
import { getPortalContext } from "../../../server/portal-context";
import { javaRequest } from "../../../server/java-api";
import {
  errorResponse,
  jsonResponse,
  PortalFailure,
} from "../../../server/errors";
export const dynamic = "force-dynamic";
const ClientRead = z.array(
  z.object({ id: z.uuid(), name: z.string(), active: z.boolean() }),
);
export async function GET(request: Request) {
  try {
    const session = await requireSession(request);
    const context = await getPortalContext(session);
    const org =
      new URL(request.url).searchParams.get("organizationId") ??
      context.subject.organizationId;
    if (
      !context.organizations.some((v) => v.id === org) ||
      !context.permissions.includes("idp-admin-app:read")
    )
      throw new PortalFailure(
        403,
        "ACCESS_DENIED",
        "You do not have access to these clients.",
      );
    return jsonResponse(
      await javaRequest(
        session,
        { kind: "clients", organizationId: org },
        undefined,
        ClientRead,
      ),
    );
  } catch (error) {
    return errorResponse(error);
  }
}
