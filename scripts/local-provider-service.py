#!/usr/bin/env python3
"""Loopback-only synthetic provider contracts. Never a production MFA/biometric service."""
import base64
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
import hashlib
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import html
import json
from pathlib import Path
import re
import secrets
import sys
import threading
import time
import urllib.parse

class Providers:
    def __init__(self, config):
        self.config=config
        self.lock=threading.RLock()
        self.codes={}
        self.proofs={}
        self.inbox=[]
        self.events=[]

    def handler(self):
        state=self
        class Handler(BaseHTTPRequestHandler):
            protocol_version="HTTP/1.1"
            def log_message(self,*args):pass
            def send(self,status,body):
                data=json.dumps(body).encode()
                self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Cache-Control','no-store');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
            def authorized(self):return hmac.compare_digest(self.headers.get('Authorization',''),'Bearer '+state.config['control_secret'])
            def signed(self,raw):
                try:
                    date=self.headers['X-SA-Ext-Date'];stamp=parsedate_to_datetime(re.sub(r'(\d\d:\d\d:\d\d)\.\d+',r'\1',date)).timestamp()
                    if abs(time.time()-stamp)>120:return False
                    pair=base64.b64decode(self.headers['Authorization'].removeprefix('Basic '),validate=True).decode()
                    client,signature=pair.split(':',1)
                    if client != state.config['mfa_client']:return False
                    canonical='\n'.join([self.command,date,client,self.path.lstrip('/')])
                    if raw:canonical+='\n'+raw.decode()
                    expected=base64.b64encode(hmac.new(bytes.fromhex(state.config['mfa_secret']),canonical.encode(),hashlib.sha256).digest()).decode()
                    return hmac.compare_digest(expected,signature)
                except Exception:return False
            def do_GET(self):
                with state.lock:
                    if self.path=='/health':return self.send(200,{'mode':'local simulation','ready':True})
                    if self.path=='/inbox':
                        rows=''
                        for item in reversed(state.inbox):
                            current=state.codes.get(item.get('recipient'),{})
                            status='Superseded' if current.get('code')!=item.get('code') else ('Used' if current.get('used') else ('Expired' if current.get('expires',0)<time.time() else 'Available'))
                            issued=datetime.fromtimestamp(item.get('created',0),timezone.utc).strftime('%H:%M:%S UTC')
                            rows+='<tr>'+''.join('<td>'+html.escape(value)+'</td>' for value in [issued,item['kind'],item.get('code',''),status])+'</tr>'
                        data=('<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Local provider inbox</title><style>body{font:16px system-ui;max-width:850px;margin:50px auto;padding:20px;color:#20334d}td,th{padding:15px;text-align:left;border-bottom:1px solid #ddd}table{width:100%}</style><h1>Local provider inbox</h1><p>Synthetic test deliveries only. These codes are for the isolated local auth server.</p><p>Refresh after requesting a code.</p><table><tr><th>Issued</th><th>Delivery</th><th>Code</th><th>Status</th></tr>'+rows+'</table>').encode()
                        self.send_response(200);self.send_header('Content-Type','text/html; charset=utf-8');self.send_header('Cache-Control','no-store');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data);return
                    if self.path=='/control/state':
                        if not self.authorized():return self.send(401,{'error':'unauthorized'})
                        return self.send(200,{'inbox':state.inbox,'events':state.events})
                    if re.fullmatch(r'/Default/api/v2/users/[^/]+/factors',self.path):
                        if not self.signed(b''):return self.send(401,{'status':'invalid'})
                        state.events.append({'operation':'factors','authenticated':True})
                        return self.send(200,{'status':'found','factors':[{'type':'email','id':'Email1','value':'tester@example.test','capabilities':['email']} ]})
                    return self.send(404,{'error':'not found'})
            def do_POST(self):
                try:
                    size=int(self.headers.get('Content-Length','0'))
                    if size<0 or size>65536:return self.send(413,{'error':'payload too large'})
                    if self.headers.get('Transfer-Encoding','').lower()=='chunked':
                        chunks=[];total=0
                        while True:
                            length=int(self.rfile.readline(128).split(b';')[0],16)
                            if length==0:
                                while self.rfile.readline(65536) not in (b'\r\n',b'\n',b''):pass
                                break
                            total+=length
                            if total>65536:return self.send(413,{'error':'payload too large'})
                            chunk=self.rfile.read(length)
                            if len(chunk)!=length or self.rfile.read(2)!=b'\r\n':raise ValueError('Invalid chunk')
                            chunks.append(chunk)
                        raw=b''.join(chunks)
                    else:raw=self.rfile.read(size)
                    body=json.loads(raw)
                    if not isinstance(body,dict):return self.send(400,{'error':'JSON object required'})
                except Exception:return self.send(400,{'error':'invalid JSON'})
                with state.lock:
                    if self.path.startswith('/control/'):
                        if not self.authorized():return self.send(401,{'error':'unauthorized'})
                        if self.path=='/control/expire-code':
                            code=state.codes.get(body.get('session'))
                            if not code:return self.send(404,{'error':'missing code'})
                            code['expires']=0;return self.send(200,{'expired':True})
                        if self.path=='/control/proof':
                            if not re.fullmatch(r'[a-fA-F0-9-]{36}',body.get('user','')):return self.send(400,{'error':'invalid user'})
                            proof=secrets.token_urlsafe(32);state.proofs[proof]=dict(user=body['user'].replace('-',''),device=body['device'],expires=time.time()+body.get('ttl',120),used=False)
                            return self.send(201,{'proof':proof})
                        return self.send(404,{'error':'unknown control'})
                    if self.path in ('/Default/api/v2/auth','/Default/api/v1/otp/validate'):
                        if not self.signed(raw):return self.send(401,{'status':'invalid'})
                        session=body.get('user_id')
                        if self.path.endswith('/auth'):
                            if body.get('factor_id')!='Email1' or body.get('type')!='email':return self.send(400,{'status':'invalid'})
                            code=f'{secrets.randbelow(1000000):06d}'
                            state.codes[session]=dict(code=code,expires=time.time()+120,used=False)
                            state.inbox.append(dict(kind='Synthetic MFA email',recipient=session,code=code,created=time.time()))
                            state.events.append(dict(operation='issue',session=session,authenticated=True))
                            return self.send(200,{'status':'valid','user_id':session})
                        entry=state.codes.get(session,{})
                        valid=bool(entry and not entry['used'] and entry['expires']>time.time() and hmac.compare_digest(entry['code'],str(body.get('otp',''))))
                        if valid:entry['used']=True
                        state.events.append(dict(operation='verify',session=session,authenticated=True,accepted=valid))
                        return self.send(200,{'status':'valid' if valid else 'invalid','message':'Code accepted' if valid else 'Code rejected'})
                    if self.path=='/mobile1e/api/v1/mobilemembersession/validateBiometricToken':
                        proof=state.proofs.get(body.get('biometricToken'),{})
                        valid=bool(proof and not proof['used'] and proof['expires']>time.time() and proof['device']==body.get('deviceUuid') and body.get('planId')=='idp')
                        state.events.append(dict(operation='biometric',accepted=valid))
                        if not valid:return self.send(401,{'error':'invalid synthetic proof'})
                        proof['used']=True
                        return self.send(200,{'_embedded':{'item':{'uuidMemberId':proof['user'],'errors':[]}}})
                    return self.send(404,{'error':'not found'})
        return Handler


def main():
    config=json.loads(Path(sys.argv[1]).read_text())
    state=Providers(config)
    server=ThreadingHTTPServer(('127.0.0.1',9191),state.handler())
    try:server.serve_forever()
    except KeyboardInterrupt:pass
    finally:server.server_close()

if __name__=='__main__':main()
