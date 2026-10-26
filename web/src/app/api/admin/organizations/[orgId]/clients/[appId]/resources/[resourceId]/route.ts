import {
  respond,
  clientAction,
} from "../../../../../../../../../features/clients/api";
export const dynamic = "force-dynamic";
type Props = {
  params: Promise<{ orgId: string; appId: string; resourceId: string }>;
};
export async function DELETE(request: Request, { params }: Props) {
  return respond(async () => {
    const { orgId, appId, resourceId } = await params;
    return clientAction(request, orgId, appId, "assignment", resourceId);
  });
}
