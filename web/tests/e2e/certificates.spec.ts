import { test, expect, type Page, type BrowserContext, type Locator, type Request } from "@playwright/test";
import { existsSync, mkdtempSync, chmodSync, readFileSync, writeFileSync, rmSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { createHmac, randomBytes, randomUUID } from "node:crypto";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createClient } from "redis";
process.env.PLAYWRIGHT_NO_COPY_PROMPT="1";
test.use({trace:"off",screenshot:"off",video:"off"});
const fixture=JSON.parse(readFileSync(process.env.PORTAL_E2E_CONFIG!,"utf8"));
const checks:{id:string;passed:boolean}[]=[];
const redis=createClient({url:"redis://127.0.0.1:16379/0",socket:{reconnectStrategy:false}});
function prior(path:string):{id:string;passed:boolean}[]{
 const raw=existsSync(path)?readFileSync(path,"utf8"):"";
 if(!raw.trim())return [];
 const parsed=JSON.parse(raw);
 return Array.isArray(parsed)?parsed:parsed.checks??[];
}
function check(id:string,passed:boolean){checks.push({id,passed});writeFileSync(process.env.PORTAL_SCREENSHOT_DIR+"/certificates-progress.json",JSON.stringify([...prior(process.env.PORTAL_SCREENSHOT_DIR+"/certificates-progress.json").filter(v=>v.id!==id),...checks]),{mode:0o600});expect(passed,id).toBe(true);}
test.beforeAll(()=>redis.connect());
test.afterAll(async()=>{await redis.quit();const merged=[...prior(process.env.PORTAL_E2E_REPORT!).filter(v=>!checks.some(c=>c.id===v.id)),...checks];writeFileSync(process.env.PORTAL_E2E_REPORT!,JSON.stringify({checks:merged,blocked:[]}),{mode:0o600});try{if(fixtures)rmSync(fixtures.dir,{recursive:true,force:true});}catch{}});
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
async function hydrated(area:Locator){await expect.poll(()=>area.evaluate(el=>Object.keys(el).some(k=>k.startsWith("__reactFiber$"))),{message:"form hydrated",timeout:15000}).toBe(true);}
async function createUser(page:Page,s:any,org:string,name:string,password:string){const r=await java(page,s,"/api/v1/users","POST",{firstName:"Owned",lastName:"Certificate",username:name,email:name+"@example.test",password:Buffer.from(password).toString("base64"),phoneNumber:"+19999999998",orgGuid:org,branding:"idp",syncFlag:false});check("owned certificate fixture user created",r.status()===201);return (await r.json()).id as string;}
async function makeOrg(page:Page,s:any,label:string){const r=await java(page,s,"/api/v1/organizations","POST",{name:label+randomUUID(),description:"Owned certificate proof"});check("owned certificate fixture organization created",r.status()===201);return (await r.json()).id as string;}
async function makeRole(page:Page,s:any,org:string,name:string,keys:string[]){const r=await java(page,s,"/api/v1/organizations/"+org+"/groups","POST",{name,description:"Owned certificate role",permissionKeys:keys});check("owned certificate fixture role created",r.status()===201);return (await r.json()).id as string;}
async function assign(page:Page,s:any,org:string,user:string,role:string|null){return java(page,s,"/api/v1/organizations/"+org+"/users/"+user+"/group","PUT",{groupId:role});}
function password(){return "P9!"+randomBytes(18).toString("hex")+"qZ";}

