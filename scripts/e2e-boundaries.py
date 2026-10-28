#!/usr/bin/env python3
"""Probe every inventoried route without credentials: guard/validation checks, not happy-path certification."""
import json
from datetime import datetime, timezone
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request

ROOT=Path(__file__).resolve().parent.parent
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args):return None

def main():
    report=dict(started_at=datetime.now(timezone.utc).isoformat(),checks=[],database_checks=[],failures=[])
    endpoints=json.loads((ROOT/'docs/recovery/ENDPOINT-INVENTORY.json').read_text())['endpoints']
    for endpoint in endpoints:
        path=re.sub(r'\{([^}]+)\}',lambda m:'login' if m[1]=='flow' else '00000000-0000-0000-0000-000000000000',endpoint['path'])
        params={p.split('=')[0]:(p.split('=',1)[1] if '=' in p else 'invalid') for p in endpoint.get('params',[]) if not p.startswith('!')}
        query=urllib.parse.urlencode(params)
        url='http://localhost:9080/services'+path+('?' + query if query else '')
        headers={'Accept':'text/html' if not path.startswith('/api/') else 'application/json','User-Agent':'AuthserverBoundary/1.0','Accept-Language':'en-US','x-request-datetime':datetime.now(timezone.utc).isoformat()}
        data=b'' if endpoint['method'] not in ('GET','HEAD') else None
        if data is not None:headers['Content-Type']='application/x-www-form-urlencoded'
        request=urllib.request.Request(url,data=data,headers=headers,method=endpoint['method'])
        # Each request has no session, cookies or bearer token; no application fixture is modified.
        opener=urllib.request.build_opener(NoRedirect())
        try:response=opener.open(request,timeout=15)
        except urllib.error.HTTPError as error:response=error
        status=response.status;response.read()
        protected=path.startswith('/api/')
        passed=(status in (400,401,403) if protected else status<500)
        check=dict(id='unauthenticated '+endpoint['id'],method=endpoint['method'],path=path+('?' + query if query else ''),status=status,passed=passed,positive=False,
                   expected='400/401/403 for protected APIs; controlled response below500 otherwise',purpose='unauthenticated boundary only')
        report['checks'].append(check)
        if not passed:
            report['failures'].append(check)
            print('FAIL',endpoint['id'],status)
    report['finished_at']=datetime.now(timezone.utc).isoformat()
    path=ROOT/'.local/e2e-boundary-report.json';path.write_text(json.dumps(report,indent=2));path.chmod(0o600)
    print('Boundary-only checks:',len(report['checks']),'failures:',len(report['failures']))
    return bool(report['failures'])

if __name__=='__main__':raise SystemExit(main())
