import { expect, it } from "vitest";
import { UserPageQuery, MetadataInput, UserCreate, UserSummary } from "../../src/contracts/users";
it("uses the approved page sizes and legacy search ceiling", () => {
  expect(UserPageQuery.parse({}).limit).toBe(25);
  for (const limit of [10,25,50,100]) expect(UserPageQuery.safeParse({limit}).success).toBe(true);
  expect(UserPageQuery.safeParse({limit:1000}).success).toBe(false);
  expect(UserPageQuery.safeParse({search:"a".repeat(51)}).success).toBe(false);
});
it("limits metadata by UTF-8 bytes and rejects non-object data", () => {
  expect(MetadataInput.safeParse({metaData:[]}).success).toBe(false);
  expect(MetadataInput.safeParse({metaData:{note:"😀".repeat(17000)}}).success).toBe(false);
  expect(MetadataInput.safeParse({metaData:{permissions:["not-authority"]}}).success).toBe(true);
});
it("local creation forbids browser-supplied identity IDs and sync import", () => {
  const input={firstName:"A",lastName:"Person",username:"owned",email:"owned@example.test",password:"transient-test-value",phoneNumber:"+18888888888"};
  expect(UserCreate.safeParse(input).success).toBe(true);
  expect(UserCreate.safeParse({...input,loginId:"11111111-1111-4111-8111-111111111111"}).success).toBe(false);
  expect(UserCreate.safeParse({...input,syncFlag:true}).success).toBe(false);
});
it("strips unknown internal fields from browser user projection", () => {
  const id="11111111-1111-4111-8111-111111111111";
  const result=UserSummary.parse({id,memberId:id,loginId:id,organizationId:id,groupId:null,userName:"owned",firstName:"A",lastName:"Person",email:"owned@example.test",branding:"idp",status:"Active",twoFactorEnabled:true,syncMode:"local",profileId:123,password:"never-forward"});
  expect(Object.keys(result).includes("profileId")).toBe(false);
  expect(Object.keys(result).includes("password")).toBe(false);
});
it("returns a definite field rejection for an invalid server list query",async()=>{
 const {listUsers,respond}=await import("../../src/features/users/api");
 const result=await respond(()=>listUsers({} as never,"11111111-1111-4111-8111-111111111111",{search:"x".repeat(51)}));
 expect(result.status).toBe(400);
 expect((await result.json()).retryable).toBe(false);
});
