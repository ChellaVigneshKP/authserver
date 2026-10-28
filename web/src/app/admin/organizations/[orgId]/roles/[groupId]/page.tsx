import { PortalShell } from "../../../../../../components/PortalShell";
import { PageFailure } from "../../../../../../components/PageFailure";
import { loadFeature } from "../../../../../../features/clients/page";
import { loadPageContext } from "../../../../../../server/page-context";
import { RoleEditor } from "../../../../../../features/roles/RoleEditor";
import { permissionCatalog, roleDetail, rolePermissions } from "../../../../../../features/roles/api";
import { canShow } from "../../../../../../features/account/permissions";
export const dynamic="force-dynamic";
export default async function Page({params}:{params:Promise<{orgId:string;groupId:string}>}){
 const {orgId,groupId}=await params;
 const path="/admin/organizations/"+orgId+"/roles/"+groupId;
 const context=await loadPageContext(path);
 if(!context.ok)return <PageFailure failure={context}/>;
 const result=await loadFeature(path,orgId,"idp-admin-group:read",async (s,org,ctx)=>({
  initial:await roleDetail(s,org,groupId),
  grants:await rolePermissions(s,org,groupId),
  catalog:canShow(ctx,"idp-admin-permission:read",org)?await permissionCatalog(s):[],
 }));
 if(!result.ok)return <PageFailure failure={result}/>;
 return <PortalShell context={result.context}><RoleEditor {...result.data} orgId={orgId} context={result.context}/></PortalShell>;
}
