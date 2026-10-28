import { PortalShell } from "../../../../../../components/PortalShell";
import { PageFailure } from "../../../../../../components/PageFailure";
import { loadFeature } from "../../../../../../features/clients/page";
import { ClientWizard } from "../../../../../../features/clients/ClientWizard";
import { canShow } from "../../../../../../features/account/permissions";
import { PortalFailure } from "../../../../../../server/errors";
export const dynamic = "force-dynamic";
export default async function Page({
  params,
}: {
  params: Promise<{ orgId: string }>;
}) {
  const { orgId } = await params;
  const result = await loadFeature(
    "/admin/organizations/" + orgId + "/clients/new",
    orgId,
    "idp-admin-app:create",
    async (_s, org, c) => {
      if (!canShow(c, "idp-admin-app:read", org))
        throw new PortalFailure(
          403,
          "ACCESS_DENIED",
          "Onboarding requires client read access for reconciliation.",
        );
      return null;
    },
  );
  if (!result.ok) return <PageFailure failure={result} />;
  return (
    <PortalShell context={result.context}>
      <ClientWizard orgId={orgId} />
    </PortalShell>
  );
}
