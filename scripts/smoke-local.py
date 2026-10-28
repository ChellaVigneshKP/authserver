#!/usr/bin/env python3
"""Exercise a running local server with real cookies, PKCE, tokens and admin requests."""
import base64
from datetime import datetime, timezone
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import re
import secrets
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
from e2e_target import load_target
config, base, database = load_target()
client_id = os.environ.get('LOCAL_CLIENT_ID') or config.get('LOCAL_CLIENT_ID')
if not client_id:
    raise SystemExit('Set LOCAL_CLIENT_ID to the non-secret client_id printed by local bootstrap.')
callback = config.get('LOCAL_CALLBACK_URL', base+'/local/callback')

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, url):
        return None

jar = http.cookiejar.CookieJar()
client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), NoRedirect())
headers = {'User-Agent': 'AuthserverSmoke/1.0', 'Accept-Language': 'en-US,en;q=0.9'}
checks = []
report_path = ROOT/'.local/e2e-smoke-report.json'
report_path.unlink(missing_ok=True)

def request(path, data=None, token=None, json_body=None, datetime_value=True, method=None):
    extra = headers | {'x-request-datetime': datetime.now(timezone.utc).isoformat()}
    if datetime_value is None:
        extra.pop('x-request-datetime')
    elif datetime_value is not True:
        extra['x-request-datetime'] = datetime_value
    if json_body is not None:
        data = json.dumps(json_body).encode()
        extra['Content-Type'] = 'application/json'
    if token:
        extra['Authorization'] = 'Bearer '+token
    if isinstance(data, dict):
        data = urllib.parse.urlencode(data).encode()
        extra['Content-Type'] = 'application/x-www-form-urlencoded'
    req = urllib.request.Request(path if path.startswith('http') else base+path, data=data, headers=extra, method=method)
    try:
        response = client.open(req, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    checks.append(dict(id=method or ('POST' if data is not None else 'GET'),
                       method=method or ('POST' if data is not None else 'GET'),
                       path=urllib.parse.urlparse(req.full_url).path.removeprefix('/services'),
                       status=response.status, positive=response.status < 400))
    return response.status, response.headers, response.read().decode()


def require(condition, message):
    if not condition:
        raise AssertionError(message)

status, _, discovery_body = request('/.well-known/openid-configuration')
require(status == 200, f'OIDC discovery must be public; got {status}')
discovery = json.loads(discovery_body)
require(discovery['issuer'] == base and discovery['token_endpoint'] == base+'/oauth2/token', 'Discovery must advertise the real issuer and token route')
status, _, jwks_body = request(discovery['jwks_uri'])
require(status == 200 and json.loads(jwks_body)['keys'], 'Advertised signing key route must return a key')
print('PASS public OIDC discovery and signing keys')

verifier = secrets.token_urlsafe(48)
challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
state = secrets.token_urlsafe(16)
params = dict(response_type='code',client_id=client_id,redirect_uri=callback,
              scope='openid profile email metadata idp-admin',code_challenge=challenge,
              code_challenge_method='S256',branding='idp',state=state)
params['x-request-datetime'] = datetime.now(timezone.utc).isoformat()
status, response_headers, body = request('/oauth2/authorize?'+urllib.parse.urlencode(params))
require(status == 302, f'Authorization must redirect to login; got {status}: {body[:200]}')
status, response_headers, body = request(response_headers['Location'])
require(status == 200 and 'name="password"' in body, f'Login page must render; got {status}: {body[:200]}')
csrf = re.search(r'name="_csrf"[^>]*value="([^"]+)"', body)
require(csrf is not None, 'Login form must contain CSRF token')
form = dict(username=config['LOCAL_ADMIN_USERNAME'],
            password=base64.b64encode(config['LOCAL_ADMIN_PASSWORD'].encode()).decode(),_csrf=csrf[1])
status, response_headers, body = request('/login', form)
require(status == 302 and 'error' not in response_headers.get('Location',''),
        f'Credentials must authenticate; got {status}: {response_headers.get("Location")} {body[:200]}')
status, response_headers, body = request(response_headers['Location'])
require(status == 302, f'Post-login authorization must issue a redirect; got {status}: {body[:300]}')
query = urllib.parse.parse_qs(urllib.parse.urlparse(response_headers['Location']).query)
require(query.get('state') == [state] and 'code' in query, 'OAuth callback must preserve state and contain code')
code = query['code'][0]
exchange = dict(grant_type='authorization_code',code=code,client_id=client_id,redirect_uri=callback,code_verifier=verifier)
status, _, body = request('/oauth2/token', exchange)
require(status == 200, f'PKCE token exchange must succeed; got {status}: {body[:400]}')
tokens = json.loads(body)
require(all(k in tokens for k in ('access_token','id_token')), 'OIDC exchange must return access and ID tokens')
status, _, body = request('/api/v1/organizations', token=tokens['access_token'])
require(status == 200 and 'Ascensus' in body, f'Admin list must authorize; got {status}: {body[:300]}')
organizations = json.loads(body)
org = next(item for item in organizations if item['name'] == 'Ascensus')
for path in (f"/api/v1/organizations/{org['id']}", f"/api/v1/organizations/{org['id']}/groups",
             f"/api/v1/organizations/{org['id']}/applications"):
    status, _, result = request(path, token=tokens['access_token'])
    require(status == 200, f'Admin read must succeed for {path}; got {status}: {result[:200]}')
name = 'Smoke '+secrets.token_hex(6)
status, _, result = request('/api/v1/organizations', token=tokens['access_token'],
                            json_body=dict(name=name, description='Disposable local smoke fixture'))
require(status == 201, f'Organization creation must succeed; got {status}: {result[:200]}')
created_id = json.loads(result)['id']
status, _, result = request('/api/v1/organizations/'+created_id, token=tokens['access_token'], method='PUT',
                            json_body=dict(name=name+' updated', description='Smoke update', status='Active'))
require(status == 200 and name+' updated' in result, f'Organization update must persist; got {status}: {result[:200]}')
# Leave only an explicitly revoked smoke fixture; never delete pre-existing data.
status, _, result = request('/api/v1/organizations/'+created_id, token=tokens['access_token'], method='PUT',
                            json_body=dict(name=name+' updated', description='Completed smoke fixture', status='Revoked'))
require(status == 200, f'Smoke fixture revocation must succeed; got {status}: {result[:200]}')
for value in (None, 'invalid-datetime'):
    status, _, _ = request('/api/v1/organizations', token=tokens['access_token'], datetime_value=value)
    require(status == 400, f'Missing or malformed request datetime must be rejected; got {status}')
status, _, _ = request('/api/v1/organizations', token='invalid-token')
require(status == 401, f'Invalid bearer token must be rejected; got {status}')
print('PASS login, CSRF, PKCE exchange, OIDC tokens, admin reads/create/update/revoke and invalid request rejection')
status, _, body = request('/userinfo',token=tokens['access_token'])
require(status == 200 and json.loads(body).get('sub') == config['LOCAL_ADMIN_USERNAME'], f'Userinfo must return user identity; got {status}: {body[:300]}')
require('refresh_token' not in tokens, 'Public clients must not receive an unbound refresh token')
print('PASS userinfo and public-client token policy')
refreshed = tokens
status, _, body = request('/oauth2/connect/logout?'+urllib.parse.urlencode(dict(id_token_hint=refreshed['id_token'],client_id=client_id,post_logout_redirect_uri=callback)))
require(status == 302, f'Logout must redirect; got {status}: {body[:300]}')
status, _, _ = request('/api/v1/organizations',token=refreshed['access_token'])
require(status == 401, f'Logged-out access token must be rejected; got {status}')
status, _, _ = request('/oauth2/token',exchange)
require(status == 400, f'Authorization-code replay must be rejected; got {status}')
print('PASS logout, inactive-token rejection and authorization-code replay rejection')

# Publish observations only after every assertion in this smoke has passed.
for check in checks:
    check["passed"] = True
report_path.write_text(json.dumps(dict(checks=checks, database_checks=[], failures=[], scenario_passed=True), indent=2))
report_path.chmod(0o600)
