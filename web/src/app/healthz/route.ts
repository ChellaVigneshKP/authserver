import { noStore } from "../../server/errors";
export const dynamic = "force-dynamic";
/** Liveness only: the process answers without touching a dependency. */
export function GET() {
  return Response.json({ status: "ok" }, { headers: noStore });
}
