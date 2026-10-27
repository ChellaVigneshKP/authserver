import { z } from "zod";
const text = z.string().trim().min(1).max(255);
const optionalText = z.string().max(255).default("");
export const UserPageQuery = z.object({
  offset: z.coerce.number().int().nonnegative().default(0),
  limit: z.coerce.number().pipe(z.union([z.literal(10),z.literal(25),z.literal(50),z.literal(100)])).default(25),
  search:z.string().max(50).default(""),
  status:z.enum(["","Active","Inactive"]).default(""),
  type:z.enum(["","User","Administrator"]).default(""),
}).strict();
export type UserQuery = z.infer<typeof UserPageQuery>;
export const UserSummary = z.object({
  id:z.uuid(),memberId:z.uuid(),loginId:z.uuid(),organizationId:z.uuid(),groupId:z.uuid().nullable(),
  userName:text,firstName:text,lastName:text,email:z.string(),branding:text,
  status:z.enum(["Active","Inactive"]),twoFactorEnabled:z.boolean(),syncMode:z.enum(["local","sync","async"]),
  title:z.string().nullish(),middleInitial:z.string().nullish(),suffix:z.string().nullish(),
  phoneNumber:z.string().nullish(),secondaryPhoneNumber:z.string().nullish(),groupName:z.string().nullish(),
});
export type UserDto=z.infer<typeof UserSummary>;
export const UserPageWire=z.object({offset:z.number().int().nonnegative(),resultsPerPage:z.number().int(),results:z.number().int().nonnegative(),users:z.array(UserSummary)});
export const UserCreate=z.object({firstName:text,lastName:text,username:text,email:z.email().max(255),password:z.string().min(1).max(4096),phoneNumber:text}).strict();
export const UserProfile=z.object({firstName:text,lastName:text,title:optionalText,middleInitial:z.string().max(1).default(""),phoneNumber:text,secondaryPhoneNumber:optionalText}).strict();
export const UserEmail=z.object({email:z.email().max(255)}).strict();
export const UserUsername=z.object({username:text}).strict();
export const UserPassword=z.object({password:z.string().min(1).max(4096)}).strict();
export const UserSecurity=z.object({twoFactorEnabled:z.boolean()}).strict();
export const UserStatus=z.object({status:z.enum(["Active","Inactive"])}).strict();
export const Metadata=z.record(z.string(),z.unknown()).refine(v=>new TextEncoder().encode(JSON.stringify(v)).length<=65536,"Metadata must be at most 64 KiB.");
export const MetadataInput=z.object({metaData:Metadata}).strict();
export const Contact=z.object({name:z.string().nullish(),email:z.string().nullish(),phoneNumber:z.string().nullish()});
export const ContactInput=z.object({name:text,email:z.email().max(255),phoneNumber:text}).strict();
export const Organization=z.object({id:z.uuid(),name:text,status:z.enum(["ACTIVE","INACTIVE"]),description:z.string().nullish(),primaryContact:Contact.nullish(),secondaryContact:Contact.nullish()});
export type OrganizationDto=z.infer<typeof Organization>;
export const OrganizationCreate=z.object({name:text,description:z.string().max(1024).default("")}).strict();
export const OrganizationUpdate=OrganizationCreate.extend({status:z.enum(["Active","Revoked"])}).strict();