function multipart(fields:Record<string,string|undefined>,file:{filename:string;bytes:Buffer}){
 const boundary="----portal"+randomBytes(12).toString("hex");
 const parts:Buffer[]=[];
 for(const [key,value] of Object.entries(fields)){
  if(value===undefined)continue;
  parts.push(Buffer.from('--'+boundary+'\r\nContent-Disposition: form-data; name="'+key+'"\r\n\r\n'+value+'\r\n'));
 }
 parts.push(Buffer.from('--'+boundary+'\r\nContent-Disposition: form-data; name="certificate"; filename="'+file.filename+'"\r\nContent-Type: application/x-pem-file\r\n\r\n'),file.bytes,Buffer.from("\r\n--"+boundary+"--\r\n"));
 return {boundary,body:Buffer.concat(parts)};
}
async function javaUpload(page:Page,s:any,path:string,fields:Record<string,string|undefined>,file:{filename:string;bytes:Buffer}){
 const {boundary,body}=multipart(fields,file);
 return page.request.fetch(fixture.issuer+path,{method:"POST",headers:{...signed(s,""),"x-signature":createHmac("sha256",Buffer.from(s.signingKey,"base64")).update(body).digest("base64"),"Content-Type":"multipart/form-data; boundary="+boundary},data:body});
}
async function bffUpload(page:Page,path:string,fields:Record<string,string|undefined>,file:{filename:string;bytes:Buffer}){
 const state=await(await page.request.get(fixture.portal+"/api/session")).json();
 const {boundary,body}=multipart(fields,file);
 return page.request.fetch(fixture.portal+path,{method:"POST",headers:{Origin:fixture.portal,"x-csrf-token":state.csrfToken,"Content-Type":"multipart/form-data; boundary="+boundary},data:body});
}

type Upload={name:string;mimeType:string;buffer:Buffer};
type Fixtures={dir:string;orgPem:Buffer;publicPem:Buffer;encOrgPem:Buffer;encPassword:string;invalidPath:string;oversizedPath:string;noKeyPath:string;expiredPath:string;expiredPem:Buffer;expiryEnd:string;expirySameUtcDay:boolean;expiryDayTooShort:boolean;todayPem:Buffer;fingerprints:{org:string;enc:string;expired:string;today:string}};
let fixtures:Fixtures|undefined;
function utcStamp(date:Date){return date.toISOString().replace(/[-:T]/g,"").slice(0,14)+"Z";}
function fingerprintOf(path:string){const out=execFileSync("openssl",["x509","-in",path,"-noout","-fingerprint","-sha256"],{encoding:"utf8"}).trim();return out.slice(out.indexOf("=")+1).replaceAll(":","").toLowerCase();}
function notAfter(path:string){const out=execFileSync("openssl",["x509","-in",path,"-noout","-dateopt","iso_8601","-enddate"],{encoding:"utf8"}).trim();return out.slice(out.indexOf("=")+1);}
function buildFixtures():Fixtures{
 const dir=mkdtempSync(join(tmpdir(),"portal-f6-"));chmodSync(dir,0o700);
 const run=(args:string[])=>execFileSync("openssl",args,{cwd:dir,stdio:"ignore"});
 const encPassword=randomBytes(18).toString("hex");
 run(["req","-x509","-newkey","rsa:2048","-nodes","-keyout","key.pem","-out","cert.pem","-days","2","-subj","/CN=portal-f6-org"]);
 run(["req","-x509","-newkey","rsa:2048","-keyout","enc-key.pem","-out","enc-cert.pem","-days","2","-subj","/CN=portal-f6-encrypted","-passout","pass:"+encPassword]);
 const now=Date.now();
 const midnight=Date.UTC(new Date(now).getUTCFullYear(),new Date(now).getUTCMonth(),new Date(now).getUTCDate()+1);
 const room=Math.min(now+2*3600e3,midnight-120e3);
 const end=new Date(room>now+300e3?room:now+2*3600e3);
 const expirySameUtcDay=new Date(end).toISOString().slice(0,10)===new Date(now).toISOString().slice(0,10);
 const expiryDayTooShort=midnight-now<15*60e3;
 run(["req","-x509","-newkey","rsa:2048","-nodes","-keyout","today-key.pem","-out","today.pem","-subj","/CN=portal-f6-today","-not_before",utcStamp(new Date(now-60e3)),"-not_after",utcStamp(end)]);
 run(["req","-x509","-newkey","rsa:2048","-nodes","-keyout","expired-key.pem","-out","expired.pem","-subj","/CN=portal-f6-expired","-not_before","20200101000000Z","-not_after","20200102000000Z"]);
 writeFileSync(join(dir,"invalid.pem"),"-----BEGIN CERTIFICATE-----\nnot base64 at all\n-----END CERTIFICATE-----\n",{mode:0o600});
 writeFileSync(join(dir,"oversized.pem"),Buffer.alloc(1024*1024+1,0x41),{mode:0o600});
 return {
  dir,
  orgPem:Buffer.concat([readFileSync(join(dir,"cert.pem")),readFileSync(join(dir,"key.pem"))]),
  publicPem:readFileSync(join(dir,"cert.pem")),
  encOrgPem:Buffer.concat([readFileSync(join(dir,"enc-cert.pem")),readFileSync(join(dir,"enc-key.pem"))]),
  encPassword,
  invalidPath:join(dir,"invalid.pem"),
  oversizedPath:join(dir,"oversized.pem"),
  noKeyPath:join(dir,"cert.pem"),
  expiredPath:join(dir,"expired.pem"),
  expiredPem:readFileSync(join(dir,"expired.pem")),
  expiryEnd:notAfter(join(dir,"today.pem")),
  expirySameUtcDay,
  expiryDayTooShort,
  todayPem:readFileSync(join(dir,"today.pem")),
  fingerprints:{org:fingerprintOf(join(dir,"cert.pem")),enc:fingerprintOf(join(dir,"enc-cert.pem")),expired:fingerprintOf(join(dir,"expired.pem")),today:fingerprintOf(join(dir,"today.pem"))},
 };
}
function pemFixture(){fixtures??=buildFixtures();return fixtures;}

