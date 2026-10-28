import { eligibleRolesAction, respond } from "../../../../../../../features/roles/api";
export const dynamic="force-dynamic";
type Context={params:Promise<{orgId:string}>};
export async function GET(r:Request,c:Context){const {orgId}=await c.params;return respond(()=>eligibleRolesAction(r,orgId));}
