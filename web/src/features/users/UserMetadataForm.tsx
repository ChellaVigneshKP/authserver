"use client";
import { z } from "zod";
import { Alert } from "@mui/material";
import { Metadata } from "../../contracts/users";
import { FormSection } from "./FormSection";
const editor=z.object({json:z.string().refine(s=>{try{return Metadata.safeParse(JSON.parse(s)).success;}catch{return false;}},"Enter a JSON object of at most 64 KiB.")});
export function UserMetadataForm({metadata,disabled,save,read}:{metadata:Record<string,unknown>;disabled:boolean;save:(v:unknown)=>Promise<Record<string,unknown>>;read:()=>Promise<Record<string,unknown>>}){const pick=(v:Record<string,unknown>)=>({json:JSON.stringify(v,null,2)});return <><Alert severity="info">Metadata is application data. It does not grant roles or permissions.</Alert><FormSection title="Metadata" values={pick(metadata)} schema={editor} fields={[{name:"json",label:"JSON metadata",multiline:true}]} disabled={disabled} save={async v=>pick(await save({metaData:JSON.parse(v.json)}))} read={async()=>pick(await read())}/></>;}
