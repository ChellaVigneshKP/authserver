#!/usr/bin/env python3
"""Real browser + Next + Java acceptance using an owned UUID fixture; no response interception."""
import argparse
import json
import os
from pathlib import Path
import runpy
import subprocess
import sys
import threading
import time
import urllib.request
import urllib.parse
import urllib.error
from e2e_target import sql_text
ROOT=Path(__file__).resolve().parent.parent
DEV=runpy.run_path(str(ROOT/'scripts/portal-dev.py'))
PHASES=('auth','navigation','clients','users','roles','certificates','journeys','outages','security')
PROJECTS=('chromium','firefox','webkit')


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *_args, **_kwargs):
        return None


def _request(url):
    return urllib.request.Request(url, headers={'User-Agent': 'PortalAcceptance/1.0', 'Accept-Language': 'en-US'})


def _json_or_empty(value):
    try:
        return json.loads(value)
    except (ValueError, TypeError):
        return {}


def login_probe(origin, opener):
    """The login route must fail closed with a retryable, uncached session-store error."""
    try:
        urllib.request.urlopen(_request(origin + '/auth/login'), timeout=15)
        return {'passed': False, 'status': 200}
    except urllib.error.HTTPError as error:
        body = _json_or_empty(error.read())
        return {'passed': error.code == 503 and body.get('code') == 'SESSION_UNAVAILABLE' and body.get('retryable') is True, 'status': error.code, 'code': body.get('code'), 'no_store': 'no-store' in error.headers.get('Cache-Control', '')}


def ready_probe(origin, opener, expect):
    """Readiness states a status word only; it must never cache or leak dependency detail."""
    wanted = 200 if expect == 'ready' else 503
    try:
        with opener.open(_request(origin + '/readyz'), timeout=15) as response:
            body = _json_or_empty(response.read())
            return {'passed': response.status == wanted and body.get('status') == expect and 'no-store' in response.headers.get('Cache-Control', ''), 'status': response.status}
    except urllib.error.HTTPError as error:
        body = _json_or_empty(error.read())
        return {'passed': error.code == wanted and body.get('status') == expect and 'no-store' in error.headers.get('Cache-Control', '') and (expect != 'unavailable' or error.headers.get('Retry-After') == '5'), 'status': error.code}


def redirect_probe(origin, opener):
    """A recovered portal must send the visitor to the issuer without following the redirect."""
    try:
        opener.open(_request(origin + '/auth/login'), timeout=15)
        return {'passed': False, 'status': 200}
    except urllib.error.HTTPError as error:
        location = error.headers.get('Location') or ''
        return {'passed': error.code == 302 and location.startswith('http'), 'status': error.code}


