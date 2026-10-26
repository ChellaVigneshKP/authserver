import { respond, resourceAction } from "../../../../features/clients/api";
export const dynamic = "force-dynamic";
export async function GET(request: Request) {
  return respond(() => resourceAction(request));
}
export async function POST(request: Request) {
  return respond(() => resourceAction(request), 201);
}
