"use client";
import { Link,Stack,Typography } from "@mui/material";
import { OrganizationCreate,type OrganizationDto } from "../../contracts/users";
import { DataTable } from "../../components/DataTable";
import { FormSection } from "../users/FormSection";
import { requestJson } from "../clients/browser-api";
export function OrganizationList({organizations,canCreate}:{organizations:OrganizationDto[];canCreate:boolean}){return <Stack spacing={3}><Typography component="h1" variant="h1">Organizations</Typography><DataTable caption="Organizations" rows={organizations} rowKey={o=>o.id} columns={[{key:"name",label:"Organization",render:o=><Link href={"/admin/organizations/"+o.id}>{o.name}</Link>},{key:"status",label:"Status",render:o=>o.status},{key:"description",label:"Description",render:o=>o.description}]}/>{canCreate&&<FormSection title="New organization" values={{name:"",description:""}} schema={OrganizationCreate} fields={[{name:"name",label:"Organization name"},{name:"description",label:"Description",multiline:true}]} save={async v=>{const created=await requestJson<{id:string}>("/api/admin/organizations","POST",v);window.location.assign("/admin/organizations/"+created.id);return {name:"",description:""};}} read={async()=>{await requestJson("/api/admin/organizations");window.location.reload();return {name:"",description:""};}}/>}</Stack>;}
