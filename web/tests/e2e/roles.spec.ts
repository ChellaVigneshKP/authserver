import { test, expect, type Page, type BrowserContext, type Locator } from "@playwright/test";
import { readFileSync, writeFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { createHmac, randomBytes, randomUUID } from "node:crypto";
import { createClient } from "redis";
import { mergeById, readChecks, writeReport } from "./report";
process.env.PLAYWRIGHT_NO_COPY_PROMPT="1";
test.use({trace:"off",screenshot:"off",video:"off"});
const fixture=JSON.parse(readFileSync(process.env.PORTAL_E2E_CONFIG!,"utf8"));
const checks:{id:string;passed:boolean}[]=[];
const redis=createClient({url:"redis://127.0.0.1:16379/0",socket:{reconnectStrategy:false}});
function check(id:string,passed:boolean){checks.push({id,passed});writeFileSync(process.env.PORTAL_SCREENSHOT_DIR+"/roles-progress.json",JSON.stringify(mergeById(readChecks(process.env.PORTAL_SCREENSHOT_DIR+"/roles-progress.json"),[{id,passed}],v=>v.id)),{mode:0o600});expect(passed,id).toBe(true);}
test.beforeAll(()=>redis.connect());
test.afterAll(async()=>{await redis.quit();writeReport(process.env.PORTAL_E2E_REPORT!,checks);});
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
function escapeRe(value:string){return value.replace(/[.*+?^${}()|[\]\\]/g,"\\$&");}
function memberOption(name:string){return new RegExp("^"+escapeRe(name)+" \\(\\d+ members\\)$");}
const writeSession="SET QUOTED_IDENTIFIER ON;SET ANSI_NULLS ON;SET ANSI_PADDING ON;SET ANSI_WARNINGS ON;SET ARITHABORT ON;SET CONCAT_NULL_YIELDS_NULL ON;SET NUMERIC_ROUNDABORT OFF;";
function write(query:string){try{return execFileSync("python3",["-c",`import sys,re;sys.path.insert(0,'scripts');from e2e_target import load_target,sql_text;c,b,d=load_target();assert re.fullmatch(r'AuthserverLifecycle_[0-9a-f]{32}',d);print(sql_text(c,d,sys.stdin.read(),scalar=True))`],{cwd:process.env.PORTAL_REPO_ROOT!,input:writeSession+query,encoding:"utf8",timeout:20000}).trim();}catch(error){const detail=(error as {stdout?:string;stderr?:string}).stdout||(error as {stderr?:string}).stderr||(error as Error).message;throw new Error("SQL write rejected: "+String(detail).split("\n").filter(Boolean).slice(0,2).join(" | ").slice(0,300));}}
const groupSelect=(role:string)=>"(SELECT OrganizationGroupId FROM Partner.OrganizationGroup WHERE RowGuid="+literal(role)+")";
function roleRow(org:string,role:string,clause="1=1"){return sql("SELECT COUNT(*) AS n FROM Partner.OrganizationGroup g JOIN Partner.Organization o ON o.OrganizationId=g.OrganizationId WHERE o.RowGuid="+literal(org)+" AND g.RowGuid="+literal(role)+" AND "+clause+" FOR JSON PATH")[0].n===1;}
function grants(role:string){return sql("SELECT PermissionKey AS k FROM Partner.OrganizationGroupPermission WHERE OrganizationGroupId="+groupSelect(role)+" AND Status=1 ORDER BY PermissionKey FOR JSON PATH").map((v:{k:string})=>v.k);}
function member(user:string,role:string){return sql("SELECT COUNT(*) AS n FROM Person.ProfileGroup pg JOIN Person.Profile p ON p.ProfileId=pg.ProfileId WHERE p.RowGuid="+literal(user)+" AND pg.OrganizationGroupId="+groupSelect(role)+" FOR JSON PATH")[0].n;}
function roleStatus(role:string){return sql("SELECT Status AS n FROM Partner.OrganizationGroup WHERE RowGuid="+literal(role)+" FOR JSON PATH")[0].n;}
function roleChecksum(role:string){return sql("SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) AS n FROM Partner.OrganizationGroup g WHERE g.RowGuid="+literal(role)+" OR g.OrganizationGroupId="+groupSelect(role)+" FOR JSON PATH")[0].n;}
function orgProfile(user:string){return sql("SELECT COUNT(*) AS n FROM Person.Profile p JOIN Person.ProfileOrganization po ON po.ProfileId=p.ProfileId AND po.Status=1 WHERE p.RowGuid="+literal(user)+" FOR JSON PATH")[0].n;}
async function hydrated(area:Locator){await expect.poll(()=>area.evaluate(el=>Object.keys(el).some(k=>k.startsWith("__reactFiber$"))),{message:"form hydrated",timeout:15000}).toBe(true);}
async function createUser(page:Page,s:any,org:string,name:string,password:string){const r=await java(page,s,"/api/v1/users","POST",{firstName:"Owned",lastName:"Role",username:name,email:name+"@example.test",password:Buffer.from(password).toString("base64"),phoneNumber:"+19999999998",orgGuid:org,branding:"idp",syncFlag:false});check("owned role fixture user created",r.status()===201);return (await r.json()).id as string;}
async function makeOrg(page:Page,s:any,label:string){const r=await java(page,s,"/api/v1/organizations","POST",{name:label+randomUUID(),description:"Owned role proof"});check("owned role fixture organization created",r.status()===201);return (await r.json()).id as string;}
async function makeRole(page:Page,s:any,org:string,name:string,keys:string[]){const r=await java(page,s,"/api/v1/organizations/"+org+"/groups","POST",{name,description:"Owned role definition",permissionKeys:keys});check("owned role fixture definition created",r.status()===201);return (await r.json()).id as string;}
async function assign(page:Page,s:any,org:string,user:string,role:string|null){return java(page,s,"/api/v1/organizations/"+org+"/users/"+user+"/group","PUT",{groupId:role});}
async function detail(page:Page,org:string,role:string){const r=await page.request.get(fixture.portal+"/api/admin/organizations/"+org+"/groups/"+role);check("role detail read "+r.status(),r.status()===200);return r.json();}
function password(){return "P9!"+randomBytes(18).toString("hex")+"qZ";}
async function choose(assign:Locator,page:Page,name:RegExp,notice:RegExp=/^Role assignment saved\.$/){try{await expect(async()=>{await assign.getByRole("combobox",{name:"Assigned role"}).click();await page.getByRole("option",{name}).click();await assign.getByRole("button",{name:"Save assignment",exact:true}).click();await expect(assign.getByRole("status")).toHaveText(notice);}).toPass({timeout:30000});}catch(error){const alerts=(await assign.getByRole("alert").allTextContents()).join(" / ").slice(0,200);throw new Error("role assignment UI failed; alerts="+JSON.stringify(alerts)+"; "+(error as Error).message.split("\n").slice(0,3).join(" | ").slice(0,300));}}

test("platform role definition, assignment and lifecycle persist through Java and SQL",async({page,context})=>{
 test.setTimeout(600000);await login(page);const admin=await session(context);
 const org=await makeOrg(page,admin,"F5 platform ");
 const roleName="F5 role "+randomUUID().slice(0,8);
 await page.goto(fixture.portal+"/admin/organizations/"+org+"/roles");
 await expect(page.getByRole("heading",{name:"Roles",level:1})).toBeVisible();
 const create=page.getByRole("region",{name:"Create role",exact:true});await hydrated(create);
 await create.getByLabel("Role name",{exact:true}).fill(roleName);
 await create.getByLabel("Description",{exact:true}).fill("Owned role definition");
 await create.getByRole("checkbox",{name:/idp-admin-user:read/}).check();
 await create.getByRole("button",{name:"Create role",exact:true}).click();
 await page.waitForURL(/\/roles\/[0-9a-f-]{36}$/,{timeout:30000});
 const role=page.url().split("/").pop()!;
 check("UI create persisted the definition with no builtin flag",roleRow(org,role,"g.GroupName="+literal(roleName)+" AND g.IsBuiltin=0"));
 check("UI create persisted exactly the selected catalog grant",JSON.stringify(grants(role))===JSON.stringify(["idp-admin-user:read"]));
 const storedRevision=sql("SELECT Revision AS n FROM Partner.OrganizationGroup WHERE RowGuid="+literal(role)+" FOR JSON PATH")[0].n;
 check("new role starts at revision 1",storedRevision===1);
 await expect(page.getByRole("heading",{name:roleName,level:1})).toBeVisible();
 check("new role renders its catalog description",await page.getByText("User: Read",{exact:false}).first().isVisible());
 await page.getByLabel("Description",{exact:true}).fill("Owned role definition edited");
 await page.getByRole("button",{name:"Save role",exact:true}).click();
 await expect(page.getByRole("status")).toHaveText("Role changes saved.");
 check("role revision advanced after save",roleRow(org,role,"g.Description=N'Owned role definition edited' AND g.Revision=2"));
 const username="f5_p_"+randomUUID().replaceAll("-","");const secret=password();
 const user=await createUser(page,admin,org,username,secret);
 await page.goto(fixture.portal+"/admin/organizations/"+org+"/users/"+user);
 const area=page.getByRole("region",{name:"Role assignment",exact:true});await hydrated(area);
 await choose(area,page,memberOption(roleName));
 check("UI assignment wrote the ProfileGroup membership",member(user,role)===1);
 await page.goto(fixture.portal+"/admin/organizations/"+org+"/roles/"+role);
 await page.getByRole("button",{name:"Deactivate role",exact:true}).click();
 await page.getByRole("dialog").getByRole("button",{name:"Deactivate role",exact:true}).click();
 await expect(page.getByText("This role still has assigned members. Remove those assignments before deactivating it.")).toBeVisible();
 check("assigned role deactivation is a real backend 409, not optimistic success",roleStatus(role)===1&&JSON.stringify(grants(role))===JSON.stringify(["idp-admin-user:read"]));
 await page.goto(fixture.portal+"/admin/organizations/"+org+"/users/"+user);
 const clear=page.getByRole("region",{name:"Role assignment",exact:true});await hydrated(clear);
 await choose(clear,page,/^No role$/);
 check("UI assignment removal cleared the ProfileGroup membership",member(user,role)===0);
 await page.goto(fixture.portal+"/admin/organizations/"+org+"/roles/"+role);
 await page.getByRole("button",{name:"Deactivate role",exact:true}).click();
 await page.getByRole("dialog").getByRole("button",{name:"Deactivate role",exact:true}).click();
 await page.waitForURL(fixture.portal+"/admin/organizations/"+org+"/roles");
 check("UI deactivation kept the record and set inactive",roleStatus(role)===0);
 const inactive=await page.request.get(fixture.portal+"/api/admin/organizations/"+org+"/roles/eligible");
 check("inactive role is no longer eligible for assignment",(await inactive.json()).items.every((v:{id:string})=>v.id!==role));
});

test("built-in definitions are immutable and foreign roles stay unavailable",async({page,context})=>{
 test.setTimeout(600000);await login(page);const admin=await session(context);
 const home=await(await java(page,admin,"/api/v1/portal/context")).json();const platform=home.subject.organizationId;
 const list=await(await page.request.get(fixture.portal+"/api/admin/organizations/"+platform+"/groups")).json();
 const builtin=list.items.find((v:{readOnly:boolean})=>v.readOnly);
 check("platform organization exposes a built-in role",Boolean(builtin));
 const role=builtin.id as string;
 await page.goto(fixture.portal+"/admin/organizations/"+platform+"/roles/"+role);
 await expect(page.getByText("This built-in role is read-only. Its grants are fixed by the identity service.")).toBeVisible();
 check("built-in role renders no save or lifecycle control",await page.getByRole("button",{name:"Save role",exact:true}).count()===0&&await page.getByRole("button",{name:"Deactivate role",exact:true}).count()===0);
 const current=await detail(page,platform,role);
 const before=roleChecksum(role);
 const put=await java(page,admin,"/api/v1/organizations/"+platform+"/groups/"+role,"PUT",{name:current.name,description:"Denied",permissionKeys:current.permissionKeys,revision:current.revision});
 check("direct Java built-in update rejected",put.status()===409);
 const del=await java(page,admin,"/api/v1/organizations/"+platform+"/groups/"+role,"DELETE");
 check("direct Java built-in deactivation rejected",del.status()===409);
 check("built-in definition SQL unchanged by denied writes",before===roleChecksum(role));
 const org=await makeOrg(page,admin,"F5 a "),foreignOrg=await makeOrg(page,admin,"F5 b ");
 const foreign=await makeRole(page,admin,foreignOrg,"F5 foreign "+randomUUID().slice(0,8),["idp-admin-org:read"]);
 const crossBff=await page.request.get(fixture.portal+"/api/admin/organizations/"+foreignOrg+"/groups/"+foreign);
 check("same-organization role read is available for the control case",crossBff.status()===200);
 const deniedBff=await page.request.get(fixture.portal+"/api/admin/organizations/"+org+"/groups/"+foreign);
 check("foreign organization role read denied by the BFF",[403,404].includes(deniedBff.status()));
 const deniedBody=await deniedBff.text();
 check("foreign denial does not leak the role name",!deniedBody.includes("F5 foreign"));
 const deniedJava=await java(page,admin,"/api/v1/organizations/"+org+"/groups/"+foreign);
 check("foreign organization role read denied by Java",[403,404].includes(deniedJava.status()));
 const name="f5_v_"+randomUUID().replaceAll("-","");
 const target=await createUser(page,admin,org,name,password());
 check("foreign role assignment denied",(await assign(page,admin,org,target,foreign)).status()===404);
 check("foreign role assignment SQL unchanged",member(target,foreign)===0);
});

test("tenant reads, eligibility, grant ceiling and live revocation are enforced by Java",async({page,context,browser})=>{
 test.setTimeout(600000);await login(page);const admin=await session(context);
 const org=await makeOrg(page,admin,"F5 tenant ");
 const operatorRoleName="F5 tenant manager "+randomUUID().slice(0,6);
 const operatorRole=await makeRole(page,admin,org,operatorRoleName,["idp-admin-group:assign","idp-admin-group:read","idp-admin-org:read","idp-admin-user:read","idp-admin-user:update"]);
 const highRole=await makeRole(page,admin,org,"F5 tenant high "+randomUUID().slice(0,6),["idp-admin-org:read","idp-admin-user:delete"]);
 const name="f5_t_"+randomUUID().replaceAll("-","");const secret=password();
 const operator=await createUser(page,admin,org,name,secret);
 const target=await createUser(page,admin,org,"f5_target_"+randomUUID().replaceAll("-",""),password());
 check("tenant fixture operator assigned",(await assign(page,admin,org,operator,operatorRole)).status()===204);
 check("owned tenant operator MFA disabled explicitly",(await java(page,admin,"/api/v1/users/"+operator+"/security-settings","PUT",{twoFactorEnabled:false})).status()===200);
 const tenant=await browser.newContext({locale:"en-US",userAgent:"PortalAcceptance/1.0",extraHTTPHeaders:{"Accept-Language":"en-US"},timezoneId:"Asia/Kolkata"});
 try{const own=await tenant.newPage();await login(own,name,secret);const s=await session(tenant);
 await own.goto(fixture.portal+"/admin/organizations/"+org+"/roles");
 await expect(own.getByRole("heading",{name:"Roles",level:1})).toBeVisible();
 check("tenant role list is readable",await own.getByText("F5 tenant high",{exact:false}).count()>0);
 check("tenant has no role definition form",await own.getByRole("region",{name:"Create role",exact:true}).count()===0);
 const eligible=await(await own.request.get(fixture.portal+"/api/admin/organizations/"+org+"/roles/eligible")).json();
 const ids=(eligible.items as {id:string}[]).map(v=>v.id);
 check("eligible list includes the within-ceiling role",ids.includes(operatorRole));
 check("eligible list excludes the role above the ceiling",!ids.includes(highRole));
 check("tenant ineligible assignment denied by the BFF",(await bff(own,"/api/admin/organizations/"+org+"/users/"+target+"/group","PUT",{groupId:highRole})).status()===403);
 check("tenant ineligible assignment denied by Java",(await assign(own,s,org,target,highRole)).status()===403);
 check("tenant ineligible assignment SQL unchanged",member(target,highRole)===0);
 {const roleKeys=(await detail(page,org,operatorRole)).permissionKeys as string[];
 const ownCtx=await(await java(own,s,"/api/v1/portal/context")).json();
 const reloaded=(sql("SELECT DISTINCT p.PermissionKey AS k FROM Person.Profile a JOIN Person.ProfileOrganization m ON m.ProfileId=a.ProfileId AND m.Status=1 JOIN Partner.Organization o ON o.OrganizationId=m.OrganizationId AND o.Status=1 JOIN Person.ProfileGroup g ON g.ProfileId=a.ProfileId JOIN Partner.OrganizationGroup r ON r.OrganizationGroupId=g.OrganizationGroupId AND r.OrganizationId=o.OrganizationId AND r.Status=1 JOIN Partner.OrganizationGroupPermission p ON p.OrganizationGroupId=r.OrganizationGroupId AND p.OrganizationId=o.OrganizationId AND p.Status=1 WHERE a.RowGuid="+literal(operator)+" AND o.RowGuid="+literal(org)+" AND EXISTS(SELECT 1 FROM Person.ActiveManagementIdentity v WHERE v.ProfileId=a.ProfileId AND v.OrganizationId=o.OrganizationId) AND (o.OrganizationId=1 OR NOT EXISTS(SELECT 1 FROM dbo.PermissionCatalog c WHERE c.PermissionKey=p.PermissionKey COLLATE Latin1_General_100_BIN2 AND c.PlatformOnly=1)) FOR JSON PATH") as {k:string}[]).map(v=>v.k);
 check("tenant token grants match the stored role definition",ownCtx.subject.organizationId===org&&roleKeys.every(k=>ownCtx.permissions.includes(k)&&reloaded.includes(k))&&reloaded.every(k=>ownCtx.permissions.includes(k)));}
 await own.goto(fixture.portal+"/admin/organizations/"+org+"/users/"+target);
 const area=own.getByRole("region",{name:"Role assignment",exact:true});await hydrated(area);
 await choose(area,own,memberOption(operatorRoleName),/^Role assignment saved\. This account now requires administrator permissions/);
 check("tenant within-ceiling assignment persisted",member(target,operatorRole)===1);
 check("assigned tenant target now requires administrator keys",(await bff(own,"/api/admin/organizations/"+org+"/users/"+target,"GET")).status()===403);
 check("platform can still read the assigned tenant target",(await bff(page,"/api/admin/organizations/"+org+"/users/"+target,"GET")).status()===200);
 await own.goto(fixture.portal+"/admin/organizations/"+org+"/roles/"+highRole);
 await expect(own.getByRole("heading",{name:/^F5 tenant high /})).toBeVisible({timeout:30000});
 check("grant above the ceiling is displayed as a locked current grant",await own.getByRole("checkbox",{name:/idp-admin-user:delete.*current grant/}).count()===1);
 check("within-ceiling grant is not marked as above the ceiling",await own.getByRole("checkbox",{name:/^idp-admin-org:read/}).count()===1&&await own.getByRole("checkbox",{name:/idp-admin-org:read.*current grant/}).count()===0);
 check("tenant sees the stored key when permission descriptions are unavailable",await own.getByText(/Permission descriptions require platform administration access/).isVisible());
 check("grant above the ceiling cannot be edited by the tenant",await own.getByRole("checkbox",{name:/idp-admin-user:delete/}).isDisabled());
 for(const [method,path] of [["POST","/api/v1/organizations/"+org+"/groups"],["PUT","/api/v1/organizations/"+org+"/groups/"+highRole],["DELETE","/api/v1/organizations/"+org+"/groups/"+highRole]] as const)
  check("direct Java role definition "+method+" denied for tenant",(await java(own,s,path,method,method==="DELETE"?undefined:{name:"Denied",description:"Denied",permissionKeys:["idp-admin-org:read"],revision:1})).status()===403);
 check("tenant permission catalog read denied",(await java(own,s,"/api/v1/portal/permissions")).status()===403);
 const current=await detail(page,org,operatorRole);
 check("platform ceiling change saves",(await bff(page,"/api/admin/organizations/"+org+"/groups/"+operatorRole,"PUT",{name:current.name,description:current.description??"",permissionKeys:current.permissionKeys.filter((k:string)=>k!=="idp-admin-user:update"),revision:current.revision})).status()===200);
 check("live grant removal committed",!grants(operatorRole).includes("idp-admin-user:update"));
 const plain=await createUser(page,admin,org,"f5_plain_"+randomUUID().replaceAll("-",""),password());
 const snapshot=()=>JSON.stringify(sql("SELECT (SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM Person.Profile WHERE RowGuid="+literal(plain)+") AS p FOR JSON PATH"));
 const before=snapshot();
 check("already logged-in tab loses the next protected BFF action",(await bff(own,"/api/admin/organizations/"+org+"/users/"+plain+"/email","PUT",{email:"revoked@example.test"})).status()===403);
 check("already logged-in tab loses the next protected Java action",(await java(own,s,"/api/v1/users/"+plain+"/email","PUT",{email:"revoked@example.test",branding:"idp",intent:"idp",metadata:{}})).status()===403);
 check("revoked action left SQL unchanged",before===snapshot());
 const restored=await detail(page,org,operatorRole);
 check("platform ceiling restore saves",(await bff(page,"/api/admin/organizations/"+org+"/groups/"+operatorRole,"PUT",{name:restored.name,description:restored.description??"",permissionKeys:[...restored.permissionKeys,"idp-admin-user:update"],revision:restored.revision})).status()===200);
 check("restored grant lets the same session act again",(await bff(own,"/api/admin/organizations/"+org+"/users/"+plain+"/email","PUT",{email:"restored@example.test"})).status()===200);
 }finally{await tenant.close();}
});

test("stale revisions, ambiguous membership and metadata cannot grant authority",async({page,context,browser})=>{
 test.setTimeout(600000);await login(page);const admin=await session(context);
 const org=await makeOrg(page,admin,"F5 stale ");
 const role=await makeRole(page,admin,org,"F5 stale "+randomUUID().slice(0,6),["idp-admin-org:read","idp-admin-user:read"]);
 const first=await context.newPage();const second=await context.newPage();
 await first.goto(fixture.portal+"/admin/organizations/"+org+"/roles/"+role);
 await second.goto(fixture.portal+"/admin/organizations/"+org+"/roles/"+role);
 await expect(first.getByRole("heading",{name:/^F5 stale /})).toBeVisible({timeout:20000});
 await expect(second.getByRole("heading",{name:/^F5 stale /})).toBeVisible({timeout:20000});
 await expect(first.getByText("Revision 1",{exact:true})).toBeVisible({timeout:20000});
 await expect(second.getByText("Revision 1",{exact:true})).toBeVisible({timeout:20000});
 check("both tabs render revision 1",await first.getByText("Revision 1",{exact:true}).isVisible()&&await second.getByText("Revision 1",{exact:true}).isVisible());
 await first.getByLabel("Description",{exact:true}).fill("First tab edit");
 await first.getByRole("button",{name:"Save role",exact:true}).click();
 await expect(first.getByRole("status")).toHaveText("Role changes saved.");
 await second.getByLabel("Description",{exact:true}).fill("Second tab edit");
 await second.getByRole("button",{name:"Save role",exact:true}).click();
 await expect(second.getByText("The role changed or is protected by a backend rule. No update was re-sent.")).toBeVisible();
 check("stale tab cannot overwrite the newer revision",roleRow(org,role,"g.Description=N'First tab edit' AND g.Revision=2"));
 await second.getByRole("button",{name:"Read current role",exact:true}).click();
 await expect(second.getByRole("status")).toHaveText("Current role loaded.");
 check("reload shows the current revision without re-sending",await second.getByText("Revision 2",{exact:true}).isVisible()&&await second.getByLabel("Description",{exact:true}).inputValue()==="First tab edit");
 const ambiguous=await createUser(page,admin,org,"f5_amb_"+randomUUID().replaceAll("-",""),password());
 const secondOrg=await makeOrg(page,admin,"F5 ambiguous ");
 write("INSERT INTO Person.ProfileOrganization(ProfileId,OrganizationId,Status) SELECT p.ProfileId,o.OrganizationId,1 FROM Person.Profile p CROSS JOIN Partner.Organization o WHERE p.RowGuid="+literal(ambiguous)+" AND o.RowGuid="+literal(secondOrg)+";");
 check("ambiguous fixture has two active organizations",orgProfile(ambiguous)===2);
 const ambiguousBody=await(await bff(page,"/api/admin/organizations/"+org+"/users/"+ambiguous+"/group","PUT",{groupId:role})).json();
 check("ambiguous membership surfaces the backend review code",ambiguousBody.code==="MEMBERSHIP_REQUIRES_REVIEW");
 check("ambiguous membership assignment denied by Java",(await assign(page,admin,org,ambiguous,role)).status()===409);
 check("ambiguous membership assignment SQL unchanged",member(ambiguous,role)===0);
 const metaName="f5_meta_"+randomUUID().replaceAll("-","");const metaSecret=password();
 const meta=await createUser(page,admin,org,metaName,metaSecret);
 check("owned metadata fixture MFA disabled explicitly",(await java(page,admin,"/api/v1/users/"+meta+"/security-settings","PUT",{twoFactorEnabled:false})).status()===200);
 write("INSERT INTO Person.Metadata(ProfileId,[Key],[Value],SystemManaged) SELECT ProfileId,[Key],'',1 FROM Person.Profile CROSS APPLY (SELECT 'permissions' AS [Key] UNION ALL SELECT 'groupId') k WHERE RowGuid="+literal(meta)+";");
 check("owned system-managed metadata fixture seeded",sql("SELECT COUNT(*) AS n FROM Person.Metadata m JOIN Person.Profile p ON p.ProfileId=m.ProfileId WHERE p.RowGuid="+literal(meta)+" AND m.SystemManaged=1 AND m.[Key] IN ('permissions','groupId') FOR JSON PATH")[0].n===2);
 const before=JSON.stringify(sql("SELECT (SELECT COUNT(*) FROM Person.ProfileGroup pg JOIN Person.Profile p ON p.ProfileId=pg.ProfileId WHERE p.RowGuid="+literal(meta)+") AS groups,(SELECT COUNT(*) FROM Person.ProfileOrganization po JOIN Person.Profile p ON p.ProfileId=po.ProfileId WHERE p.RowGuid="+literal(meta)+" AND po.Status=1) AS orgs FOR JSON PATH"));
 check("metadata write accepted",(await bff(page,"/api/admin/organizations/"+org+"/users/"+meta+"/metadata","PUT",{metaData:{permissions:"idp-admin-group:create,idp-admin-org:switch",groupId:role}})).status()===200);
 check("metadata never grants a role or membership",before===JSON.stringify(sql("SELECT (SELECT COUNT(*) FROM Person.ProfileGroup pg JOIN Person.Profile p ON p.ProfileId=pg.ProfileId WHERE p.RowGuid="+literal(meta)+") AS groups,(SELECT COUNT(*) FROM Person.ProfileOrganization po JOIN Person.Profile p ON p.ProfileId=po.ProfileId WHERE p.RowGuid="+literal(meta)+" AND po.Status=1) AS orgs FOR JSON PATH")));
 check("system-managed metadata values are never overwritten by the write",sql("SELECT COUNT(*) AS n FROM Person.Metadata m JOIN Person.Profile p ON p.ProfileId=m.ProfileId WHERE p.RowGuid="+literal(meta)+" AND m.SystemManaged=1 AND m.[Key] IN ('permissions','groupId') AND m.[Value]='' FOR JSON PATH")[0].n===2);
 check("role member count ignores metadata",sql("SELECT MemberCount AS n FROM (SELECT COUNT(DISTINCT pg.ProfileId) AS MemberCount FROM Person.ProfileGroup pg WHERE pg.OrganizationGroupId="+groupSelect(role)+") v FOR JSON PATH")[0].n===0);
 const personal=await browser.newContext({locale:"en-US",userAgent:"PortalAcceptance/1.0",extraHTTPHeaders:{"Accept-Language":"en-US"},timezoneId:"Asia/Kolkata"});
 try{const own=await personal.newPage();await login(own,metaName,metaSecret);const s=await session(personal);
  check("metadata user cannot create role definitions",(await bff(own,"/api/admin/organizations/"+org+"/groups","POST",{name:"Metadata escalation",description:"",permissionKeys:["idp-admin-org:read"]})).status()===403);
  const held=await(await java(own,s,"/api/v1/portal/context")).json();
  check("metadata user holds no management permissions",Array.isArray(held.permissions)&&held.permissions.length===0);
 }finally{await personal.close();}
 await second.close();await first.close();
});
