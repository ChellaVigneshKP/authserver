import { organizationListAction } from "../../../../features/organizations/api";
import { respond } from "../../../../features/users/api";
export const dynamic="force-dynamic";
export async function GET(request:Request){return respond(()=>organizationListAction(request));}
export async function POST(request:Request){return respond(()=>organizationListAction(request),201);}
