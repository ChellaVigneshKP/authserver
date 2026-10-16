#!/usr/bin/env python3
"""Real HTTP/SQL application flows against explicitly synthetic local provider contracts."""
import base64
from datetime import datetime, timezone
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import re
import runpy
import secrets
import subprocess
import urllib.error
import urllib.parse
import urllib.request

ROOT=Path(__file__).resolve().parent.parent
LAB=ROOT/'.local/providers'
state=json.loads((LAB/'state.json').read_text())
os.environ.update(AUTH_E2E_CONFIG=str(LAB/'dev.json'),AUTH_E2E_BASE=state['app_base'],AUTH_E2E_DB=state['database'])
BrowserFlows=runpy.run_path(str(ROOT/'scripts/e2e-browser-flows.py'))['BrowserFlows']
NoRedirect=runpy.run_path(str(ROOT/'scripts/e2e-local.py'))['NoRedirect']


class LocalFlows(BrowserFlows):
    def __init__(self):
        super().__init__()
        self.report['suite']='isolated local provider simulation; real HTTP and SQL application flows'
        self.report['provider_simulation']=True
        self.report['qualification']='Synthetic local contracts only; no SecureAuth, production biometric, legacy crypto or Touch ID certification'
        self.provider=json.loads((LAB/'provider.json').read_text())
        self.report['provider_checks']=[]

    def control(self,path,body=None):
        request=urllib.request.Request(state['provider_base']+path,data=None if body is None else json.dumps(body).encode(),headers={'Authorization':'Bearer '+self.provider['control_secret'],'Content-Type':'application/json'})
        with urllib.request.urlopen(request,timeout=10) as r:return json.load(r)

    def csrf(self,body):
        match=re.search(r'name="_csrf"[^>]*value="([^"]+)"',body)
        self.assert_state('actual form includes CSRF',bool(match))
        return match[1]

    def fresh(self):
        self.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),NoRedirect())
        self.token=None
        self.verifier=secrets.token_urlsafe(48)
        self.params=dict(response_type='code',client_id=self.config['LOCAL_CLIENT_ID'],redirect_uri=self.config['LOCAL_CALLBACK_URL'],scope='openid profile email metadata',state=secrets.token_hex(20),code_challenge=base64.urlsafe_b64encode(hashlib.sha256(self.verifier.encode()).digest()).decode().rstrip('='),code_challenge_method='S256',branding='idp',**{'x-request-datetime':datetime.now(timezone.utc).isoformat()})
        h,_=self.request('GET','/oauth2/authorize?'+urllib.parse.urlencode(self.params),302,authenticated=False)
        _,body=self.request('GET',h['Location'],200,authenticated=False)
        return self.csrf(body)

    def code(self,flow):
        _,page=self.request('GET','/mfa/'+flow+'/request-pin',200,authenticated=False)
        self.assert_state('synthetic email factor appears in real form','Email1-email' in page)
        h,_=self.request('POST','/mfa/'+flow+'/request-pin',302,authenticated=False,form=dict(option='Email1-email',_csrf=self.csrf(page)))
        _,page=self.request('GET',h['Location'],200,authenticated=False)
        item=self.control('/control/state')['inbox'][-1]
        return item,self.csrf(page)

    def exchange(self,location):
        h,_=self.request('GET',location,302,authenticated=False)
        query=urllib.parse.parse_qs(urllib.parse.urlsplit(h['Location']).query)
        self.assert_state('completed MFA issues code and preserves state',bool(query.get('code')) and query.get('state')==[self.params['state']])
        _,tokens=self.request('POST','/oauth2/token',200,authenticated=False,form=dict(grant_type='authorization_code',client_id=self.config['LOCAL_CLIENT_ID'],redirect_uri=self.config['LOCAL_CALLBACK_URL'],code=query['code'][0],code_verifier=self.verifier))
        self.token=tokens['access_token']
        self.request('GET','/userinfo',200)

    def mfa(self):
        self.admin.request('PUT','/api/v1/users/'+self.user_id+'/security-settings',200,payload=dict(twoFactorEnabled=True))
        csrf=self.fresh()
        h,_=self.request('POST','/login',302,authenticated=False,form=dict(username=self.user['username'],password=base64.b64encode(self.user['password'].encode()).decode(),_csrf=csrf))
        self.assert_state('password alone requires MFA','/mfa/' in h['Location'])
        item,csrf=self.code('login')
        h,_=self.request('POST','/mfa/login/verify-pin',302,authenticated=False,positive=False,form=dict(pin='not-a-code',_csrf=csrf))
        self.assert_state('wrong PIN stays in MFA','/mfa/' in h['Location'])
        self.control('/control/expire-code',{'session':item['recipient']})
        h,_=self.request('POST','/mfa/login/verify-pin',302,authenticated=False,positive=False,form=dict(pin=item['code'],_csrf=csrf))
        self.assert_state('expired PIN stays in MFA','/mfa/' in h['Location'])
        item,csrf=self.code('login')
        h,_=self.request('POST','/mfa/login/verify-pin',302,authenticated=False,form=dict(pin=item['code'],_csrf=csrf))
        self.exchange(h['Location'])
        h,_=self.request('POST','/mfa/login/verify-pin',302,authenticated=False,positive=False,form=dict(pin=item['code'],_csrf=csrf))
        self.assert_state('consumed PIN replay stays in MFA','/mfa/' in h['Location'])
        events=self.control('/control/state')['events']
        self.assert_state('actual provider validates signed requests and rejects invalid/expired/replayed codes',sum(e.get('accepted') is False for e in events if e['operation']=='verify')>=3 and any(e.get('accepted') is True for e in events if e['operation']=='verify'))

    def recovery_start(self,flow='forgot-password'):
        self.fresh()
        path='/'+flow
        if flow=='mobile-forgot-password':
            external=self.sql("SELECT SourceId FROM dbo.ExternalSource WHERE SourceCode='idp' FOR JSON PATH")[0]['SourceId']
            path+='?'+urllib.parse.urlencode(dict(cid=self.config['LOCAL_CLIENT_ID'],eid=external.replace('-','')))
        _,body=self.request('GET',path,200,authenticated=False)
        h,_=self.request('POST','/'+flow+'/identify-yourself',302,authenticated=False,form=dict(username=self.user['username'],_csrf=self.csrf(body)))
        self.assert_state('recovery identification requires MFA','/mfa/'+flow+'/' in h['Location'])

    def recovery_bypass(self,flow='forgot-password'):
        self.recovery_start(flow)
        before=self.user_state()
        _,body=self.request('GET','/'+flow+'/update-password',(200,403),authenticated=False,positive=False,label='recovery reset form cannot authorize a password change before MFA')
        if 'name="_csrf"' not in body:
            _,body=self.request('GET','/login',200,authenticated=False)
        password='B8!'+secrets.token_hex(15)+'qZ'
        encoded=base64.b64encode(password.encode()).decode()
        self.request('POST','/'+flow+'/update-password',(200,302,403),authenticated=False,positive=False,form=dict(password=encoded,confirmPassword=encoded,_csrf=self.csrf(body)),label='direct password reset before MFA rejected without SQL mutation')
        self.no_mutation('recovery cannot change SQL password before MFA',before)

    def recovery(self,flow='forgot-password'):
        self.recovery_start(flow)
        item,csrf=self.code(flow)
        h,_=self.request('POST','/mfa/'+flow+'/verify-pin',302,authenticated=False,form=dict(pin=item['code'],_csrf=csrf))
        _,page=self.request('GET',h['Location'],200,authenticated=False)
        password='R8!'+secrets.token_hex(15)+'qZ'
        encoded=base64.b64encode(password.encode()).decode()
        before=self.user_state()
        h,_=self.request('POST','/'+flow+'/update-password',302,authenticated=False,form=dict(password=encoded,confirmPassword=encoded,_csrf=self.csrf(page)))
        self.assert_state('MFA-verified reset returns expected result',('/login' in h['Location']) if flow!='mobile-forgot-password' else ('/mobile-forgot-password/update-password' in h['Location']))
        self.assert_state('MFA-verified reset changes SQL password',self.user_state()['PasswordDigest']!=before['PasswordDigest'])
        self.report['database_checks'].append(dict(id='verified password recovery persisted hash',passed=True))
        self.user['password']=password

    def biometric(self):
        csrf=self.fresh()
        device=secrets.token_hex(16)
        proof=self.control('/control/proof',dict(user=self.user_id,device=device))['proof']
        h,_=self.request('POST','/login-biometric',302,authenticated=False,form=dict(biometricToken=proof,deviceUuid=device,_csrf=csrf))
        # Biometric proof is a synthetic provider credential, not a real device or Touch ID.
        self.assert_state('synthetic biometric success returns authorization continuation','/oauth2/authorize' in h['Location'])
        self.exchange(h['Location'])
        csrf=self.fresh()
        h,_=self.request('POST','/login-biometric',302,authenticated=False,positive=False,form=dict(biometricToken=proof,deviceUuid=device,_csrf=csrf))
        self.assert_state('consumed biometric proof replay rejected','/oauth2/authorize' not in h['Location'])
        self.request('GET','/oauth2/sso',401,authenticated=False,positive=False)
        for label,ttl,actual_device in [('expired proof',-1,device),('wrong device',120,'different-device')]:
            csrf=self.fresh()
            rejected=self.control('/control/proof',dict(user=self.user_id,device=device,ttl=ttl))['proof']
            h,_=self.request('POST','/login-biometric',302,authenticated=False,positive=False,form=dict(biometricToken=rejected,deviceUuid=actual_device,_csrf=csrf),label='synthetic biometric '+label+' rejected')
            self.assert_state(label+' cannot continue authorization','/oauth2/authorize' not in h['Location'])
            self.request('GET','/oauth2/sso',401,authenticated=False,positive=False)

    def provider_security(self):
        for path,body in [('/Default/api/v2/users/synthetic/factors',None),('/Default/api/v2/auth',dict(user_id='synthetic',type='email',factor_id='Email1')),('/control/state',None)]:
            request=urllib.request.Request(state['provider_base']+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json'})
            try:
                with urllib.request.urlopen(request,timeout=5) as response:status=response.status
            except urllib.error.HTTPError as error:status=error.code
            self.report['provider_checks'].append(dict(id='unauthenticated local provider '+path,status=status,passed=status==401))
            self.assert_state('local provider rejects unsigned request '+path,status==401)

    def browser(self):
        sample=runpy.run_path(str(ROOT/'scripts/sample-frontend.py'))
        config,path=sample['register'](self.admin,self.org,callback='http://localhost:3001/')
        httpd=sample['server'](config)
        before=self.user_state()
        password='C8!'+secrets.token_hex(15)+'qZ'
        profile=dict(firstName='Chloé',lastName='Browser',email=self.user['username']+'.browser@example.test',confirmEmail=self.user['username']+'.browser@example.test',phoneNumber='(202) 555-0198',secondaryPhoneNumber='(202) 555-0199')
        config.update(username=self.user['username'],password=self.user['password'],new_password=password,profile=profile,provider=dict(base=state['provider_base'],secret=self.provider['control_secret']),screenshot=str(LAB/'browser-mfa.png'))
        try:
            result=subprocess.run(['node',str(ROOT/'scripts/e2e-browser-engine.mjs')],input=json.dumps(config),text=True,capture_output=True,cwd=ROOT,timeout=180)
            browser=json.loads(result.stdout)
            self.report['browser']=browser
            self.report['assertions'].extend(browser['assertions'])
            self.assert_state('Chrome MFA and account changes finish successfully',result.returncode==0 and not browser['failures'])
            after=self.user_state()
            self.assert_state('MFA browser profile and password persist in SQL',after['FirstName']=='Chloé' and after['Email']==profile['email'] and before['PasswordDigest']!=after['PasswordDigest'])
            self.report['database_checks'].append(dict(id='Chrome MFA profile/password persisted',passed=True))
            self.user['password']=password
        finally:
            httpd.shutdown();httpd.server_close()
            self.admin.request('DELETE',path,200)

    def run(self):
        try:
            self.create_fixture()
            for name,case in [('signed MFA login, wrong/expired/replayed codes',self.mfa),('recovery cannot bypass MFA',self.recovery_bypass),('verified password recovery',self.recovery),('activation cannot bypass MFA',lambda:self.recovery_bypass('activate-account')),('verified activation password setup',lambda:self.recovery('activate-account')),('mobile recovery cannot bypass MFA',lambda:self.recovery_bypass('mobile-forgot-password')),('verified mobile recovery',lambda:self.recovery('mobile-forgot-password')),('synthetic biometric success and replay',self.biometric),('local provider rejects unsigned/control requests',self.provider_security),('real Chrome MFA and sample frontend',self.browser)]:
                try:case();print('PASS',name,flush=True)
                except Exception as error:self.report['failures'].append(dict(case=name,error=str(error)));print('FAIL',name,str(error),flush=True)
        except Exception as error:
            self.report['failures'].append(dict(case='provider fixture setup',type=type(error).__name__))
        finally:
            try:self.cleanup()
            except Exception as error:self.report['failures'].append(dict(case='owned fixture cleanup',type=type(error).__name__))
            self.report['finished_at']=datetime.now(timezone.utc).isoformat()
            descriptor=os.open(ROOT/'.local/e2e-local-providers-report.json',os.O_CREAT|os.O_TRUNC|os.O_WRONLY,0o600)
            with os.fdopen(descriptor,'w') as output:json.dump(self.report,output,indent=2)
        if self.report['failures']:raise SystemExit(1)

if __name__=='__main__':LocalFlows().run()
