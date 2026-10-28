import { PortalShell } from "../../../../../../components/PortalShell";
import { PageFailure } from "../../../../../../components/PageFailure";
import { loadFeature } from "../../../../../../features/clients/page";
import { loadPageContext } from "../../../../../../server/page-context";
import { UserDetails } from "../../../../../../features/users/UserDetails";
import { userDetail } from "../../../../../../features/users/api";
import { javaRequest } from "../../../../../../server/java-api";
import { Metadata } from "../../../../../../contracts/users";
export const dynamic="force-dynamic";
export default async function Page({params,searchParams}:{params:Promise<{orgId:string;userId:string}>;searchParams:Promise<{loginId?:string}>}){const {orgId,userId}=await params;const {loginId}=await searchParams;const path="/admin/organizations/"+orgId+"/users/"+userId;const context=await loadPageContext(path);if(!context.ok)return <PageFailure failure={context}/>;const permission=context.context.permissions.includes("idp-admin-user:read")?"idp-admin-user:read":"idp-admin-admin:read";const result=await loadFeature(path,orgId,permission,async s=>({initial:await userDetail(s,orgId,userId,loginId),initialMetadata:await javaRequest(s,{kind:"userMetadataGet",userId},undefined,Metadata)}));if(!result.ok)return <PageFailure failure={result}/>;return <PortalShell context={result.context}><UserDetails {...result.data} context={result.context}/></PortalShell>;}
