#!/usr/bin/env python3
"""Real local metadata, page variants, POST authorization, logout and revocation checks."""
import base64
from datetime import datetime, timezone
import hashlib
import hmac
import http.cookiejar
import json
from pathlib import Path
import re
import runpy
import secrets
import sys
import time
import urllib.parse
import urllib.request
import uuid

ROOT=Path(__file__).resolve().parent.parent
BrowserFlows=runpy.run_path(str(ROOT/'scripts/e2e-browser-flows.py'))['BrowserFlows']
NoRedirect=runpy.run_path(str(ROOT/'scripts/e2e-local.py'))['NoRedirect']

class Variants(BrowserFlows):
    def __init__(self):
        super().__init__()
        self.report['suite']='real HTTP protocol variants'
        self.report['blocked']=[]
        self.report['limitations']=['Standard Spring POST /logout verifies browser session logout/redirect only. Bearer revocation is separately proved through custom OAuth logout and token revocation.']
        self.server_app=None

    def request(self, method, path, expected, **kwargs):
        headers,body=super().request(method,path,expected,**kwargs)
        query=urllib.parse.parse_qs(urllib.parse.urlsplit(path).query,keep_blank_values=True)
        allowed={k:v for k,v in query.items() if k in ('error','errorMessage')}
        if allowed:self.report['checks'][-1]['path']+='?'+urllib.parse.urlencode(allowed,doseq=True)
        return headers,body

    def fresh(self):
        self.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),NoRedirect())
        self.token=None
        return self.login(self.user['username'],self.user['password'],scopes='openid profile email metadata')

    def metadata(self):
        _,discovery=self.request('GET','/.well-known/openid-configuration',200,authenticated=False,label='standard discovery advertises implemented logout')
        self.assert_state('standard discovery advertises custom logout',discovery.get('end_session_endpoint')==self.base+'/oauth2/connect/logout')
        self.report['not_enabled']=[dict(endpoints=['GET /connect/logout','POST /connect/logout'],reason='AuthorizationServerSettings explicitly routes OIDC logout to /oauth2/connect/logout, handled by custom MVC/logout chain.')]
        for path in ('/.well-known/oauth-authorization-server',f"/oauth2/.well-known/organizations/{self.org}/openid-configuration"):
            _,body=self.request('GET',path,200,authenticated=False,label='metadata issuer and endpoint contract '+path)
            self.assert_state('metadata uses configured issuer',body['issuer']==self.base)
            self.assert_state('metadata uses real token endpoint',body['token_endpoint']==self.base+'/oauth2/token')
            self.assert_state('metadata uses real authorization endpoint',body['authorization_endpoint']==self.base+'/oauth2/authorize')

    def views(self):
        for path,fragment in [('/login?error=invalid','name="password"'),('/login?errorMessage=invalid','name="password"'),
                              ('/user/change-profile?error=invalid','name="firstName"'),('/user/404','Unable to continue')]:
            _,body=self.request('GET',path,200,authenticated=False,positive=False,label='controlled page variant '+path)
            self.assert_state('page variant renders expected form/message '+path,fragment in body)
            self.report['checks'][-1]['semantic_view_verified']=True

    def authorize_post(self):
        verifier=secrets.token_urlsafe(48)
        challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
        state=secrets.token_hex(20)
        callback=self.config.get('LOCAL_CALLBACK_URL',self.base+'/local/callback')
        fields=dict(response_type='code',client_id=self.config['LOCAL_CLIENT_ID'],redirect_uri=callback,
                    scope='openid profile email metadata',state=state,continue_='',code_challenge=challenge,code_challenge_method='S256',branding='idp',
                    **{'x-request-datetime':datetime.now(timezone.utc).isoformat()})
        fields.pop('continue_')
        self.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),NoRedirect())
        self.token=None
        headers,_=self.request('GET','/oauth2/authorize?'+urllib.parse.urlencode(fields),302,authenticated=False,label='prepare fresh POST authorization login')
        _,page=self.request('GET',headers['Location'],200,authenticated=False)
        csrf=re.search(r'name="_csrf"[^>]*value="([^"]+)"',page)
        self.assert_state('POST authorization login contains CSRF',csrf is not None)
        headers,_=self.request('POST','/login',302,authenticated=False,form=dict(username=self.user['username'],password=base64.b64encode(self.user['password'].encode()).decode(),_csrf=csrf[1]),label='authenticate fresh POST authorization session')
        self.assert_state('POST authorization login succeeds','error' not in headers.get('Location',''))
        fields['continue']=''
        headers,_=self.request('POST','/oauth2/authorize',302,form=fields,authenticated=False,positive=False,label='POST authorization issues PKCE code')
        check=self.report['checks'][-1]
        query=urllib.parse.parse_qs(urllib.parse.urlsplit(headers['Location']).query)
        self.report.setdefault('diagnostics',[]).append(dict(case='POST authorize redirect',path=urllib.parse.urlsplit(headers['Location']).path,error=query.get('error'),error_description=query.get('error_description'),has_code=bool(query.get('code')),state_preserved=query.get('state')==[state]))
        self.assert_state('POST authorization preserves state and issues code',query.get('state')==[state] and bool(query.get('code')))
        _,tokens=self.request('POST','/oauth2/token',200,authenticated=False,form=dict(grant_type='authorization_code',client_id=self.config['LOCAL_CLIENT_ID'],redirect_uri=callback,code=query['code'][0],code_verifier=verifier),label='POST authorization code exchanges successfully')
        self.assert_state('POST authorization exchange yields identity token',bool(tokens.get('id_token')))
        self.token=tokens['access_token']
        self.authorization_fields=fields
        check['positive']=True

    def repeated_continuation(self):
        query = f"SELECT (SELECT COUNT(*) FROM Token.Pkce p JOIN Token.AuthSession s ON s.SessionId=p.SessionId WHERE s.SubjectId='{self.user['username']}') AS PkceRows,(SELECT COUNT(*) FROM Token.AuthCode c JOIN Token.AuthSession s ON s.SessionId=c.SessionId WHERE s.SubjectId='{self.user['username']}') AS CodeRows FOR JSON PATH"
        before=self.sql(query)
        self.request('POST','/oauth2/authorize',400,form=self.authorization_fields,authenticated=False,positive=False,label='fully exchanged POST continuation is rejected without duplicate state')
        unchanged=self.sql(query)==before
        self.report['database_checks'].append(dict(id='rejected repeated continuation adds no PKCE or authorization-code rows',passed=unchanged))
        self.assert_state('rejected repeated continuation rolls back all custom state',unchanged)

    def custom_logout(self):
        tokens=self.fresh()
        callback=self.config.get('LOCAL_CALLBACK_URL',self.base+'/local/callback')
        fields=dict(id_token_hint=tokens['id_token'],client_id=self.config['LOCAL_CLIENT_ID'],post_logout_redirect_uri=callback)
        headers,_=self.request('POST','/oauth2/connect/logout',302,authenticated=False,form=fields,positive=False,label='custom POST logout with real identity token')
        check=self.report['checks'][-1]
        self.assert_state('custom POST logout redirects to registered application',headers['Location']==callback)
        self.request('GET','/userinfo',401,positive=False,label='custom POST logout deactivates access token')
        check['positive']=True
        self.db('custom logout session inactive in SQL',f"SELECT TOP 1 e.Code FROM Token.AuthSession s JOIN dbo.Enum e ON e.EnumId=s.AuthSessionStatusId WHERE SubjectId='{self.user['username']}' ORDER BY AuthSessionId DESC FOR JSON PATH",lambda rows:rows[0]['Code']=='Session Inactive')

    def standard_logout(self):
        self.fresh()
        _,body=self.request('GET','/login',200,authenticated=False)
        csrf=re.search(r'name="_csrf"[^>]*value="([^"]+)"',body)
        self.assert_state('standard logout has valid CSRF context',csrf is not None)
        headers,_=self.request('POST','/logout',302,form={'_csrf':csrf[1]},authenticated=False,positive=False,label='standard Spring POST logout accepts session CSRF')
        check=self.report['checks'][-1]
        self.assert_state('standard logout redirects to logout result',headers.get('Location','').endswith('/login?logout'))
        check['positive']=True

    def revocation(self):
        root=f'/api/v1/organizations/{self.org}/applications'
        _,app=self.admin.request('POST',root,201,payload=dict(name=self.prefix+' revocation',description='Disposable protocol client',type='SERVER',authMethod='CLIENT_SECRET_JWT'))
        self.server_app=root+'/'+app['id']
        _,app=self.admin.request('GET',self.server_app,200)
        client_id=app['clientId']
        _,credential=self.admin.request('POST',self.server_app+'/credentials',201,payload=dict(name='Protocol revocation secret',type='SHARED_SECRET',algorithm='HS256',status='Active'))
        secret=credential['secretValue']
        def auth(fields):
            now=int(time.time())
            encode=lambda value:base64.urlsafe_b64encode(json.dumps(value,separators=(',',':')).encode()).decode().rstrip('=')
            signing=encode({'alg':'HS256','typ':'JWT'})+'.'+encode(dict(iss=client_id,sub=client_id,aud=self.base+'/oauth2/token',iat=now,exp=now+60,jti=str(uuid.uuid4())))
            signature=base64.urlsafe_b64encode(hmac.new(secret.encode(),signing.encode(),hashlib.sha256).digest()).decode().rstrip('=')
            return fields|dict(client_id=client_id,client_assertion_type='urn:ietf:params:oauth:client-assertion-type:jwt-bearer',client_assertion=signing+'.'+signature)
        _,tokens=self.request('POST','/oauth2/token',200,authenticated=False,form=auth(dict(grant_type='client_credentials')),label='confidential client obtains revocation fixture token')
        token=tokens['access_token']
        self.request('POST','/oauth2/revoke',200,authenticated=False,form=auth(dict(token=token,token_type_hint='access_token')),positive=False,label='authenticated OAuth token revocation')
        check=self.report['checks'][-1]
        digest=hashlib.sha256(token.encode()).hexdigest()
        self.db('revocation invalidates actual token session in SQL',f"SELECT e.Code FROM Token.Token t JOIN Token.AuthSession s ON s.SessionId=t.SessionId JOIN dbo.Enum e ON e.EnumId=s.AuthSessionStatusId WHERE t.DataHash=0x{digest} FOR JSON PATH",lambda rows:len(rows)==1 and rows[0]['Code']=='Session Inactive')
        previous=self.token
        self.token=token
        self.request('GET','/userinfo',401,positive=False,label='revoked bearer rejected by userinfo')
        self.token=previous
        check['positive']=True
        self.request('POST','/oauth2/token',400,authenticated=False,form=auth(dict(grant_type='urn:ietf:params:oauth:grant-type:device_code',device_code='unconfigured-device-code')),positive=False,label='unconfigured device grant rejected')
        self.report['blocked'].append(dict(endpoints=['POST /oauth2/device_authorization','GET /oauth2/device_verification','POST /oauth2/device_verification'],reason='ApplicationService registers only authorization_code/refresh_token or client_credentials grants; no device-grant client is supported/configured. Rejection does not qualify device flow.'))

    def run(self):
        try:
            self.create_fixture()
            for name,case in [('metadata contracts',self.metadata),('controlled page variants',self.views),('POST PKCE authorization',self.authorize_post),('repeated authorization continuation',self.repeated_continuation),('custom POST logout',self.custom_logout),('standard POST logout',self.standard_logout),('confidential revocation and device policy',self.revocation)]:
                try:case();print('PASS',name)
                except Exception as error:self.report['failures'].append(dict(case=name,error=str(error)));print('FAIL',name,':',str(error))
        except Exception as error:self.report['failures'].append(dict(case='disposable fixture',error=str(error)));print('FAIL fixture:',str(error))
        finally:
            try:
                if self.server_app:self.admin.request('DELETE',self.server_app,200)
                self.cleanup()
            except Exception as error:self.report['failures'].append(dict(case='cleanup',error=str(error)))
            self.report['finished_at']=datetime.now(timezone.utc).isoformat()
            (ROOT/'.local/e2e-protocol-report.json').write_text(json.dumps(self.report,indent=2)+'\n')
        print('HTTP',len(self.report['checks']),'SQL',len(self.report['database_checks']),'failures',len(self.report['failures']))
        return bool(self.report['failures'])

if __name__=='__main__':sys.exit(Variants().run())
