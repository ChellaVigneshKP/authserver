"use client";
import { useState } from "react";
import { Alert,Button,Chip,Stack,Typography } from "@mui/material";
import { OrganizationCreate,type OrganizationDto } from "../../contracts/users";
import type { PortalContext } from "../../contracts/portal";
import { FormSection } from "../users/FormSection";
import { ContactForm } from "./ContactForm";
import { canShow } from "../account/permissions";
import { ConfirmAction } from "../../components/ConfirmAction";
import { requestJson,actionMessage } from "../clients/browser-api";
export function OrganizationForm({initial,context}:{initial:OrganizationDto;context:PortalContext}){
 const [org,setOrg]=useState(initial),[error,setError]=useState(""),[pending,setPending]=useState(false),[uncertain,setUncertain]=useState(false),[revision,setRevision]=useState(0);
 const api="/api/admin/organizations/"+org.id,editable=canShow(context,"idp-admin-org:update",org.id);
 const pick=(o:OrganizationDto)=>({name:o.name,description:o.description??""});
 async function read(){const fresh=await requestJson<OrganizationDto>(api);setOrg(fresh);return fresh;}
 async function write(path:string,value:unknown){const fresh=await requestJson<OrganizationDto>(api+path,"PUT",value);setOrg(fresh);return fresh;}
 return <Stack spacing={2}><Typography component="h1" variant="h1">{org.name}</Typography><Chip label={org.status}/>{error&&<Alert severity="error">{error}</Alert>}
 <Button onClick={async()=>{try{await read();setRevision(v=>v+1);setUncertain(false);setError("");}catch(e){setError(actionMessage(e));}}}>Reload organization and discard drafts</Button>
 <FormSection key={"details"+revision} title="Organization details" values={pick(org)} schema={OrganizationCreate} fields={[{name:"name",label:"Organization name"},{name:"description",label:"Description",multiline:true}]} disabled={!editable} save={async v=>pick(await write("",{...v,status:org.status==="ACTIVE"?"Active":"Revoked"}))} read={async()=>pick(await read())}/>
 {([['primaryContact','primary-contact','Primary contact'],['secondaryContact','secondary-contact','Secondary contact']] as const).map(([key,path,title])=><ContactForm key={key+revision} title={title} contact={org[key]} disabled={!editable} save={async v=>(await write('/'+path,v))[key]} read={async()=>(await read())[key]}/>)}
 {uncertain&&<Alert severity="warning">Read current organization state before another status change.</Alert>}
 {editable&&canShow(context,"idp-admin-org:deactivate",org.id)&&!pending&&!uncertain&&<ConfirmAction label={org.status==="ACTIVE"?"Revoke organization":"Activate organization"} title="Change organization status?" description="The platform organization and final administrator protections are enforced. Records are retained." confirmLabel="Change status" onConfirm={()=>{if(pending)return;setPending(true);void write("",{...pick(org),status:org.status==="ACTIVE"?"Revoked":"Active"}).catch(e=>{setError(actionMessage(e));setUncertain(true);}).finally(()=>setPending(false));}}/>}
 </Stack>;
}
