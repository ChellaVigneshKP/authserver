import { PortalShell } from "../../../../components/PortalShell";
import { PageFailure } from "../../../../components/PageFailure";
import { loadFeature } from "../../../../features/clients/page";
import { OrganizationForm } from "../../../../features/organizations/OrganizationForm";
import { organizationDetail } from "../../../../features/organizations/api";
export const dynamic="force-dynamic";
export default async function Page({params}:{params:Promise<{orgId:string}>}){const {orgId}=await params;const result=await loadFeature("/admin/organizations/"+orgId,orgId,"idp-admin-org:read",s=>organizationDetail(s,orgId));if(!result.ok)return <PageFailure failure={result}/>;return <PortalShell context={result.context}><OrganizationForm initial={result.data} context={result.context}/></PortalShell>;}
