import {
  respond,
  authorize,
  listClients,
  startWizard,
} from "../../../../../../features/clients/api";
export const dynamic = "force-dynamic";
type Props = { params: Promise<{ orgId: string }> };
export async function GET(request: Request, { params }: Props) {
  return respond(async () => {
    const { orgId } = await params;
    const { session } = await authorize(request, orgId, "idp-admin-app:read");
    return listClients(session, orgId);
  });
}
export async function POST(request: Request, { params }: Props) {
  return respond(async () => startWizard(request, (await params).orgId), 201);
}
