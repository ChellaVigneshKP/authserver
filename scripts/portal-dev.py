#!/usr/bin/env python3
"""Own a disposable UUID SQL/Redis/Java/Next portal fixture; never restart shared services."""
import argparse
from contextlib import contextmanager
import hashlib
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import threading
import json
import os
from pathlib import Path
import runpy
import secrets
import shutil
import socket
import subprocess
import time
import urllib.request
import uuid
from e2e_target import load_target, sql_text

ROOT = Path(__file__).resolve().parent.parent
LIFECYCLE = runpy.run_path(str(ROOT / 'scripts/e2e-isolated-lifecycles.py'))
NODE = ROOT / '.local/portal-node24/node_modules/node/bin/node'


def free_port():
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 0))
        return probe.getsockname()[1]


def wait_ready(url, child):
    for _ in range(240):
        if child.poll() is not None:
            raise RuntimeError('Owned service exited; inspect its private run log')
        try:
            with urllib.request.urlopen(url, timeout=1) as response:
                if response.status == 200:
                    return
        except (OSError, ValueError):
            pass
        time.sleep(.5)
    raise RuntimeError('Owned service readiness timeout')


def wait_portal_client(config, database, child):
    # The HTTP listener/metadata endpoint can become ready before bootstrap runners commit.
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        if child.poll() is not None:
            raise RuntimeError('Owned service exited during bootstrap readiness')
        raw = sql_text(config, database, "SELECT DB_NAME() AS DatabaseName,ClientId FROM Client.Application WHERE Name='Admin Portal' FOR JSON PATH")
        rows = json.loads(raw) if raw else []
        if rows:
            if len(rows) != 1 or rows[0].get('DatabaseName') != database or not rows[0].get('ClientId'):
                raise RuntimeError('Owned SQL/HTTP fixture mismatch')
            return rows[0]
        time.sleep(.25)
    raise RuntimeError('Owned bootstrap readiness timeout')


def stop(child):
    if child is None:
        return
    if child.poll() is None:
        child.terminate()
    try:
        child.wait(timeout=20)
    except subprocess.TimeoutExpired:
        child.kill()
        child.wait()


