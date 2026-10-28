import { noStore } from "../../server/errors";
import { readiness } from "../../server/readiness";
export const dynamic = "force-dynamic";
/** Readiness reports only a status word; dependency details never leave the process. */
export async function GET() {
  try {
    await readiness();
    return Response.json({ status: "ready" }, { headers: noStore });
  } catch {
    return Response.json(
      { status: "unavailable" },
      { status: 503, headers: { ...noStore, "Retry-After": "5" } },
    );
  }
}
