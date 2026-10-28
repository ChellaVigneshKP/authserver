import {
  respond,
  clientAction,
} from "../../../../../../../../features/clients/api";
export const dynamic = "force-dynamic";
type Props = { params: Promise<{ orgId: string; appId: string }> };

export async function PUT(request: Request, { params }: Props) {
  return respond(async () => {
    const { orgId, appId } = await params;
    return clientAction(request, orgId, appId, "settings");
  }, 200);
}
