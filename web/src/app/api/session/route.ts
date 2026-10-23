import { requireSession } from "../../../server/auth";
import { errorResponse, publicSession } from "../../../server/errors";
export const dynamic = "force-dynamic";
export async function GET(request: Request) {
  try {
    return publicSession(await requireSession(request));
  } catch (error) {
    return errorResponse(error);
  }
}
