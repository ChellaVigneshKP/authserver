import { z } from "zod";
import { PortalShell } from "../../../../../../components/PortalShell";
import { PageFailure } from "../../../../../../components/PageFailure";
import { loadFeature } from "../../../../../../features/clients/page";
import { clientBundle } from "../../../../../../features/clients/api";
import { ClientDetails } from "../../../../../../features/clients/ClientDetails";
import { canShow } from "../../../../../../features/account/permissions";
import { javaRequest } from "../../../../../../server/java-api";
import { Resource } from "../../../../../../contracts/resources";
export const dynamic = "force-dynamic";
export default async function Page({
  params,
}: {
  params: Promise<{ orgId: string; appId: string }>;
}) {
  const { orgId, appId } = await params;
  const result = await loadFeature(
    "/admin/organizations/" + orgId + "/clients/" + appId,
    orgId,
    "idp-admin-app:read",
    async (s, org, c) => ({
      initial: await clientBundle(s, org, appId),
      catalog: canShow(c, "idp-admin-resource:read", org)
        ? await javaRequest(
            s,
            { kind: "resourceList" },
            undefined,
            z.array(Resource).max(10000),
          )
        : [],
    }),
  );
  if (!result.ok) return <PageFailure failure={result} />;
  return (
    <PortalShell context={result.context}>
      <ClientDetails {...result.data} context={result.context} />
    </PortalShell>
  );
}
