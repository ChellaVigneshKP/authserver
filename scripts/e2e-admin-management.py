#!/usr/bin/env python3
"""Real HTTP/SQL management campaigns on exclusively owned disposable databases.

JUnit provides assertion results only. Route observations come from actual HTTP
calls and SQL readbacks emitted by test support, never inferred from test names.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import uuid
import xml.etree.ElementTree as ET

from e2e_provenance import source_provenance

ROOT = Path(__file__).resolve().parent.parent
LOCAL = ROOT / '.local'
TESTS = {
    'policy': ['security.ManagementPolicyHttpIntegrationTest'],
    'credentials': ['recovery.CredentialMetadataIntegrationTest'],
    'context': ['adminportal.portal.PortalContextIntegrationTest'],
    'roles': ['database.RoleContractTest', 'database.RoleMigrationContractTest'],
    'assignments': ['database.RoleAssignmentContractTest', 'database.RoleMigrationContractTest'],
    'lifecycle': ['recovery.AdminLifecycleIntegrationTest'],
    'acceptance': ['database.PortalAcceptanceIntegrationTest'],
}




def qualify_observations(events, passed, provenance):
    checks = []
    for index, event in enumerate(events, 1):
        if (not isinstance(event.get('status'), int) or not event.get('method')
                or not event.get('url', '').startswith('/services/')
                or event.get('sql_outcome', {}).get('readback') != 'completed'
                or event.get('source_commit') != provenance['source_commit']
                or event.get('source_tree_sha256') != provenance['source_tree_sha256']):
            raise ValueError('Incomplete or mismatched actual HTTP/SQL observation')
        checks.append(event | dict(id='management-'+str(index), passed=passed,
                                  positive=passed and 200 <= event['status'] < 400))
    return checks


def mutation_matrix(checks):
    routes = sorted({(check['method'], check['url']) for check in checks
                     if check['scenario'].endswith('#everyManagementMutationRejectsMissingBearerWithoutSqlChanges')})
    result = []
    for method, route in routes:
        observed = [check for check in checks if check['method'] == method and check['url'] == route]
        denied = [check for check in observed if check['status'] in (401, 403, 409)
                  and check['passed'] and check['sql_outcome']['changed_tables'] == []]
        positive = [check for check in observed if check['positive']]
        result.append(dict(method=method, route=route, denial_with_unchanged_sql=bool(denied),
                           positive_http_observed=bool(positive),
                           positive_sql_change_observed=any(check['sql_outcome']['changed_tables'] for check in positive),
                           check_ids=[check['id'] for check in observed]))
    return result


def expected_management_mutations():
    spec = importlib.util.spec_from_file_location('endpoint_inventory', ROOT / 'scripts/endpoint-inventory.py')
    inventory = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(inventory)
    controllers = {'OrganizationController', 'UserController', 'ApplicationController', 'CredentialController',
                   'OrganizationCertificateController', 'ResourceLibraryController', 'RoleController', 'RoleAssignmentController'}
    return {(row['method'], '/services'+re.sub(r'\{[^}]+\}', '{id}', row['path']))
            for row in inventory.mvc_rows() if row['controller'] in controllers
            and row['method'] in {'POST', 'PUT', 'DELETE'}
            and row['path'] != '/api/v1/users/validate-password'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phase', choices=[*TESTS, 'all'], required=True)
    args = parser.parse_args()
    values = json.loads((LOCAL / 'dev.json').read_text())
    if not re.fullmatch(r'jdbc:sqlserver://(?:127\.0\.0\.1|localhost):15433;databaseName=AGSAuth;encrypt=true;trustServerCertificate=true', values['DB_URL']):
        raise SystemExit('Refusing unapproved database host/name or connection options')
    provenance = source_provenance()
    observations = LOCAL / ('management-observations-'+uuid.uuid4().hex+'.jsonl')
    descriptor = os.open(observations, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    os.close(descriptor)
    env = os.environ | values | {
        'AUTH_DB_IT_URL': 'jdbc:sqlserver://127.0.0.1:15433;encrypt=true;trustServerCertificate=true',
        'AUTH_DB_IT_USER': values['DB_USERNAME'], 'AUTH_DB_IT_PASSWORD': values['DB_PASSWORD'], 'AUTH_APP_IT': 'true',
        'AUTH_MANAGEMENT_OBSERVATIONS': str(observations),
        'AUTH_MANAGEMENT_SOURCE_COMMIT': provenance['source_commit'],
        'AUTH_MANAGEMENT_SOURCE_TREE': provenance['source_tree_sha256'],
    }
    selected = list(dict.fromkeys(name for phase in (TESTS if args.phase == 'all' else [args.phase]) for name in TESTS[phase]))
    classes = ['com.chellavignesh.authserver.'+name for name in selected]
    command = [str(ROOT / 'mvnw'), '-B', '-ntp', '-Dmaven.repo.local=' + str(LOCAL / 'm2'),
               '-Dtest=' + ','.join(classes), 'test']
    reports = [ROOT / ('target/surefire-reports/TEST-' + name + '.xml') for name in classes]
    for xml in reports:
        xml.unlink(missing_ok=True)
    log = LOCAL / ('e2e-admin-management-' + args.phase + '.log')
    with log.open('w') as output:
        os.chmod(log, 0o600)
        result = subprocess.run(command, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
    report = dict(schema_version=2, phase=args.phase, **provenance,
                  passed=result.returncode == 0 and all(xml.exists() for xml in reports),
                  transport='real HTTP', database='owned UUID disposable SQL Server databases',
                  provider='not exercised', provider_simulation=False, tests=[])
    for xml in reports:
        if xml.exists():
            for test in ET.parse(xml).getroot().findall('testcase'):
                report['tests'].append(dict(name=test.attrib['name'],
                    passed=all(test.find(kind) is None for kind in ('failure', 'error', 'skipped'))))
    report['passed'] = report['passed'] and bool(report['tests']) and all(test['passed'] for test in report['tests'])
    events = [json.loads(line) for line in observations.read_text().splitlines() if line]
    report['checks'] = qualify_observations(events, report['passed'], provenance)
    report['mutation_matrix'] = mutation_matrix(report['checks'])
    if args.phase == 'all':
        expected = expected_management_mutations()
        observed = {(row['method'], row['route']) for row in report['mutation_matrix']}
        report['mutation_inventory'] = [dict(method=method, route=route) for method, route in sorted(expected)]
        report['missing_mutation_denials'] = [dict(method=method, route=route) for method, route in sorted(expected-observed)]
        report['passed'] = report['passed'] and expected == observed and all(
            row['denial_with_unchanged_sql'] for row in report['mutation_matrix'])
    report['limitations'] = [
        'JUnit names describe assertions, not endpoint observations.',
        'SQL snapshots compare explicit public structural projections; secret/password/keystore/token columns are never selected.',
        'Concurrent tests may show unrelated concurrent changes; the dedicated denial matrix runs serially and asserts no structural writes.',
        'Provider-dependent positive flows and hosted browser integration require separate qualification.',
    ]
    target = LOCAL / 'e2e-admin-management-report.json'
    target.write_text(json.dumps(report, indent=2) + '\n')
    os.chmod(target, 0o600)
    print(json.dumps(dict(phase=args.phase, passed=report['passed'], tests=len(report['tests']),
                         actual_http_sql_observations=len(report['checks']), mutation_routes=len(report['mutation_matrix']),
                         evidence=str(target.relative_to(ROOT))), indent=2))
    raise SystemExit(0 if report['passed'] else (result.returncode or 1))


if __name__ == '__main__':
    main()
