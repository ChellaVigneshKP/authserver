import { userAction } from "../../../../../../../features/users/api";
import { respond } from "../../../../../../../features/users/api";
export const dynamic="force-dynamic";
type Context={params:Promise<{orgId:string;userId:string}>};
export async function GET(r:Request,c:Context){const {orgId,userId}=await c.params;return respond(()=>userAction(r,orgId,userId));}
export async function DELETE(r:Request,c:Context){const {orgId,userId}=await c.params;return respond(()=>userAction(r,orgId,userId,"delete"));}
