import { PortalShell } from "../../../../../components/PortalShell";
import { PageFailure } from "../../../../../components/PageFailure";
import { loadFeature } from "../../../../../features/clients/page";
import { loadPageContext } from "../../../../../server/page-context";
import { UserList } from "../../../../../features/users/UserList";
import { listUsers } from "../../../../../features/users/api";
import { UserPageQuery } from "../../../../../contracts/users";
export const dynamic="force-dynamic";
export default async function Page({params,searchParams}:{params:Promise<{orgId:string}>;searchParams:Promise<Record<string,string>>}){const {orgId}=await params;const path="/admin/organizations/"+orgId+"/users";const context=await loadPageContext(path);if(!context.ok)return <PageFailure failure={context}/>;const parsed=UserPageQuery.safeParse(await searchParams);const query=parsed.success?parsed.data:UserPageQuery.parse({});const permission=context.context.permissions.includes("idp-admin-user:read")?"idp-admin-user:read":"idp-admin-admin:read";const result=await loadFeature(path,orgId,permission,s=>listUsers(s,orgId,query));if(!result.ok)return <PageFailure failure={result}/>;return <PortalShell context={result.context}><UserList initial={result.data} initialQuery={query} orgId={orgId} context={result.context}/></PortalShell>;}
