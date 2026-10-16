#!/usr/bin/env python3
"""Compare resolved Maven Bouncy Castle versions with current open GitHub alerts.

This checks the repository's reported Bouncy Castle advisory ranges, not all dependencies
or exploit reachability. Requires authenticated gh and an actual dependency:tree output.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess

ROOT=Path(__file__).resolve().parent.parent


def version(value):
    if not re.fullmatch(r'\d+(?:\.\d+)*',value):raise ValueError('Unsupported version format')
    return tuple(int(part) for part in value.split('.'))


def affected(installed, expression):
    current=version(installed)
    for clause in expression.split(','):
        match=re.fullmatch(r'\s*(>=|<=|>|<|=)?\s*(\d+(?:\.\d+)*)\s*',clause)
        if not match:raise ValueError('Unsupported advisory range; manual assessment required')
        operator,number=match.groups();target=version(number)
        width=max(len(current),len(target));left=current+(0,)*(width-len(current));right=target+(0,)*(width-len(target))
        if not {'>=':left>=right,'<=':left<=right,'>':left>right,'<':left<right,'=':left==right}[operator or '=']:return False
    return True


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tree',default='.local/bouncycastle-tree.txt')
    args=parser.parse_args()
    tree=Path(args.tree).read_text()
    modules=dict(re.findall(r'org\.bouncycastle:([\w-]+):jar:([\d.]+):',tree))
    for artifact in ['bcprov-jdk18on','bcpkix-jdk18on','bcutil-jdk18on']:
        if artifact not in modules:raise SystemExit('Required module missing from resolved dependency tree: '+artifact)
    result=subprocess.run(['gh','api','--paginate','repos/ChellaVigneshKP/authserver/dependabot/alerts?state=open&per_page=100','--slurp'],text=True,capture_output=True,check=True)
    alerts=[item for page in json.loads(result.stdout) for item in page]
    report=dict(checked_at=datetime.now(timezone.utc).isoformat(),scope='Open repository Bouncy Castle alerts only; no full dependency or reachability clearance',resolved=modules,checks=[])
    for alert in alerts:
        package=alert['dependency']['package']['name']
        if not package.startswith('org.bouncycastle:'):continue
        artifact=package.split(':',1)[1]
        if artifact not in modules:raise SystemExit('Alert package missing from resolved tree; manual review required')
        vulnerability=alert['security_vulnerability']
        vulnerable=affected(modules[artifact],vulnerability['vulnerable_version_range'])
        report['checks'].append(dict(alert=alert['number'],ghsa=alert['security_advisory']['ghsa_id'],severity=alert['security_advisory']['severity'],package=package,resolved=modules[artifact],affected_range=vulnerability['vulnerable_version_range'],first_patched=vulnerability.get('first_patched_version'),passed=not vulnerable))
    path=ROOT/'.local/dependency-alert-report.json'
    descriptor=os.open(path,os.O_CREAT|os.O_TRUNC|os.O_WRONLY,0o600)
    with os.fdopen(descriptor,'w') as output:json.dump(report,output,indent=2)
    print('Resolved modules:',', '.join(name+'='+value for name,value in modules.items()))
    print('Current open Bouncy Castle advisory checks:',len(report['checks']),'; affected:',sum(not item['passed'] for item in report['checks']))
    if any(not item['passed'] for item in report['checks']):raise SystemExit(1)


if __name__=='__main__':main()