async function submitImport(page:Page,org:string,values:{name:string;type:string;password?:string;file:string|Upload},waitForResponse=true){
 await page.goto(fixture.portal+"/admin/organizations/"+org+"/certificates");
 await expect(page.getByRole("heading",{name:"Certificates",level:1})).toBeVisible();
 const form=page.getByRole("region",{name:"Import certificate",exact:true});await hydrated(form);
 await form.getByLabel("Certificate name",{exact:true}).fill(values.name);
 await form.getByRole("combobox",{name:"Certificate type"}).click();
 await page.getByRole("option",{name:values.type,exact:true}).click();
 if(values.password)await form.getByLabel("Private key password",{exact:true}).fill(values.password);
 await form.locator('input[type="file"]').setInputFiles(values.file);
 const endpoint=fixture.portal+"/api/admin/organizations/"+org+"/certificates";
 if(!waitForResponse){await form.getByRole("button",{name:"Import certificate",exact:true}).click();return null;}
 const response=page.waitForResponse(r=>r.url()===endpoint&&r.request().method()==="POST",{timeout:30000});
 await form.getByRole("button",{name:"Import certificate",exact:true}).click();
 return response;
}
async function importedId(page:Page){await page.waitForURL(/\/admin\/organizations\/[0-9a-f-]+\/certificates\/[0-9a-f-]{36}$/,{timeout:30000});return page.url().split("/").at(-1)!;}
function rowsOf(org:string){return sql("SELECT COUNT(*) AS n FROM Partner.Certificate c JOIN Partner.Organization o ON o.OrganizationId=c.OrganizationId WHERE o.RowGuid="+literal(org)+" FOR JSON PATH")[0].n;}
async function dd(page:Page,index:number){return (await page.locator("dl dd").nth(index).innerText()).trim();}
async function ddLines(page:Page,index:number){return (await page.locator("dl dd").nth(index).innerText()).split("\n").map(v=>v.trim()).filter(Boolean);}
function localText(page:Page,iso:string){return page.evaluate(value=>new Date(value).toLocaleString("en-GB",{timeZoneName:"short"}),iso);}

