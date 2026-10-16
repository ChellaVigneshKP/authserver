#!/usr/bin/env python3
"""Interactive synthetic-provider demo; requires local-providers.py start."""
import json
import os
from pathlib import Path
import runpy
import threading

ROOT=Path(__file__).resolve().parent.parent
LAB=ROOT/'.local/providers'


def main():
    state=json.loads((LAB/'state.json').read_text())
    os.environ.update(AUTH_E2E_CONFIG=str(LAB/'dev.json'),AUTH_E2E_BASE=state['app_base'],AUTH_E2E_DB=state['database'])
    BrowserFlows=runpy.run_path(str(ROOT/'scripts/e2e-browser-flows.py'))['BrowserFlows']
    sample=runpy.run_path(str(ROOT/'scripts/sample-frontend.py'))
    suite=BrowserFlows();httpd=path=None
    try:
        suite.create_fixture()
        suite.admin.request('PUT','/api/v1/users/'+suite.user_id+'/security-settings',200,payload=dict(twoFactorEnabled=True))
        config,path=sample['register'](suite.admin,suite.org,callback='http://localhost:3001/')
        config['inbox_url']='http://localhost:9191/inbox'
        httpd=sample['server'](config)
        descriptor=os.open(LAB/'sample-account.json',os.O_CREAT|os.O_TRUNC|os.O_WRONLY,0o600)
        with os.fdopen(descriptor,'w') as output:json.dump(suite.user,output,indent=2)
        print('Local MFA sample: http://localhost:3001/',flush=True)
        print('Test credentials: .local/providers/sample-account.json',flush=True)
        print('Synthetic code inbox: http://localhost:9191/inbox',flush=True)
        print('Ctrl-C deletes this demo account and deactivates its own client/organization.',flush=True)
        threading.Event().wait()
    except KeyboardInterrupt:pass
    finally:
        if httpd:httpd.shutdown();httpd.server_close()
        if path:suite.admin.request('DELETE',path,200)
        suite.cleanup()


if __name__=='__main__':main()
