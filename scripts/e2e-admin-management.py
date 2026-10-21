#!/usr/bin/env python3
"""Local-only real HTTP/SQL policy campaign. Never uses the configured application DB."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
LOCAL = ROOT / '.local'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phase', choices=['policy'], required=True)
    args = parser.parse_args()
    values = json.loads((LOCAL / 'dev.json').read_text())
    url = values['DB_URL']
    # Configuration is read privately; never echo connection strings or credentials.
    if not re.fullmatch(r'jdbc:sqlserver://(?:127\.0\.0\.1|localhost):15433;databaseName=AGSAuth;encrypt=true;trustServerCertificate=true', url):
        raise SystemExit('Refusing unapproved database host/name or connection options')
    env = os.environ | values | {
        'AUTH_DB_IT_URL': 'jdbc:sqlserver://127.0.0.1:15433;encrypt=true;trustServerCertificate=true',
        'AUTH_DB_IT_USER': values['DB_USERNAME'], 'AUTH_DB_IT_PASSWORD': values['DB_PASSWORD'],
        'AUTH_APP_IT': 'true',
    }
    command = [str(ROOT / 'mvnw'), '-B', '-ntp', '-Dmaven.repo.local=' + str(LOCAL / 'm2'),
               '-Dtest=ManagementPolicyHttpIntegrationTest', 'test']
    xml = ROOT / 'target/surefire-reports/TEST-com.chellavignesh.authserver.security.ManagementPolicyHttpIntegrationTest.xml'
    xml.unlink(missing_ok=True)
    log = LOCAL / 'e2e-admin-management-policy.log'
    with log.open('w') as output:
        os.chmod(log, 0o600)
        result = subprocess.run(command, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
    report = {'phase': args.phase, 'passed': result.returncode == 0,
              'transport': 'real HTTP', 'database': 'owned UUID disposable SQL Server database',
              'provider': 'not exercised', 'tests': []}
    if xml.exists():
        for test in ET.parse(xml).getroot().findall('testcase'):
            report['tests'].append({'name': test.attrib['name'],
                                    'passed': test.find('failure') is None and test.find('error') is None})
    target = LOCAL / 'e2e-admin-management-report.json'
    target.write_text(json.dumps(report, indent=2) + '\n')
    os.chmod(target, 0o600)
    print(json.dumps(report, indent=2))
    raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
