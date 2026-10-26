import {
  respond,
  clientAction,
} from "../../../../../../../../../features/clients/api";
export const dynamic = "force-dynamic";
type Props = {
  params: Promise<{ orgId: string; appId: string; credentialId: string }>;
};
export async function PUT(request: Request, { params }: Props) {
  return respond(async () => {
    const { orgId, appId, credentialId } = await params;
    return clientAction(request, orgId, appId, "credential", credentialId);
  });
}
export async function DELETE(request: Request, { params }: Props) {
  return respond(async () => {
    const { orgId, appId, credentialId } = await params;
    return clientAction(request, orgId, appId, "credential", credentialId);
  });
}
