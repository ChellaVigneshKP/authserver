#!/usr/bin/env python3
"""Real local machine user-policy APIs; reports never include tokens or passwords."""
import base64
from datetime import datetime, timezone
import hashlib
import hmac
import importlib.util
import json
import os
from pathlib import Path
import secrets
import time
import uuid

ROOT=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('acceptance',ROOT/'scripts/e2e-local.py')
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def main():
    suite=module.Acceptance()
    report=suite.report
    report['blocked']=[]
    app_path=credential_path=credential_id=admin=user_id=None
    try:
        suite.login()
        admin=suite.token
        # Create a fresh named organization explicitly; never mutate an existing organization.
        _,organization=suite.request('POST','/api/v1/organizations',201,payload=dict(name=suite.prefix+' machine fixture',description='Disposable machine user-policy test'))
        org=organization['id']
        _,app=suite.request('POST',f'/api/v1/organizations/{org}/applications',201,payload=dict(name=suite.prefix+' machine policy',description='Disposable policy fixture',type='SERVER',authMethod='CLIENT_SECRET_JWT'))
        app_path=f"/api/v1/organizations/{org}/applications/{app['id']}"
        _,application=suite.request('GET',app_path,200)
        suite.request('PUT',app_path+'/settings',200,payload=dict(authCodeTimeToLive=120,accessTokenTimeToLive=600,deviceCodeTimeToLive=120,refreshTokenTimeToLive=3600,reuseRefreshTokens=False,maxRequestTransitTime=25))
        _,resources=suite.request('GET','/api/v1/endpoints',200)
        resource=next((r for r in resources if r.get('uri')=='http://user' and r.get('allowedMethod')=='POST'),None)
        if resource is None:
            _,resource=suite.request('POST','/api/v1/endpoints',201,payload=dict(name=suite.prefix+' user POST',description='Local machine-policy fixture',uri='http://user',allowedMethod='POST',urn='urn:e2e:machine:'+secrets.token_hex(8)))
        suite.request('POST',app_path+'/endpoints',201,payload=dict(resourceId=resource['id']))
        credential_path=app_path+'/credentials'
        _,credential=suite.request('POST',credential_path,201,payload=dict(name=suite.prefix+' HMAC',type='SHARED_SECRET',algorithm='HS256',status='Active'))
        credential_id=credential['id']
        suite.db('machine secret encrypted and URL permission persisted',f"SELECT DATALENGTH(s.KeyStore) AS SecretBytes,dr.Uri,dr.AllowedMethod FROM Client.Credential c JOIN Client.Secret s ON s.SecretId=c.SecretId JOIN Resource.Resource r ON r.ApplicationId=c.ApplicationId JOIN dbo.ResourceLibrary dr ON dr.ResourceLibraryId=r.ResourceLibraryId WHERE c.RowGuid='{credential_id}' FOR JSON PATH",lambda r:len(r)==1 and r[0]['SecretBytes']>100 and r[0]['Uri']=='http://user' and r[0]['AllowedMethod']=='POST')
        def b64(value):return base64.urlsafe_b64encode(value).decode().rstrip('=')
        now=int(time.time())
        signed='.'.join(b64(json.dumps(v,separators=(',',':')).encode()) for v in ({'alg':'HS256','typ':'JWT'},dict(iss=application['clientId'],sub=application['clientId'],aud=suite.base+'/oauth2/token',iat=now,exp=now+120,jti=secrets.token_hex(16))))
        assertion=signed+'.'+b64(hmac.new(credential['secretValue'].encode(),signed.encode(),hashlib.sha256).digest())
        _,tokens=suite.request('POST','/oauth2/token',200,authenticated=False,form=dict(grant_type='client_credentials',client_id=application['clientId'],scope='confidential-client',client_assertion_type='urn:ietf:params:oauth:client-assertion-type:jwt-bearer',client_assertion=assertion),label='real HS256 machine token with URL permissions')
        assert tokens.get('expires_in',0)>60,'Machine token lifetime must permit stable tests'
        suite.token=tokens['access_token']
        password='Z8!'+secrets.token_hex(15)+'qW'
        def encoded(value):return base64.b64encode(value.encode()).decode()
        payload=dict(validationType='SYNTACTIC',password=encoded(password),confirmPassword=encoded(password),firstName='Local',lastName='Machine',username='machine_'+secrets.token_hex(6),email='machine@example.test')
        _,result=suite.request('POST','/api/v1/users/validate-password',200,payload=payload,label='machine accepts compliant password syntax')
        assert result['password']==password and result['confirmPassword']==password,'Accepted DTO must contain decoded password'
        cases=[('weak password',dict(password=encoded('weak'),confirmPassword=encoded('weak'))),('mismatched confirmation',dict(confirmPassword=encoded(password+'x'))),('unsupported validation type',dict(validationType='unsupported')),('invalid email',dict(email='invalid-email'))]
        semantic='Local9!'+secrets.token_hex(10)+'zQ'
        cases.append(('password containing first name',dict(validationType='SEMANTIC',password=encoded(semantic),confirmPassword=encoded(semantic))))
        for label,changes in cases:
            suite.request('POST','/api/v1/users/validate-password',400,payload=payload|changes,positive=False,label='machine rejects '+label)
        machine=suite.token
        suite.token=admin
        history_username='machine_history_'+secrets.token_hex(8)
        _,history_user=suite.request('POST','/api/v1/users',201,payload=dict(firstName='History',lastName='Fixture',username=history_username,email=history_username+'@example.test',password=encoded(password),phoneNumber='+19999999999',orgGuid=org,branding='idp',syncFlag=False))
        user_id=history_user['id']
        suite.token=machine
        suite.db('actual user password audit available',f"SELECT COUNT(*) AS Count FROM Person.CredentialAudit a JOIN Person.Credential c ON c.Id=a.CredentialId JOIN Person.Profile p ON p.ProfileId=c.ProfileId WHERE p.RowGuid='{user_id}' FOR JSON PATH",lambda r:r[0]['Count']>0)
        suite.request('POST','/api/v1/users/validate-password',400,payload=payload|dict(userGuid=user_id),positive=False,label='machine rejects actual KPCV password reuse from SQL audit')
        try:
            suite.request('POST','/api/v1/users/profile',400,payload={},positive=False,label='machine profile rejects missing schema payload')
        except AssertionError as error:
            report['failures'].append(str(error))
        # No genuine upstream legacy hash is available; never mislabel a KPCV hash as version 0.
        schema='urn:ascensus:529:1.0:accountsync'
        profile=dict(branding='unconfigured-e2e-'+secrets.token_hex(6),intent='conversion',timestamp=datetime.now(timezone.utc).isoformat(),orgId=org,credential=dict(loginId=str(uuid.uuid4()),username='machine_profile_'+secrets.token_hex(8),passwordHash='',cIndex=''),profile=dict(memberId=str(uuid.uuid4()),firstName='Local',lastName='Machine',email='fixture@example.test'),metadata=dict(idp=[],unite=[]),syncFlag=False)
        suite.request('POST','/api/v1/users/profile',400,payload=dict(schema=schema,**{schema:profile}),positive=False,label='machine profile rejects missing upstream hash and cipher index')
        suite.db('local IDP synchronization schema unavailable',"SELECT t.SyncUserSchema FROM dbo.ExternalSource s JOIN dbo.ExternalType t ON t.ExternalTypeId=s.ExternalTypeId WHERE s.SourceCode='idp' FOR JSON PATH",lambda r:len(r)==1 and r[0].get('SyncUserSchema') is None)
        report['blocked'].append(dict(path='/api/v1/users/profile',scenario='genuine upstream synchronized profile creation',prerequisite='Configured external branding with matching SyncUserSchema, genuine legacy version-0 passwordHash/cIndex and crypto provider; real notification integration where required. Local idp SyncUserSchema is NULL. No upstream hash fabricated.'))
        suite.db('failed profile requests persist no credential',"SELECT COUNT(*) AS Count FROM Person.Credential WHERE UserName='"+profile['credential']['username']+"' FOR JSON PATH",lambda r:r[0]['Count']==0)
        print(('FAIL' if report['failures'] else 'PASS')+' machine-token password policy and invalid synchronized-profile checks')
        print('BLOCKED synchronized profile success: requires configured upstream schema, legacy credential and provider')
    except Exception as error:
        report['failures'].append(str(error))
        raise
    finally:
        suite.token=admin
        # Only the application and credential created above are disabled/deleted.
        if user_id:suite.request('DELETE','/api/v1/users/'+user_id,204)
        if credential_id:
            suite.request('PUT',credential_path+'/'+credential_id,200,payload=dict(status='Disabled'))
            suite.request('DELETE',credential_path+'/'+credential_id,204)
        if app_path:suite.request('DELETE',app_path,200)
        report['finished_at']=datetime.now(timezone.utc).isoformat()
        descriptor=os.open(ROOT/'.local/e2e-machine-report.json',os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
        with os.fdopen(descriptor,'w') as output:json.dump(report,output,indent=2)
    if report['failures']:raise SystemExit('Machine-user checks failed; see sanitized local report')


if __name__=='__main__':main()
