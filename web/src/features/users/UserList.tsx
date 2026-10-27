"use client";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Alert,Box,Button,Link,MenuItem,Stack,Typography } from "@mui/material";
import type { PortalContext } from "../../contracts/portal";
import { UserPageQuery,type UserQuery,type UserDto } from "../../contracts/users";
import { canShow } from "../account/permissions";
import { DataTable } from "../../components/DataTable";
import { FormField,validationErrors } from "../clients/FormField";
import { requestJson,actionMessage } from "../clients/browser-api";
import { CreateUserForm } from "./CreateUserForm";
export type UserPage={items:UserDto[];total:number;offset:number;limit:number};
export function UserList({initial,orgId,context,initialQuery}:{initial:UserPage;orgId:string;context:PortalContext;initialQuery:UserQuery}){
 const [page,setPage]=useState(initial),[query,setQuery]=useState(initialQuery),[pending,setPending]=useState(false),[error,setError]=useState("");
 const form=useForm({defaultValues:initialQuery});
 async function load(next=query){setPending(true);try{const params=new URLSearchParams(Object.entries(next).map(([k,v])=>[k,String(v)]));setPage(await requestJson<UserPage>("/api/admin/organizations/"+orgId+"/users?"+params));setQuery(next);setError("");}catch(e){setError(actionMessage(e));throw e;}finally{setPending(false);}}
 return <Stack spacing={3}><Typography component="h1" variant="h1">Users</Typography>
 {error&&<Alert severity="error">{error}</Alert>}
 <Box component="form" noValidate onSubmit={form.handleSubmit(async v=>{const parsed=UserPageQuery.safeParse({...v,offset:0});if(!parsed.success){validationErrors(form,parsed.error);return;}try{await load(parsed.data);}catch{}})}>
 <Stack direction={{xs:"column",md:"row"}} spacing={2}>
 <FormField control={form.control} name="search" label="Search users" helperText="Up to 50 characters"/>
 <FormField control={form.control} name="type" label="Account class" select><MenuItem value="">All permitted accounts</MenuItem>{canShow(context,"idp-admin-user:read",orgId)&&<MenuItem value="User">Users</MenuItem>}{canShow(context,"idp-admin-admin:read",orgId)&&<MenuItem value="Administrator">Administrators</MenuItem>}</FormField>
 <FormField control={form.control} name="status" label="Status" select><MenuItem value="">All statuses</MenuItem><MenuItem value="Active">Active</MenuItem><MenuItem value="Inactive">Inactive</MenuItem></FormField>
 <FormField control={form.control} name="limit" label="Page size" select>{[10,25,50,100].map(n=><MenuItem value={n} key={n}>{n}</MenuItem>)}</FormField>
 <Button type="submit" variant="contained" disabled={pending}>Search</Button></Stack></Box>
 {page.items.length?<DataTable caption="Users in the selected organization" rows={page.items} rowKey={u=>u.loginId} columns={[{key:"name",label:"Name",render:u=><Link href={"/admin/organizations/"+orgId+"/users/"+u.id+"?loginId="+u.loginId}>{u.firstName} {u.lastName}</Link>},{key:"username",label:"Username",render:u=>u.userName},{key:"email",label:"Email",render:u=>u.email},{key:"role",label:"Role",render:u=>u.groupName??"Unassigned"},{key:"status",label:"Status",render:u=>u.status},{key:"sync",label:"Synchronization",render:u=>u.syncMode}]}/>:<Typography>No accounts match the current filters.</Typography>}
 <Stack direction="row" spacing={2} sx={{alignItems:"center",flexWrap:"wrap"}}><Button disabled={pending||page.offset===0} onClick={()=>void load({...query,offset:Math.max(0,page.offset-page.limit)}).catch(()=>{})}>Previous page</Button><Typography>{page.total} accounts · {page.total?Math.min(page.offset+1,page.total):0}–{Math.min(page.offset+page.items.length,page.total)}</Typography><Button disabled={pending||page.offset+page.limit>=page.total} onClick={()=>void load({...query,offset:page.offset+page.limit}).catch(()=>{})}>Next page</Button><Button disabled={pending} onClick={()=>void load().catch(()=>{})}>Reload users</Button></Stack>
 {canShow(context,"idp-admin-user:create",orgId)&&<CreateUserForm orgId={orgId} readList={()=>load({...query,offset:0,search:"",status:"",type:""})}/>}
 </Stack>;
}
