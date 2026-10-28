"use client";
import { useRef,useState } from "react";
import { useForm } from "react-hook-form";
import { Alert,Box,Button,Stack,Typography } from "@mui/material";
import { UserCreate } from "../../contracts/users";
import { FormField,validationErrors } from "../clients/FormField";
import { requestJson,ActionFailure,actionMessage } from "../clients/browser-api";
export function CreateUserForm({orgId,readList}:{orgId:string;readList:()=>Promise<void>}){
 const form=useForm({defaultValues:{firstName:"",lastName:"",username:"",email:"",phoneNumber:"",password:""}});
 const active=useRef(false),[pending,setPending]=useState(false),[error,setError]=useState(""),[uncertain,setUncertain]=useState(false);
 return <Box component="section" aria-label="Create local user" sx={{py:3}}><Typography component="h2" variant="h2">Create local user</Typography>
 <Typography color="text.secondary" sx={{my:2}}>New accounts require MFA by default. Creating an account does not send an invitation or confirm delivery.</Typography>
 {error&&<Alert severity="error">{error}</Alert>}
 {uncertain&&<Alert severity="warning">Creation may have completed. Review the current user list before deciding whether to create another account.</Alert>}
 <Box component="form" noValidate autoComplete="off" onSubmit={form.handleSubmit(async values=>{
  if(active.current||uncertain)return;const parsed=UserCreate.safeParse(values);if(!parsed.success){validationErrors(form,parsed.error);return;}
  active.current=true;setPending(true);setError("");try{const created=await requestJson<{id:string}>("/api/admin/organizations/"+orgId+"/users","POST",parsed.data);form.reset();window.location.assign("/admin/organizations/"+orgId+"/users/"+created.id);}
  catch(e){setError(actionMessage(e));setUncertain(!(e instanceof ActionFailure)||e.status>=500);}
  finally{form.setValue("password","");active.current=false;setPending(false);}
 })}><Stack spacing={2} sx={{maxWidth:640}}>
 {([['firstName','First name'],['lastName','Last name'],['username','Username'],['email','Email address'],['phoneNumber','Phone number'],['password','Initial password']] as const).map(([name,label])=><FormField key={name} control={form.control} name={name} label={label} type={name==='password'?'password':name==='email'?'email':'text'} autoComplete={name==='password'?'new-password':'off'} disabled={pending}/>)}
 <Button variant="contained" type="submit" disabled={pending||uncertain}>Create local user</Button>
 {uncertain&&<Button onClick={async()=>{try{await readList();setUncertain(false);setError("Review matching usernames in the refreshed list before submitting another request.");}catch(e){setError(actionMessage(e));}}}>Read current user list</Button>}
 </Stack></Box></Box>;
}
