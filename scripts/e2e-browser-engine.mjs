// Real frontend + real auth server. No network interception. Secrets arrive only over stdin.
import {createRequire} from 'node:module';
import {readFileSync} from 'node:fs';
const require=createRequire(new URL('../.local/browser/package.json',import.meta.url));
const {chromium}=require('playwright');
const config=JSON.parse(readFileSync(0,'utf8'));
const report={suite:'real Chrome and sample frontend',assertions:[],network_observations:[],failures:[],page_errors:0};
const verify=(id,value)=>{report.assertions.push({id,passed:!!value});if(!value)throw Error(id);};
let browser;
try{
  browser=await chromium.launch({channel:process.env.AUTH_E2E_BROWSER_CHANNEL || 'chrome',headless:true});
  report.browser_version=browser.version();
  const context=await browser.newContext({userAgent:'AuthserverAcceptance/1.0',locale:'en-US'});
  const page=await context.newPage();
  page.on('pageerror',()=>{report.page_errors++;});
  page.on('response',response=>{const url=new URL(response.url());if([new URL(config.base).origin,new URL(config.callback).origin].includes(url.origin))report.network_observations.push({method:response.request().method(),path:url.pathname,status:response.status()});});
  const ready=async()=>{await page.waitForFunction(()=>document.querySelector('#client')?.textContent && document.querySelector('#client').textContent!=='—');};
  const signedIn=async()=>{await page.waitForFunction(()=>document.querySelector('#session-state')?.textContent==='Signed in');await page.waitForFunction(()=>document.querySelector('#message')?.textContent==='Account information received from the auth server.');};
  const login=async password=>{
    await ready();await page.getByRole('button',{name:'Sign in',exact:true}).click();
    await page.locator('#username').fill(config.username);await page.locator('#password').fill(password);
    await page.getByRole('button',{name:'Sign in',exact:true}).click();await signedIn();
    verify('frontend browser login completes PKCE exchange and userinfo',await page.locator('#activity').innerText().then(t=>t.includes('/oauth2/token')&&t.includes('/userinfo')));
    verify('frontend removes authorization code from address bar',!new URL(page.url()).searchParams.has('code'));
  };
  await page.goto(config.callback);await login(config.password);
  await page.getByRole('button',{name:'Change profile',exact:true}).click();
  await page.locator('form[data-signed-profile]').waitFor();
  for(const [field,value] of Object.entries(config.profile))await page.locator('#'+field).fill(value);
  await page.getByRole('button',{name:'Update profile',exact:true}).click();await signedIn();
  verify('real WebCrypto profile form returns successful callback',(await page.locator('#activity').innerText()).includes('Account change callback'));
  await page.getByRole('button',{name:'Change password',exact:true}).click();
  await page.locator('#currentPassword').fill(config.password);await page.locator('#newPassword').fill(config.new_password);await page.locator('#confirmPassword').fill(config.new_password+'x');
  let posted=0;const observer=request=>{if(request.method()==='POST'&&new URL(request.url()).pathname.endsWith('/user/change-password'))posted++;};page.on('request',observer);
  await page.getByRole('button',{name:'Change password',exact:true}).click();await page.locator('[data-form-error]:visible').waitFor();
  verify('password mismatch displays browser validation',await page.locator('[data-form-error]').innerText()==='The passwords do not match.');
  verify('password mismatch sends no password POST',posted===0);
  verify('failed validation preserves password for retry',await page.locator('#newPassword').inputValue()===config.new_password);page.off('request',observer);
  await page.locator('#confirmPassword').fill(config.new_password);await page.getByRole('button',{name:'Change password',exact:true}).click();await signedIn();
  verify('real WebCrypto password form returns successful callback',(await page.locator('#activity').innerText()).includes('Account change callback'));
  const access=await page.evaluate(()=>JSON.parse(sessionStorage.getItem('auth-test-tokens')).access_token);
  await page.getByRole('button',{name:'Sign out',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#message')?.textContent==='Signed out.');
  verify('frontend logout clears token storage',await page.evaluate(()=>sessionStorage.getItem('auth-test-tokens')===null));
  const rejected=await context.request.get(config.base+'/userinfo',{headers:{'x-request-datetime':new Date().toISOString(),Authorization:'Bearer '+access}});
  verify('frontend logout invalidates bearer token at auth server',rejected.status()===401);
  await context.clearCookies();await login(config.new_password);
  verify('fresh browser session accepts changed password',await page.locator('#session-state').innerText()==='Signed in');
  verify('frontend never displays raw access or identity tokens',await page.locator('body').innerText().then(t=>!t.includes(access)&&!t.includes('eyJhbG')));
  verify('no uncaught browser JavaScript errors',report.page_errors===0);
  verify('browser requests produce no HTTP 500s',report.network_observations.every(r=>r.status<500));
  // Capture only this synthetic account's sample UI, with no credentials or tokens visible.
  if(config.screenshot)await page.screenshot({path:config.screenshot,fullPage:true});
  await context.close();
}catch(error){report.failures.push({type:error.name || 'Error',last_assertion:report.assertions.at(-1)?.id || 'browser initialization',message:String(error.message || 'Browser failed').split('\n')[0].replace(/https?:\/\/[^\s"']+/g,value=>{try{const u=new URL(value);return u.origin+u.pathname;}catch{return '[URL redacted]';}})});
}finally{if(browser)await browser.close();process.stdout.write(JSON.stringify(report));}
if(report.failures.length)process.exitCode=1;
