#!/usr/bin/env python3
"""Real HTTP + SQL acceptance checks; uses only explicitly created local test fixtures.

Requires the documented local app and Compose services. No mocked providers.
Reports statuses and assertion results, never credentials, bearer tokens or secret responses.
"""
import base64
from datetime import datetime, timezone
import hashlib
import hmac
import http.cookiejar
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parent.parent

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, url):
        return None

class Acceptance:
    def __init__(self):
        self.config = json.loads(Path(os.environ.get('AUTH_E2E_CONFIG', ROOT/'.local/dev.json')).read_text())
        self.base = os.environ.get('AUTH_E2E_BASE', 'http://localhost:9080/services')
        if urllib.parse.urlparse(self.base).hostname not in ('localhost', '127.0.0.1'):
            raise SystemExit('This fixture-mutating suite is restricted to a local development app.')
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())
        self.prefix = 'E2E '+secrets.token_hex(6)
        self.token = None
        self.report = dict(started_at=datetime.now(timezone.utc).isoformat(), checks=[], database_checks=[], failures=[])

    def request(self, method, path, expected, *, payload=None, form=None, authenticated=True, label=None, positive=None):
        headers = {'User-Agent':'AuthserverAcceptance/1.0', 'Accept-Language':'en-US',
                   'x-request-datetime':datetime.now(timezone.utc).isoformat()}
        if self.token and authenticated:
            headers['Authorization'] = 'Bearer '+self.token
        data = None
        if payload is not None:
            data=json.dumps(payload).encode(); headers['Content-Type']='application/json'
        if form is not None:
            data=urllib.parse.urlencode(form).encode(); headers['Content-Type']='application/x-www-form-urlencoded'
        url = path if path.startswith('http') else self.base+path
        request = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            response=self.http.open(request, timeout=25)
        except urllib.error.HTTPError as error:
            response=error
        body=response.read().decode()
        status=response.status
        allowed=[expected] if isinstance(expected,int) else list(expected)
        check=dict(id=label or method+' '+urllib.parse.urlparse(url).path, method=method,
                   path=urllib.parse.urlparse(url).path.removeprefix('/services'), status=status,
                   expected=allowed, passed=status in allowed)
        if positive is not None:check['positive']=positive
        self.report['checks'].append(check)
        if status not in allowed:
            # Do not include response bodies: successful credential responses can contain secrets.
            raise AssertionError(f"{check['id']}: expected {allowed}, received {status}")
        return response.headers, json.loads(body) if body and (body[0] in '[{' or body in ('true','false','null')) else body

    def sql(self, query):
        # Only this repo's explicitly task-owned Compose development database.
        database = os.environ.get('AUTH_E2E_DB', 'AGSAuth')
        if not re.fullmatch(r'AGSAuth(?:Providers_[a-f0-9]{8})?', database):
            raise ValueError('Only task-owned local development databases are supported')
        command=['docker','compose','exec','-T','sqlserver','bash','-lc',
                 'SQLCMDPASSWORD="$MSSQL_SA_PASSWORD" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -d '+database+' -C -b -h -1 -y 8000 -w 65535']
        # -W and -y cannot be combined with some sqlcmd versions; use -y only.
        command[-1]=command[-1].replace(' -W','')
        result=subprocess.run(command,cwd=ROOT,input='SET NOCOUNT ON;\n'+query+'\nGO\n',text=True,capture_output=True,check=True)
        text=result.stdout.strip()
        return json.loads(text) if text else []

    def db(self, label, query, predicate):
        result=self.sql(query)
        passed=bool(predicate(result))
        self.report['database_checks'].append(dict(id=label,passed=passed))
        if not passed:raise AssertionError('Database assertion failed: '+label)

    def login(self, username=None, password=None, scopes='openid profile email metadata idp-admin'):
        client_id=self.config['LOCAL_CLIENT_ID']
        callback=self.config.get('LOCAL_CALLBACK_URL',self.base+'/local/callback')
        verifier=secrets.token_urlsafe(48)
        challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
        state=secrets.token_urlsafe(16)
        params=dict(response_type='code', client_id=client_id, redirect_uri=callback,scope=scopes,
                    code_challenge=challenge,code_challenge_method='S256',branding='idp',state=state,
                    **{'x-request-datetime':datetime.now(timezone.utc).isoformat()})
        h,_=self.request('GET','/oauth2/authorize?'+urllib.parse.urlencode(params),302,authenticated=False)
        _,body=self.request('GET',h['Location'],200,authenticated=False)
        csrf=re.search(r'name="_csrf"[^>]*value="([^"]+)"',body)
        assert csrf,'Missing login CSRF token'
        h,_=self.request('POST','/login',302,form=dict(username=username or self.config['LOCAL_ADMIN_USERNAME'],
                password=base64.b64encode((password or self.config['LOCAL_ADMIN_PASSWORD']).encode()).decode(),_csrf=csrf[1]),authenticated=False)
        assert 'error' not in h['Location'],'Login rejected'
        h,_=self.request('GET',h['Location'],302,authenticated=False)
        query=urllib.parse.parse_qs(urllib.parse.urlparse(h['Location']).query)
        assert query.get('state')==[state] and 'code' in query,'Authorization did not issue a code'
        _,tokens=self.request('POST','/oauth2/token',200,form=dict(grant_type='authorization_code',client_id=client_id,
                    code=query['code'][0],redirect_uri=callback,code_verifier=verifier),authenticated=False)
        self.token=tokens['access_token']
        return tokens

    def organization(self):
        _,created=self.request('POST','/api/v1/organizations',201,payload=dict(name=self.prefix,description='Acceptance fixture'))
        return created['id']

    def applications(self, org):
        root=f'/api/v1/organizations/{org}/applications'
        _,app=self.request('POST',root,201,payload=dict(name=self.prefix+' Web',description='Real HTTP fixture',type='WEB',authMethod='PKCE'))
        path=root+'/'+app['id']
        self.request('GET',root,200)
        self.request('GET',path,200)
        self.request('PUT',path,200,payload=dict(name=self.prefix+' Web updated',description='HTTP update',usernameType='USERNAME',allowForgotUsername=False,pinTimeToLive=300))
        self.db('application name persisted',f"SELECT Name FROM Client.Application WHERE RowGuid='{app['id']}' FOR JSON PATH",lambda r:r[0]['Name']==self.prefix+' Web updated')
        urls=dict(applicationUrl='https://example.test/app',returnUrls=['https://example.test/callback'],logoutUrls=['https://example.test/logout'])
        self.request('PUT',path+'/urls',200,payload=urls)
        _,got=self.request('GET',path+'/urls',200)
        assert got['redirectUrls']==urls['returnUrls'],'Redirect URLs not persisted'
        self.db('redirect URI persisted',f"SELECT r.RedirectUri FROM Client.RedirectUri r JOIN Client.Application a ON a.ApplicationId=r.ApplicationId WHERE a.RowGuid='{app['id']}' FOR JSON PATH",lambda r:any(v['RedirectUri']==urls['returnUrls'][0] for v in r))
        settings=dict(authCodeTimeToLive=90,accessTokenTimeToLive=500,deviceCodeTimeToLive=100,refreshTokenTimeToLive=1800,reuseRefreshTokens=False,maxRequestTransitTime=25)
        self.request('PUT',path+'/settings',200,payload=settings)
        self.db('token lifetime settings persisted',f"SELECT t.AuthCodeTimeToLive,t.RefreshTokenTimeToLive FROM Client.TokenSetting t JOIN Client.Application a ON a.ApplicationId=t.ApplicationId WHERE a.RowGuid='{app['id']}' FOR JSON PATH",lambda r:r[0]['AuthCodeTimeToLive']==90 and r[0]['RefreshTokenTimeToLive']==1800)
        self.request('PUT',path+'/settings',400,payload=settings|dict(accessTokenTimeToLive=-1),positive=False)
        _,resource=self.request('POST','/api/v1/endpoints',201,payload=dict(name=self.prefix+' Resource',description='HTTP fixture',uri='https://example.test/'+secrets.token_hex(8),allowedMethod='GET',urn='urn:e2e:'+secrets.token_hex(6)))
        resource_path='/api/v1/endpoints/'+resource['id']
        self.request('GET','/api/v1/endpoints',200)
        self.request('GET',resource_path,200)
        self.request('PUT',resource_path,200,payload=dict(name=self.prefix+' Updated resource',description='Updated',uri='https://example.test/updated/'+secrets.token_hex(8),allowedMethod='POST',urn='urn:e2e:'+secrets.token_hex(6)))
        self.request('POST',path+'/endpoints',201,payload=dict(resourceId=resource['id']))
        _,assigned=self.request('GET',path+'/endpoints',200)
        assert len(assigned)==1,'Assigned endpoint did not appear'
        self.db('resource assignment persisted',f"SELECT r.ResourceId FROM Resource.Resource r JOIN Client.Application a ON a.ApplicationId=r.ApplicationId WHERE a.RowGuid='{app['id']}' FOR JSON PATH",lambda r:len(r)==1)
        self.request('DELETE',path+'/endpoints/'+resource['id'],204)
        _,assigned=self.request('GET',path+'/endpoints',200)
        assert assigned==[],'Unassigned endpoint still returned'
        self.request('DELETE',path,200)
        self.db('application inactive after delete',f"SELECT Active FROM Client.Application WHERE RowGuid='{app['id']}' FOR JSON PATH",lambda r:r[0]['Active'] is False)

    def credentials(self, org):
        root=f'/api/v1/organizations/{org}/applications'
        _,app=self.request('POST',root,201,payload=dict(name=self.prefix+' Server',description='Credential fixture',type='SERVER',authMethod='CLIENT_SECRET_JWT'))
        path=root+'/'+app['id']+'/credentials'
        _,credential=self.request('POST',path,201,payload=dict(name='Acceptance secret',type='SHARED_SECRET',algorithm='HS256',status='Active'))
        assert len(credential['secretValue'])>=32,'Secret must have sufficient length'
        self.request('GET',path,200)
        self.db('encrypted secret and credential persisted',f"SELECT DATALENGTH(s.KeyStore) AS EncryptedBytes FROM Client.Credential c JOIN Client.Secret s ON s.SecretId=c.SecretId WHERE c.RowGuid='{credential['id']}' FOR JSON PATH",lambda r:len(r)==1 and r[0]['EncryptedBytes']>100)
        self.request('PUT',path+'/'+credential['id'],200,payload=dict(status='Disabled'))
        self.db('credential disabled in SQL',f"SELECT e.Code FROM Client.Credential c JOIN dbo.Enum e ON e.EnumId=c.CredentialStatus WHERE c.RowGuid='{credential['id']}' FOR JSON PATH",lambda r:r[0]['Code']=='Disabled')
        self.request('DELETE',path+'/'+credential['id'],(204,400),positive=False,label='unexpired credential deletion policy')
        self.request('DELETE',root+'/'+app['id'],200)

    def users(self, org):
        username='e2e_'+secrets.token_hex(6)
        password='X7!'+secrets.token_hex(14)+'qZ'
        payload=dict(firstName='Acceptance',lastName='Fixture',username=username,email=username+'@example.test',password=base64.b64encode(password.encode()).decode(),phoneNumber='+19999999999',orgGuid=org,branding='idp',syncFlag=False)
        _,user=self.request('POST','/api/v1/users',201,payload=payload)
        user_id=user['id'];path='/api/v1/users/'+user_id
        self.request('GET',path,200)
        self.request('GET','/api/v1/users?search='+username,200)
        self.db('new user and binary password persisted',f"SELECT p.FirstName,DATALENGTH(c.Password) AS PasswordBytes FROM Person.Profile p JOIN Person.Credential c ON c.ProfileId=p.ProfileId WHERE p.RowGuid='{user_id}' FOR JSON PATH",lambda r:r[0]['FirstName']=='Acceptance' and r[0]['PasswordBytes']>32)
        self.request('PUT',path+'/security-settings',200,payload=dict(twoFactorEnabled=False))
        self.request('PUT',path+'/metadata',200,payload=dict(metaData={'acceptance_test':self.prefix}))
        _,metadata=self.request('GET',path+'/metadata',200)
        assert self.prefix in json.dumps(metadata),'Metadata round trip lost data'
        self.request('PUT',path+'/profile',200,payload=dict(firstName='Updated',lastName='Fixture',phoneNumber='+19999999999',branding='idp',intent='idp',metadata={}))
        self.request('PUT',path+'/email',200,payload=dict(email=username+'.updated@example.test',branding='idp',intent='idp',metadata={}))
        self.request('PUT',path+'/username',204,payload=dict(username=username+'_renamed',branding='idp',intent='idp',metadata={}))
        self.db('renamed username and profile persisted',f"SELECT p.FirstName,c.UserName FROM Person.Profile p JOIN Person.Credential c ON c.ProfileId=p.ProfileId WHERE p.RowGuid='{user_id}' FOR JSON PATH",lambda r:r[0]['FirstName']=='Updated' and r[0]['UserName']==username+'_renamed')
        password='P8!'+secrets.token_hex(14)+'qZ'
        self.request('PUT',path+'/password',204,payload=dict(password=base64.b64encode(password.encode()).decode(),branding='idp',intent='idp',metadata={}))
        admin=self.token
        self.login(username+'_renamed',password,scopes='openid profile email metadata')
        self.request('GET','/api/v1/organizations',403,positive=False,label='ordinary user cannot list organizations')
        ordinary_token = self.token
        self.token=admin
        self.request('PUT',path+'/status',204,payload=dict(status='Inactive'))
        self.db('user deactivation persisted',f"SELECT Status FROM Person.Profile WHERE RowGuid='{user_id}' FOR JSON PATH",lambda r:r[0]['Status']==0)
        self.token=ordinary_token
        self.request('GET','/userinfo',401,positive=False,label='deactivated user bearer rejected')
        self.token=admin
        self.request('DELETE',path,204)

    def protocol(self):
        _,sso=self.request('GET','/oauth2/sso',200)
        self.request('GET','/userinfo',200)
        self.request('POST','/userinfo',200,form={})
        _,introspection=self.request('POST','/oauth2/introspect',200,form=dict(token=self.token,
                res_url=base64.b64encode(b'https://unassigned.example.test').decode(),res_method='GET'))
        assert introspection['active'] is True, 'Live token introspection must be active'
        assert introspection['res_access']=='DENIED', 'Unassigned resource must be denied'
        body='{"acceptance":"signature"}'
        key=base64.b64decode(sso['signing_key']) if isinstance(sso['signing_key'],str) else bytes(sso['signing_key'])
        signature=base64.b64encode(hmac.new(key,body.encode(),hashlib.sha256).digest()).decode()
        _,verified=self.request('POST','/oauth2/signature/verify',200,form=dict(clientId=self.config['LOCAL_CLIENT_ID'],
                requestTimeDate=datetime.now(timezone.utc).isoformat(),signature=signature,body=body))
        assert verified is True, 'Real token HMAC must verify'

    def run(self):
        self.login()
        org=self.organization()
        for name,case in [('protocol helpers',lambda _:self.protocol()),('organization contacts',self.contacts),('applications and resources',self.applications),('encrypted credentials',self.credentials),('users and permissions',self.users)]:
            try:
                case(org)
                print('PASS',name)
            except Exception as error:
                self.report['failures'].append(dict(case=name,error=str(error)))
                print('FAIL',name,':',str(error))
        self.request('PUT','/api/v1/organizations/'+org,200,payload=dict(name=self.prefix,description='Completed acceptance fixture',status='Revoked'))
        self.db('fixture organization revoked',f"SELECT Status FROM Partner.Organization WHERE RowGuid='{org}' FOR JSON PATH",lambda r:r[0]['Status']==0)
        self.report['finished_at']=datetime.now(timezone.utc).isoformat()
        (ROOT/'.local/e2e-report.json').write_text(json.dumps(self.report,indent=2))
        print('HTTP checks:',len(self.report['checks']),'DB checks:',len(self.report['database_checks']),'failed cases:',len(self.report['failures']))
        return 1 if self.report['failures'] else 0

    def contacts(self, org):
        path='/api/v1/organizations/'+org
        self.request('GET',path,200)
        _,groups=self.request('GET',path+'/groups',200)
        assert groups,'New organization must have groups'
        for group in groups:
            self.request('GET',path+'/groups/'+group['id']+'/permissions',200)
        for kind in ('primary-contact','secondary-contact'):
            self.request('PUT',path+'/'+kind,200,payload=dict(name='Acceptance '+kind,email='contact@example.test',phoneNumber='+19999999999'))
        _,result=self.request('GET',path,200)
        assert result['primaryContact']['name']=='Acceptance primary-contact'
        assert result['secondaryContact']['name']=='Acceptance secondary-contact'
        self.db('organization contact links persisted',f"SELECT PrimaryContactName,SecondaryContactName FROM Partner.Organization WHERE RowGuid='{org}' FOR JSON PATH",lambda r:r[0]['PrimaryContactName']=='Acceptance primary-contact' and r[0]['SecondaryContactName']=='Acceptance secondary-contact')

if __name__=='__main__':
    sys.exit(Acceptance().run())
