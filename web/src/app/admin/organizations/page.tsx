import { PortalShell } from "../../../components/PortalShell";
import { PageFailure } from "../../../components/PageFailure";
import { loadFeature } from "../../../features/clients/page";
import { OrganizationList } from "../../../features/organizations/OrganizationList";
import { javaRequest } from "../../../server/java-api";
import { Organization } from "../../../contracts/users";
import { canShow } from "../../../features/account/permissions";
export const dynamic="force-dynamic";
import { z } from "zod";
export default async function Page(){const result=await loadFeature("/admin/organizations",undefined,"idp-admin-org:list",s=>javaRequest(s,{kind:"organizationList"},undefined,z.array(Organization)));if(!result.ok)return <PageFailure failure={result}/>;return <PortalShell context={result.context}><OrganizationList organizations={result.data} canCreate={canShow(result.context,"idp-admin-org:create",result.context.subject.organizationId)}/></PortalShell>;}
