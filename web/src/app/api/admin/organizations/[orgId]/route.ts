import { organizationAction } from "../../../../../features/organizations/api";
import { respond } from "../../../../../features/users/api";
export const dynamic="force-dynamic";
type Context={params:Promise<{orgId:string}>};
export async function GET(r:Request,c:Context){const {orgId}=await c.params;return respond(()=>organizationAction(r,orgId));}
export async function PUT(r:Request,c:Context){const {orgId}=await c.params;return respond(()=>organizationAction(r,orgId));}
