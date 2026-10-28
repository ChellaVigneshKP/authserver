import {
  certificateListAction,
  respond,
} from "../../../../../../features/certificates/api";
export const dynamic = "force-dynamic";
type Context = { params: Promise<{ orgId: string }> };
export async function GET(r: Request, c: Context) {
  const { orgId } = await c.params;
  return respond(() => certificateListAction(r, orgId));
}
export async function POST(r: Request, c: Context) {
  const { orgId } = await c.params;
  return respond(() => certificateListAction(r, orgId), 201);
}
