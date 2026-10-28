import { z } from "zod";
import { PortalShell } from "../../../components/PortalShell";
import { PageFailure } from "../../../components/PageFailure";
import { loadFeature } from "../../../features/clients/page";
import { ResourceCatalog } from "../../../features/resources/ResourceCatalog";
import { canShow } from "../../../features/account/permissions";
import { javaRequest } from "../../../server/java-api";
import { Resource } from "../../../contracts/resources";
export const dynamic = "force-dynamic";
export default async function Page() {
  const result = await loadFeature(
    "/admin/resources",
    undefined,
    "idp-admin-resource:read",
    (s) =>
      javaRequest(
        s,
        { kind: "resourceList" },
        undefined,
        z.array(Resource).max(10000),
      ),
  );
  if (!result.ok) return <PageFailure failure={result} />;
  const org = result.context.subject.organizationId;
  return (
    <PortalShell context={result.context}>
      <ResourceCatalog
        initial={result.data}
        canCreate={canShow(result.context, "idp-admin-resource:create", org)}
        canUpdate={canShow(result.context, "idp-admin-resource:update", org)}
      />
    </PortalShell>
  );
}
