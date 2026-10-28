import {
  certificateItemAction,
  respond,
} from "../../../../../../../features/certificates/api";
export const dynamic = "force-dynamic";
type Context = { params: Promise<{ orgId: string; certId: string }> };
export async function GET(r: Request, c: Context) {
  const { orgId, certId } = await c.params;
  return respond(() => certificateItemAction(r, orgId, certId));
}
export async function DELETE(r: Request, c: Context) {
  const { orgId, certId } = await c.params;
  return respond(() => certificateItemAction(r, orgId, certId));
}
