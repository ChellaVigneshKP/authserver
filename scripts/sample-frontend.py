#!/usr/bin/env python3
"""Local-only relying-party sample. Registers its own PKCE client via the actual admin API."""
import base64
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
import os
import secrets
from pathlib import Path
import runpy
import threading
import urllib.parse

ROOT = Path(__file__).resolve().parent.parent
Acceptance = runpy.run_path(str(ROOT/'scripts/e2e-local.py'))['Acceptance']


def register(suite, org, callback='http://localhost:3000/'):
    root = '/api/v1/organizations/'+org+'/applications'
    _, created = suite.request('POST',root,201,payload=dict(name=suite.prefix+' sample frontend',description='Local browser PKCE test client',type='WEB',authMethod='PKCE'))
    path = root+'/'+created['id']
    suite.request('PUT',path+'/urls',200,payload=dict(applicationUrl=callback,returnUrls=[callback],logoutUrls=[callback]))
    suite.request('PUT',path+'/settings',200,payload=dict(authCodeTimeToLive=120,accessTokenTimeToLive=600,deviceCodeTimeToLive=120,refreshTokenTimeToLive=3600,reuseRefreshTokens=False,maxRequestTransitTime=30))
    _, app = suite.request('GET',path,200)
    return dict(base=suite.base,client_id=app['clientId'],callback=callback), path


def server(config):
    if urllib.parse.urlparse(config['callback']).hostname != 'localhost':
        raise ValueError('Sample callback must use localhost')
    class Handler(SimpleHTTPRequestHandler):
        def __init__(self,*args,**kwargs):
            super().__init__(*args,directory=str(ROOT/'examples/test-client'),**kwargs)

        def do_GET(self):
            if urllib.parse.urlsplit(self.path).path == '/config.json':
                data=json.dumps(config).encode()
                self.send_response(200)
                self.send_header('Content-Type','application/json')
                self.send_header('Cache-Control','no-store')
                self.send_header('Content-Length',str(len(data)))
                self.end_headers()
                self.wfile.write(data)
            else:super().do_GET()

        def log_message(self,*args):
            pass  # Callback URLs contain authorization codes: never access-log them.

        def end_headers(self):
            self.send_header('Referrer-Policy','no-referrer')
            self.send_header('X-Content-Type-Options','nosniff')
            self.send_header('Cache-Control','no-store')
            super().end_headers()
    port=urllib.parse.urlparse(config['callback']).port or 80
    httpd=ThreadingHTTPServer(('127.0.0.1',port),Handler)
    thread=threading.Thread(target=httpd.serve_forever,daemon=True)
    thread.start()
    return httpd


def main():
    suite=Acceptance()
    org=path=httpd=user_id=None
    try:
        suite.login()
        org=suite.organization()
        config,path=register(suite,org)
        username='sample_'+secrets.token_hex(6)
        password='S8!'+secrets.token_hex(15)+'qZ'
        _,user=suite.request('POST','/api/v1/users',201,payload=dict(firstName='Sample',lastName='Tester',username=username,email=username+'@example.test',password=base64.b64encode(password.encode()).decode(),phoneNumber='+19999999999',orgGuid=org,branding='idp',syncFlag=False))
        user_id=user['id']
        suite.request('PUT','/api/v1/users/'+user_id+'/security-settings',200,payload=dict(twoFactorEnabled=False))
        credentials=ROOT/'.local/sample-frontend-account.json'
        descriptor=os.open(credentials,os.O_CREAT|os.O_TRUNC|os.O_WRONLY,0o600)
        with os.fdopen(descriptor,'w') as output:json.dump(dict(username=username,password=password),output,indent=2)
        httpd=server(config)
        print('Sample frontend ready: http://localhost:3000/',flush=True)
        print('Disposable test-account credentials: .local/sample-frontend-account.json (private local file).',flush=True)
        print('Ctrl-C deletes the test account and deactivates the sample client.',flush=True)
        threading.Event().wait()
    except KeyboardInterrupt:pass
    finally:
        if httpd:httpd.shutdown();httpd.server_close()
        if user_id:suite.request('DELETE','/api/v1/users/'+user_id,204)
        if path:suite.request('DELETE',path,200)
        if org:suite.request('PUT','/api/v1/organizations/'+org,200,payload=dict(name=suite.prefix,description='Stopped sample frontend',status='Revoked'))


if __name__ == '__main__':main()
