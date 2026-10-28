import {
  respond,
  authorize,
  readWizard,
  reconcileWizard,
} from "../../../../../../../features/clients/api";
export const dynamic = "force-dynamic";
type Props = { params: Promise<{ orgId: string }> };
export async function GET(request: Request, { params }: Props) {
  return respond(async () => {
    const { orgId } = await params;
    const { session } = await authorize(request, orgId, "idp-admin-app:read");
    return readWizard(session, orgId);
  });
}
export async function POST(request: Request, { params }: Props) {
  return respond(async () => reconcileWizard(request, (await params).orgId));
}