test("platform admin imports, reads, downloads and deactivates a genuine certificate",async({page,context})=>{
 test.setTimeout(600000);await login(page);const admin=await session(context);const org=await makeOrg(page,admin,"F6 lifecycle ");
 const fx=pemFixture();const name="F6 org cert "+randomUUID().slice(0,8);
 const response=await submitImport(page,org,{name,type:"Organization signing",password:fx.encPassword,file:{name:"org.pem",mimeType:"application/x-pem-file",buffer:fx.encOrgPem}});
 check("multipart import returned 201",(await response!).status()===201);
 const certId=await importedId(page);
 check("import navigated to the new certificate detail",/^[0-9a-f-]{36}$/.test(certId));
 await expect.poll(()=>page.getByRole("heading",{name,level:1}).isVisible(),{message:"detail heading",timeout:20000}).toBe(true);
 check("detail heading is the certificate name",(await page.getByRole("heading",{level:1}).innerText()).trim()===name);
 check("organization certificate is active",await dd(page,0)==="Active");
 check("detail fingerprint matches the uploaded certificate",await dd(page,2)===fx.fingerprints.enc);
 const encUntil=notAfter(join(fx.dir,"enc-cert.pem"));
 await expect.poll(async()=>(await ddLines(page,1)).includes("Local: "+await localText(page,encUntil)),{message:"validity local instant",timeout:20000}).toBe(true);
 const encValidity=await ddLines(page,1);
 check("validity shows the UTC X509 instant",encValidity.includes("UTC: "+new Date(encUntil).toISOString()));
 check("validity is rendered as the local instant of the UTC timestamp",encValidity.includes("Local: "+await localText(page,encUntil)));
 const html=await page.content();
 check("detail HTML omits private key material",!html.includes("PRIVATE KEY"));
 check("detail HTML omits the import password",!html.includes(fx.encPassword));
 const storage=await page.evaluate(()=>JSON.stringify({l:Object.entries(localStorage),s:Object.entries(sessionStorage)}));
 check("browser storage omits certificate secrets",!storage.includes(fx.encPassword)&&!storage.includes("PRIVATE KEY"));
 const state=JSON.stringify(await session(context));
 check("server session omits certificate secrets",!state.includes(fx.encPassword)&&!state.includes("PRIVATE KEY"));
 const detailJson=await(await page.request.get(fixture.portal+"/api/admin/organizations/"+org+"/certificates/"+certId)).text();
 check("detail response omits the private key and password",!detailJson.includes("PRIVATE KEY")&&!detailJson.includes(fx.encPassword)&&!detailJson.includes(fx.orgPem.toString("base64")));
 const download=await page.request.get(fixture.portal+"/api/admin/organizations/"+org+"/certificates/"+certId+"/download");
 const downloaded=Buffer.from(await download.body());
 const downloadPath=join(fx.dir,"download-"+certId+".pem");writeFileSync(downloadPath,downloaded,{mode:0o600});
 check("download is a no-store public certificate",download.status()===200&&String(download.headers()["content-type"]??"").startsWith("text/plain")&&String(download.headers()["cache-control"]??"").includes("no-store")&&downloaded.toString().includes("BEGIN CERTIFICATE")&&!downloaded.toString().includes("PRIVATE KEY"));
 check("downloaded PEM parses to the advertised fingerprint",fingerprintOf(downloadPath)===fx.fingerprints.enc);
 await page.goto(fixture.portal+"/admin/organizations/"+org+"/certificates");
 await expect.poll(()=>page.getByRole("link",{name}).isVisible(),{message:"certificate list link",timeout:20000}).toBe(true);
 check("certificate list links the imported record",await page.getByRole("link",{name}).getAttribute("href")==="/admin/organizations/"+org+"/certificates/"+certId);
 const row=page.getByRole("row").filter({hasText:name});
 check("list shows the certificate as active",(await row.innerText()).includes("Active"));
 await page.getByRole("link",{name}).click();
 await page.waitForURL(/\/certificates\/[0-9a-f-]{36}$/);
 await page.getByRole("button",{name:"Deactivate certificate",exact:true}).click();
 const dialog=page.getByRole("dialog");
 check("deactivation explains downstream impact without counts",/does not estimate how many credentials/.test(await dialog.innerText()));
 await dialog.getByRole("button",{name:"Deactivate certificate",exact:true}).click();
 await expect(page.getByText("Certificate deactivated. It can no longer be used for new signatures.")).toBeVisible({timeout:20000});
 check("deactivated certificate shows the inactive status",await dd(page,0)==="Inactive (deactivated)");
 check("deactivation removes the lifecycle control",await page.getByRole("button",{name:"Deactivate certificate",exact:true}).count()===0);
 check("deactivation persisted as inactive without deleting the record",sql("SELECT Status AS n FROM Partner.Certificate WHERE RowGuid="+literal(certId)+" FOR JSON PATH")[0].n===0);
 const todayName="F6 today cert "+randomUUID().slice(0,8);
 const todayResponse=await submitImport(page,org,{name:todayName,type:"Client public key",file:{name:"today.pem",mimeType:"application/x-pem-file",buffer:fx.todayPem}});
 check("later-today certificate imported",(await todayResponse!).status()===201);
 await importedId(page);
 check("later-today certificate is active",await dd(page,0)==="Active");
 await expect.poll(async()=>(await ddLines(page,1)).includes("Local: "+await localText(page,fx.expiryEnd)),{message:"later-today local expiry",timeout:20000}).toBe(true);
 const todayValidity=await ddLines(page,1);
 check("later-today expiry shows the UTC instant",todayValidity.includes("UTC: "+new Date(fx.expiryEnd).toISOString()));
 check("later-today UTC expiry renders in the browser timezone",todayValidity.includes("Local: "+await localText(page,fx.expiryEnd)));
 check("expiry fixture ends in the future",new Date(fx.expiryEnd).getTime()>Date.now());
 check("expiry fixture falls later today in UTC unless the UTC day is nearly over",fx.expirySameUtcDay||fx.expiryDayTooShort);
});

