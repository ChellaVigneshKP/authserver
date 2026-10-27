"use client";
import { UserProfile,type UserDto } from "../../contracts/users";
import { FormSection } from "./FormSection";
export function profileValues(u:UserDto){return {firstName:u.firstName,lastName:u.lastName,title:u.title??"",middleInitial:u.middleInitial??"",phoneNumber:u.phoneNumber??"",secondaryPhoneNumber:u.secondaryPhoneNumber??""};}
export function UserProfileForm({user,disabled,save,read}:{user:UserDto;disabled:boolean;save:(v:unknown)=>Promise<UserDto>;read:()=>Promise<UserDto>}){return <FormSection title="Profile" values={profileValues(user)} schema={UserProfile} fields={[{name:"firstName",label:"First name"},{name:"lastName",label:"Last name"},{name:"title",label:"Title"},{name:"middleInitial",label:"Middle initial"},{name:"phoneNumber",label:"Phone number",type:"tel"},{name:"secondaryPhoneNumber",label:"Secondary phone number",type:"tel"}]} disabled={disabled} save={async v=>profileValues(await save(v))} read={async()=>profileValues(await read())}/>;}
