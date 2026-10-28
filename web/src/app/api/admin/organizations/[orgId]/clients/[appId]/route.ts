import {
  respond,
  authorize,
  clientBundle,
  clientAction,
} from "../../../../../../../features/clients/api";
export const dynamic = "force-dynamic";
type Props = { params: Promise<{ orgId: string; appId: string }> };
export async function GET(request: Request, { params }: Props) {
  return respond(async () => {
    const { orgId, appId } = await params;
    const { session } = await authorize(request, orgId, "idp-admin-app:read");
    return clientBundle(session, orgId, appId);
  });
}
export async function PUT(request: Request, { params }: Props) {
  return respond(async () => {
    const { orgId, appId } = await params;
    return clientAction(request, orgId, appId, "identity");
  });
}
export async function DELETE(request: Request, { params }: Props) {
  return respond(async () => {
    const { orgId, appId } = await params;
    return clientAction(request, orgId, appId, "identity");
  });
}
