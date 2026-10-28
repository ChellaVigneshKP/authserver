import { loadPageContext } from "../../server/page-context";
import { PageFailure } from "../../components/PageFailure";
import { PortalShell } from "../../components/PortalShell";
import { AccountSummary } from "../../features/account/AccountSummary";
import { ReturnNotice } from "../../features/account/ReturnNotice";
export const dynamic = "force-dynamic";
export default async function Account({
  searchParams,
}: {
  searchParams: Promise<{ notice?: string }>;
}) {
  const result = await loadPageContext("/account");
  if (!result.ok) return <PageFailure failure={result} />;
  return (
    <PortalShell context={result.context}>
      <ReturnNotice notice={(await searchParams).notice} />
      <AccountSummary context={result.context} />
    </PortalShell>
  );
}
