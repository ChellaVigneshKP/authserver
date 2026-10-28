import { userListAction } from "../../../../../../features/users/api";
import { respond } from "../../../../../../features/users/api";
export const dynamic="force-dynamic";
type Context={params:Promise<{orgId:string}>};
export async function GET(r:Request,c:Context){const {orgId}=await c.params;return respond(()=>userListAction(r,orgId));}
export async function POST(r:Request,c:Context){const {orgId}=await c.params;return respond(()=>userListAction(r,orgId),201);}
