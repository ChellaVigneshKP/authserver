#!/usr/bin/env python3
"""Exercise actual cookie/CSRF/SSO/HMAC HTTP forms against the local SQL-backed app.

No external recovery/biometric provider is simulated. Uses a disposable organization
and user, never modifies the bootstrap administrator. Reports omit passwords, cookie
values, tokens, signing keys, DTO bodies and raw SQL output.
"""
import base64
from datetime import datetime, timezone
import hashlib
import hmac
from html import unescape
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import re
import runpy
import secrets
import sys
import urllib.parse
import uuid

ROOT = Path(__file__).resolve().parent.parent
Acceptance = runpy.run_path(str(ROOT / 'scripts/e2e-local.py'))['Acceptance']


def b64(value):
    return base64.b64encode(value.encode()).decode()


class FormParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.forms = []
        self.current = None

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'form':
            self.current = dict(attrs=attrs, inputs=[])
            self.forms.append(self.current)
        elif tag == 'input' and self.current is not None:
            self.current['inputs'].append(attrs)

    def handle_endtag(self, tag):
        if tag == 'form':
            self.current = None


class BrowserFlows(Acceptance):
    def __init__(self):
        super().__init__()
        self.org = None
        self.user_id = None
        self.user = None
        self.admin = None
        self.report['suite'] = 'real HTTP signed self-service forms'

    def assert_state(self, label, condition):
        self.report.setdefault('assertions', []).append(dict(id=label, passed=bool(condition)))
        if not condition:
            raise AssertionError(label)

    def user_state(self):
        return self.sql(f"SELECT p.FirstName,p.LastName,p.Email,p.PhoneNumber,p.SecondaryPhoneNumber,CONVERT(VARCHAR(64),HASHBYTES('SHA2_256',c.Password),2) AS PasswordDigest FROM Person.Profile p JOIN Person.Credential c ON c.ProfileId=p.ProfileId WHERE p.RowGuid='{self.user_id}' FOR JSON PATH")[0]

    def no_mutation(self, label, state):
        current = self.user_state()
        passed = current == state
        self.report['database_checks'].append(dict(id=label, passed=passed))
        if not passed:
            raise AssertionError(label)

    def create_fixture(self):
        self.login()
        self.admin = Acceptance()
        self.admin.token = self.token
        self.admin.prefix = self.prefix
        self.org = self.organization()
        username = 'forms_' + secrets.token_hex(6)
        password = 'W8!' + secrets.token_hex(15) + 'qZ'
        payload = dict(firstName='Browser', lastName='Fixture', username=username,
                       email=username+'@example.test', password=b64(password),
                       phoneNumber='+19999999999', orgGuid=self.org, branding='idp', syncFlag=False)
        _, user = self.request('POST', '/api/v1/users', 201, payload=payload, label='create disposable forms user')
        self.user_id = user['id']
        self.request('PUT', '/api/v1/users/'+self.user_id+'/security-settings', 200,
                     payload=dict(twoFactorEnabled=False), label='disable MFA only for disposable forms user')
        self.user = dict(username=username, password=password)
        self.db('forms user creation persisted', f"SELECT p.FirstName FROM Person.Profile p WHERE p.RowGuid='{self.user_id}' FOR JSON PATH",
                lambda rows: len(rows) == 1 and rows[0]['FirstName'] == 'Browser')
        # Separate real cookie jars prevent accidental bootstrap-admin session reuse.
        import http.cookiejar
        import urllib.request
        no_redirect = runpy.run_path(str(ROOT / 'scripts/e2e-local.py'))['NoRedirect']
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), no_redirect())
        self.token = None
        self.login(username, password, scopes='openid profile email metadata')
        self.sso()

    def sso(self):
        _, data = self.request('GET', '/oauth2/sso', 200, authenticated=False, label='SSO cookie returns active token and signing key')
        self.assert_state('SSO response contains access token and signing key', isinstance(data, dict) and bool(data.get('access_token')) and bool(data.get('signing_key')))
        self.sso_data = data
        raw = data['signing_key']
        self.signing_key = bytes((byte % 256 for byte in raw)) if isinstance(raw, list) else base64.b64decode(raw)
        return data

    def page(self, name):
        params = dict(surl='/fixture-return', mt=json.dumps(dict(idp=[], unite=[]), separators=(',', ':')),
                      **{'x-request-datetime': datetime.now(timezone.utc).isoformat()})
        p = b64(urllib.parse.urlencode(params))
        _, body = self.request('GET', '/user/'+name+'?'+urllib.parse.urlencode(dict(p=p)), 200,
                               authenticated=False, label='render signed '+name+' form')
        parser = FormParser()
        parser.feed(body)
        forms = [form for form in parser.forms if 'data-signed-profile' in form['attrs']]
        self.assert_state(name+' page contains signed form', len(forms) == 1)
        form = forms[0]
        self.assert_state(name+' form posts to correct endpoint', form['attrs'].get('action') == '/services/user/'+name)
        fields = {field['name']: unescape(field.get('value', '')) for field in form['inputs'] if field.get('name')}
        self.assert_state(name+' form contains CSRF token', bool(fields.get('_csrf')))
        return form, fields

    def signed(self, form, fields, values):
        # Exactly the frontend's input:not([type=hidden]) insertion order.
        order = [field['name'] for field in form['inputs'] if field.get('type') != 'hidden' and field.get('name')]
        self.assert_state('all signed DTO fields supplied in declaration order', set(order) == set(values))
        dto = {name: values[name] for name in order}
        data = json.dumps(dto, separators=(',', ':'), ensure_ascii=False).encode()
        self.sso()
        signature = base64.b64encode(hmac.new(self.signing_key, data, hashlib.sha256).digest()).decode()
        return fields | dto | {'Authorization':'Bearer '+self.sso_data['access_token'], 'x-signature':signature,
                               'x-request-datetime':datetime.now(timezone.utc).isoformat(), 'x-request-id':str(uuid.uuid4())}

    def assert_redirect(self, label, headers, error=None):
        location = headers.get('Location', '')
        query = urllib.parse.parse_qs(urllib.parse.urlparse(location).query)
        encoded = query.get('p', [''])[0]
        try:
            decoded = base64.b64decode(encoded).decode()
        except Exception:
            decoded = ''
        self.assert_state(label+' returns application callback', location.startswith(self.config.get('LOCAL_CALLBACK_URL', self.base+'/local/callback')+'?'))
        self.assert_state(label+' preserves source URL', 'surl=/fixture-return' in decoded)
        if error:
            self.assert_state(label+' identifies expected error', 'errors.codes='+error in decoded)
        else:
            self.assert_state(label+' has no error code', 'errors.codes=' not in decoded)

    def profile(self):
        form, fields = self.page('change-profile')
        values = dict(firstName='Changed', lastName='Updated', email=self.user['username']+'.changed@example.test',
                      confirmEmail=self.user['username']+'.changed@example.test', phoneNumber='(202) 555-0198', secondaryPhoneNumber='(202) 555-0199')
        signed = self.signed(form, fields, values)
        before = self.user_state()
        bad = signed | {'x-signature':base64.b64encode(bytes(32)).decode()}
        headers, _ = self.request('POST', '/user/change-profile', 302, form=bad, authenticated=False,
                                  positive=False, label='invalid profile HMAC rejected')
        self.assert_redirect('invalid profile HMAC', headers, 'auths.401.002')
        self.no_mutation('invalid profile HMAC leaves SQL unchanged', before)
        no_csrf = dict(signed)
        no_csrf.pop('_csrf', None)
        self.request('POST', '/user/change-profile', 403, form=no_csrf, authenticated=False,
                     positive=False, label='profile missing CSRF rejected')
        self.no_mutation('missing profile CSRF leaves SQL unchanged', before)
        headers, _ = self.request('POST', '/user/change-profile', 302, form=signed, authenticated=False,
                                  positive=False, label='valid signed self-service profile update')
        mutation_check = self.report['checks'][-1]
        self.assert_redirect('profile update', headers)
        self.db('self-service profile and email persisted',
                f"SELECT p.FirstName,p.LastName,p.Email,p.PhoneNumber,p.SecondaryPhoneNumber FROM Person.Profile p WHERE p.RowGuid='{self.user_id}' FOR JSON PATH",
                lambda rows: rows[0]['FirstName'] == 'Changed' and rows[0]['LastName'] == 'Updated' and rows[0]['Email'] == values['email'] and rows[0]['PhoneNumber'] == '+12025550198' and rows[0]['SecondaryPhoneNumber'] == '+12025550199')
        mutation_check['positive'] = True

    def validation_retry(self):
        form, fields = self.page('change-password')
        values = dict(currentPassword=b64(self.user['password']), newPassword='!!!invalid-base64!!!', confirmPassword='!!!invalid-base64!!!')
        before = self.user_state()
        signed = self.signed(form, fields, values)
        headers, _ = self.request('POST', '/user/change-password', 302, form=signed, authenticated=False,
                                  positive=False, label='malformed password encoding redirects to validation form')
        location = headers.get('Location', '')
        self.assert_state('invalid password encoding redirects to password form', '/user/change-password?p=' in location)
        self.no_mutation('malformed password encoding leaves SQL unchanged', before)
        self.request('GET', location, 200, authenticated=False, positive=False,
                     label='password validation retry page retains required datetime')

    def password(self):
        form, fields = self.page('change-password')
        new_password = 'Y6!' + secrets.token_hex(15) + 'uV'
        values = dict(currentPassword=b64(self.user['password']), newPassword=b64(new_password), confirmPassword=b64(new_password))
        signed = self.signed(form, fields, values)
        before = self.user_state()
        bad = signed | {'x-signature':base64.b64encode(bytes(32)).decode()}
        headers, _ = self.request('POST', '/user/change-password', 302, form=bad, authenticated=False,
                                  positive=False, label='invalid password HMAC rejected')
        self.assert_redirect('invalid password HMAC', headers, 'auths.401.002')
        self.no_mutation('invalid password HMAC leaves SQL unchanged', before)
        no_csrf = dict(signed)
        no_csrf.pop('_csrf', None)
        self.request('POST', '/user/change-password', 403, form=no_csrf, authenticated=False,
                     positive=False, label='password missing CSRF rejected')
        self.no_mutation('missing password CSRF leaves SQL unchanged', before)
        headers, _ = self.request('POST', '/user/change-password', 302, form=signed, authenticated=False,
                                  positive=False, label='valid signed self-service password change')
        mutation_check = self.report['checks'][-1]
        self.assert_redirect('password change', headers)
        after = self.user_state()
        passed = before['PasswordDigest'] != after['PasswordDigest']
        self.report['database_checks'].append(dict(id='self-service password binary hash changed', passed=passed))
        self.assert_state('self-service password hash changed', passed)
        # A fresh HTTP session proves password verification, without reusing SSO.
        import http.cookiejar
        import urllib.request
        no_redirect = runpy.run_path(str(ROOT / 'scripts/e2e-local.py'))['NoRedirect']
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), no_redirect())
        self.token = None
        self.login(self.user['username'], new_password, scopes='openid profile email metadata')
        self.assert_state('fresh PKCE login accepts new self-service password', bool(self.token))
        self.user['password'] = new_password
        mutation_check['positive'] = True

    def cleanup(self):
        if self.admin:
            try:
                if self.user_id:
                    self.admin.request('DELETE', '/api/v1/users/'+self.user_id, 204, label='delete disposable forms user')
                if self.org:
                    self.admin.request('PUT', '/api/v1/organizations/'+self.org, 200,
                                       payload=dict(name=self.prefix, description='Completed browser forms fixture', status='Revoked'),
                                       label='revoke disposable forms organization')
                    self.admin.db('forms fixture organization revoked', f"SELECT Status FROM Partner.Organization WHERE RowGuid='{self.org}' FOR JSON PATH", lambda rows: rows[0]['Status'] == 0)
            finally:
                self.report['checks'].extend(self.admin.report['checks'])
                self.report['database_checks'].extend(self.admin.report['database_checks'])

    def record_logs(self):
        source = Path(os.environ.get('AUTH_E2E_LOG', '/tmp/authserver-recovery/app-e2e.log'))
        self.report['log_checks'] = []
        if not self.user or not source.is_file():
            self.report['log_evidence_available'] = False
            return
        lines = source.read_text(errors='replace').splitlines()
        for method, path, status in [('GET', '/oauth2/sso', 200), ('GET', '/user/change-profile', 200),
                                     ('POST', '/user/change-profile', 302), ('GET', '/user/change-password', 200),
                                     ('POST', '/user/change-password', 302)]:
            marker = f'"{method} /services{path} HTTP/1.1" {status} '
            matches = [number for number, line in enumerate(lines, 1) if self.user['username'] in line and marker in line]
            self.report['log_checks'].append(dict(id=f'local access log confirms {method} {path} {status}',
                                                  passed=bool(matches), count=len(matches), source=str(source),
                                                  source_lines=matches))
        self.report['log_evidence_available'] = all(item['passed'] for item in self.report['log_checks'])

    def run(self):
        try:
            self.create_fixture()
            for name, case in [('signed profile form', self.profile), ('password validation retry', self.validation_retry), ('signed password form', self.password)]:
                try:
                    case()
                    print('PASS', name)
                except Exception as error:
                    self.report['failures'].append(dict(case=name, error=str(error)))
                    print('FAIL', name, ':', str(error))
        except Exception as error:
            self.report['failures'].append(dict(case='fixture and user login', error=str(error)))
            print('FAIL fixture and user login:', str(error))
        finally:
            try:
                self.cleanup()
            except Exception as error:
                self.report['failures'].append(dict(case='fixture cleanup', error=str(error)))
                print('FAIL fixture cleanup:', str(error))
            self.record_logs()
            self.report['finished_at'] = datetime.now(timezone.utc).isoformat()
            (ROOT/'.local/e2e-browser-report.json').write_text(json.dumps(self.report, indent=2)+'\n')
        print('HTTP checks:', len(self.report['checks']), 'DB checks:', len(self.report['database_checks']), 'failed cases:', len(self.report['failures']))
        return bool(self.report['failures'])


if __name__ == '__main__':
    sys.exit(BrowserFlows().run())
