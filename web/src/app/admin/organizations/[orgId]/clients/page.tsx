import { PortalShell } from "../../../../../components/PortalShell";
import { PageFailure } from "../../../../../components/PageFailure";
import { loadFeature } from "../../../../../features/clients/page";
import { listClients } from "../../../../../features/clients/api";
import { ClientList } from "../../../../../features/clients/ClientList";
import { canShow } from "../../../../../features/account/permissions";
export const dynamic = "force-dynamic";
export default async function Page({
  params,
}: {
  params: Promise<{ orgId: string }>;
}) {
  const { orgId } = await params;
  const result = await loadFeature(
    "/admin/organizations/" + orgId + "/clients",
    orgId,
    "idp-admin-app:read",
    (s, org) => listClients(s, org),
  );
  if (!result.ok) return <PageFailure failure={result} />;
  return (
    <PortalShell context={result.context}>
      <ClientList
        orgId={orgId}
        clients={result.data}
        canCreate={canShow(result.context, "idp-admin-app:create", orgId)}
      />
    </PortalShell>
  );
}
