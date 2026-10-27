"use client";
import { Alert } from "@mui/material";
import { UserSecurity,type UserDto } from "../../contracts/users";
import { FormSection } from "./FormSection";
export function UserSecurityForm({user,disabled,save,read}:{user:UserDto;disabled:boolean;save:(v:unknown)=>Promise<UserDto>;read:()=>Promise<UserDto>}){const pick=(u:UserDto)=>({twoFactorEnabled:u.twoFactorEnabled});return <><Alert severity="info">This changes the account's MFA requirement. It does not enroll an authenticator or confirm message delivery.</Alert><FormSection title="Security settings" values={pick(user)} schema={UserSecurity} fields={[{name:"twoFactorEnabled",label:"Require multi-factor authentication",type:"boolean"}]} disabled={disabled} save={async v=>pick(await save(v))} read={async()=>pick(await read())}/></>;}
