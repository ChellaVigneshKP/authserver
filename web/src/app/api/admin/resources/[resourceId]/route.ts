import { respond, resourceAction } from "../../../../../features/clients/api";
export const dynamic = "force-dynamic";
type Props = { params: Promise<{ resourceId: string }> };
export async function GET(request: Request, { params }: Props) {
  return respond(async () =>
    resourceAction(request, (await params).resourceId),
  );
}
export async function PUT(request: Request, { params }: Props) {
  return respond(async () =>
    resourceAction(request, (await params).resourceId),
  );
}