def restart_watcher(directory,next_process,portal_env,portal,stop_event,state):
    """Restart the owned Next when an outage scenario requests it; never touch shared services."""
    request=directory/'restart-next.json'
    while not stop_event.is_set():
        if request.exists():
            request.unlink()
            try:
                DEV['stop'](next_process)
                with (directory/'next-restart.log').open('w') as log:
                    os.chmod(log.name,0o600)
                    replacement=subprocess.Popen([str(DEV['NODE']),str(ROOT/'web/node_modules/next/dist/bin/next'),'start','--hostname','127.0.0.1','--port',str(urllib.parse.urlsplit(portal).port)],cwd=ROOT/'web',env=portal_env,stdout=log,stderr=subprocess.STDOUT)
                DEV['wait_ready'](portal,replacement)
                state['process']=replacement
                DEV['LIFECYCLE']['private_json'](directory/'restart-next-status.json',dict(status='ready',exit=0))
            except Exception as error:
                DEV['LIFECYCLE']['private_json'](directory/'restart-next-status.json',dict(status='failed',exit=1,detail=type(error).__name__))
        time.sleep(.25)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phase',choices=PHASES+('all',),default='auth')
    parser.add_argument('--browsers',default='chromium',help='Comma-separated subset of '+','.join(PROJECTS))
    parser.add_argument('--grep',help='Run only matching browser scenarios; filter and blocked tests are reported with the evidence')
    parser.add_argument('--container',metavar='IMAGE',help='Serve the portal from this built image instead of next start (first-slice container smoke; no outage sub-process probes)')
    args=parser.parse_args()
    browsers=tuple(v.strip() for v in args.browsers.split(',') if v.strip())
    unknown=[v for v in browsers if v not in PROJECTS]
    if not browsers or unknown:
        parser.error('--browsers must be a non-empty subset of '+','.join(PROJECTS))
    specs=PHASES if args.phase=='all' else (args.phase,)
    passed=False
    with DEV['owned_portal'](transport_faults=bool({'clients','outages'}&set(specs)), account_sync='sync' if {'users','roles','journeys'}&set(specs) else None, container_image=args.container) as fixture:
        directory=fixture['directory'];report=fixture['report'];next_process=fixture['next_process']
        report['browser_filter']=(args.grep or 'all '+args.phase+' scenarios')+' | browsers='+','.join(browsers)+' | specs='+','.join(v+'.spec.ts' for v in specs)
        config_path=directory/'dev.json'
        DEV['LIFECYCLE']['private_json'](config_path,fixture['config'])
        environment=os.environ|dict(PORTAL_E2E_CONFIG=str(fixture['private_path']),PORTAL_E2E_REPORT=str(directory/'browser-report.json'),
            PORTAL_NEXT_LOG=str(directory/'next.log'),PORTAL_REPO_ROOT=str(ROOT),PORTAL_OWNED_JAVA_PID=str(fixture['java'].pid),AUTH_E2E_CONFIG=str(config_path),
            PORTAL_SCREENSHOT_DIR=str(directory),AUTH_E2E_BASE=report['issuer'],AUTH_E2E_DB=fixture['database'],NEXT_TELEMETRY_DISABLED='1',PLAYWRIGHT_NO_COPY_PROMPT='1')
        stop_event=threading.Event();restart_state={}
        watcher=threading.Thread(target=restart_watcher,args=(directory,next_process,fixture['portal_env'],report['portal'],stop_event,restart_state),daemon=True)
        readiness={'started':False}
        if {'outages'}&set(specs) and not args.container:
            watcher.start();readiness['started']=True
        try:
            with (directory/'browser.log').open('w') as log:
                os.chmod(log.name,0o600)
                command=[str(DEV['NODE']),str(ROOT/'web/node_modules/@playwright/test/cli.js'),'test']+[v+'.spec.ts' for v in specs]
                for browser in browsers:
                    command+=['--project',browser]
                if args.grep:command+=['--grep',args.grep]
                result=subprocess.run(command,cwd=ROOT/'web',env=environment,stdout=log,stderr=subprocess.STDOUT,timeout=int(os.environ.get('PORTAL_BROWSER_TIMEOUT','1800')))
        finally:
            stop_event.set()
            if readiness['started']:
                watcher.join(timeout=5)
                report['restart_watcher_stopped']=not watcher.is_alive()
        browser_path=directory/'browser-report.json'
        report['browser']=json.loads(browser_path.read_text()) if browser_path.exists() else {'checks':[],'failed_before_report':True}
        report['sql']=json.loads(sql_text(fixture['config'],fixture['database'],"SELECT COUNT(*) AS Sessions,SUM(CASE WHEN ClientFingerprint IS NOT NULL THEN 1 ELSE 0 END) AS Fingerprints FROM Token.AuthSession FOR JSON PATH"))
        if args.container:
            report['container_smoke']=fixture['report'].get('container')
            report['blocked']=report['browser'].get('blocked',[])
            checks_ok=bool(result.returncode==0 and report['browser']['checks'] and all(v['passed'] for v in report['browser']['checks']))
            report['passed']=bool(not report['blocked'] and checks_ok)
            print(('PASS' if report['passed'] else 'BLOCKED' if report['blocked'] and result.returncode==0 else 'FAIL')+' portal container '+args.phase+' ['+','.join(browsers)+']: '+str(len(report['browser']['checks']))+' browser/API checks; SQL verified',flush=True)
            return 0 if report['passed'] else 1
        # A second owned Next process points only at an unused loopback Redis port.
        outage_port=DEV['free_port']();redis_port=DEV['free_port']()
        outage_env=fixture['portal_env']|dict(PORTAL_ORIGIN='http://127.0.0.1:'+str(outage_port),PORTAL_REDIS_URL='redis://127.0.0.1:'+str(redis_port)+'/0')
        outbound=urllib.request.build_opener(_NoRedirect())
        outage=None
        try:
            with (directory/'outage.log').open('w') as log:
                os.chmod(log.name,0o600)
                outage=subprocess.Popen([str(DEV['NODE']),str(ROOT/'web/node_modules/next/dist/bin/next'),'start','--hostname','127.0.0.1','--port',str(outage_port)],cwd=ROOT/'web',env=outage_env,stdout=log,stderr=subprocess.STDOUT)
            outage_origin=outage_env['PORTAL_ORIGIN'];DEV['wait_ready'](outage_origin,outage)
            report['redis_outage']=login_probe(outage_origin, outbound)
            report['redis_readiness_outage']=ready_probe(outage_origin, outbound, expect='unavailable')
            # Recovery: the same owned Next configuration is restarted against the live Redis.
            DEV['stop'](outage)
            recovery_env=fixture['portal_env']|dict(PORTAL_ORIGIN=outage_origin)
            with (directory/'outage-recovery.log').open('w') as log:
                os.chmod(log.name,0o600)
                outage=subprocess.Popen([str(DEV['NODE']),str(ROOT/'web/node_modules/next/dist/bin/next'),'start','--hostname','127.0.0.1','--port',str(outage_port)],cwd=ROOT/'web',env=recovery_env,stdout=log,stderr=subprocess.STDOUT)
            DEV['wait_ready'](outage_origin,outage)
            report['redis_readiness_recovery']=ready_probe(outage_origin, outbound, expect='ready')
            report['redis_login_recovery']=redirect_probe(outage_origin, outbound)
            report['redis_recovery']={'passed':report['redis_readiness_recovery']['passed'] and report['redis_login_recovery']['passed'],'status':report['redis_readiness_recovery']['status']}
        finally:DEV['stop'](outage)
        report['blocked']=report['browser'].get('blocked',[])
        report['passed']=bool(not report['blocked'] and report['redis_outage']['passed'] and report.get('redis_readiness_outage',{}).get('passed') and report.get('redis_recovery',{}).get('passed') and result.returncode==0 and report['browser']['checks'] and all(v['passed'] for v in report['browser']['checks']))
        passed=report['passed']
        print(('PASS' if passed else 'BLOCKED' if report['blocked'] and result.returncode==0 else 'FAIL')+' portal '+args.phase+' ['+','.join(browsers)+']: '+str(len(report['browser']['checks']))+' browser/API checks; SQL verified',flush=True)
    return 0 if passed else 1


if __name__=='__main__':sys.exit(main())
