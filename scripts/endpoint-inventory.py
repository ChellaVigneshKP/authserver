#!/usr/bin/env python3
"""Deterministic source inventory; optional HTTP reports are observations, not qualification.

Usage: python3 scripts/endpoint-inventory.py [--results .local/e2e-report.json ...]
No server, database, network, or Maven calls are made. Unknown mapping expressions fail
closed so a new source construct cannot silently disappear from the inventory.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import parse_qs, urlsplit

ROOT = Path(__file__).resolve().parent.parent
MAPPING = re.compile(r'@(Request|Get|Post|Put|Patch|Delete|Head|Options)Mapping\b')
STR = r'"(?:\\.|[^"\\])*"'
BASELINE = 'scripts/smoke-local.py'
CONFIDENTIAL = 'src/test/java/com/chellavignesh/authserver/recovery/ConfidentialOAuthIntegrationTest.java'


def split_top(value, separator=','):
    result, start, depth, quoted, escaped = [], 0, 0, False, False
    for i, char in enumerate(value):
        if quoted:
            if escaped:
                escaped = False
            elif char == '\\':
                escaped = True
            elif char == '"':
                quoted = False
        elif char == '"':
            quoted = True
        elif char in '({[':
            depth += 1
        elif char in ')}]':
            depth -= 1
        elif char == separator and depth == 0:
            result.append(value[start:i].strip())
            start = i + 1
    return result + [value[start:].strip()]


def annotation_args(source, end):
    pos = end
    while pos < len(source) and source[pos].isspace():
        pos += 1
    if pos == len(source) or source[pos] != '(':
        return '', end
    depth, quoted, escaped = 1, False, False
    for i in range(pos + 1, len(source)):
        char = source[i]
        if quoted:
            if escaped:
                escaped = False
            elif char == '\\':
                escaped = True
            elif char == '"':
                quoted = False
        elif char == '"':
            quoted = True
        elif char == '(':
            depth += 1
        elif char == ')':
            depth -= 1
            if depth == 0:
                return source[pos + 1:i], i + 1
    raise ValueError('Unterminated mapping annotation')


def strings(expression, constants):
    expression = expression.strip()
    if expression.startswith('{') and expression.endswith('}'):
        return [item for part in split_top(expression[1:-1]) for item in strings(part, constants)]
    parts = split_top(expression, '+')
    if len(parts) > 1:
        return [''.join(strings(part, constants)[0] for part in parts)]
    if re.fullmatch(STR, expression):
        return [json.loads(expression)]
    if expression in constants:
        return [constants[expression]]
    raise ValueError('Unsupported mapping expression: ' + expression)


def attributes(arguments):
    result = {}
    for part in split_top(arguments):
        if not part:
            continue
        # A positional path can contain '='; only identifiers before '=' are keys.
        match = re.match(r'^(\w+)\s*=\s*(.*)$', part, re.S)
        if match:
            result[match[1]] = match[2]
        else:
            result['value'] = part
    return result


def fixture(controller, path):
    if controller in ('AccountAccessController', 'ForgotUsernameController'):
        return 'SQL/Redis; branded OAuth client and CMS templates; eligible local user; CSRF and recovery session; configured MFA delivery/verification for completed recovery; external source fixture for mobile aliases'
    if controller == 'MFAController':
        return 'SQL/Redis; eligible user and active flow session; client/CMS; real configured SecureAuth/SMS/email factor delivery and valid PIN; CSRF'
    if controller.startswith('ChangeUser'):
        return 'SQL/Redis; user and SSO session/cookie; encoded signed p parameter; client URLs, CMS, CSRF; valid token/signature; on-prem sync when enabled'
    if controller == 'LoginController':
        return 'SQL/Redis; branded authorization request/client, CMS, user and CSRF; matching failure/session context for error variants'
    if controller == 'OrganizationCertificateController':
        return 'SQL; organization membership and certificate permissions; valid PKCS12/private-key or public certificate multipart fixture; configured encryption/signing material'
    if controller == 'CredentialController':
        return 'SQL; organization/client and credential permissions; shared secret or valid certificate-backed credential, supported algorithm and active dates'
    if controller == 'SignatureController':
        return 'SQL; authenticated bearer and active signing credential; correctly signed body, clientId and current requestTimeDate'
    if controller == 'SessionTokenController':
        return 'SQL/Redis; valid ags-idp-session cookie bound to active user/client and request fingerprint'
    if controller == 'JWKSetController':
        return ('SQL; registered clientId and associated active credential/public certificate; verify public RSA modulus and no private fields'
                if '{clientId}' in path else 'SQL; organization GUID with valid organization signing certificate/key; verify public key and no private fields')
    if controller == 'OidcOrgConfigController':
        return 'Configured brand URL mapping; valid organization GUID; verify advertised organization semantics separately'
    if controller == 'LogoutController':
        return 'SQL/Redis; authenticated OAuth session, valid ID token hint/client and registered post-logout redirect; verify token/session invalidation'
    return 'SQL/Redis; valid idp-admin bearer, organization membership and endpoint permissions; disposable organization/application/user/resource fixture; current x-request-datetime; assert persisted mutations'


def mvc_rows():
    rows = []
    for file in sorted((ROOT / 'src/main/java').rglob('*.java')):
        source = file.read_text()
        # Blank comments while preserving offsets and quoted string content.
        source = re.sub(r'"(?:\\.|[^"\\])*"|/\*.*?\*/|//[^\n]*',
                        lambda m: m[0] if m[0].startswith('"') else re.sub(r'[^\n]', ' ', m[0]), source, flags=re.S)
        if not re.search(r'@(?:RestController|Controller)\b', source):
            continue
        declaration = re.search(r'\bclass\s+(\w+)', source)
        if not declaration:
            raise ValueError('Controller without class: ' + str(file))
        controller, class_start = declaration[1], declaration.start()
        constants = {m[1]: json.loads(m[2]) for m in re.finditer(r'\bString\s+(\w+)\s*=\s*(' + STR + r')\s*;', source)}
        mappings = []
        for match in MAPPING.finditer(source):
            args, end = annotation_args(source, match.end())
            attrs = attributes(args)
            paths = strings(attrs.get('path', attrs.get('value', '""')), constants)
            params = strings(attrs['params'], constants) if 'params' in attrs else []
            if match[1] == 'Request':
                methods = re.findall(r'RequestMethod\.(\w+)', attrs.get('method', '')) or ['ANY']
            else:
                methods = [match[1].upper()]
            mappings.append((match.start(), end, paths, methods, params, attrs))
        class_maps = [m for m in mappings if m[0] < class_start]
        if len(class_maps) > 1:
            raise ValueError('Multiple class mappings: ' + str(file))
        bases = class_maps[0][2] if class_maps else ['']
        for start, end, paths, methods, params, attrs in mappings:
            if start < class_start:
                continue
            # Skip intervening method annotations, including nested parentheses.
            cursor = end
            while True:
                ann = re.match(r'\s*@\w+(?:\.\w+)*', source[cursor:])
                if not ann:
                    break
                _, cursor = annotation_args(source, cursor + ann.end())
            method_match = re.match(r'\s*(?:public|protected|private)\s+[^;{=]*?\b(\w+)\s*\(', source[cursor:], re.S)
            if not method_match:
                raise ValueError(f'Cannot find handler after {file}:{source.count(chr(10), 0, start)+1}')
            for base in bases:
                for path in paths:
                    full = '/' + '/'.join(part.strip('/') for part in (base, path) if part.strip('/'))
                    for method in methods:
                        rows.append(dict(kind='mvc', method=method, path=full, params=params,
                                         controller=controller, handler=method_match[1],
                                         source=str(file.relative_to(ROOT)), line=source.count('\n', 0, start) + 1,
                                         mapping_attributes=attrs, required_fixtures=fixture(controller, full)))
    return rows


def builtins():
    auth = 'src/main/java/com/chellavignesh/authserver/config/AuthServerConfig.java'
    security = 'src/main/java/com/chellavignesh/authserver/security/SpringSecurityConfig.java'
    defaults = [
        ('GET', '/.well-known/openid-configuration', 'OidcProviderConfigurationEndpointFilter'),
        ('GET', '/.well-known/oauth-authorization-server', 'OAuth2AuthorizationServerMetadataEndpointFilter'),
        ('GET', '/oauth2/jwks', 'NimbusJwkSetEndpointFilter'),
        ('GET', '/oauth2/authorize', 'OAuth2AuthorizationEndpointFilter'),
        ('POST', '/oauth2/authorize', 'OAuth2AuthorizationEndpointFilter'),
        ('POST', '/oauth2/token', 'OAuth2TokenEndpointFilter'),
        ('POST', '/oauth2/introspect', 'OAuth2TokenIntrospectionEndpointFilter'),
        ('POST', '/oauth2/revoke', 'OAuth2TokenRevocationEndpointFilter'),
        ('POST', '/oauth2/device_authorization', 'OAuth2DeviceAuthorizationEndpointFilter'),
        ('GET', '/oauth2/device_verification', 'OAuth2DeviceVerificationEndpointFilter'),
        ('POST', '/oauth2/device_verification', 'OAuth2DeviceVerificationEndpointFilter'),
        ('GET', '/userinfo', 'OidcUserInfoEndpointFilter'),
        ('POST', '/userinfo', 'OidcUserInfoEndpointFilter'),
        ('GET', '/connect/logout', 'OidcLogoutEndpointFilter'),
        ('POST', '/connect/logout', 'OidcLogoutEndpointFilter'),
    ]
    def requirements(path):
        if path.startswith('/.well-known/'):
            return 'Configured issuer and enabled authorization-server/OIDC metadata; assert advertised endpoint URLs and issuer. Standalone smoke assertions are separate evidence unless imported.'
        if path == '/oauth2/jwks':
            return 'Configured server signing certificate/key; assert a nonempty public key set matching signed ID tokens. Standalone smoke assertions are separate evidence unless imported.'
        if path == '/oauth2/authorize':
            return 'SQL/Redis; registered OAuth client, valid redirects/scopes, user login and CSRF; POST authorization/consent branch needs its own positive scenario'
        if 'device_' in path:
            return 'SQL/Redis; client registered for device authorization grant; real user login, user/device codes, consent, and successful device-code token exchange'
        if path == '/connect/logout':
            return 'SQL/Redis; active OIDC session and valid ID token hint/client/registered redirect; exercise default OIDC logout GET/POST confirmation and invalidation'
        if path == '/oauth2/revoke':
            return 'SQL/Redis; supported confidential client authentication and valid issued token; prove revocation and subsequent inactive-token rejection'
        if path == '/userinfo':
            return 'SQL/Redis; active openid-scoped bearer token, user/session and claims; assert authenticated identity via GET and POST'
        if path == '/oauth2/introspect':
            return 'SQL/Redis; supported authenticated client and valid active/inactive issued tokens; assert introspection identity, scope and active state'
        return 'SQL/Redis; registered client, supported grant/auth method, code/token and valid redirect or PKCE; verify issuance, cryptographic claims and replay policy'

    auth_source = (ROOT / auth).read_text()
    if re.search(r'\.oidcLogoutEndpoint\(\s*"/oauth2/connect/logout"\s*\)', auth_source):
        # The order-1 custom logout chain handles the configured endpoint before SAS.
        defaults = [item for item in defaults if item[1] != '/connect/logout']
    rows = [dict(kind='security_filter', method=method, path=path, params=[], controller=handler,
                 handler=handler, source=auth, line=None,
                 required_fixtures=requirements(path))
            for method, path, handler in defaults]
    for method, path, handler in [('POST', '/login', 'UsernamePasswordAuthenticationFilter'),
                                  ('POST', '/login-biometric', 'BiometricAuthenticationFilter'),
                                  ('POST', '/logout', 'LogoutFilter')]:
        rows.append(dict(kind='security_filter', method=method, path=path, params=[], controller=handler,
                         handler=handler, source=security, line=None,
                         required_fixtures=('Real configured UniteMSC biometric token/device verification, enabled biometric integration and CSRF'
                                            if path == '/login-biometric' else 'SQL/Redis; branded OAuth login session, user and CSRF; logout requires an active authenticated session')))
    return rows


def authored(row):
    key = (row['method'], row['path'])
    result = []
    if row['params']:
        return result
    if key in {('GET', '/.well-known/openid-configuration'), ('GET', '/oauth2/jwks'),
               ('GET', '/oauth2/authorize'), ('GET', '/login'), ('POST', '/login'), ('POST', '/oauth2/token')}:
        result += [BASELINE, CONFIDENTIAL]
    if key in {('GET', '/userinfo'), ('GET', '/oauth2/connect/logout'),
               ('GET', '/api/v1/organizations'), ('POST', '/api/v1/organizations'),
               ('GET', '/api/v1/organizations/{id}'), ('PUT', '/api/v1/organizations/{orgGuid}'),
               ('GET', '/api/v1/organizations/{orgGuid}/groups'),
               ('GET', '/api/v1/organizations/{orgGuid}/applications')}:
        result += [BASELINE]
    if key in {('GET', '/oauth2/.well-known/{clientId}/jwks.json'),
               ('GET', '/oauth2/.well-known/organizations/{orgId}/jwks.json')}:
        result += ['scripts/e2e-certificates.py']
    if key in {('GET', '/oauth2/sso'), ('GET', '/user/change-profile'), ('POST', '/user/change-profile'),
               ('GET', '/user/change-password'), ('POST', '/user/change-password')}:
        result += ['scripts/e2e-browser-flows.py']
    return result


def matching(row, check):
    if row['method'] != check.get('method', '').upper():
        return False
    parts = urlsplit(check.get('path', check.get('url', '')))
    path = parts.path
    if path.startswith('/services/'):
        path = path[len('/services'):]
    pattern = re.sub(r'\\\{[^}]+\\\}', '[^/]+', re.escape(row['path']))
    if not re.fullmatch(pattern, path):
        return False
    query = parse_qs(parts.query, keep_blank_values=True)
    for param in row['params']:
        if '=' in param:
            key, value = param.split('=', 1)
            if query.get(key) != [value]:
                return False
        elif param.startswith('!'):
            if param[1:] in query:
                return False
        elif param not in query:
            return False
    # More-specific error handlers take priority over generic GET mappings.
    if not row['params'] and row['path'] in ('/login', '/mfa', '/user/change-profile'):
        if ('error' in query) or (row['path'] == '/login' and 'errorMessage' in query):
            return False
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--results', action='append', default=[], type=Path)
    args = parser.parse_args()
    rows = mvc_rows() + builtins()
    rows.sort(key=lambda row: (row['path'], row['method'], row['params'], row['source']))
    reports = []
    for path in args.results:
        raw = path.read_bytes()
        report = json.loads(raw)
        reports.append((str(path), report))
    for row in rows:
        row['id'] = row['method'] + ' ' + row['path'] + (' params=' + ','.join(row['params']) if row['params'] else '')
        row['authored_positive_scenarios'] = authored(row)
        row['observations'] = []
        for path, report in reports:
            for check in report.get('checks', []):
                if matching(row, check):
                    status = check.get('status')
                    positive = (check.get('passed') is True and isinstance(status, int) and
                                200 <= status < 400 and check.get('positive', True) is not False)
                    row['observations'].append(dict(report=path, check_id=check.get('id'),
                                                    status=status, passed=check.get('passed'), positive_http=positive,
                                                    actor_class=check.get('actor_class'), sql_outcome=check.get('sql_outcome'),
                                                    source_commit=check.get('source_commit', report.get('source_commit')),
                                                    source_tree_sha256=check.get('source_tree_sha256', report.get('source_tree_sha256')),
                                                    provider_simulation=bool(report.get('provider_simulation') or report.get('qualification','').startswith('Synthetic local')),
                                                    semantic_view_verified=check.get('semantic_view_verified') is True and check.get('passed') is True))
        positive = any(item['positive_http'] and not item['provider_simulation'] for item in row['observations'])
        simulated = any(item['positive_http'] and item['provider_simulation'] for item in row['observations'])
        view = any(item['semantic_view_verified'] for item in row['observations'])
        row['coverage'] = 'positive_http_observed' if positive else ('simulated_provider_positive_observed' if simulated else ('controlled_view_verified' if view else ('no_positive_http_observed' if row['observations'] else 'not_yet_qualified')))
        if positive:
            row['qualification'] = 'imported_positive_scenario_observed'
            row['remaining'] = 'Imported positive scenario observed; this does not certify every input or conditional branch.'
        elif simulated:
            row['qualification'] = 'local_simulated_provider_contract_only'
            row['remaining'] = 'Positive flow observed against an explicit local provider simulation; genuine external-provider compatibility remains unverified.'
        elif view:
            row['qualification'] = 'intentional_error_view_verified'
            row['remaining'] = 'HTTP 200 and expected form/message explicitly verified; intentional error view is not counted as a business success path.'
        elif 'Device' in row['controller']:
            row['qualification'] = 'blocked_no_supported_device_client'
            row['remaining'] = 'ApplicationService registers authorization_code/refresh_token or client_credentials, never device_code; successful device flow cannot be configured through the current client model.'
        elif row['controller'] == 'BiometricAuthenticationFilter':
            row['qualification'] = 'blocked_biometric_provider_not_configured'
            row['remaining'] = 'Local biometric integration is disabled; real UniteMSC token/device verification is not configured or simulated.'
        elif row['controller'] in ('AccountAccessController', 'ForgotUsernameController', 'MFAController'):
            row['qualification'] = 'blocked_external_flow_fixture_not_configured'
            row['remaining'] = 'Complete eligible recovery/MFA flow requires a real configured delivery/verification provider and flow session; mobile aliases additionally need external-source metadata. Boundary responses do not complete those flows.'
        else:
            row['qualification'] = 'not_yet_qualified'
            row['remaining'] = 'No positive HTTP result imported for this mapping. Required: ' + row['required_fixtures']
    counts = dict(Counter(row['kind'] for row in rows))
    coverage = dict(Counter(row['coverage'] for row in rows))
    output = dict(schema_version=2, context_path='/services',
                  scope='Explicit application MVC handler variants plus enabled security-filter routes; source-derived, not live runtime mappings.',
                  counts=dict(total=len(rows), **counts, **coverage),
                  limitations=[
                      'Rows expand class-level aliases and annotation params conditions; HEAD implicit on GET and framework OPTIONS/CORS are not separate rows.',
                      'Spring built-ins verified against local SAS 1.5.3 jar bytecode and repository configuration; inventory is not a live handler/filter dump.',
                      'Static resources, actuator and servlet error fallback are documented separately and excluded from route denominator.',
                      'Authored scenarios identify baseline source assertions only; suite execution and DB/log evidence are not inferred.',
                      'Passing expected 400/401/403/404 probes do not qualify positive handler behavior.',
                      'Local simulated-provider successes have a separate coverage state and do not certify genuine external providers.',
                      'A passing 2xx/3xx report check is only positive HTTP observation; it does not certify all branches or persisted mutations.',
                  ],
                  excluded_or_conditional=[
                      dict(path='/oauth2/par', reason='PAR configurer not enabled; setting default alone does not expose route'),
                      dict(path='/connect/register', reason='Dynamic client registration not enabled'),
                      dict(path='/connect/logout', reason='Explicit OIDC logout endpoint override uses custom /oauth2/connect/logout; default route not enabled'),
                      dict(path='/actuator', reason='Framework links endpoint; runtime exposure must be checked separately'),
                      dict(path='/actuator/health/**', reason='Framework health endpoint, selectors depend on runtime configuration'),
                      dict(path='/error', reason='Framework servlet error dispatch/fallback; no application controller'),
                      dict(path='/css/**,/js/**,/images/**,/static/**,/favicon.ico,/robots.txt', reason='Static resources; file-dependent, not business handler mappings'),
                  ],
                  evidence_reports=[dict(path=path, sha256=hashlib.sha256(Path(path).read_bytes()).hexdigest(),
                                         started_at=report.get('started_at'), finished_at=report.get('finished_at'),
                                         source_commit=report.get('source_commit'),
                                         source_tree_sha256=report.get('source_tree_sha256'),
                                         working_tree_dirty=report.get('working_tree_dirty'),
                                         provider_simulation=bool(report.get('provider_simulation') or report.get('qualification','').startswith('Synthetic local')),
                                         qualification=report.get('qualification'),
                                         database_checks=report.get('database_checks', []),
                                         log_checks=report.get('log_checks', []),
                                         blocked=report.get('blocked', []),
                                         not_enabled=report.get('not_enabled', []),
                                         failures=report.get('failures', []),
                                         failed_assertions=[item for item in report.get('assertions', []) if not item.get('passed')],
                                         failed_scenarios=[item for item in report.get('scenarios', []) if not item.get('passed')]) for path, report in reports],
                  endpoints=rows)
    destination = ROOT / 'docs/recovery/ENDPOINT-INVENTORY.json'
    destination.write_text(json.dumps(output, indent=2) + '\n')
    lines = ['# Endpoint coverage inventory', '',
             f"Source inventory: **{len(rows)} mapping rows** ({counts.get('mvc', 0)} MVC handler variants; {counts.get('security_filter', 0)} security-filter routes). Paths are relative to `/services`.", '',
             f"Imported results: **{coverage.get('positive_http_observed', 0)} mapping variants with positive HTTP observations outside the provider simulation**, **{coverage.get('simulated_provider_positive_observed', 0)} additional variants verified only with simulated providers**, **{coverage.get('controlled_view_verified', 0)} intentional error views semantically verified**, **{coverage.get('no_positive_http_observed', 0)} exercised without a completed positive flow**, **{coverage.get('not_yet_qualified', 0)} unobserved**. Observations qualify the imported scenarios and do not certify every conditional branch. Per-row qualification records distinguish controlled views from concrete integration/client-model blockers.", '',
             'Regenerate with `python3 scripts/endpoint-inventory.py` and repeatable `--results .local/e2e-report.json`. No network or runtime requests occur. The JSON records report hashes and database-check summaries; reports can contain sensitive runtime data and stay local.', '',
             'Baseline `smoke-local.py` authors public discovery/JWKS, login/CSRF, PKCE authorization-code exchange, admin organization reads/create/update/revoke, userinfo and custom GET logout. `ConfidentialOAuthIntegrationTest` authors real HTTP login/code exchange and refresh rotation using `client_secret_jwt`, plus ID-token cryptographic verification and replay rejection. It creates application/credential fixtures directly through services/repositories; this does **not** cover application/credential creation HTTP endpoints. `e2e-browser-flows.py` authors real cookie/CSRF/SSO/HMAC profile/password changes on a disposable user, SQL persistence and new-password login checks, plus HMAC/CSRF/encoding rejection checks. Error responses alone never count as positive coverage.', '',
             'MVC rows expand recovery aliases and `params` variants. Implicit HEAD/OPTIONS/CORS, static assets, actuator links/health selectors, and servlet `/error` are excluded from this denominator; their presence and exposure require separate runtime checks. Built-in OAuth routes follow local SAS 1.5.3 defaults plus explicit repository overrides. OIDC logout is configured at the custom `/oauth2/connect/logout` route; `/connect/logout` is not enabled. Device flows have default filters but no supported registered device-grant client. PAR and dynamic client registration are not enabled.', '',
             '| Method | Path / mapping condition | Handler | Positive scenario source / observations | State |',
             '|---|---|---|---|---|']
    # Keep remaining coverage gaps explicit even when a boundary probe passed.
    gap_groups = Counter(row['controller'] for row in rows if row['coverage'] not in ('positive_http_observed', 'controlled_view_verified'))
    gap_lines = ['Mappings still lacking positive evidence from genuine external providers, including simulated-only successes:', '']
    for controller, count in sorted(gap_groups.items()):
        example = next(row for row in rows if row['controller'] == controller and row['coverage'] not in ('positive_http_observed', 'controlled_view_verified'))
        gap_lines.append(f"- **{controller}: {count} mappings.** {example['required_fixtures']}")
    gap_lines += ['', 'Only supplied JSON reports contribute runtime observations; authored source scenarios alone do not. The requirements below each mapping describe needed fixtures, not a claim that every gap is externally blocked. Exact rows and observed failures remain in the table and JSON.', '']
    # Insert the gap list before the route table.
    table_start = lines.index('| Method | Path / mapping condition | Handler | Positive scenario source / observations | State |')
    lines[table_start:table_start] = gap_lines
    for row in rows:
        condition = ('; params=' + ','.join(row['params'])) if row['params'] else ''
        scenarios = ', '.join(Path(path).name for path in row['authored_positive_scenarios']) or 'none in baseline'
        observed = '; '.join(f"{item['check_id']} ({item['status']}, {'pass' if item['passed'] else 'FAIL'}{', simulation' if item['provider_simulation'] else ''})" for item in row['observations'])
        if observed:
            scenarios += '; observed: ' + observed
        lines.append(f"| {row['method']} | `{row['path']}{condition}` | {row['controller']}.{row['handler']} | {scenarios.replace('|', '/')} | {row['coverage']}; {row['qualification']} |")
    lines += ['', 'Required fixtures and integrations for every row are in `ENDPOINT-INVENTORY.json`. Recovery/MFA needs active branded flow sessions and a configured real delivery/verification provider; mobile recovery needs external-source metadata. Profile/password changes need signed encoded parameters, SSO cookie/fingerprint, CMS, CSRF and valid tokens. Certificate/credential endpoints need valid cryptographic material, multipart content and membership/permissions. Biometric success needs UniteMSC token/device verification. A local disabled/unconfigured provider is a qualification blocker, not a successful end-to-end result.', '']
    (ROOT / 'docs/recovery/ENDPOINT-COVERAGE.md').write_text('\n'.join(lines))
    print(json.dumps(output['counts'], sort_keys=True))


if __name__ == '__main__':
    main()
