#!/usr/bin/env python3
"""Local-only real HTTP/SQL management campaigns. Never uses the configured application DB."""
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
    parser.add_argument('--phase', choices=['policy', 'credentials', 'context', 'roles', 'assignments'], required=True)
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
    test_class = {
        'policy': 'com.chellavignesh.authserver.security.ManagementPolicyHttpIntegrationTest',
        'credentials': 'com.chellavignesh.authserver.recovery.CredentialMetadataIntegrationTest',
        'context': 'com.chellavignesh.authserver.adminportal.portal.PortalContextIntegrationTest',
        'assignments': 'com.chellavignesh.authserver.database.RoleAssignmentContractTest,com.chellavignesh.authserver.database.RoleMigrationContractTest',
        'roles': 'com.chellavignesh.authserver.database.RoleContractTest,com.chellavignesh.authserver.database.RoleMigrationContractTest',
    }[args.phase]
    command = [str(ROOT / 'mvnw'), '-B', '-ntp', '-Dmaven.repo.local=' + str(LOCAL / 'm2'),
               '-Dtest=' + test_class, 'test']
    reports = [ROOT / ('target/surefire-reports/TEST-' + name + '.xml') for name in test_class.split(',')]
    for xml in reports:
        xml.unlink(missing_ok=True)
    log = LOCAL / ('e2e-admin-management-' + args.phase + '.log')
    with log.open('w') as output:
        os.chmod(log, 0o600)
        result = subprocess.run(command, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
    report = {'phase': args.phase, 'passed': result.returncode == 0 and all(xml.exists() for xml in reports),
              'transport': 'real HTTP', 'database': 'owned UUID disposable SQL Server database',
              'provider': 'not exercised', 'tests': []}
    for xml in reports:
        if not xml.exists():
            continue
        for test in ET.parse(xml).getroot().findall('testcase'):
            report['tests'].append({'name': test.attrib['name'],
                                    'passed': test.find('failure') is None and test.find('error') is None and test.find('skipped') is None})
    report['passed'] = report['passed'] and bool(report['tests']) and all(test['passed'] for test in report['tests'])
    target = LOCAL / 'e2e-admin-management-report.json'
    target.write_text(json.dumps(report, indent=2) + '\n')
    os.chmod(target, 0o600)
    print(json.dumps(report, indent=2))
    raise SystemExit(0 if report['passed'] else (result.returncode or 1))


if __name__ == '__main__':
    main()
