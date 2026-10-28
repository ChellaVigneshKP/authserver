import { PortalShell } from "../../../../../components/PortalShell";
import { PageFailure } from "../../../../../components/PageFailure";
import { loadCertificateFeature } from "../../../../../features/certificates/page";
import { CertificateList } from "../../../../../features/certificates/CertificateList";
import { listCertificates } from "../../../../../features/certificates/api";
export const dynamic = "force-dynamic";
export default async function Page({
  params,
}: {
  params: Promise<{ orgId: string }>;
}) {
  const { orgId } = await params;
  const path = "/admin/organizations/" + orgId + "/certificates";
  const result = await loadCertificateFeature(path, orgId, (session, org) =>
    listCertificates(session, org),
  );
  if (!result.ok) return <PageFailure failure={result} />;
  return (
    <PortalShell context={result.context}>
      <CertificateList
        initial={result.data}
        orgId={orgId}
        context={result.context}
      />
    </PortalShell>
  );
}
