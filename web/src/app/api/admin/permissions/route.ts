import { roleCatalogAction, respond } from "../../../../features/roles/api";
export const dynamic="force-dynamic";
export async function GET(r:Request){return respond(()=>roleCatalogAction(r));}