test("invalid, oversized, keyless and expired uploads fail closed or report expiry",async({page,context})=>{
 test.setTimeout(600000);await login(page);const admin=await session(context);const org=await makeOrg(page,admin,"F6 rejection ");
 const fx=pemFixture();const before=rowsOf(org);
 const supplied="Wrong-"+randomUUID().slice(0,8);
 const invalid=await submitImport(page,org,{name:"F6 invalid",type:"Organization signing",password:supplied,file:fx.invalidPath});
 check("invalid PEM rejected with 400",(await invalid!).status()===400);
 await expect.poll(()=>page.getByRole("alert").filter({hasText:"Check the supplied values."}).isVisible(),{message:"invalid PEM error alert",timeout:15000}).toBe(true);
 check("invalid PEM surfaces an actionable error",(await page.getByRole("alert").filter({hasText:"Check the supplied values."}).innerText()).includes("Check the supplied values."));
 check("rejected import clears the password field",(await page.locator('input[type="password"]').inputValue())==="");
 check("rejected import clears the chosen file",(await page.locator('input[type="file"]').evaluate(el=>((el as HTMLInputElement).files?.length??0)))===0&&await page.getByText("No file selected").isVisible());
 check("rejected import does not leave the password in the page",!(await page.content()).includes(supplied));
 check("invalid PEM did not create a certificate",rowsOf(org)===before);
 const keyless=await submitImport(page,org,{name:"F6 keyless",type:"Organization signing",password:"",file:fx.noKeyPath});
 check("organization import without a private key rejected with 400",(await keyless!).status()===400);
 check("keyless import stays on the list after the rejection",page.url()===fixture.portal+"/admin/organizations/"+org+"/certificates");
 check("keyless import did not create a certificate",rowsOf(org)===before);
 let posts=0;const listener=(request:Request)=>{if(request.method()==="POST"&&request.url()===fixture.portal+"/api/admin/organizations/"+org+"/certificates")posts++;};
 page.on("request",listener);
 await submitImport(page,org,{name:"F6 oversized",type:"Organization signing",password:"",file:fx.oversizedPath},false);
 await expect(page.getByText("Certificate files are limited to 1 MiB.")).toBeVisible({timeout:15000});
 page.off("request",listener);
 check("oversized file is rejected before any upload",posts===0&&rowsOf(org)===before);
 const expired=await submitImport(page,org,{name:"F6 expired",type:"Client public key",file:{name:"expired.pem",mimeType:"application/x-pem-file",buffer:fx.expiredPem}});
 check("expired certificate import rejected with 400",(await expired!).status()===400);
 await expect.poll(()=>page.getByRole("alert").filter({hasText:"Check the supplied values."}).isVisible(),{message:"expired certificate error alert",timeout:15000}).toBe(true);
 check("expired certificate surfaces an actionable error",(await page.getByRole("alert").filter({hasText:"Check the supplied values."}).innerText()).includes("Check the supplied values."));
 check("expired certificate import did not create a record",rowsOf(org)===before);
});

