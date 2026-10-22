#!/usr/bin/env python3
"""Explicit, local-only development setup. Never resets a user or deletes a volume."""
import getpass
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parent.parent
LOCAL = ROOT / '.local'
CONFIG = LOCAL / 'dev.json'


def run(command, environment=None):
    subprocess.run(command, cwd=ROOT, env=environment, check=True)


def configure():
    LOCAL.mkdir(mode=0o700, exist_ok=True)
    if CONFIG.exists():
        print('Local configuration exists; keeping credentials and keystore unchanged.')
        ensure_signing_key(json.loads(CONFIG.read_text()))
        return
    password = os.environ.get('LOCAL_ADMIN_PASSWORD') or getpass.getpass('New local admin password (12+ characters): ')
    if len(password) < 12:
        raise SystemExit('Use a local admin password with at least 12 characters.')
    values = dict(
        SPRING_PROFILES_ACTIVE='local',
        DB_URL='jdbc:sqlserver://127.0.0.1:15433;databaseName=AGSAuth;encrypt=true;trustServerCertificate=true',
        DB_USERNAME='sa', DB_PASSWORD='Db!'+secrets.token_hex(20),
        REDIS_HOST='127.0.0.1', REDIS_PORT='16379',
        KEY_STORE_PASSWORD=secrets.token_urlsafe(32),
        AUTH_HASHER_PASSWORD=secrets.token_urlsafe(32),
        AUTH_HASHER_PEPPER=str(uuid.uuid4()),
        AUTH_HASHER_KEYSTORE=(LOCAL / 'password.jceks').as_uri(),
        LOCAL_BOOTSTRAP_ENABLED='true',
        LOCAL_ADMIN_USERNAME=os.environ.get('LOCAL_ADMIN_USERNAME', 'admin'),
        LOCAL_ADMIN_PASSWORD=password,
        MAVEN_USER_HOME=str(LOCAL/'maven'),
    )
    env = os.environ | values
    run(['keytool', '-genseckey', '-keyalg', 'AES', '-keysize', '256', '-storetype', 'JCEKS',
         '-keystore', str(LOCAL/'password.jceks'), '-alias', values['AUTH_HASHER_PEPPER'],
         '-storepass:env', 'AUTH_HASHER_PASSWORD', '-keypass:env', 'AUTH_HASHER_PASSWORD'], env)
    for path, content in [(CONFIG, json.dumps(values, indent=2)),
                          (LOCAL/'sqlserver.env', 'MSSQL_SA_PASSWORD='+values['DB_PASSWORD']+'\n')]:
        with path.open('x') as output:
            os.chmod(path, 0o600)
            output.write(content)
    os.chmod(LOCAL/'password.jceks', 0o600)
    ensure_signing_key(values)
    print('Created private local configuration. Credentials are in ignored .local/dev.json.')


def ensure_signing_key(values):
    signing = LOCAL/'signing.p12'
    if not signing.exists():
        run(['keytool','-genkeypair','-alias','authserver-signing','-keyalg','RSA','-keysize','3072',
             '-dname','CN=Authserver Local Development','-validity','3650','-storetype','PKCS12',
             '-keystore',str(signing),'-storepass:env','KEY_STORE_PASSWORD','-keypass:env','KEY_STORE_PASSWORD'],
            os.environ | values)
        os.chmod(signing,0o600)


def main():
    action = sys.argv[1] if len(sys.argv) > 1 else 'help'
    if action == 'init':
        configure()
        return
    if action not in ('up', 'start', 'test', 'stop'):
        print('Usage: scripts/local-dev.py init|up|start|test|stop')
        return
    if not CONFIG.exists():
        raise SystemExit('Run scripts/local-dev.py init first.')
    values = json.loads(CONFIG.read_text())
    env = os.environ | values
    if action == 'up':
        run(['docker', 'compose', 'up', '-d', '--wait'], env)
        run(['docker', 'compose', 'exec', '-T', 'sqlserver', 'bash', '-lc',
             'SQLCMDPASSWORD="$MSSQL_SA_PASSWORD" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b '
             '-Q "IF DB_ID(N\'AGSAuth\') IS NULL CREATE DATABASE [AGSAuth]"'], env)
    elif action == 'stop':
        run(['docker', 'compose', 'stop'], env)
    else:
        cache = os.environ.get('MAVEN_REPO_LOCAL', str(LOCAL/'m2'))
        command = [str(ROOT/'mvnw'), '-B', '-ntp', '-Dmaven.repo.local='+cache]
        if action == 'start':
            run(command+['-DskipTests', 'package'], env)
            runtime = LOCAL/'runs'/('authserver-'+str(uuid.uuid4())+'.jar')
            runtime.parent.mkdir(exist_ok=True)
            shutil.copy2(ROOT/'target/authserver-0.0.1-SNAPSHOT.jar', runtime)
            run(['java', '-jar', str(runtime)], env)
        else:
            env.update(AUTH_DB_IT_URL='jdbc:sqlserver://127.0.0.1:15433;encrypt=true;trustServerCertificate=true',
                       AUTH_DB_IT_USER=values['DB_USERNAME'], AUTH_DB_IT_PASSWORD=values['DB_PASSWORD'],
                       AUTH_APP_IT='true')
            run(command+['clean','verify'], env)
            run(['node', '--test', 'src/test/js/auth-forms.test.mjs'], env)
            run([sys.executable, str(ROOT/'scripts/test_e2e_target.py')], env)
            run([sys.executable, str(ROOT/'scripts/test_e2e_cleanup.py')], env)
            run([sys.executable, str(ROOT/'scripts/test_e2e_management.py')], env)


if __name__ == '__main__':
    main()
