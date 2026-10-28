import { PortalShell } from "../../../../../components/PortalShell";
import { PageFailure } from "../../../../../components/PageFailure";
import { loadFeature } from "../../../../../features/clients/page";
import { loadPageContext } from "../../../../../server/page-context";
import { RoleList } from "../../../../../features/roles/RoleList";
import { listRoles, permissionCatalog } from "../../../../../features/roles/api";
import { canShow } from "../../../../../features/account/permissions";
export const dynamic="force-dynamic";
export default async function Page({params}:{params:Promise<{orgId:string}>}){
 const {orgId}=await params;
 const path="/admin/organizations/"+orgId+"/roles";
 const context=await loadPageContext(path);
 if(!context.ok)return <PageFailure failure={context}/>;
 const result=await loadFeature(path,orgId,"idp-admin-group:read",async (s,org,ctx)=>({
  items:await listRoles(s,org),
  catalog:canShow(ctx,"idp-admin-permission:read",org)?await permissionCatalog(s):[],
 }));
 if(!result.ok)return <PageFailure failure={result}/>;
 return <PortalShell context={result.context}><RoleList initial={result.data.items} catalog={result.data.catalog} orgId={orgId} context={result.context}/></PortalShell>;
}
