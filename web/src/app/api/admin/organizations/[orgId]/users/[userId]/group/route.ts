import { roleAssignmentAction, respond } from "../../../../../../../../features/roles/api";
export const dynamic="force-dynamic";
type Context={params:Promise<{orgId:string;userId:string}>};
export async function PUT(r:Request,c:Context){const {orgId,userId}=await c.params;return respond(()=>roleAssignmentAction(r,orgId,userId));}
