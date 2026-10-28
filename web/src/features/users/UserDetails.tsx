"use client";
import { useState } from "react";
import { Alert,Button,Chip,Link,Stack,Typography } from "@mui/material";
import { UserEmail,UserUsername,UserPassword,type UserDto } from "../../contracts/users";
import type { PortalContext } from "../../contracts/portal";
import { canShow } from "../account/permissions";
import { requestJson,actionMessage } from "../clients/browser-api";
import { ConfirmAction } from "../../components/ConfirmAction";
import { FormSection } from "./FormSection";
import { UserProfileForm } from "./UserProfileForm";
import { UserSecurityForm } from "./UserSecurityForm";
import { UserMetadataForm } from "./UserMetadataForm";
import { RoleAssignment } from "../roles/RoleAssignment";
export function UserDetails({initial,initialMetadata,context}:{initial:UserDto;initialMetadata:Record<string,unknown>;context:PortalContext}){
 const [user,setUser]=useState(initial),[metadata,setMetadata]=useState(initialMetadata),[error,setError]=useState(""),[revision,setRevision]=useState(0);
 const [pending,setPending]=useState(false),[uncertain,setUncertain]=useState(false);
 const api="/api/admin/organizations/"+user.organizationId+"/users/"+user.id;
 const endpoint=(suffix="")=>api+suffix+"?loginId="+user.loginId;
 const prefix=user.groupId?"idp-admin-admin:":"idp-admin-user:";
 const editable=canShow(context,prefix+"update",user.organizationId),deletable=canShow(context,prefix+"delete",user.organizationId);
 async function read(){const fresh=await requestJson<UserDto>(endpoint());setUser(fresh);return fresh;}
 async function readMetadata(){const fresh=await requestJson<Record<string,unknown>>(endpoint("/metadata"));setMetadata(fresh);return fresh;}
 async function save(section:string,value:unknown){await requestJson(endpoint("/"+section),"PUT",value);return read();}
 async function lifecycle(remove:boolean){if(pending||uncertain)return;setPending(true);try{if(remove)await requestJson(endpoint(),"DELETE");else await requestJson(endpoint("/status"),"PUT",{status:user.status==="Active"?"Inactive":"Active"});window.location.assign("/admin/organizations/"+user.organizationId+"/users");}catch(e){setError(actionMessage(e));setUncertain(true);}finally{setPending(false);}}
 return <Stack spacing={2}>
  <Link href={"/admin/organizations/"+user.organizationId+"/users"}>Users</Link>
  <Typography component="h1" variant="h1">{user.firstName} {user.lastName}</Typography>
  <Stack direction="row" spacing={1} useFlexGap sx={{flexWrap:"wrap"}}><Chip label={user.status}/><Chip label={user.syncMode==="local"?"Local account":user.syncMode+" synchronization"}/><Chip label={user.groupName??"No role assigned"}/></Stack>
  <Typography color="text.secondary">Selected login: {user.userName} ({user.branding})</Typography>
  <Typography>Profile, email, MFA policy, metadata and lifecycle changes apply to all logins sharing this profile. Username and password changes apply to the selected login.</Typography>
  {error&&<Alert severity="error">{error}</Alert>}
  <Button onClick={async()=>{try{await read();await readMetadata();setRevision(v=>v+1);setUncertain(false);setError("");}catch(e){setError(actionMessage(e));}}}>Reload account and discard drafts</Button>
  <UserProfileForm key={"profile"+revision} user={user} disabled={!editable} save={v=>save("profile",v)} read={read}/>
  <FormSection key={"email"+revision} title="Email" values={{email:user.email}} schema={UserEmail} fields={[{name:"email",label:"Email address",type:"email"}]} disabled={!editable} save={async v=>({email:(await save("email",v)).email})} read={async()=>({email:(await read()).email})}/>
  <FormSection key={"username"+revision} title="Username" values={{username:user.userName}} schema={UserUsername} fields={[{name:"username",label:"Username"}]} disabled={!editable} save={async v=>({username:(await save("username",v)).userName})} read={async()=>({username:(await read()).userName})}/>
  <Alert severity="info">Changing the username or password ends this login's existing sessions. No invitation or delivery is implied.</Alert>
  <FormSection title="Password" values={{password:""}} schema={UserPassword} fields={[{name:"password",label:"New password",type:"password"}]} sensitive disabled={!editable} save={async v=>{await requestJson(endpoint("/password"),"PUT",v);return {password:""};}} read={async()=>{await read();return {password:""};}}/>
  <UserSecurityForm key={"security"+revision} user={user} disabled={!editable} save={v=>save("security-settings",v)} read={read}/>
  <UserMetadataForm key={"metadata"+revision} metadata={metadata} disabled={!editable} save={async v=>{await requestJson(endpoint("/metadata"),"PUT",v);return readMetadata();}} read={readMetadata}/>
  {canShow(context,"idp-admin-group:assign",user.organizationId)&&<RoleAssignment orgId={user.organizationId} userId={user.id} groupId={user.groupId} groupName={user.groupName??null} onChange={async()=>{await read();}}/>}
  <Typography component="h2" variant="h2">Account lifecycle</Typography>
  <Typography>Deactivation preserves account records. It is not permanent erasure.</Typography>
  {uncertain&&<Alert severity="warning">Read current account state before another lifecycle action.</Alert>}
  <Stack direction="row" spacing={2} useFlexGap sx={{flexWrap:"wrap"}}>
   {!pending&&!uncertain&&deletable&&<ConfirmAction label={user.status==="Active"?"Deactivate account":"Activate account"} title="Change account status?" description="Records are retained. Current access and final administrator protections are checked again." confirmLabel="Confirm change" onConfirm={()=>void lifecycle(false)}/>}
   {!pending&&!uncertain&&deletable&&user.status==="Active"&&<ConfirmAction label="Delete account access" title="Delete account access?" description="This deactivates account access and retains records. It is not permanent erasure." confirmLabel="Delete access" onConfirm={()=>void lifecycle(true)}/>}
  </Stack>
 </Stack>;
}
