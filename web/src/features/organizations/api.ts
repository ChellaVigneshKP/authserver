import "server-only";
import { z } from "zod";
import { access, input } from "../users/api";
import { requireSession } from "../../server/auth";
import { getPortalContext } from "../../server/portal-context";
import { javaRequest } from "../../server/java-api";
import { PortalFailure } from "../../server/errors";
import { canShow } from "../account/permissions";
import { Organization,OrganizationCreate,OrganizationUpdate,ContactInput } from "../../contracts/users";
import type { PortalSession } from "../../server/types";
export async function organizationDetail(session:PortalSession,organizationId:string){return javaRequest(session,{kind:"organizationGet",organizationId},undefined,Organization);}
export async function organizationListAction(request:Request){
 const session=await requireSession(request),context=await getPortalContext(session);
 const org=context.subject.organizationId;
 const permission=request.method==="POST"?"idp-admin-org:create":"idp-admin-org:list";
 await access(request,org,[permission],request.method==="POST");
 return request.method==="POST"?javaRequest(session,{kind:"organizationCreate"},await input(request,OrganizationCreate),z.object({id:z.uuid()})):javaRequest(session,{kind:"organizationList"},undefined,z.array(Organization));
}
export async function organizationAction(request:Request,orgId:string,contact?:"primaryContact"|"secondaryContact"){
 const mutation=request.method!=="GET";
 const {session,context}=await access(request,orgId,[mutation?"idp-admin-org:update":"idp-admin-org:read"],mutation);
 const current=await organizationDetail(session,orgId);
 if(!mutation)return current;
 if(contact)return javaRequest(session,{kind:contact,organizationId:orgId},await input(request,ContactInput),Organization);
 const value=await input(request,OrganizationUpdate);
 const status=current.status==="ACTIVE"?"Active":"Revoked";
 if(value.status!==status && !canShow(context,"idp-admin-org:deactivate",orgId))throw new PortalFailure(403,"ACCESS_DENIED","Organization status requires platform authority.");
 return javaRequest(session,{kind:"organizationUpdate",organizationId:orgId},value,Organization);
}
