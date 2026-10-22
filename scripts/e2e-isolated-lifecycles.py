#!/usr/bin/env python3
"""Run certificate and protocol campaigns on one owned UUID database and immutable JAR.

Uses existing loopback SQL/Redis services without resetting, stopping or restarting them.
Only the child Java process, this run's UUID Redis prefix and this run's database are removed.
"""
import hashlib
import json
import os
import re
from pathlib import Path
import secrets
import shutil
import signal
import socket
import subprocess
import sys
import time
import urllib.request
import uuid

from e2e_target import load_target, sql_text, validate_target
from e2e_provenance import source_provenance

ROOT=Path(__file__).resolve().parent.parent


def private_json(path,value):
    descriptor=os.open(path,os.O_CREAT|os.O_TRUNC|os.O_WRONLY,0o600)
    with os.fdopen(descriptor,'w') as output: json.dump(value,output,indent=2)


def configured_redis_databases(config):
    raw = json.loads(config.get('SPRING_APPLICATION_JSON', '{}'))
    properties = {}
    def flatten(value, prefix=''):
        for key, item in value.items():
            name = prefix+key
            if isinstance(item, dict):
                flatten(item, name+'.')
            else:
                properties[name] = item
    flatten(raw)
    databases = []
    for name, default in [('spring.data.redis.database', 0), ('spring.data.redis.cache.database', 1)]:
        value = properties.get(name, config.get(name.upper().replace('.', '_'), default))
        if isinstance(value, bool) or not re.fullmatch(r'0|[1-9][0-9]*', str(value)) or not 0 <= int(value) <= 15:
            raise ValueError('Refusing unsafe local Redis database')
        databases.append(int(value))
    return sorted(set(databases))


def cleanup_redis_owned(config, namespace, run=subprocess.run):
    if not re.fullmatch(r'authserver:lifecycle:[0-9a-f]{32}:', namespace):
        raise ValueError('Refusing unsafe Redis ownership prefix')
    databases = configured_redis_databases(config)
    evidence = []
    for database in databases:
        command = ['docker', 'exec', 'authserver-dev-redis-1', 'redis-cli', '-n', str(database)]
        def scan():
            keys = run(command+['--scan', '--pattern', namespace+'*'], capture_output=True, text=True, check=True).stdout.splitlines()
            if any(not key.startswith(namespace) for key in keys):
                raise ValueError('Redis scan returned a key outside this run')
            return keys
        keys = scan()
        removed = 0
        for start in range(0, len(keys), 100):
            result = run(command+['DEL']+keys[start:start+100], capture_output=True, text=True, check=True)
            removed += int(result.stdout.strip())
        remaining = len(scan())
        evidence.append(dict(database=database, removed=removed, remaining=remaining))
        if remaining:
            raise RuntimeError('Owned Redis keys remain after cleanup')
    return evidence


