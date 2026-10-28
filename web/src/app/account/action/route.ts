import { z } from "zod";
import { requireSession } from "../../../server/auth";
import { getPortalContext } from "../../../server/portal-context";
import { buildHostedAction } from "../../../server/hosted-actions";
import { errorResponse, noStore, PortalFailure } from "../../../server/errors";
export const dynamic = "force-dynamic";
export async function GET(request: Request) {
  try {
    const session = await requireSession(request);
    const params = new URL(request.url).searchParams;
    if (
      [...params.keys()].length !== 2 ||
      params.getAll("action").length !== 1 ||
      params.getAll("returnTo").length !== 1
    )
      throw new PortalFailure(
        400,
        "INVALID_INPUT",
        "Choose an account action.",
      );
    const parsed = z
      .object({
        action: z.enum(["change-profile", "change-password", "recovery"]),
        returnTo: z.enum(["/account", "/account/security"]),
      })
      .safeParse({
        action: params.get("action"),
        returnTo: params.get("returnTo"),
      });
    if (!parsed.success)
      throw new PortalFailure(
        400,
        "INVALID_INPUT",
        "Choose an account action and return page.",
      );
    const context = await getPortalContext(session);
    const target = buildHostedAction(
      parsed.data.action,
      parsed.data.returnTo,
      context.subject.loginId,
    );
    return new Response(null, {
      status: 303,
      headers: {
        ...noStore,
        Location: target.href,
        "Referrer-Policy": "origin",
      },
    });
  } catch (error) {
    return errorResponse(error);
  }
}
