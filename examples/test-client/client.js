const $ = id => document.getElementById(id);
const encode = bytes => btoa(String.fromCharCode(...bytes)).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');
const utf8base64 = value => btoa(String.fromCharCode(...new TextEncoder().encode(value)));
let config, tokens = JSON.parse(sessionStorage.getItem('auth-test-tokens') || 'null');
const log = (action,status) => {const row=document.createElement('li');row.textContent=action;const state=document.createElement('span');state.textContent=String(status);row.append(state);$('activity').prepend(row);};
const show = message => {$('message').textContent=message;};
const render = () => {const active=!!tokens?.access_token;$('session-state').textContent=active?'Signed in':'Signed out';for(const id of ['userinfo','profile','password','sign-out'])$(id).disabled=!active;$('sign-in').disabled=active;};
const request = async (path,options={}) => {
  const response=await fetch(config.base+path,{...options,headers:{'x-request-datetime':new Date().toISOString(),...(tokens?.access_token?{Authorization:'Bearer '+tokens.access_token}:{}),...options.headers}});
  log((options.method || 'GET')+' '+path,response.status);
  if(!response.ok)throw Error('Request failed with HTTP '+response.status+'.');
  return response.json();
};
async function userinfo(){const identity=await request('/userinfo');const safe={};for(const key of ['sub','name','given_name','family_name','email','preferred_username','organization','org_id'])if(identity[key]!==undefined)safe[key]=identity[key];$('identity').textContent=JSON.stringify(safe,null,2);show('Account information received from the auth server.');}
async function signIn(){
  const state=crypto.randomUUID(),verifier=encode(crypto.getRandomValues(new Uint8Array(48)));
  const challenge=encode(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(verifier))));
  sessionStorage.setItem('auth-test-pkce',JSON.stringify({state,verifier}));
  const query=new URLSearchParams({client_id:config.client_id,redirect_uri:config.callback,response_type:'code',scope:'openid profile email metadata',state,code_challenge:challenge,code_challenge_method:'S256',branding:'idp','x-request-datetime':new Date().toISOString()});
  location.assign(config.base+'/oauth2/authorize?'+query);
}
function changeForm(name){const p=utf8base64(new URLSearchParams({surl:config.callback,mt:JSON.stringify({idp:[],unite:[]}),'x-request-datetime':new Date().toISOString()}).toString());location.assign(config.base+'/user/'+name+'?'+new URLSearchParams({p}));}
async function signOut(){const previous=tokens;tokens=null;sessionStorage.removeItem('auth-test-tokens');sessionStorage.setItem('auth-test-logout','true');render();location.assign(config.base+'/oauth2/connect/logout?'+new URLSearchParams({id_token_hint:previous.id_token,client_id:config.client_id,post_logout_redirect_uri:config.callback}));}
const action = fn => async () => {try{await fn();}catch(error){show(error.message);log('Action failed','Error');}};
try{
  config=await fetch('/config.json').then(r=>{if(!r.ok)throw Error('Start the local sample server first.');return r.json();});
  $('issuer').textContent=config.base;$('client').textContent=config.client_id;
  if(config.inbox_url){$('provider-description').textContent='Local MFA simulation is configured. Request a code during sign-in and read it from the local inbox. This tests application integration; it does not verify SecureAuth, Touch ID or real email delivery.';$('provider-inbox').href=config.inbox_url;$('provider-inbox').hidden=false;}
  const query=new URLSearchParams(location.search);history.replaceState(null,'',location.pathname);
  if(query.has('code') || query.has('error')){
    const saved=JSON.parse(sessionStorage.getItem('auth-test-pkce') || 'null');sessionStorage.removeItem('auth-test-pkce');
    if(!saved || query.get('state')!==saved.state)throw Error('Authorization state did not match. Start a fresh sign-in.');
    if(query.has('error'))throw Error('Authorization was rejected. Start a fresh sign-in.');
    tokens=await request('/oauth2/token',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'authorization_code',client_id:config.client_id,redirect_uri:config.callback,code:query.get('code'),code_verifier:saved.verifier})});
    sessionStorage.setItem('auth-test-tokens',JSON.stringify(tokens));render();await userinfo();
  }else if(sessionStorage.getItem('auth-test-logout')){sessionStorage.removeItem('auth-test-logout');$('identity').textContent='Session cleared.';show('Signed out.');log('Logout callback','Complete');}
  else if(query.has('p')){const outcome=atob(query.get('p'));if(outcome.includes('errors.codes='))throw Error('The auth server rejected the account change.');log('Account change callback','Complete');if(tokens)await userinfo();}
  else show(tokens?'Session restored in this tab.':'Ready. Sign in with a local development account.');
}catch(error){show(error.message);log('Client initialization','Error');}
render();
$('sign-in').onclick=action(signIn);$('userinfo').onclick=action(userinfo);$('profile').onclick=action(()=>changeForm('change-profile'));$('password').onclick=action(()=>changeForm('change-password'));$('sign-out').onclick=action(signOut);$('recovery').onclick=action(()=>location.assign(config.base+'/forgot-password?'+new URLSearchParams({cid:config.client_id})));
