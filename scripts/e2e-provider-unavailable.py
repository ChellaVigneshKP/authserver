#!/usr/bin/env python3
"""Actual HTTP failure-path checks with missing providers. Never claims provider success."""
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
import urllib.parse
import urllib.request

ROOT=Path(__file__).resolve().parent.parent
BrowserFlows=runpy.run_path(str(ROOT/'scripts/e2e-browser-flows.py'))['BrowserFlows']
NoRedirect=runpy.run_path(str(ROOT/'scripts/e2e-local.py'))['NoRedirect']


def main():
    suite=BrowserFlows()
    suite.report['suite']='missing-provider rejection only; no successful external provider qualification'
    suite.report['blocked']=['Real MFA delivery/verification, notifications, biometric approval and synchronized profile success: no provider environment available.']
    try:
        suite.create_fixture()
        suite.admin.request('PUT','/api/v1/users/'+suite.user_id+'/security-settings',200,payload=dict(twoFactorEnabled=True))
        def fresh():
            suite.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),NoRedirect())
            suite.token=None
            verifier=secrets.token_urlsafe(48)
            params=dict(response_type='code',client_id=suite.config['LOCAL_CLIENT_ID'],redirect_uri=suite.config.get('LOCAL_CALLBACK_URL',suite.base+'/local/callback'),scope='openid profile email metadata',state=secrets.token_hex(20),code_challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('='),code_challenge_method='S256',branding='idp',**{'x-request-datetime':datetime.now(timezone.utc).isoformat()})
            h,_=suite.request('GET','/oauth2/authorize?'+urllib.parse.urlencode(params),302,authenticated=False,positive=False)
            _,body=suite.request('GET',h['Location'],200,authenticated=False,positive=False)
            return re.search(r'name="_csrf"[^>]*value="([^"]+)"',body)[1],params
        def count_tokens():
            return suite.sql(f"SELECT COUNT(*) AS Count FROM Token.Token WHERE SubjectId='{suite.user['username']}' FOR JSON PATH")
        csrf,params=fresh()
        before=count_tokens()
        h,_=suite.request('POST','/login',302,authenticated=False,positive=False,form=dict(username=suite.user['username'],password=base64.b64encode(suite.user['password'].encode()).decode(),_csrf=csrf),label='correct password requires enrolled MFA before grant')
        suite.assert_state('MFA-enabled password login does not issue code','code=' not in h['Location'] and '/mfa/' in h['Location'])
        suite.request('GET',h['Location'],503,authenticated=False,positive=False,label='missing MFA factor provider gives controlled 503')
        # Get the current session's CSRF token after the password authentication rotation.
        _,body=suite.request('GET','/login',200,authenticated=False,positive=False)
        csrf=re.search(r'name="_csrf"[^>]*value="([^"]+)"',body)[1]
        h,_=suite.request('POST','/mfa/login/request-pin',302,authenticated=False,positive=False,form=dict(option='synthetic-email',_csrf=csrf),label='unavailable OTP generation cannot complete MFA')
        suite.assert_state('missing OTP provider returns to factor request','/mfa/login/request-pin' in h['Location'])
        suite.request('POST','/mfa/login/verify-pin',(200,302,503),authenticated=False,positive=False,form=dict(pin='000000',_csrf=csrf),label='synthetic OTP cannot authenticate with missing provider')
        h,_=suite.request('GET','/oauth2/authorize?'+urllib.parse.urlencode(params|{'continue':''}),(302,400),authenticated=False,positive=False,label='PRE_AUTH cannot bypass MFA at authorization endpoint')
        suite.assert_state('MFA bypass attempt never returns authorization code','code=' not in h.get('Location',''))
        suite.request('GET','/oauth2/sso',401,authenticated=False,positive=False,label='incomplete MFA receives no SSO access token')
        suite.assert_state('missing MFA provider issues no SQL tokens',count_tokens()==before)
        suite.report['database_checks'].append(dict(id='failed MFA attempts add no SQL access tokens',passed=True))
        csrf,_=fresh()
        h,_=suite.request('POST','/login-biometric',(302,401,503),authenticated=False,positive=False,form=dict(biometricToken='synthetic-not-a-provider-proof',deviceUuid='00000000-0000-0000-0000-000000000001',_csrf=csrf),label='unconfigured biometric provider rejects synthetic proof')
        suite.assert_state('biometric failure issues no authorization code','code=' not in h.get('Location',''))
        suite.request('GET','/oauth2/sso',401,authenticated=False,positive=False,label='biometric rejection receives no SSO access token')
        suite.assert_state('biometric rejection issues no SQL tokens',count_tokens()==before)
        suite.report['database_checks'].append(dict(id='biometric failure adds no SQL access tokens',passed=True))
    except Exception as error:
        suite.report['failures'].append(dict(case='provider unavailability',type=type(error).__name__,message=str(error)))
    finally:
        try:suite.cleanup()
        except Exception as error:suite.report['failures'].append(dict(case='owned fixture cleanup',type=type(error).__name__))
        suite.report['finished_at']=datetime.now(timezone.utc).isoformat()
        descriptor=os.open(ROOT/'.local/e2e-provider-unavailable-report.json',os.O_CREAT|os.O_TRUNC|os.O_WRONLY,0o600)
        with os.fdopen(descriptor,'w') as output:json.dump(suite.report,output,indent=2)
    print(('FAIL' if suite.report['failures'] else 'PASS')+' missing-provider MFA/biometric rejection and no-token SQL checks')
    if suite.report['failures']:raise SystemExit(1)


if __name__=='__main__':main()