def main():
    def interrupt(*_): raise KeyboardInterrupt()
    signal.signal(signal.SIGTERM,interrupt)
    original,_,source_database=load_target()
    if source_database != 'AGSAuth': raise SystemExit('Launch from the normal private local configuration')
    artifact=ROOT/'target/authserver-0.0.1-SNAPSHOT.jar'
    if not artifact.is_file(): raise SystemExit('Build the application with scripts/local-dev.py test first')
    run_id=uuid.uuid4().hex
    database='AuthserverLifecycle_'+run_id
    directory=ROOT/'.local/b6-campaigns'/run_id
    directory.mkdir(mode=0o700,parents=True)
    namespace='authserver:lifecycle:'+run_id+':'
    with socket.socket() as probe:
        probe.bind(('127.0.0.1',0)); port=probe.getsockname()[1]
    base='http://localhost:'+str(port)+'/services'
    config=original|dict(DB_URL=original['DB_URL'].replace('databaseName=AGSAuth;','databaseName='+database+';'),
            SERVER_PORT=str(port),SERVER_BASE_PATH=base,LOCAL_CALLBACK_URL=base+'/local/callback',
            LOCAL_BOOTSTRAP_ENABLED='true',LOCAL_ADMIN_USERNAME='lifecycle_'+run_id,
            LOCAL_ADMIN_PASSWORD='L8!'+secrets.token_hex(20)+'qZ')
    properties=json.loads(config.get('SPRING_APPLICATION_JSON','{}'))
    properties.update({'spring.session.redis.namespace':namespace+'session','spring.cache.redis.key-prefix':namespace+'cache:',
                       'logging.level.root':'WARN','logback.access.enabled':'false'})
    config['SPRING_APPLICATION_JSON']=json.dumps(properties)
    validate_target(config,{'AUTH_E2E_BASE':base,'AUTH_E2E_DB':database})
    config_path=directory/'dev.json';private_json(config_path,config)
    runtime=directory/'authserver.jar';shutil.copy2(artifact,runtime);os.chmod(runtime,0o400)
    report=dict(**source_provenance(),database=database,app_base=base,artifact_sha256=hashlib.sha256(runtime.read_bytes()).hexdigest(),
                provider='unconfigured; no external provider success claimed',campaigns=[],cleanup={})
    owned=False;child=None
    try:
        sql_text(original,'AGSAuth','CREATE DATABASE ['+database+'];',scalar=True);owned=True
        with (directory/'app.log').open('w') as log:
            os.chmod(log.name,0o600)
            child=subprocess.Popen(['java','-jar',str(runtime)],cwd=ROOT,env=os.environ|config,stdout=log,stderr=subprocess.STDOUT)
        for _ in range(180):
            if child.poll() is not None: raise RuntimeError('Owned app exited; inspect private run log')
            try:
                with urllib.request.urlopen(base+'/.well-known/openid-configuration',timeout=1) as response:
                    discovery=json.load(response)
                if discovery.get('issuer')==base: break
            except (OSError,ValueError): pass
            time.sleep(.5)
        else: raise RuntimeError('Owned app readiness timeout')
        # Bootstrap unique username/password and random client ID come from this same SQL DB.
        rows=json.loads(sql_text(config,database,"SELECT DB_NAME() AS DatabaseName,ClientId FROM Client.Application WHERE Name='Admin Portal' FOR JSON PATH"))
        if len(rows)!=1 or rows[0]['DatabaseName']!=database: raise RuntimeError('Owned SQL/HTTP target mismatch')
        config['LOCAL_CLIENT_ID']=rows[0]['ClientId'];private_json(config_path,config)
        report['sql_target_verified']=True
        env=os.environ|{'AUTH_E2E_CONFIG':str(config_path),'AUTH_E2E_DB':database,'AUTH_E2E_BASE':base,'AUTH_SMOKE_BASE':base}
        env.pop('LOCAL_CLIENT_ID',None)
        for script,filename in [('e2e-certificates.py','certificate-e2e-report.json'),('e2e-protocol-variants.py','e2e-protocol-report.json')]:
            source=ROOT/'.local'/filename
            source.unlink(missing_ok=True)
            with (directory/(script+'.log')).open('w') as log:
                os.chmod(log.name,0o600)
                result=subprocess.run([sys.executable,str(ROOT/'scripts'/script)],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
            campaign=dict(script=script,passed=result.returncode==0 and source.exists())
            source=ROOT/'.local'/filename
            if source.exists():
                os.chmod(source,0o600)
                evidence=json.loads(source.read_text());private_json(directory/filename,evidence)
                campaign['passed']=campaign['passed'] and evidence.get('database')==database and evidence.get('app_base')==base
                campaign.update(http_checks=len(evidence.get('checks',[])),sql_checks=len(evidence.get('database_checks',[])))
                campaign['passed']=campaign['passed'] and campaign['http_checks']>0 and campaign['sql_checks']>0
            report['campaigns'].append(campaign)
            print(('PASS' if campaign['passed'] else 'FAIL')+' '+script,flush=True)
        report['final_sql']=json.loads(sql_text(config,database,"SELECT (SELECT COUNT(*) FROM Partner.Certificate) AS Certificates,(SELECT COUNT(*) FROM Client.Credential) AS Credentials,(SELECT COUNT(*) FROM Token.Token) AS Tokens FOR JSON PATH"))[0]
        report['passed']=all(campaign['passed'] for campaign in report['campaigns'])
    except Exception as error:
        report.update(passed=False,error=type(error).__name__)
        print('FAIL isolated campaign setup/execution: '+type(error).__name__,flush=True)
    finally:
        if child is not None:
            if child.poll() is None: child.terminate()
            try: child.wait(timeout=20)
            except subprocess.TimeoutExpired: child.kill();child.wait()
            report['cleanup']['owned_process_stopped']=True
        if owned:
            # Namespace is generated by this invocation. No other session/cache keys are touched.
            try:
                report['cleanup']['redis_databases']=cleanup_redis_owned(config,namespace)
            except Exception as error:
                report['cleanup']['redis_error']=type(error).__name__;report['passed']=False
            try:
                sql_text(original,'AGSAuth','ALTER DATABASE ['+database+'] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE ['+database+'];',scalar=True)
                report['cleanup']['owned_database_dropped']=sql_text(original,'AGSAuth',"SELECT COUNT(*) FROM sys.databases WHERE name='"+database+"'",scalar=True)=='0'
                report['passed']=report.get('passed',False) and report['cleanup']['owned_database_dropped']
            except Exception as error:
                report['cleanup']['database_error']=type(error).__name__;report['passed']=False
        private_json(directory/'report.json',report)
        private_json(ROOT/'.local/b6-isolated-campaign-report.json',report)
    print('Evidence: '+str(directory.relative_to(ROOT))+'/report.json',flush=True)
    return 0 if report.get('passed') else 1


if __name__=='__main__': sys.exit(main())
