import {beforeEach,expect,it,vi} from "vitest";
const owned=vi.hoisted(()=>({org:"11111111-1111-4111-8111-111111111111",user:"22222222-2222-4222-8222-222222222222",login:"33333333-3333-4333-8333-333333333333",calls:[] as any[]}));
vi.mock("../../src/server/auth",()=>({requireSession:async()=>({}),boundedBody:async(r:Response)=>new Uint8Array(await r.arrayBuffer())}));
vi.mock("../../src/server/csrf",()=>({requireMutation:async()=>{}}));
vi.mock("../../src/server/portal-context",()=>({getPortalContext:async()=>({subject:{organizationId:owned.org},organizations:[{id:owned.org,name:"Owned"}],permissions:["idp-admin-org:switch","idp-admin-org:create","idp-admin-user:update","idp-admin-user:read"]})}));
vi.mock("../../src/server/java-api",()=>({javaRequest:async(_s:unknown,operation:any,input:unknown,schema:any)=>{owned.calls.push({operation,input});return schema.parse(operation.kind==="organizationCreate"?{id:owned.org}:operation.kind==="userGet"?{id:owned.user,memberId:owned.user,loginId:owned.login,organizationId:owned.org,groupId:null,userName:"owned",firstName:"Owned",lastName:"Person",email:"owned@example.test",branding:"secondary",status:"Active",twoFactorEnabled:false,syncMode:"local"}:undefined);}}));
import {organizationListAction} from "../../src/features/organizations/api";
import {userAction} from "../../src/features/users/api";
beforeEach(()=>{owned.calls=[];});
it("accepts the actual organization create ID-only response without retry or invented details",async()=>{
 const result=await organizationListAction(new Request("http://localhost/api/admin/organizations",{method:"POST",body:JSON.stringify({name:"Owned",description:"Test"})}));
 expect(result).toEqual({id:owned.org});expect(owned.calls.length).toBe(1);
 expect(owned.calls[0].operation).toEqual({kind:"organizationCreate"});
});
it("takes branding and exact login from fresh detail for password mutation",async()=>{
 await userAction(new Request("http://localhost/users/"+owned.user+"/password?loginId="+owned.login,{method:"PUT",body:JSON.stringify({password:"synthetic-only"})}),owned.org,owned.user,"password");
 expect(owned.calls.map(c=>c.operation)).toEqual([{kind:"userGet",userId:owned.user,loginId:owned.login},{kind:"userPassword",userId:owned.user,loginId:owned.login}]);
 expect(owned.calls[1].input).toEqual({password:Buffer.from("synthetic-only").toString("base64"),branding:"secondary",intent:"idp",metadata:{}});
});
it("rejects forged browser branding before the credential write",async()=>{
 await expect(userAction(new Request("http://localhost/users/"+owned.user+"/password?loginId="+owned.login,{method:"PUT",body:JSON.stringify({password:"synthetic-only",branding:"forged"})}),owned.org,owned.user,"password")).rejects.toMatchObject({status:400});
 expect(owned.calls.length).toBe(1);
});
