import { loadPageContext } from "../../server/page-context";
import { canAdminister } from "../../features/account/permissions";
import { PageFailure } from "../../components/PageFailure";
import { PortalShell } from "../../components/PortalShell";
import { ErrorPanel } from "../../components/ErrorPanel";
export const dynamic = "force-dynamic";
export default async function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const result = await loadPageContext("/admin");
  if (!result.ok) return <PageFailure failure={result} />;
  if (!canAdminister(result.context))
    return (
      <PortalShell context={result.context}>
        <ErrorPanel
          title="Access denied"
          message="Your current permissions do not allow administration. Contact your administrator if you need access."
        />
      </PortalShell>
    );
  return children;
}
