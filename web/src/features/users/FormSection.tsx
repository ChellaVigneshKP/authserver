"use client";
import { useRef,useState } from "react";
import { useForm, Controller, type FieldValues, type FieldPath, type DefaultValues } from "react-hook-form";
import { z } from "zod";
import { Alert,Box,Button,Checkbox,FormControlLabel,MenuItem,Stack,Typography } from "@mui/material";
import { FormField,validationErrors } from "../clients/FormField";
import { ActionFailure,actionMessage } from "../clients/browser-api";
export function FormSection<T extends FieldValues>({title,values,schema,fields,save,read,disabled=false,sensitive=false}:{
 title:string;values:T;schema:z.ZodType<T>;fields:{name:FieldPath<T>;label:string;type?:string;multiline?:boolean;options?:string[]}[];
 save:(value:T)=>Promise<T>;read:()=>Promise<T>;disabled?:boolean;sensitive?:boolean;
}){
 const form=useForm<T>({defaultValues:values as DefaultValues<T>,values,resetOptions:{keepDirtyValues:true}});
 const dirty=Object.keys(form.formState.dirtyFields).length>0;
 const busy=useRef(false),[pending,setPending]=useState(false),[error,setError]=useState(""),[notice,setNotice]=useState(""),[uncertain,setUncertain]=useState(false);
 async function submit(value:T){
  if(busy.current||uncertain)return;form.clearErrors();const parsed=schema.safeParse(value);
  if(!parsed.success){validationErrors(form,parsed.error);return;}
  busy.current=true;setPending(true);setError("");setNotice("");
  try{const fresh=await save(parsed.data);form.reset(fresh,{keepDirtyValues:false,keepDirty:false});setNotice("Changes saved.");}
  catch(e){setError(actionMessage(e));setUncertain(!(e instanceof ActionFailure)||e.status>=500);}
  finally{if(sensitive)form.reset(values,{keepDirtyValues:false,keepDirty:false});busy.current=false;setPending(false);}
 }
 return <Box component="section" sx={{py:3,borderBottom:1,borderColor:"divider"}} aria-label={title}>
  <Typography component="h2" variant="h2" gutterBottom>{title}</Typography>
  {error&&<Alert severity="error">{error}</Alert>}{notice&&<Alert severity="success" role="status">{notice}</Alert>}
  {uncertain&&<Alert severity="warning">The result is uncertain. Read current state before deciding whether to submit again.</Alert>}
  <Box component="form" noValidate autoComplete="off" onSubmit={form.handleSubmit(submit)}>
   <Stack spacing={2} sx={{mt:2,maxWidth:640}}>
    {fields.map(f=>f.type==="boolean"?<Controller key={f.name} control={form.control} name={f.name} render={({field})=><FormControlLabel label={f.label} control={<Checkbox checked={Boolean(field.value)} onChange={(_,v)=>field.onChange(v)} disabled={disabled||pending}/>} />}/>:
     <FormField key={f.name} control={form.control} name={f.name} label={f.label} type={f.type??"text"} multiline={f.multiline} minRows={f.multiline?6:undefined} select={!!f.options} disabled={disabled||pending} autoComplete={f.type==="password"?"new-password":"off"}>{f.options?.map(o=><MenuItem key={o} value={o}>{o}</MenuItem>)}</FormField>)}
    {!disabled&&<Stack direction="row" spacing={2} sx={{flexWrap:"wrap"}}><Button type="submit" variant="contained" disabled={pending||uncertain||!dirty}>Save {title.toLowerCase()}</Button>
    <Button disabled={pending} onClick={async()=>{try{const fresh=await read();form.reset(fresh,{keepDirtyValues:false,keepDirty:false});setUncertain(false);setError("");setNotice("Current state loaded.");}catch(e){setError(actionMessage(e));}}}>Read current {title.toLowerCase()}</Button></Stack>}
   </Stack>
  </Box>
 </Box>;
}
