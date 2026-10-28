import { PortalShell } from "../../../../../../components/PortalShell";
import { PageFailure } from "../../../../../../components/PageFailure";
import { loadCertificateFeature } from "../../../../../../features/certificates/page";
import { CertificateDetails } from "../../../../../../features/certificates/CertificateDetails";
import { certificateDetail } from "../../../../../../features/certificates/api";
export const dynamic = "force-dynamic";
export default async function Page({
  params,
}: {
  params: Promise<{ orgId: string; certId: string }>;
}) {
  const { orgId, certId } = await params;
  const path = "/admin/organizations/" + orgId + "/certificates/" + certId;
  const result = await loadCertificateFeature(path, orgId, (session, org) =>
    certificateDetail(session, org, certId),
  );
  if (!result.ok) return <PageFailure failure={result} />;
  return (
    <PortalShell context={result.context}>
      <CertificateDetails
        initial={result.data}
        orgId={orgId}
        context={result.context}
      />
    </PortalShell>
  );
}