test("tenant certificate permissions and cross-organization isolation are enforced",async({page,context,browser})=>{
 test.setTimeout(600000);await login(page);const admin=await session(context);const fx=pemFixture();
 const org=await makeOrg(page,admin,"F6 tenant ");const foreign=await makeOrg(page,admin,"F6 foreign ");
 const role=await makeRole(page,admin,org,"F6 certificate operator "+randomUUID().slice(0,6),["idp-admin-cred-cert:create","idp-admin-cred-cert:read","idp-admin-cred-cert:delete"]);
 const name="f6_cert_"+randomUUID().replaceAll("-","");const secret=password();
 const operator=await createUser(page,admin,org,name,secret);
 check("tenant certificate operator assigned",(await assign(page,admin,org,operator,role)).status()===204);
 check("tenant certificate operator MFA disabled explicitly",(await java(page,admin,"/api/v1/users/"+operator+"/security-settings","PUT",{twoFactorEnabled:false})).status()===200);
 const orgCert="F6 tenant org key "+randomUUID().slice(0,8);
 const orgResponse=await submitImport(page,org,{name:orgCert,type:"Organization signing",password:"",file:{name:"org.pem",mimeType:"application/x-pem-file",buffer:fx.orgPem}});
 check("platform admin imported the tenant organization key",(await orgResponse!).status()===201);
 const orgCertId=await importedId(page);
 const tenant=await browser.newContext({locale:"en-US",userAgent:"PortalAcceptance/1.0",extraHTTPHeaders:{"Accept-Language":"en-US"},timezoneId:"Asia/Kolkata"});
 try{
  const own=await tenant.newPage();await login(own,name,secret);const s=await session(tenant);
  await own.goto(fixture.portal+"/admin/organizations/"+org+"/certificates");
  const tenantList=await own.request.get(fixture.portal+"/api/admin/organizations/"+org+"/certificates");
  check("tenant certificate list API is readable",tenantList.status()===200);
  await expect.poll(()=>own.getByRole("heading",{name:"Certificates",level:1}).isVisible(),{message:"tenant list heading",timeout:20000}).toBe(true);
  check("tenant certificate list is readable",(await own.getByRole("heading",{name:"Certificates",level:1}).innerText()).trim()==="Certificates");
  check("tenant sees the platform-imported organization key",await own.getByRole("link",{name:orgCert}).isVisible());
  check("tenant certificate import form is available for client keys",await own.getByRole("region",{name:"Import certificate",exact:true}).count()===1);
  check("tenant import form does not offer organization signing",!(await own.getByRole("region",{name:"Import certificate",exact:true}).innerText()).includes("Organization signing"));
  const publicName="F6 tenant public "+randomUUID().slice(0,8);
  const publicResponse=await submitImport(own,org,{name:publicName,type:"Client public key",file:{name:"public.pem",mimeType:"application/x-pem-file",buffer:fx.publicPem}});
  check("tenant client public key import allowed",(await publicResponse!).status()===201);
  const publicId=await importedId(own);
  const beforeDenial=rowsOf(org);
  const deniedBff=await bffUpload(own,"/api/admin/organizations/"+org+"/certificates",{name:"F6 tenant direct",type:"ORGANIZATION",password:""},{filename:"org.pem",bytes:fx.orgPem});
  check("direct BFF organization key import denied",deniedBff.status()===403);
  const deniedJava=await javaUpload(own,s,"/api/v1/organizations/"+org+"/certificates",{name:"F6 tenant java",type:"ORGANIZATION",password:""},{filename:"org.pem",bytes:fx.orgPem});
  check("direct Java organization key import denied for tenant",deniedJava.status()===403);
  check("denied tenant imports created no certificate",rowsOf(org)===beforeDenial);
  const delName="F6 tenant delete "+randomUUID().slice(0,8);
  const delResponse=await submitImport(own,org,{name:delName,type:"Client public key",file:{name:"public.pem",mimeType:"application/x-pem-file",buffer:fx.publicPem}});
  check("tenant imported a second client public key",(await delResponse!).status()===201);
  const delId=await importedId(own);
  const deleteOnly=await makeRole(page,admin,org,"F6 delete only "+randomUUID().slice(0,6),["idp-admin-cred-cert:delete"]);
  const deleterName="f6_deleter_"+randomUUID().replaceAll("-","");const deleterSecret=password();
  const deleter=await createUser(page,admin,org,deleterName,deleterSecret);
  check("tenant delete-only role assigned",(await assign(page,admin,org,deleter,deleteOnly)).status()===204);
  check("tenant delete-only MFA disabled explicitly",(await java(page,admin,"/api/v1/users/"+deleter+"/security-settings","PUT",{twoFactorEnabled:false})).status()===200);
  const third=await browser.newContext({locale:"en-US",userAgent:"PortalAcceptance/1.0",extraHTTPHeaders:{"Accept-Language":"en-US"},timezoneId:"Asia/Kolkata"});
  try{
   const del=await third.newPage();await login(del,deleterName,deleterSecret);
   check("delete-only actor cannot read the certificate",(await bff(del,"/api/admin/organizations/"+org+"/certificates/"+delId,"GET",{})).status()===403);
   const deactivated=await bff(del,"/api/admin/organizations/"+org+"/certificates/"+delId,"DELETE");
   const deactivatedBody=await deactivated.json();check("delete-only actor can deactivate the certificate",deactivated.status()===200&&deactivatedBody.updated===true);
   check("delete-only deactivation is stored as inactive",sql("SELECT Status AS n FROM Partner.Certificate WHERE RowGuid="+literal(delId)+" FOR JSON PATH")[0].n===0);
  }finally{await third.close();}
  check("tenant can deactivate its own client public key",(await bff(own,"/api/admin/organizations/"+org+"/certificates/"+publicId,"DELETE")).status()===200);
  check("tenant organization key deactivation denied by the BFF",(await bff(own,"/api/admin/organizations/"+org+"/certificates/"+orgCertId,"DELETE")).status()===403);
  check("tenant organization key deactivation denied by Java",(await java(own,s,"/api/v1/organizations/"+org+"/certificates/"+orgCertId,"DELETE")).status()===403);
  check("denied tenant deactivation left the organization key active",sql("SELECT Status AS n FROM Partner.Certificate WHERE RowGuid="+literal(orgCertId)+" FOR JSON PATH")[0].n===1);
  const foreignList=await java(own,s,"/api/v1/organizations/"+foreign+"/certificates");
  check("tenant cannot read a foreign organization certificate list",[403,404].includes(foreignList.status())&&!(await foreignList.text()).includes(orgCert));
  const foreignBff=await bff(own,"/api/admin/organizations/"+foreign+"/certificates","GET",{});
  check("tenant cannot read a foreign organization through the BFF",[403,404].includes(foreignBff.status())&&!(await foreignBff.text()).includes(orgCert));
 }finally{await tenant.close();}
 const crossDetail=await bff(page,"/api/admin/organizations/"+foreign+"/certificates/"+orgCertId,"GET",{});
 check("wrong target organization rejects the certificate read",[403,404].includes(crossDetail.status())&&!(await crossDetail.text()).includes(orgCert));
 const crossDownload=await page.request.get(fixture.portal+"/api/admin/organizations/"+foreign+"/certificates/"+orgCertId+"/download");
 check("wrong target organization rejects the download",[403,404].includes(crossDownload.status())&&!String(crossDownload.headers()["content-disposition"]??"").includes(orgCert));
});
