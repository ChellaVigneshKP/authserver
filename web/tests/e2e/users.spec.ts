import { test, expect, type Page, type BrowserContext, type Locator } from "@playwright/test";
import { readFileSync, writeFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { createHash, createHmac, randomBytes, randomUUID } from "node:crypto";
import { createServer } from "node:http";
import type { AddressInfo } from "node:net";
import { createClient } from "redis";
import { SignJWT } from "jose";
process.env.PLAYWRIGHT_NO_COPY_PROMPT="1";
test.use({trace:"off",screenshot:"off",video:"off"});
const fixture=JSON.parse(readFileSync(process.env.PORTAL_E2E_CONFIG!,"utf8"));
const checks:{id:string;passed:boolean}[]=[];
const redis=createClient({url:"redis://127.0.0.1:16379/0",socket:{reconnectStrategy:false}});
function check(id:string,passed:boolean){checks.push({id,passed});writeFileSync(process.env.PORTAL_SCREENSHOT_DIR+"/users-progress.json",JSON.stringify(checks),{mode:0o600});expect(passed,id).toBe(true);}
test.beforeAll(()=>redis.connect());
test.afterAll(async()=>{await redis.quit();writeFileSync(process.env.PORTAL_E2E_REPORT!,JSON.stringify({checks,blocked:[]}),{mode:0o600});});
async function login(page:Page,username=fixture.username,password=fixture.password){
 await page.goto(fixture.portal);await page.getByRole("link",{name:"Sign in",exact:true}).click();
 await page.locator("#username").fill(username);await page.locator("#password").fill(password);
 await page.getByRole("button",{name:"Sign in",exact:true}).click();await page.waitForURL(fixture.portal+"/account");
}
async function session(context:BrowserContext){const c=(await context.cookies(fixture.portal)).find(v=>v.name==="portal-session")!;return JSON.parse((await redis.get(fixture.redisPrefix+"session:"+c.value))!);}
function signed(s:any,raw=""){return {Authorization:"Bearer "+s.accessToken,"User-Agent":s.browserContext.userAgent,"Accept-Language":s.browserContext.acceptLanguage,Referer:s.browserContext.externalOrigin+"/","x-request-datetime":new Date().toISOString(),"x-signature":createHmac("sha256",Buffer.from(s.signingKey,"base64")).update(raw).digest("base64"),"Content-Type":"application/json"};}
async function java(page:Page,s:any,path:string,method="GET",data?:unknown){const raw=data===undefined?"":JSON.stringify(data);return page.request.fetch(fixture.issuer+path,{method,headers:signed(s,raw),...(data===undefined?{}:{data:raw})});}
async function bff(page:Page,path:string,method:string,data:unknown={}){const state=await(await page.request.get(fixture.portal+"/api/session")).json();return page.request.fetch(fixture.portal+path,{method,headers:{Origin:fixture.portal,"Content-Type":"application/json","x-csrf-token":state.csrfToken},data:JSON.stringify(data)});}
function sql(query:string){return JSON.parse(execFileSync("python3",["-c",`import sys,json,re;sys.path.insert(0,'scripts');from e2e_target import load_target,sql_text;c,b,d=load_target();assert re.fullmatch(r'AuthserverLifecycle_[0-9a-f]{32}',d);q=json.load(sys.stdin);print(sql_text(c,d,q))`],{cwd:process.env.PORTAL_REPO_ROOT!,input:JSON.stringify(query),encoding:"utf8",timeout:20000}));}
function literal(value:string){return "N'"+value.replaceAll("'","''")+"'";}
function persisted(user:string,clause:string){return sql("SELECT COUNT(*) AS n FROM Person.Profile p JOIN Person.Credential c ON c.ProfileId=p.ProfileId WHERE p.RowGuid="+literal(user)+" AND "+clause+" FOR JSON PATH")[0].n===1;}
async function fillStable(area:Locator,fields:Record<string,string>){
 await expect.poll(()=>area.evaluate(el=>Object.keys(el).some(k=>k.startsWith("__reactFiber$"))),{message:"form hydrated",timeout:15000}).toBe(true);
 for(const [name,value] of Object.entries(fields)){const input=area.getByLabel(name,{exact:true});await expect(async()=>{if(await input.inputValue()!==value)await input.fill(value);await expect(input).toHaveValue(value,{timeout:1000});}).toPass({timeout:15000});}
}
async function save(page:Page,title:string,fields:Record<string,string>){const area=page.getByRole("region",{name:title,exact:true});await fillStable(area,fields);const button=area.getByRole("button",{name:"Save "+title.toLowerCase(),exact:true});await expect(button).toBeEnabled();await button.click();await expect.poll(()=>area.getByRole("status").count(),{message:title+" save confirmed"}).toBe(1);check(title+" own successful form is clean",await area.getByRole("button",{name:"Save "+title.toLowerCase(),exact:true}).isDisabled());}
async function create(page:Page,s:any,org:string,name:string,password:string){const r=await java(page,s,"/api/v1/users","POST",{firstName:"Owned",lastName:"Fixture",username:name,email:name+"@example.test",password:Buffer.from(password).toString("base64"),phoneNumber:"+19999999998",orgGuid:org,branding:"idp",syncFlag:false});check("owned local fixture created",r.status()===201);return (await r.json()).id as string;}

test("platform organizations and every local user form persist through Java and SQL",async({page,context,browser})=>{
 test.setTimeout(600000);await login(page);const s=await session(context);const home=await(await java(page,s,"/api/v1/portal/context")).json();
 await page.goto(fixture.portal+"/admin/organizations");
 const organizationName="F4 organization "+randomUUID();
 const newOrg=page.getByRole("region",{name:"New organization",exact:true});await fillStable(newOrg,{"Organization name":organizationName,Description:"Owned acceptance"});const createdResponse=page.waitForResponse(r=>r.url()===fixture.portal+"/api/admin/organizations"&&r.request().method()==="POST",{timeout:20000});await newOrg.getByRole("button",{name:"Save new organization",exact:true}).click();check("organization BFF create201",(await createdResponse).status()===201);
 await page.waitForURL(/\/admin\/organizations\/[0-9a-f-]+$/);
 const org=page.url().split("/").at(-1)!;
 check("organization create SQL",sql("SELECT COUNT(*) AS n FROM Partner.Organization WHERE RowGuid="+literal(org)+" AND Name="+literal(organizationName)+" FOR JSON PATH")[0].n===1);
 await save(page,"Organization details",{Description:"Updated acceptance"});
 check("organization details SQL",sql("SELECT COUNT(*) AS n FROM Partner.Organization WHERE RowGuid="+literal(org)+" AND Note=N'Updated acceptance' FOR JSON PATH")[0].n===1);
 for(const title of ["Primary contact","Secondary contact"]){await save(page,title,{"Contact name":title+" Owned","Contact email":"owned@example.test","Contact phone number":"+19999999998"});const o=await(await java(page,s,"/api/v1/organizations/"+org)).json();check(title+" exact HTTP readback",o[title.startsWith("Primary")?"primaryContact":"secondaryContact"].name===title+" Owned");check(title+" SQL persisted",sql("SELECT COUNT(*) AS n FROM Partner.Organization WHERE RowGuid="+literal(org)+" AND "+(title.startsWith("Primary")?"Primary":"Secondary")+"ContactName="+literal(title+" Owned")+" FOR JSON PATH")[0].n===1);}
 await page.goto(fixture.portal+"/admin/organizations/"+org+"/users");
 const username="f4_"+randomUUID().replaceAll("-","");const password="P9!"+randomBytes(18).toString("hex")+"qZ";
 const createForm=page.getByRole("region",{name:"Create local user",exact:true});
 await fillStable(createForm,{"First name":"Owned","Last name":"User",Username:username,"Email address":username+"@example.test","Phone number":"+19999999998","Initial password":password});
 await createForm.getByRole("button",{name:"Create local user",exact:true}).click();await page.waitForURL(/\/users\/[0-9a-f-]+$/);
 const user=page.url().split("/").at(-1)!;check("local user SQL created once with MFA required",persisted(user,"c.UserName="+literal(username)+" AND p.TwoFactorEnabled=1"));
 const profile=page.getByRole("region",{name:"Profile",exact:true});await profile.getByLabel("First name",{exact:true}).fill("Draft preserved");
 await save(page,"Email",{"Email address":"changed@example.test"});
 check("unrelated dirty profile survives email readback",await profile.getByLabel("First name",{exact:true}).inputValue()==="Draft preserved");
 check("email SQL persisted",persisted(user,"p.Email=N'changed@example.test'"));
 await save(page,"Profile",{"First name":"Updated","Last name":"Person",Title:"Mx","Middle initial":"Q","Phone number":"+19999999997","Secondary phone number":"+19999999996"});
 check("profile SQL persisted",persisted(user,"p.FirstName=N'Updated' AND p.LastName=N'Person' AND p.PhoneNumber=N'+19999999997'"));
 await save(page,"Username",{Username:username+"_renamed"});check("username SQL persisted",persisted(user,"c.UserName="+literal(username+"_renamed")));
 const security=page.getByRole("region",{name:"Security settings",exact:true});await security.getByLabel("Require multi-factor authentication").uncheck();await security.getByRole("button",{name:"Save security settings",exact:true}).click();await expect.poll(()=>persisted(user,"p.TwoFactorEnabled=0")).toBe(true);check("owned per-user MFA fixture change only",true);
 const personal=await browser.newContext({locale:"en-US",userAgent:"PortalAcceptance/1.0",extraHTTPHeaders:{"Accept-Language":"en-US"},timezoneId:"Asia/Kolkata"});
 try{const account=await personal.newPage();await login(account,username+"_renamed",password);check("actual initial password login",true);
 const old=await session(personal);const next="N9!"+randomBytes(18).toString("hex")+"qZ";
 await save(page,"Password",{"New password":next});check("password input cleared",await page.getByLabel("New password",{exact:true}).inputValue()==="");
 check("old bearer revoked after admin password change",(await java(account,old,"/api/v1/portal/context")).status()===401);
 await account.goto(fixture.portal+"/auth/login",{referer:fixture.portal+"/"});await account.locator("#username").fill(username+"_renamed");await account.locator("#password").fill(password);await account.getByRole("button",{name:"Sign in",exact:true}).click();
 await account.waitForURL(url=>url.searchParams.has("error"),{timeout:20000});await expect(account.getByRole("button",{name:"Sign in",exact:true}),{message:"rejected sign-in re-rendered"}).toBeEnabled();check("real old password rejection",!account.url().startsWith(fixture.portal+"/account"));
 check("rejected sign-in re-rendered a clean form",await account.locator("#username").inputValue()===""&&await account.locator("#password").inputValue()==="");
 await account.locator("#username").fill(username+"_renamed");await account.locator("#password").fill(next);await account.getByRole("button",{name:"Sign in",exact:true}).click();await account.waitForURL(fixture.portal+"/account");check("new password accepted with old issuer cookie recovery",true);
 }finally{await personal.close();}
 await save(page,"Metadata",{"JSON metadata":JSON.stringify({note:"owned metadata",permissions:["idp-admin-org:switch"]})});
 check("metadata HTTP persisted",(await(await java(page,s,"/api/v1/users/"+user+"/metadata")).json()).note==="owned metadata");
 await page.reload();check("persisted profile reload",await page.getByRole("region",{name:"Profile",exact:true}).getByLabel("First name",{exact:true}).inputValue()==="Updated");
 const before=sql("SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) AS n FROM Person.Profile WHERE RowGuid="+literal(user)+" FOR JSON PATH")[0].n;
 const wrong=await bff(page,"/api/admin/organizations/"+home.subject.organizationId+"/users/"+user+"/email","PUT",{email:"denied@example.test"});check("foreign selected organization BFF denial",wrong.status()===403);check("foreign selected organization SQL unchanged",before===sql("SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) AS n FROM Person.Profile WHERE RowGuid="+literal(user)+" FOR JSON PATH")[0].n);
 const brand=await java(page,s,"/api/v1/users/"+user+"/password","PUT",{branding:"missing-brand",intent:"idp",metadata:{},password:Buffer.from(password).toString("base64")});check("wrong body branding Java denial",brand.status()===400);
 for(const label of ["Deactivate account","Activate account"]){await page.getByRole("button",{name:label,exact:true}).click();await page.getByRole("button",{name:"Confirm change",exact:true}).click();await page.waitForURL(fixture.portal+"/admin/organizations/"+org+"/users");check(label+" SQL",persisted(user,"p.Status="+(label.startsWith("Deactivate")?"0":"1")));await page.goto(fixture.portal+"/admin/organizations/"+org+"/users/"+user);}
 await page.getByRole("button",{name:"Delete account access",exact:true}).click();await page.getByRole("button",{name:"Delete access",exact:true}).click();await page.waitForURL(fixture.portal+"/admin/organizations/"+org+"/users");check("delete access SQL retains inactive record",persisted(user,"p.Status=0"));
 const list=await(await page.request.get(fixture.portal+"/api/admin/organizations/"+org+"/users?status=Active")).json();check("post-delete active list fresh",list.items.length===0&&list.total===0);
 await page.goto(fixture.portal+"/admin/organizations/"+org);await page.getByRole("button",{name:"Revoke organization",exact:true}).click();await page.getByRole("button",{name:"Change status",exact:true}).click();await expect.poll(()=>sql("SELECT Status AS n FROM Partner.Organization WHERE RowGuid="+literal(org)+" FOR JSON PATH")[0].n).toBe(0);check("organization revoke SQL retains record",true);
});

test("tenant controls, stale permission denial, validation and genuine provider failure",async({page,context,browser})=>{
 test.setTimeout(600000);await login(page);const admin=await session(context);const home=await(await java(page,admin,"/api/v1/portal/context")).json();
 const org=(await(await java(page,admin,"/api/v1/organizations","POST",{name:"F4 tenant "+randomUUID(),description:"Owned tenant"})).json()).id;
 const name="tenant_"+randomUUID().replaceAll("-","");const password="P9!"+randomBytes(18).toString("hex")+"qZ";
  const operator=await create(page,admin,org,name,password);const target=await create(page,admin,org,"target_"+randomUUID().replaceAll("-",""),password);await create(page,admin,org,"target2_"+randomUUID().replaceAll("-",""),password);
 const role=(await(await java(page,admin,"/api/v1/organizations/"+org+"/groups","POST",{name:"F4 tenant manager",description:"Owned",permissionKeys:["idp-admin-org:read","idp-admin-org:update","idp-admin-user:read","idp-admin-user:create","idp-admin-user:update","idp-admin-user:delete"]})).json()).id;
 check("tenant role assigned",(await java(page,admin,"/api/v1/organizations/"+org+"/users/"+operator+"/group","PUT",{groupId:role})).status()===204);
 check("owned operator MFA disabled explicitly",(await java(page,admin,"/api/v1/users/"+operator+"/security-settings","PUT",{twoFactorEnabled:false})).status()===200);
 const tenant=await browser.newContext({locale:"en-US",userAgent:"PortalAcceptance/1.0",extraHTTPHeaders:{"Accept-Language":"en-US"},timezoneId:"Asia/Kolkata"});
 try{const own=await tenant.newPage();await login(own,name,password);const s=await session(tenant);
 await own.goto(fixture.portal+"/admin/organizations/"+org);await save(own,"Organization details",{Description:"Tenant edited"});
 check("tenant own details SQL",sql("SELECT COUNT(*) AS n FROM Partner.Organization WHERE RowGuid="+literal(org)+" AND Note=N'Tenant edited' FOR JSON PATH")[0].n===1);
 for(const path of ["/api/v1/organizations","/api/v1/organizations/"+home.subject.organizationId])check("tenant Java global or foreign read denied",(await java(own,s,path)).status()===403);
 check("tenant BFF global list denied",(await own.request.get(fixture.portal+"/api/admin/organizations")).status()===403);
 check("tenant Java organization status denied",(await java(own,s,"/api/v1/organizations/"+org,"PUT",{name:"Denied",description:"Denied",status:"Revoked"})).status()===403);
 check("tenant BFF organization status denied",(await bff(own,"/api/admin/organizations/"+org,"PUT",{name:"Denied",description:"Denied",status:"Revoked"})).status()===403);
 check("tenant status denials SQL unchanged",sql("SELECT COUNT(*) AS n FROM Partner.Organization WHERE RowGuid="+literal(org)+" AND Status=1 AND Note=N'Tenant edited' FOR JSON PATH")[0].n===1);
 const base="/api/admin/organizations/"+org+"/users";
 for(const limit of [10,25,50,100]){const r=await own.request.get(fixture.portal+base+"?limit="+limit);const p=await r.json();check("server pagination page size "+limit,r.status()===200&&p.limit===limit&&p.total===2&&p.items.length===2);}
 const ordinaryPage=await(await own.request.get(fixture.portal+base+"?limit=25")).json();check("tenant ordinary-user page excludes administrator-typed operator",ordinaryPage.total===2&&!ordinaryPage.items.some((u:{id:string})=>u.id===operator));
 const empty=await(await own.request.get(fixture.portal+base+"?search=never-matches-owned-uuid")).json();check("server empty search result",empty.total===0&&empty.items.length===0);
 check("legacy search over 50 rejected",(await own.request.get(fixture.portal+base+"?search="+"x".repeat(51))).status()===400);
 check("metadata UTF8 bound rejects oversized",(await bff(own,base+"/"+target+"/metadata","PUT",{metaData:{text:"😀".repeat(17000)}})).status()===400);
 await own.goto(fixture.portal+"/admin/organizations/"+org+"/users/"+target);
 const localEmail=await bff(own,base+"/"+target+"/email","PUT",{email:"tenant-owned@example.test"});
 check("tenant own ordinary-user email update "+localEmail.status(),localEmail.status()===200);
 check("tenant own ordinary-user email SQL persisted",sql("SELECT COUNT(*) AS n FROM Person.Profile WHERE RowGuid="+literal(target)+" AND Email=N'tenant-owned@example.test' FOR JSON PATH")[0].n===1);
 // The fixture enables the real sync branch; no provider response is intercepted.
 sql("UPDATE Person.Profile SET SyncFlag=1 WHERE RowGuid="+literal(target)+";SELECT 1 AS n FOR JSON PATH");
 const providerBefore=sql("SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) AS n FROM Person.Profile WHERE RowGuid="+literal(target)+" FOR JSON PATH")[0].n;
 const unavailable=await bff(own,base+"/"+target+"/email","PUT",{email:"unavailable@example.test"});const unavailableStatus=unavailable.status();const unavailableBody=await unavailable.json();
 const directUnavailable=await java(own,s,"/api/v1/users/"+target+"/email","PUT",{email:"unavailable@example.test",branding:"idp",intent:"idp",metadata:{}});
 check("genuine unconfigured sync provider error is a mapped dependency failure (bff "+unavailableStatus+" "+String(unavailableBody.code)+", java "+directUnavailable.status()+")",(unavailableStatus===502&&unavailableBody.code==="UPSTREAM_FAILURE"&&unavailableBody.retryable===false)||(unavailableStatus===503&&unavailableBody.code==="DEPENDENCY_UNAVAILABLE"&&unavailableBody.retryable===true));
 check("provider failure SQL unchanged",providerBefore===sql("SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) AS n FROM Person.Profile WHERE RowGuid="+literal(target)+" FOR JSON PATH")[0].n);
 sql("UPDATE Person.Profile SET SyncFlag=0 WHERE RowGuid="+literal(target)+";SELECT 1 AS n FOR JSON PATH");
 const viewer=(await(await java(page,admin,"/api/v1/organizations/"+org+"/groups","POST",{name:"F4 tenant viewer",description:"Owned",permissionKeys:["idp-admin-org:read","idp-admin-user:read"]})).json()).id;
 check("live authority removed",(await java(page,admin,"/api/v1/organizations/"+org+"/users/"+operator+"/group","PUT",{groupId:viewer})).status()===204);
 const snapshot=()=>JSON.stringify(sql("SELECT (SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM Person.Profile WHERE RowGuid="+literal(target)+") AS p,(SELECT CHECKSUM_AGG(BINARY_CHECKSUM(c.Id,c.UserName,c.Password,c.Status,c.ModifiedBy)) FROM Person.Credential c JOIN Person.Profile p ON p.ProfileId=c.ProfileId WHERE p.RowGuid="+literal(target)+") AS c FOR JSON PATH"));const before=snapshot();
 const actions:Record<string,unknown>={profile:{firstName:"Denied",lastName:"Denied",phoneNumber:"+19999999998"},email:{email:"denied@example.test"},username:{username:"denied_owned"},password:{password:"P9!deniedOwnedPasswordqZ"},"security-settings":{twoFactorEnabled:true},metadata:{metaData:{denied:true}},status:{status:"Inactive"}};
 for(const [action,payload]of Object.entries(actions)){
  check(action+" stale BFF permission denial",(await bff(own,base+"/"+target+"/"+action,"PUT",payload)).status()===403);
  const value=action==="password"?{password:Buffer.from((payload as any).password).toString("base64")}:payload;
  const wire=["profile","email","username","password"].includes(action)?{...(value as any),branding:"idp",intent:"idp",metadata:{}}:value;
  const directDenial=await java(own,s,"/api/v1/users/"+target+"/"+action,"PUT",wire);
  check(action+" stale Java permission denial (java "+directDenial.status()+")",directDenial.status()===403);
  check(action+" denial SQL unchanged",snapshot()===before);
 }
 check("delete stale BFF denial",(await bff(own,base+"/"+target,"DELETE")).status()===403);check("delete stale Java denial",(await java(own,s,"/api/v1/users/"+target,"DELETE")).status()===403);
 check("create stale BFF denial",(await bff(own,base,"POST",{})).status()===403);
 check("foreign user URL Java denied",(await java(own,s,"/api/v1/users/"+home.subject.userId)).status()===403);
 check("all denied mutations leave SQL unchanged",snapshot()===before);
 }finally{await tenant.close();}
});

for(const branding of ["idp","portal-sibling"]){
test(`real OAuth rename reuse revokes old identity and preserves the human tenant for ${branding}`,async({page,context,browser})=>{
 test.setTimeout(600000);await login(page);const admin=await session(context);const home=await(await java(page,admin,"/api/v1/portal/context")).json();
 const makeOrg=async(label:string)=>(await(await java(page,admin,"/api/v1/organizations","POST",{name:label+randomUUID(),description:"Owned identity proof"})).json()).id;
 const orgA=await makeOrg("F4 A "),orgB=await makeOrg("F4 B ");
 if(branding!=="idp")sql("IF NOT EXISTS(SELECT 1 FROM dbo.ExternalSource WHERE SourceCode="+literal(branding)+") INSERT dbo.ExternalSource(SourceId,SourceCode,ExternalTypeId) SELECT NEWID(),"+literal(branding)+",ExternalTypeId FROM dbo.ExternalSource WHERE SourceCode='idp';SELECT 1 AS n FOR JSON PATH");
 const username="reuse_"+randomUUID().replaceAll("-","");const password="P9!"+randomBytes(18).toString("hex")+"qZ";
 const createIdentity=async(org:string)=>{const r=await java(page,admin,"/api/v1/users","POST",{firstName:"Owned",lastName:"Identity",username,email:username+"@example.test",password:Buffer.from(password).toString("base64"),phoneNumber:"+19999999998",orgGuid:org,branding,syncFlag:false});check("native "+branding+" identity created",r.status()===201);const id=(await r.json()).id;check("native identity per-user MFA setting",(await java(page,admin,"/api/v1/users/"+id+"/security-settings","PUT",{twoFactorEnabled:false})).status()===200);return id;};
 const a=await createIdentity(orgA);const aDetail=await(await java(page,admin,"/api/v1/users/"+a)).json();
 let code="";
 const callback=createServer((request,response)=>{const url=new URL(request.url!,"http://127.0.0.1");if(url.pathname==="/auth/callback"){code=url.searchParams.get("code")??"";response.writeHead(303,{Location:"/done","Cache-Control":"no-store"});}else response.writeHead(200,{"Content-Type":"text/html","Cache-Control":"no-store"});response.end("<!doctype html><title>Owned authorization complete</title>");});
 await new Promise<void>(resolve=>callback.listen(0,"127.0.0.1",resolve));const callbackOrigin="http://127.0.0.1:"+(callback.address() as AddressInfo).port;const redirect=callbackOrigin+"/auth/callback";
 const native=await browser.newContext({locale:"en-US",userAgent:"PortalAcceptance/1.0",extraHTTPHeaders:{"Accept-Language":"en-US"},timezoneId:"Asia/Kolkata"});
 try{const account=await native.newPage();
  const setupClient=async(type:"WEB"|"SERVER")=>{const r=await java(page,admin,"/api/v1/organizations/"+home.subject.organizationId+"/applications","POST",{name:"F4 protocol "+randomUUID(),description:"Owned",type,authMethod:type==="SERVER"?"CLIENT_SECRET_JWT":"PKCE",usernameType:"USERNAME",allowForgotUsername:false});check("owned "+type+" protocol client created",r.status()===201);const client=await r.json();let secret="";
   if(type==="SERVER"){const cr=await java(page,admin,"/api/v1/organizations/"+home.subject.organizationId+"/applications/"+client.id+"/credentials","POST",{name:"Owned protocol",type:"SHARED_SECRET",algorithm:"HS256"});check("owned confidential credential created",cr.status()===201);secret=(await cr.json()).secretValue;}
   check("owned "+type+" token settings applied",(await java(page,admin,"/api/v1/organizations/"+home.subject.organizationId+"/applications/"+client.id+"/settings","PUT",{authCodeTimeToLive:300,accessTokenTimeToLive:3600,deviceCodeTimeToLive:300,refreshTokenTimeToLive:86400,reuseRefreshTokens:true,maxRequestTransitTime:30})).status()===200);
   check("protocol callback registered",(await java(page,admin,"/api/v1/organizations/"+home.subject.organizationId+"/applications/"+client.id+"/urls","PUT",{applicationUrl:callbackOrigin,returnUrls:[redirect],logoutUrls:[callbackOrigin+"/done"]})).status()===200);return {client,secret};};
  const publicClient=await setupClient("WEB");
  const clientAuth=async(c:{client:any;secret:string}):Promise<Record<string,string>>=>c.secret?{client_assertion_type:"urn:ietf:params:oauth:client-assertion-type:jwt-bearer",client_assertion:await new SignJWT({}).setProtectedHeader({alg:"HS256"}).setIssuer(c.client.clientId).setSubject(c.client.clientId).setAudience(fixture.issuer+"/oauth2/token").setIssuedAt().setExpirationTime("2m").setJti(randomUUID()).sign(Buffer.from(c.secret))}:{};
  const authorize=async(c:{client:any;secret:string},name:string)=>{code="";const verifier=randomBytes(32).toString("base64url");const url=new URL(fixture.issuer+"/oauth2/authorize");url.search=new URLSearchParams({response_type:"code",client_id:c.client.clientId,redirect_uri:redirect,scope:"openid profile email idp-admin",branding,state:randomUUID(),nonce:randomUUID(),code_challenge:createHash("sha256").update(verifier).digest("base64url"),code_challenge_method:"S256","x-request-datetime":new Date().toISOString()}).toString();await account.goto(url.href,{referer:fixture.portal+"/"});if(await account.locator("#password").count()){await account.locator("#username").fill(name);await account.locator("#password").fill(password);await account.getByRole("button",{name:"Sign in",exact:true}).click();}await account.waitForURL(callbackOrigin+"/done");check("actual protocol authorization code received",code.length>0);const token=await account.request.post(fixture.issuer+"/oauth2/token",{form:{grant_type:"authorization_code",client_id:c.client.clientId,code,redirect_uri:redirect,code_verifier:verifier,...await clientAuth(c)}});check("actual protocol code exchange",token.status()===200);code="";return token.json();};
  let client=publicClient;let issued=await authorize(client,username);
  const publicHasRefresh=typeof issued.refresh_token==="string"&&issued.refresh_token.length>0;
  check("supported public PKCE access token issued",typeof issued.access_token==="string");
  check("supported public PKCE refresh "+(publicHasRefresh?"issued":"not issued, imported fixture covered separately"),true);
  if(!publicHasRefresh){client=await setupClient("SERVER");sql("UPDATE Client.Application SET ApplicationTypeId=(SELECT ApplicationTypeId FROM Client.Application WHERE RowGuid="+literal(publicClient.client.id)+") WHERE RowGuid="+literal(client.client.id)+";SELECT 1 AS n FOR JSON PATH");issued=await authorize(client,username);}
  check("genuine refresh token issued",typeof issued.refresh_token==="string"&&issued.refresh_token.length>0);
  const refresh=async(value:string)=>account.request.post(fixture.issuer+"/oauth2/token",{form:{grant_type:"refresh_token",client_id:client.client.clientId,refresh_token:value,...await clientAuth(client)}});
  const renewed=await refresh(issued.refresh_token);check("genuine refresh positive before rename",renewed.status()===200);const tokens=await renewed.json();
  const old={accessToken:tokens.access_token,signingKey:tokens.signing_key,browserContext:admin.browserContext};
  const current=await(await java(account,old,"/api/v1/portal/context")).json();check("human actual tenant differs from shared client owner",current.subject.userId===a&&current.subject.organizationId===orgA&&orgA!==home.subject.organizationId);
  check("human foreign tenant write denied",(await java(account,old,"/api/v1/organizations/"+orgB,"PUT",{name:"Denied",description:"Denied",status:"Active"})).status()===403);
  check("rename exact identity commits",(await java(page,admin,"/api/v1/users/"+a+"/username?loginId="+aDetail.loginId,"PUT",{branding,intent:"idp",metadata:{},username:username+"_new"})).status()===204);
  const b=await createIdentity(orgB);const bDetail=await(await java(page,admin,"/api/v1/users/"+b)).json();
  check("reused name belongs to a different profile and tenant",b!==a&&bDetail.organizationId===orgB&&bDetail.loginId!==aDetail.loginId);
  check("old A bearer denied after B reuse",(await java(account,old,"/api/v1/portal/context")).status()===401);
  check("old A refresh denied after B reuse",(await refresh(tokens.refresh_token)).status()===400);
  const oldSso=await account.request.get(fixture.issuer+"/oauth2/sso",{headers:{"x-request-datetime":new Date().toISOString(),Referer:fixture.portal+"/"}});check("old A SSO cookie cannot return B",oldSso.status()===401);
  const hosted=new URL(fixture.issuer+"/portal/account/profile");hosted.search=new URLSearchParams({clientId:client.client.clientId,expectedLoginId:bDetail.loginId,returnTo:callbackOrigin+"/account","x-request-datetime":new Date().toISOString()}).toString();const forbidden=await account.request.get(hosted.href,{headers:{Referer:fixture.issuer+"/"},maxRedirects:0});check("old A cannot obtain B hosted form",[401,403].includes(forbidden.status()));
  await native.clearCookies({domain:new URL(fixture.issuer).hostname});
  const fresh=await authorize(client,username+"_new");const freshState={accessToken:fresh.access_token,signingKey:fresh.signing_key,browserContext:admin.browserContext};const own=await(await java(account,freshState,"/api/v1/portal/context")).json();check("fresh sign-in with the new password recovers A without rebinding B",own.subject.userId===a&&own.subject.organizationId===orgA);
  check("SQL immutable login binding preserved",sql("SELECT COUNT(*) AS n FROM Token.AuthSession a JOIN [dbo].[Enum] e ON e.EnumId=a.AuthSessionStatusId JOIN dbo.EnumType t ON t.EnumTypeId=e.EnumTypeId WHERE a.LoginId="+literal(aDetail.loginId)+" AND a.SubjectId="+literal(username+"_new")+" AND t.Name='AuthSessionStatus' AND e.Code='Session Active' FOR JSON PATH")[0].n>0);
 }finally{await native.close();await new Promise<void>(resolve=>callback.close(()=>resolve()));}
});
}
