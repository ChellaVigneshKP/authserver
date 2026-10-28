import { roleAction, respond } from "../../../../../../../features/roles/api";
export const dynamic="force-dynamic";
type Context={params:Promise<{orgId:string;groupId:string}>};
export async function GET(r:Request,c:Context){const {orgId,groupId}=await c.params;return respond(()=>roleAction(r,orgId,groupId));}
export async function PUT(r:Request,c:Context){const {orgId,groupId}=await c.params;return respond(()=>roleAction(r,orgId,groupId));}
export async function DELETE(r:Request,c:Context){const {orgId,groupId}=await c.params;return respond(()=>roleAction(r,orgId,groupId));}