def start_fault_transport(issuer, directory):
    """Forward actual bytes to this owned Java only; optionally lose one committed response.

    Never persist bodies/headers (credential responses may contain a one-time secret).
    The browser campaign arms a nonsecret path/method control file, never a fake response.
    """
    target = urllib.parse.urlsplit(issuer)
    control = directory / 'fault-control.json'
    events = directory / 'fault-events.json'
    observed = []
    lock = threading.Lock()

    class Forward(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def forward(self):
            body = self.rfile.read(int(self.headers.get('Content-Length', '0')))
            upstream = http.client.HTTPConnection(target.hostname, target.port, timeout=20)
            try:
                headers = {key: value for key, value in self.headers.items()
                           if key.lower() not in ('host', 'connection', 'transfer-encoding')}
                upstream.request(self.command, self.path, body=body or None, headers=headers)
                response = upstream.getresponse()
                payload = response.read()
                drop = False
                with lock:
                    if self.command in ('POST', 'PUT', 'DELETE'):
                        observed.append(dict(method=self.command, path=self.path, status=response.status))
                        LIFECYCLE['private_json'](events, observed)
                    if control.exists():
                        fault = json.loads(control.read_text())
                        if fault.get('method') == self.command and fault.get('path') == self.path:
                            control.unlink()
                            drop = True
                if drop:
                    # Java has answered after commit; BFF's 10s deadline expires first.
                    time.sleep(12)
                    self.close_connection = True
                    return
                self.send_response(response.status)
                for key, value in response.getheaders():
                    if key.lower() not in ('connection', 'transfer-encoding', 'content-length'):
                        self.send_header(key, value)
                self.send_header('Content-Length', str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
            except (BrokenPipeError, ConnectionResetError):
                pass
            finally:
                upstream.close()

        do_GET = do_POST = do_PUT = do_DELETE = forward

    server = ThreadingHTTPServer(('127.0.0.1', 0), Forward)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server, thread, 'http://127.0.0.1:' + str(server.server_port), control, events


@contextmanager
def owned_portal(port=3100, transport_faults=False):
    original, _, source_database = load_target()
    if source_database != 'AGSAuth' or not NODE.is_file():
        raise ValueError('Use normal private local configuration and install isolated Node24 first')
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', port))
        port = probe.getsockname()[1]
    run_id = uuid.uuid4().hex
    database = 'AuthserverLifecycle_' + run_id
    namespace = 'authserver:lifecycle:' + run_id + ':'
    portal_prefix = 'portal:v1:' + run_id + ':'
    directory = ROOT / '.local/portal-runs' / run_id
    directory.mkdir(parents=True, mode=0o700)
    cms_directory = directory / 'cms'
    issuer = 'http://localhost:' + str(free_port()) + '/services'
    portal = 'http://127.0.0.1:' + str(port)
    config = original | dict(DB_URL=original['DB_URL'].replace('databaseName=AGSAuth;', 'databaseName=' + database + ';'),
        SERVER_PORT=str(urllib.parse.urlsplit(issuer).port), SERVER_BASE_PATH=issuer,
        LOCAL_CALLBACK_URL=portal+'/auth/callback', LOCAL_BOOTSTRAP_ENABLED='true',
        LOCAL_ADMIN_USERNAME='portal_' + run_id, LOCAL_ADMIN_PASSWORD='P8!' + secrets.token_hex(18) + 'qZ')
    environment = LIFECYCLE['effective_child_environment'](config, os.environ, namespace)
    properties = json.loads(environment['SPRING_APPLICATION_JSON'])
    properties.update({'toggles.fingerprinting.enabled': True, 'toggles.fingerprint.referer.disabled': False,
        'toggles.fingerprinting.referer.disabled': False, 'toggles.signature.required': True,
        'cms.file.location': cms_directory.as_uri()})
    if transport_faults:
        properties['spring.flyway.target'] = '15'
    environment['SPRING_APPLICATION_JSON'] = json.dumps(properties)
    redis = LIFECYCLE['verified_redis_target'](environment)
    report = dict(run_id=run_id, database=database, portal=portal, issuer=issuer,
                  security_checks_enabled=True, provider='unconfigured; no provider success claimed', cleanup={})
    java = next_process = None
    fault_server = fault_thread = None
    owned = False
    try:
        sql_text(original, 'AGSAuth', 'CREATE DATABASE [' + database + '];', scalar=True)
        owned = True
        # Local synthetic source uses the same reviewed CMS content; no production source selector.
        cms_directory.mkdir(mode=0o700)
        for name in ('idp.json', 'portal-sibling.json'):
            shutil.copy2(ROOT / 'src/main/resources/cms/idp.json', cms_directory / name)
        runtime = directory / 'authserver.jar'
        shutil.copy2(ROOT / 'target/authserver-0.0.1-SNAPSHOT.jar', runtime)
        os.chmod(runtime, 0o400)
        report['artifact_sha256'] = hashlib.sha256(runtime.read_bytes()).hexdigest()
        with (directory / 'java.log').open('w') as log:
            os.chmod(log.name, 0o600)
            java = subprocess.Popen(['java', '-jar', str(runtime)], cwd=ROOT, env=environment, stdout=log, stderr=subprocess.STDOUT)
        wait_ready(issuer + '/.well-known/openid-configuration', java)
        portal_client = wait_portal_client(config, database, java)
        if transport_faults:
            preservation_sql = """SELECT
             (SELECT COUNT(*) FROM Client.Application) AS apps,
             (SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM Client.Application) AS appChecksum,
             (SELECT COUNT(*) FROM Client.TokenSetting) AS settings,
             (SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM Client.TokenSetting) AS settingsChecksum,
             (SELECT COUNT(*) FROM Client.ForgetUserNameSetting) AS usernameRows,
             (SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM Client.ForgetUserNameSetting) AS usernameChecksum
             FOR JSON PATH"""
            before_upgrade = json.loads(sql_text(config, database, preservation_sql))
            old_history = json.loads(sql_text(config, database, "SELECT COUNT(*) AS n FROM flyway_schema_history WHERE version='16' AND success=1 FOR JSON PATH"))[0]['n']
            stop(java)
            properties.pop('spring.flyway.target')
            environment['SPRING_APPLICATION_JSON'] = json.dumps(properties)
            for attempt in ('upgrade', 'repeat'):
                with (directory / ('java-' + attempt + '.log')).open('w') as log:
                    os.chmod(log.name, 0o600)
                    java = subprocess.Popen(['java', '-jar', str(runtime)], cwd=ROOT, env=environment, stdout=log, stderr=subprocess.STDOUT)
                wait_ready(issuer + '/.well-known/openid-configuration', java)
                portal_client = wait_portal_client(config, database, java)
                after_upgrade = json.loads(sql_text(config, database, preservation_sql))
                history = json.loads(sql_text(config, database, "SELECT COUNT(*) AS n FROM flyway_schema_history WHERE version='16' AND success=1 FOR JSON PATH"))[0]['n']
                report['migration_v16_' + attempt] = dict(preserved=before_upgrade == after_upgrade, previous_v16=old_history, installed_v16=history)
                if before_upgrade != after_upgrade or old_history != 0 or history != 1:
                    raise RuntimeError('Owned V15-to-V16 migration preservation failed')
                if attempt == 'upgrade':
                    stop(java)
        # Only the fresh UUID fixture is amended, never existing clients or records.
        sql_text(config, database, "UPDATE r SET PostLogoutRedirectUri='"+portal+"/auth/logged-out' FROM Client.PostLogoutRedirectUri r JOIN Client.Application a ON a.ApplicationId=r.ApplicationId WHERE a.Name='Admin Portal';", scalar=True)
        portal_env = os.environ | dict(PORTAL_ORIGIN=portal, AUTH_ISSUER=issuer, AUTH_CLIENT_ID=portal_client['ClientId'],
            PORTAL_REDIS_URL='redis://127.0.0.1:16379/0', PORTAL_REDIS_NAMESPACE=run_id,
            PORTAL_ALLOW_INSECURE_LOOPBACK='true', NEXT_TELEMETRY_DISABLED='1', TZ='UTC')
        fault_control = fault_events = None
        if transport_faults:
            fault_server, fault_thread, transport_origin, fault_control, fault_events = start_fault_transport(issuer, directory)
            portal_env['AUTH_TRANSPORT_ORIGIN'] = transport_origin
        with (directory / 'next.log').open('w') as log:
            os.chmod(log.name, 0o600)
            next_process = subprocess.Popen([str(NODE), str(ROOT/'web/node_modules/next/dist/bin/next'), 'start', '--hostname', '127.0.0.1', '--port', str(port)], cwd=ROOT/'web', env=portal_env, stdout=log, stderr=subprocess.STDOUT)
        wait_ready(portal, next_process)
        private = dict(portal=portal, issuer=issuer, clientId=portal_client['ClientId'], redisPrefix=portal_prefix,
                       username=config['LOCAL_ADMIN_USERNAME'], password=config['LOCAL_ADMIN_PASSWORD'],
                       secondUsername='account_'+run_id, secondPassword='P9!'+secrets.token_hex(18)+'qZ')
        if transport_faults:
            private.update(faultControl=str(fault_control), faultEvents=str(fault_events))
        private_path = directory / 'browser.json'
        LIFECYCLE['private_json'](private_path, private)
        yield dict(directory=directory, private_path=private_path, report=report, java=java,
                   portal_env=portal_env, next_process=next_process, config=config, database=database)
    finally:
        stop(next_process)
        stop(java)
        if fault_server is not None:
            fault_server.shutdown()
            fault_server.server_close()
            fault_thread.join(timeout=2)
            report['cleanup']['fault_transport_stopped'] = not fault_thread.is_alive()
        if cms_directory.exists():
            shutil.rmtree(cms_directory)
        report['cleanup']['cms_fixture_removed'] = not cms_directory.exists()
        report['cleanup']['owned_processes_stopped'] = True
        if owned:
            try:
                report['cleanup']['java_redis'] = LIFECYCLE['cleanup_redis_owned'](redis, namespace)
                command = ['docker', 'exec', redis['container_id'], 'redis-cli', '-n', '0']
                keys = subprocess.check_output(command+['--scan', '--pattern', portal_prefix+'*'], text=True).splitlines()
                if any(not key.startswith(portal_prefix) for key in keys):
                    raise ValueError('Unsafe portal Redis cleanup target')
                for start in range(0, len(keys), 100):
                    subprocess.run(command+['DEL']+keys[start:start+100], check=True, capture_output=True)
                report['cleanup']['portal_redis_remaining'] = len(subprocess.check_output(command+['--scan', '--pattern', portal_prefix+'*'], text=True).splitlines())
            finally:
                sql_text(original, 'AGSAuth', 'ALTER DATABASE ['+database+'] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE ['+database+'];', scalar=True)
                report['cleanup']['database_dropped'] = sql_text(original,'AGSAuth',"SELECT COUNT(*) FROM sys.databases WHERE name='"+database+"'",scalar=True)=='0'
        LIFECYCLE['private_json'](directory/'report.json', report)
        print('Portal evidence: '+str(directory.relative_to(ROOT))+'/report.json', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=3100)
    args = parser.parse_args()
    try:
        with owned_portal(args.port) as fixture:
            print('Portal ready: '+fixture['report']['portal'], flush=True)
            print('Synthetic sign-in credentials remain in '+str(fixture['private_path'].relative_to(ROOT)), flush=True)
            while True:
                time.sleep(1)
    except KeyboardInterrupt:
        pass


if __name__ == '__main__':
    main()
