import { requireSession } from "../../../server/auth";
import { getPortalContext } from "../../../server/portal-context";
import { errorResponse, jsonResponse } from "../../../server/errors";
export const dynamic = "force-dynamic";
export async function GET(request: Request) {
  try {
    return jsonResponse(await getPortalContext(await requireSession(request)));
  } catch (error) {
    return errorResponse(error);
  }
}
