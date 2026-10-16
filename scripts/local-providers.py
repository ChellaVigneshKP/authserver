#!/usr/bin/env python3
"""Launch an isolated local-provider lab; never reconfigure the normal development app."""
import json
import os
from pathlib import Path
import re
import runpy
import secrets
import shutil
import signal
import socket
import subprocess
import sys
import time
import urllib.request
import uuid

ROOT=Path(__file__).resolve().parent.parent
LAB=ROOT/'.local/providers'


def private(path,data):
    descriptor=os.open(path,os.O_CREAT|os.O_TRUNC|os.O_WRONLY,0o600)
    with os.fdopen(descriptor,'w') as output:json.dump(data,output,indent=2)


def ready(url,process):
    for _ in range(60):
        if process.poll() is not None:raise RuntimeError('Lab child exited; inspect private local logs')
        try:
            with urllib.request.urlopen(url,timeout=1) as response:
                if response.status==200:return
        except Exception:time.sleep(.5)
    raise RuntimeError('Lab readiness timeout; inspect private local logs')


def main():
    if len(sys.argv)!=2 or sys.argv[1]!='start':raise SystemExit('Usage: python3 scripts/local-providers.py start')
    jar=ROOT/'target/authserver-0.0.1-SNAPSHOT.jar'
    if not jar.exists():raise SystemExit('Build the application first: ./scripts/local-dev.py test')
    for port in (9082,9191):
        with socket.socket() as probe:
            probe.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
            try:probe.bind(('127.0.0.1',port))
            except OSError:raise SystemExit('Port '+str(port)+' is already in use; keep the existing lab or stop it first. No configuration changed.')
    LAB.mkdir(mode=0o700,parents=True,exist_ok=True)
    config=json.loads((ROOT/'.local/dev.json').read_text())
    database='AGSAuthProviders_'+secrets.token_hex(4)
    config.update(SERVER_PORT='9082',SERVER_BASE_PATH='http://localhost:9082/services',LOCAL_CALLBACK_URL='http://localhost:3001/',LOCAL_ADMIN_PASSWORD='L8!'+secrets.token_hex(15)+'qZ')
    config['DB_URL']=re.sub(r'databaseName=[^;]+','databaseName='+database,config['DB_URL'])
    provider=dict(control_secret=secrets.token_urlsafe(32),mfa_client='local-synthetic-provider',mfa_secret=secrets.token_hex(32))
    props={'spring.session.redis.namespace':'authserver:providers:'+database+':sessions','spring.cache.redis.key-prefix':'authserver:providers:'+database+':cache:',
           'secureauth.api.access.1.clientId':provider['mfa_client'],'secureauth.api.access.1.clientSecret':provider['mfa_secret'],
           'ascensus.url.unite.mobile':'http://localhost:9191','endpoints.web.cors.allowed-origins':'http://localhost:3001'}
    config['SPRING_APPLICATION_JSON']=json.dumps(props)
    private(LAB/'dev.json',config);private(LAB/'provider.json',provider)
    subprocess.run(['docker','compose','exec','-T','sqlserver','bash','-lc','SQLCMDPASSWORD="$MSSQL_SA_PASSWORD" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b'],input='CREATE DATABASE ['+database+'];\nGO\n',text=True,cwd=ROOT,check=True,capture_output=True)
    runtime=LAB/('authserver-'+str(uuid.uuid4())+'.jar');shutil.copy2(jar,runtime)
    children=[]
    def interrupt(*args):raise KeyboardInterrupt()
    signal.signal(signal.SIGTERM,interrupt)
    try:
        for command,name in [([sys.executable,str(ROOT/'scripts/local-provider-service.py'),str(LAB/'provider.json')],'provider'),(['java','-jar',str(runtime)],'app')]:
            with (LAB/(name+'.log')).open('w') as log:
                os.chmod(log.name,0o600)
                children.append(subprocess.Popen(command,cwd=ROOT,env=os.environ|config,stdout=log,stderr=subprocess.STDOUT))
        ready('http://localhost:9191/health',children[0]);ready(config['SERVER_BASE_PATH']+'/.well-known/openid-configuration',children[1])
        os.environ.update(AUTH_E2E_CONFIG=str(LAB/'dev.json'),AUTH_E2E_BASE=config['SERVER_BASE_PATH'],AUTH_E2E_DB=database)
        Acceptance=runpy.run_path(str(ROOT/'scripts/e2e-local.py'))['Acceptance']
        suite=Acceptance()
        suite.sql("UPDATE Client.MFARealm SET Uri='http://localhost:9191',Name='Default' WHERE MFARealmId=1;")
        rows=suite.sql("SELECT ClientId FROM Client.Application WHERE Name='Admin Portal' FOR JSON PATH")
        config['LOCAL_CLIENT_ID']=rows[0]['ClientId'];private(LAB/'dev.json',config)
        private(LAB/'state.json',dict(database=database,app_base=config['SERVER_BASE_PATH'],provider_base='http://localhost:9191'))
        print('Isolated provider lab ready: http://localhost:9082/services',flush=True)
        print('Synthetic delivery inbox: http://localhost:9191/inbox',flush=True)
        print('Run: python3 scripts/e2e-local-providers.py',flush=True)
        print('Local simulations only; Ctrl-C stops these processes and preserves the disposable lab database.',flush=True)
        while all(child.poll() is None for child in children):time.sleep(1)
        raise RuntimeError('Lab child exited; inspect .local/providers logs')
    except KeyboardInterrupt:pass
    finally:
        for child in children:
            if child.poll() is None:child.terminate()
        for child in children:
            try:child.wait(timeout=15)
            except subprocess.TimeoutExpired:child.kill();child.wait()


if __name__=='__main__':main()
