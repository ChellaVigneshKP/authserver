#!/usr/bin/env python3
"""Real browser + Next + Java acceptance using an owned UUID fixture; no response interception."""
import argparse
import json
import os
from pathlib import Path
import runpy
import subprocess
import sys
import urllib.request
import urllib.error
from e2e_target import sql_text
ROOT=Path(__file__).resolve().parent.parent
DEV=runpy.run_path(str(ROOT/'scripts/portal-dev.py'))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phase',choices=['auth','navigation','clients','users','roles'],default='auth')
    parser.add_argument('--grep',help='Run only matching browser scenarios; filter and blocked tests are reported with the evidence')
    args=parser.parse_args()
    passed=False
    with DEV['owned_portal'](transport_faults=args.phase=='clients', account_sync='sync' if args.phase in ('users','roles') else None) as fixture:
        directory=fixture['directory'];report=fixture['report']
        report['browser_filter']=args.grep or 'all '+args.phase+' scenarios'
        config_path=directory/'dev.json'
        DEV['LIFECYCLE']['private_json'](config_path,fixture['config'])
        environment=os.environ|dict(PORTAL_E2E_CONFIG=str(fixture['private_path']),PORTAL_E2E_REPORT=str(directory/'browser-report.json'),
            PORTAL_NEXT_LOG=str(directory/'next.log'),PORTAL_REPO_ROOT=str(ROOT),PORTAL_OWNED_JAVA_PID=str(fixture['java'].pid),AUTH_E2E_CONFIG=str(config_path),
            PORTAL_SCREENSHOT_DIR=str(directory),AUTH_E2E_BASE=report['issuer'],AUTH_E2E_DB=fixture['database'],NEXT_TELEMETRY_DISABLED='1',PLAYWRIGHT_NO_COPY_PROMPT='1')
        with (directory/'browser.log').open('w') as log:
            os.chmod(log.name,0o600)
            command=[str(DEV['NODE']),str(ROOT/'web/node_modules/@playwright/test/cli.js'),'test',args.phase+'.spec.ts']
            if args.grep:command+=['--grep',args.grep]
            result=subprocess.run(command,cwd=ROOT/'web',env=environment,stdout=log,stderr=subprocess.STDOUT,timeout=int(os.environ.get('PORTAL_BROWSER_TIMEOUT','600')))
        browser_path=directory/'browser-report.json'
        report['browser']=json.loads(browser_path.read_text()) if browser_path.exists() else {'checks':[],'failed_before_report':True}
        report['sql']=json.loads(sql_text(fixture['config'],fixture['database'],"SELECT COUNT(*) AS Sessions,SUM(CASE WHEN ClientFingerprint IS NOT NULL THEN 1 ELSE 0 END) AS Fingerprints FROM Token.AuthSession FOR JSON PATH"))
        # A second owned Next process points only at an unused loopback Redis port.
        outage_port=DEV['free_port']();redis_port=DEV['free_port']()
        outage_env=fixture['portal_env']|dict(PORTAL_ORIGIN='http://127.0.0.1:'+str(outage_port),PORTAL_REDIS_URL='redis://127.0.0.1:'+str(redis_port)+'/0')
        outage=None
        try:
            with (directory/'outage.log').open('w') as log:
                os.chmod(log.name,0o600)
                outage=subprocess.Popen([str(DEV['NODE']),str(ROOT/'web/node_modules/next/dist/bin/next'),'start','--hostname','127.0.0.1','--port',str(outage_port)],cwd=ROOT/'web',env=outage_env,stdout=log,stderr=subprocess.STDOUT)
            outage_origin=outage_env['PORTAL_ORIGIN'];DEV['wait_ready'](outage_origin,outage)
            try:
                urllib.request.urlopen(urllib.request.Request(outage_origin+'/auth/login',headers={'User-Agent':'PortalAcceptance/1.0','Accept-Language':'en-US'}),timeout=15)
                report['redis_outage']={'passed':False,'status':200}
            except urllib.error.HTTPError as error:
                body=json.load(error)
                report['redis_outage']={'passed':error.code==503 and body.get('code')=='SESSION_UNAVAILABLE' and body.get('retryable') is True,'status':error.code,'code':body.get('code'),'no_store':'no-store' in error.headers.get('Cache-Control','')}
        finally:DEV['stop'](outage)
        report['blocked']=report['browser'].get('blocked',[])
        report['passed']=not report['blocked'] and report['redis_outage']['passed'] and result.returncode==0 and bool(report['browser']['checks']) and all(v['passed'] for v in report['browser']['checks'])
        passed=report['passed']
        print(('PASS' if passed else 'BLOCKED' if report['blocked'] and result.returncode==0 else 'FAIL')+' portal '+args.phase+': '+str(len(report['browser']['checks']))+' browser/API checks; SQL verified',flush=True)
    return 0 if passed else 1


if __name__=='__main__':sys.exit(main())
