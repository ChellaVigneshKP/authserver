#!/usr/bin/env python3
"""Run real Chrome JavaScript login/forms/logout, then verify SQL; disposable local fixtures only."""
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import runpy
import secrets
import subprocess

ROOT = Path(__file__).resolve().parent.parent
BrowserFlows = runpy.run_path(str(ROOT/'scripts/e2e-browser-flows.py'))['BrowserFlows']
Sample = runpy.run_path(str(ROOT/'scripts/sample-frontend.py'))


def main():
    suite = BrowserFlows()
    suite.report['suite'] = 'real Chrome browser and SQL acceptance'
    target = ROOT/'.local/e2e-browser-engine-report.json'
    target.unlink(missing_ok=True)
    httpd = client_path = None
    try:
        suite.create_fixture()
        before = suite.user_state()
        public, client_path = Sample['register'](suite.admin,suite.org,callback=os.environ.get('AUTH_E2E_FRONTEND_CALLBACK','http://localhost:3000/'))
        httpd = Sample['server'](public)
        profile = dict(firstName='Chloé',lastName='Browser',email=suite.user['username']+'.browser@example.test',
                       confirmEmail=suite.user['username']+'.browser@example.test',phoneNumber='(202) 555-0198',secondaryPhoneNumber='(202) 555-0199')
        inputs = dict(**public,screenshot=str(ROOT/'.local/sample-frontend.png'),username=suite.user['username'],password=suite.user['password'],
                      new_password='N9!'+secrets.token_hex(15)+'uQ',profile=profile)
        result = subprocess.run(['node',str(ROOT/'scripts/e2e-browser-engine.mjs')],input=json.dumps(inputs),
                                text=True,capture_output=True,cwd=ROOT,timeout=180)
        # Never print subprocess stderr: it can include browser URLs carrying authorization codes.
        try:
            browser = json.loads(result.stdout)
        except json.JSONDecodeError:
            raise AssertionError('Browser runner failed before emitting a sanitized report') from None
        suite.report['browser'] = browser
        suite.report['assertions'].extend(browser['assertions'])
        suite.report['failures'].extend(browser['failures'])
        if result.returncode:
            raise AssertionError('Real browser journey failed; see sanitized browser report')
        after = suite.user_state()
        suite.assert_state('real browser profile fields persist in SQL',all(after[k] == value for k,value in dict(FirstName=profile['firstName'],LastName=profile['lastName'],Email=profile['email'],PhoneNumber='+12025550198',SecondaryPhoneNumber='+12025550199').items()))
        suite.report['database_checks'].append(dict(id='browser profile fields persisted in SQL',passed=True))
        suite.assert_state('real browser password changes SQL binary hash',before['PasswordDigest'] != after['PasswordDigest'])
        suite.report['database_checks'].append(dict(id='browser password hash changed in SQL',passed=True))
    except Exception as error:
        suite.report['failures'].append(dict(case='browser engine campaign',type=type(error).__name__))
    finally:
        try:
            if httpd:
                httpd.shutdown()
                httpd.server_close()
            if client_path:suite.admin.request('DELETE',client_path,200)
            suite.cleanup()
        except Exception as error:
            suite.report['failures'].append(dict(case='owned fixture cleanup',type=type(error).__name__))
        suite.report['finished_at'] = datetime.now(timezone.utc).isoformat()
        descriptor = os.open(target,os.O_CREAT|os.O_TRUNC|os.O_WRONLY,0o600)
        with os.fdopen(descriptor,'w') as output:json.dump(suite.report,output,indent=2)
    print(('FAIL' if suite.report['failures'] else 'PASS')+' actual Chrome login, JavaScript profile/password forms, logout and SQL persistence')
    if suite.report['failures']:raise SystemExit(1)


if __name__ == '__main__':main()
