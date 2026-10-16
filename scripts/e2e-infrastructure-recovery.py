#!/usr/bin/env python3
"""Real HTTP outage/recovery test using only its own disposable Redis container and app.

Never pauses/restarts the normal development Redis, SQL Server or Docker Desktop.
"""
import base64
from datetime import datetime, timezone
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT=Path(__file__).resolve().parent.parent


def main():
    config=json.loads((ROOT/'.local/dev.json').read_text())
    if not config['DB_URL'].startswith('jdbc:sqlserver://127.0.0.1:15433;'):
        raise SystemExit('Only the documented local development database is supported')
    for port in (9084,16380):
        with socket.socket() as probe:
            probe.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
            try:probe.bind(('127.0.0.1',port))
            except OSError:raise SystemExit('Test port '+str(port)+' is already occupied')
    container='authserver-outage-'+secrets.token_hex(6)
    report=dict(started_at=datetime.now(timezone.utc).isoformat(),checks=[],assertions=[],failures=[],scope='Only task-owned disposable Redis and app; SQL migrations already applied by normal local test gate')
    app=None;created=False;runtime=None
    base='http://localhost:9084/services'
    def docker(*args):return subprocess.run(['docker',*args],cwd=ROOT,text=True,capture_output=True,timeout=30,check=True)
    def verify(label,value):
        report['assertions'].append(dict(id=label,passed=bool(value)))
        if not value:raise AssertionError(label)
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self,*args,**kwargs):return None
    def authorization():
        verifier=secrets.token_urlsafe(48)
        query=urllib.parse.urlencode(dict(response_type='code',client_id=config['LOCAL_CLIENT_ID'],redirect_uri=config.get('LOCAL_CALLBACK_URL','http://localhost:9080/services/local/callback'),scope='openid profile email metadata',state=secrets.token_hex(16),code_challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('='),code_challenge_method='S256',branding='idp',**{'x-request-datetime':datetime.now(timezone.utc).isoformat()}))
        return base+'/oauth2/authorize?'+query
    def request(url,expected,label):
        opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),NoRedirect())
        try:response=opener.open(urllib.request.Request(url,headers={'User-Agent':'AuthserverAcceptance/1.0','Accept-Language':'en-US'}),timeout=20)
        except urllib.error.HTTPError as error:response=error
        body=response.read().decode()
        report['checks'].append(dict(id=label,method='GET',path=urllib.parse.urlsplit(url).path.removeprefix('/services'),status=response.status,expected=[expected],passed=response.status==expected,positive=False))
        verify(label,response.status==expected)
        return response,body
    try:
        docker('run','--rm','--detach','--name',container,'--publish','127.0.0.1:16380:6379','redis:7-alpine')
        created=True
        runtime=ROOT/'.local/runs'/('authserver-outage-'+str(uuid.uuid4())+'.jar')
        shutil.copy2(ROOT/'target/authserver-0.0.1-SNAPSHOT.jar',runtime)
        props=json.loads(config.get('SPRING_APPLICATION_JSON','{}'))
        props.update({'spring.session.redis.namespace':container+':sessions','spring.cache.redis.key-prefix':container+':cache:',
                      'app.local.bootstrap.enabled':False,'app.datasource.hikari.minimum-idle':1,'app.datasource.hikari.maximum-pool-size':4})
        env=os.environ|config|dict(SERVER_PORT='9084',SERVER_BASE_PATH=base,REDIS_PORT='16380',SPRING_APPLICATION_JSON=json.dumps(props))
        log=ROOT/'.local/infrastructure-outage.log'
        with log.open('w') as output:
            os.chmod(log,0o600)
            app=subprocess.Popen(['java','-XX:ActiveProcessorCount=2','-Xmx384m','-jar',str(runtime)],cwd=ROOT,env=env,stdout=output,stderr=subprocess.STDOUT)
        for _ in range(60):
            if app.poll() is not None:raise AssertionError('Outage-test app exited before readiness')
            try:
                with urllib.request.urlopen(base+'/.well-known/openid-configuration',timeout=1) as response:
                    if response.status==200:break
            except Exception:time.sleep(.5)
        else:raise AssertionError('Outage-test app readiness timeout')
        request(authorization(),302,'healthy Redis permits authorization redirect')
        docker('pause',container)
        response,body=request(authorization(),503,'paused test Redis yields controlled 503')
        verify('outage response has retry guidance and no internal error details',response.headers.get('Retry-After')=='5' and body=='Service temporarily unavailable. Please try again shortly.')
        docker('unpause',container)
        recovered=False
        # Readiness probes have no credentials or grant side effects; require a fresh working auth request.
        for _ in range(15):
            opener=urllib.request.build_opener(NoRedirect())
            try:
                response=opener.open(urllib.request.Request(authorization(),headers={'User-Agent':'AuthserverAcceptance/1.0','Accept-Language':'en-US'}),timeout=5)
            except urllib.error.HTTPError as error:response=error
            except (urllib.error.URLError,TimeoutError):time.sleep(.5);continue
            if response.status==302:recovered=True;break
            time.sleep(.5)
        verify('authorization recovers after Redis resumes without restarting app',recovered)
        request(authorization(),302,'resumed Redis permits authorization redirect')
    except Exception as error:
        report['failures'].append(dict(type=type(error).__name__,message=str(error) if isinstance(error,AssertionError) else 'Inspect private outage-test logs'))
    finally:
        if created:
            subprocess.run(['docker','unpause',container],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=15)
        if app and app.poll() is None:
            app.terminate()
            try:app.wait(timeout=15)
            except subprocess.TimeoutExpired:app.kill();app.wait()
        if created:subprocess.run(['docker','rm','--force',container],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=20)
        if runtime:runtime.unlink(missing_ok=True)
        report['finished_at']=datetime.now(timezone.utc).isoformat()
        descriptor=os.open(ROOT/'.local/e2e-infrastructure-report.json',os.O_CREAT|os.O_TRUNC|os.O_WRONLY,0o600)
        with os.fdopen(descriptor,'w') as output:json.dump(report,output,indent=2)
    print(('FAIL' if report['failures'] else 'PASS')+' real Redis outage returns 503 and recovers without app restart')
    if report['failures']:raise SystemExit(1)


if __name__=='__main__':main()
