import "server-only";
import { z, type ZodType } from "zod";
import { requireSession, boundedBody } from "../../server/auth";
import { requireMutation } from "../../server/csrf";
import { getPortalContext } from "../../server/portal-context";
import { javaRequest, type JavaOperation } from "../../server/java-api";
import { PortalFailure } from "../../server/errors";
import { canShow } from "../account/permissions";
import * as C from "../../contracts/users";
import type { PortalSession } from "../../server/types";
export { respond } from "../clients/api";
export async function input<T>(request:Request,schema:ZodType<T>) {
  try { const bytes=await boundedBody(new Response(request.body),70000); return schema.parse(JSON.parse(Buffer.from(bytes).toString("utf8"))); }
  catch { throw new PortalFailure(400,"INVALID_INPUT","Check the supplied fields and metadata size."); }
}
export async function access(request:Request,orgId:string,permissions:string[],mutation=false){
  const session=await requireSession(request); if(mutation)await requireMutation(request,session);
  const context=await getPortalContext(session);const organizationId=z.uuid().parse(orgId);
  if(!permissions.some(p=>canShow(context,p,organizationId)))throw new PortalFailure(403,"ACCESS_DENIED","Your current permissions do not allow this action.");
  return {session,context,organizationId};
}
export async function listUsers(session:PortalSession,organizationId:string,query:unknown){
  const page=await javaRequest(session,{kind:"userList",organizationId,query:C.UserPageQuery.parse(query)},undefined,C.UserPageWire);
  if(page.users.some(u=>u.organizationId!==organizationId))throw new PortalFailure(502,"UPSTREAM_INVALID","The current user list could not be verified.");
  return {items:page.users,total:page.results,offset:page.offset,limit:page.resultsPerPage};
}
export async function userDetail(session:PortalSession,organizationId:string,userId:string,loginId?:string){
  const user=await javaRequest(session,{kind:"userGet",userId:z.uuid().parse(userId),...(loginId?{loginId:z.uuid().parse(loginId)}:{})},undefined,C.UserSummary);
  if(user.organizationId!==organizationId)throw new PortalFailure(403,"ACCESS_DENIED","This account is outside the selected organization.");
  return user;
}
export async function userListAction(request:Request,orgId:string){
  const mutation=request.method==="POST";
  const {session}=await access(request,orgId,mutation?["idp-admin-user:create"]:["idp-admin-user:read","idp-admin-admin:read"],mutation);
  if(!mutation){const query=Object.fromEntries(new URL(request.url).searchParams);return listUsers(session,orgId,query);}
  const value=await input(request,C.UserCreate);
  return javaRequest(session,{kind:"userCreate"},{...value,password:Buffer.from(value.password,"utf8").toString("base64"),orgGuid:orgId,branding:"idp",metaData:{},syncFlag:false},z.object({id:z.uuid()}));
}
export type UserAction="profile"|"email"|"username"|"password"|"security-settings"|"status"|"metadata"|"delete";
export async function userAction(request:Request,orgId:string,userId:string,action?:UserAction){
  const mutation=request.method!=="GET";
  const {session,context}=await access(request,orgId,mutation?["idp-admin-user:update","idp-admin-admin:update","idp-admin-user:delete","idp-admin-admin:delete"]:["idp-admin-user:read","idp-admin-admin:read"],mutation);
  const loginId=new URL(request.url).searchParams.get("loginId")??undefined;
  const current=await userDetail(session,orgId,userId,loginId);
  const authority="idp-admin-"+(current.groupId?"admin":"user")+":"+(mutation?(action==="delete"?"delete":"update"):"read");
  if(!canShow(context,authority,orgId))throw new PortalFailure(403,"ACCESS_DENIED","Your current permissions do not allow this account action.");
  if(!mutation)return action==="metadata"?javaRequest(session,{kind:"userMetadataGet",userId},undefined,C.Metadata):current;
  if(action==="delete"){await javaRequest(session,{kind:"userDelete",userId},undefined,z.unknown());return {updated:true};}
  const definitions={profile:[C.UserProfile,"userProfile"],email:[C.UserEmail,"userEmail"],username:[C.UserUsername,"userUsername"],password:[C.UserPassword,"userPassword"],"security-settings":[C.UserSecurity,"userSecurity"],status:[C.UserStatus,"userStatus"],metadata:[C.MetadataInput,"userMetadataPut"]} as const;
  if(!action || !(action in definitions))throw new PortalFailure(400,"INVALID_OPERATION","This action is unavailable.");
  const [schema,kind]=definitions[action];
  const value=await input(request,schema as ZodType<Record<string,unknown>>);
  const mapped=action==="password"?{password:Buffer.from(String(value.password),"utf8").toString("base64")}:value;
  const payload=["profile","email","username","password"].includes(action)?{...mapped,branding:current.branding,intent:"idp",metadata:{}}:mapped;
  await javaRequest(session,{kind,userId,loginId:current.loginId} as JavaOperation,payload,z.unknown());
  return {updated:true};
}
