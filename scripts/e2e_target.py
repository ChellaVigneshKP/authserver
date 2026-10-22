"""Validated local HTTP/SQL target shared by acceptance suites and their login helper."""
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.parse

ROOT=Path(__file__).resolve().parent.parent
DATABASE_PATTERN=r'(?:AGSAuth(?:Providers_[a-f0-9]{8})?|AuthserverLifecycle_[a-f0-9]{32})'


def validate_target(config, environment):
    match=re.fullmatch(r'jdbc:sqlserver://(?:127\.0\.0\.1|localhost):15433;databaseName=('+DATABASE_PATTERN+r');encrypt=true;trustServerCertificate=true',config.get('DB_URL',''))
    if not match: raise ValueError('Only the approved loopback database configuration is supported')
    database=environment.get('AUTH_E2E_DB',match[1])
    if database != match[1]: raise ValueError('SQL database override must match the private application configuration')
    base=environment.get('AUTH_E2E_BASE') or environment.get('AUTH_SMOKE_BASE') or config.get('SERVER_BASE_PATH','http://localhost:9080/services')
    parsed=urllib.parse.urlsplit(base)
    if parsed.scheme != 'http' or parsed.hostname not in ('localhost','127.0.0.1') or parsed.username or parsed.password or parsed.path != '/services' or parsed.query or parsed.fragment:
        raise ValueError('Acceptance tests require an HTTP loopback application at /services')
    if database.startswith('AuthserverLifecycle_') and base != config.get('SERVER_BASE_PATH'):
        raise ValueError('Owned application HTTP target must match its private configuration')
    return base,database


def load_target():
    config=json.loads(Path(os.environ.get('AUTH_E2E_CONFIG',ROOT/'.local/dev.json')).read_text())
    base,database=validate_target(config,os.environ)
    return config,base,database


def sql_text(config,database,query,*,scalar=False):
    # Credentials stay in this child environment, never in command arguments or reports.
    if not re.fullmatch(DATABASE_PATTERN,database): raise ValueError('Unapproved database')
    command=['docker','exec','-i','-e','SQLCMDPASSWORD',os.environ.get('AUTH_SQL_CONTAINER','authserver-dev-sqlserver-1'),
             '/opt/mssql-tools18/bin/sqlcmd','-S','localhost','-U',config['DB_USERNAME'],'-d',database,'-C','-b','-h','-1']
    command+=['-W'] if scalar else ['-y','8000','-w','65535']
    result=subprocess.run(command,cwd=ROOT,env=os.environ|{'SQLCMDPASSWORD':config['DB_PASSWORD']},input='SET NOCOUNT ON;\n'+query+'\nGO\n',text=True,capture_output=True,check=True)
    return result.stdout.strip()
