#!/usr/bin/env python3
"""Real local HTTP/SQL certificate and RSA client-assertion acceptance tests.

Requires the local app, Compose SQL Server, openssl and .local/dev.json.
Only uniquely named fixtures are created; their certificates are deactivated on exit.
Private keys live in an automatically removed mode-0700 temporary directory.
"""
import atexit
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import time
import uuid
import urllib.parse

from e2e_target import load_target, sql_text

ROOT = Path(__file__).resolve().parent.parent


def main():
    _, base_override, database = load_target()
    if urllib.parse.urlparse(base_override).hostname not in ('localhost', '127.0.0.1'):
        raise SystemExit('Certificate fixture tests are restricted to the local development app.')
    results = dict(checks=[], database_checks=[], scenarios=[], database=database, app_base=base_override, provider='unconfigured; not exercised')
    def save_results():
        report_path = ROOT / '.local/certificate-e2e-report.json'
        descriptor = os.open(report_path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(descriptor, 'w') as report:
            json.dump(results, report, indent=2)
    atexit.register(save_results)
    # Share the established real browser/PKCE login, stopping before its fixture/logout.
    smoke = (ROOT / 'scripts/smoke-local.py').read_text()
    login = smoke.split("name = 'Smoke '", 1)[0]
    assert login != smoke, 'Smoke login boundary has changed'
    context = {'__file__': str(ROOT / 'scripts/smoke-local.py')}
    exec(compile(login, 'smoke-local.py login', 'exec'), context)
    request, require = context['request'], context['require']
    token, org, base = context['tokens']['access_token'], context['org'], context['base']
    config = context['config']
    cert_path = f"/api/v1/organizations/{org['id']}/certificates"
    app_path = f"/api/v1/organizations/{org['id']}/applications"
    fixture = 'Certificate E2E ' + secrets.token_hex(8)
    created = []
    failures = []
    credential_path = credential_id = None

    def check(condition, message):
        require(condition, message)

    def api(path, expected=200, **kwargs):
        status, headers, body = request(path, token=token, **kwargs)
        results['checks'].append(dict(id=path, method=kwargs.get('method') or ('POST' if 'data' in kwargs or 'json_body' in kwargs else 'GET'), path=path, status=status, expected=[expected], passed=status == expected))
        check(status == expected, f'{path}: expected {expected}, got {status}: {body[:220]}')
        return headers, body

    def sql(query):
        output = sql_text(config,database,query,scalar=True)
        results['database_checks'].append(dict(operation=query.split()[0], passed=True))
        return output

    def upload(contents, name, kind, expected=201):
        boundary = 'e2e' + secrets.token_hex(16)
        body = b''
        for key, value in {'name': name, 'type': kind, 'password': ''}.items():
            body += f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode()
        body += f'--{boundary}\r\nContent-Disposition: form-data; name="certificate"; filename="fixture.pem"\r\nContent-Type: application/x-pem-file\r\n\r\n'.encode() + contents + f'\r\n--{boundary}--\r\n'.encode()
        # The smoke helper accepts arbitrary bytes; add the multipart content type temporarily.
        context['headers']['Content-Type'] = 'multipart/form-data; boundary=' + boundary
        try:
            _, result = api(cert_path, expected=expected, data=body)
        finally:
            context['headers'].pop('Content-Type', None)
        if expected == 201:
            cert = json.loads(result)['id']
            created.append(cert)
            return cert

    def report(label, test):
        try:
            test()
            results['scenarios'].append(dict(id=label, passed=True))
            print('PASS ' + label)
        except Exception as error:
            results['scenarios'].append(dict(id=label, passed=False))
            failures.append(label)
            print('FAIL ' + label + ': ' + str(error))

    with tempfile.TemporaryDirectory(prefix='authserver-cert-e2e-') as directory:
        directory = Path(directory)
        key, cert = directory/'key.pem', directory/'cert.pem'
        subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes',
            '-keyout', str(key), '-out', str(cert), '-days', '2', '-subj', '/CN='+fixture],
            capture_output=True, check=True)
        os.chmod(key, 0o600)
        pem = cert.read_bytes()
        der = subprocess.run(['openssl', 'x509', '-in', str(cert), '-outform', 'DER'], capture_output=True, check=True).stdout
        try:
            public_id = upload(pem, fixture+' public', 'PUBLIC_KEY')
            report('real organization private-key PEM upload', lambda: upload(pem+key.read_bytes(), fixture+' signing', 'ORGANIZATION'))
            print('PASS real RSA public certificate PEM upload')

            def persistence():
                ids = ','.join("'"+item+"'" for item in created)
                rows = sql(f"SELECT COUNT(*) FROM Partner.Certificate c JOIN Partner.KeyStorePassword p ON p.PasswordKeyId=c.PasswordKeyId WHERE c.RowGuid IN ({ids}) AND DATALENGTH(c.KeyStore)>0 AND DATALENGTH(p.KeyStore)>0 AND c.Status=1")
                check(rows == str(len(created)), 'Both certificate and password keystores must be persisted: '+rows)
                _, body = api(cert_path)
                check(set(created).issubset({item['id'] for item in json.loads(body)}), 'List must contain both uploaded certificates')
                for cert_id in created:
                    _, body = api(cert_path+'/'+cert_id)
                    result = json.loads(body)
                    check(base64.b64decode(result['certificate']) == der, 'DB-loaded certificate DER must round trip exactly')
                    check(result['fingerprint'] == hashlib.sha256(der).hexdigest(), 'Fingerprint must match original DER')
            report('SQL keystore persistence, certificate list/get and fingerprint', persistence)

            def download():
                for cert_id in created:
                    headers, body = api(cert_path+'/'+cert_id+'/download')
                    check('attachment;' in headers['Content-Disposition'], 'Download disposition missing')
                    downloaded = directory/'download.pem'
                    downloaded.write_text(body)
                    parsed = subprocess.run(['openssl','x509','-in',str(downloaded),'-outform','DER'],capture_output=True)
                    check(parsed.returncode == 0 and parsed.stdout == der,
                          'Downloaded PEM must parse and contain the exact original certificate')
            report('certificate downloads preserve complete X.509 DER', download)
            report('organization certificate rejects public-only PEM', lambda: upload(pem, fixture+' invalid', 'ORGANIZATION',400))
            report('invalid certificate rejects malformed PEM', lambda: upload(b'not a certificate', fixture+' malformed','PUBLIC_KEY',400))
            report('missing certificate returns not found', lambda: api(cert_path+'/'+str(uuid.uuid4()),expected=404))

            _, body = api(app_path, expected=201, json_body=dict(name=fixture, description='Disposable RSA client assertion fixture',type='SERVER',authMethod='PRIVATE_KEY_JWT',allowForgotUsername=False))
            app = json.loads(body)
            # Creation returns an ID; obtain the registered public client identifier through real GET.
            _, body = api(app_path+'/'+app['id'])
            app = json.loads(body)
            client_id = app['clientId']
            credential_path = app_path+'/'+app['id']+'/credentials'
            _, body = api(credential_path, expected=201, json_body=dict(name=fixture, type='PRIVATE_KEY',algorithm='RS256',certificateId=public_id,status='Active'))
            credential_id = json.loads(body)['id']
            check(sql(f"SELECT COUNT(*) FROM Client.Credential cr JOIN Partner.Certificate c ON c.CertificateId=cr.CertificateId JOIN Client.Application a ON a.ApplicationId=cr.ApplicationId WHERE cr.RowGuid='{credential_id}' AND c.RowGuid='{public_id}' AND a.RowGuid='{app['id']}'") == '1', 'SQL must associate created application, credential and certificate')
            print('PASS SERVER PRIVATE_KEY_JWT application and associated RS256 credential creation with SQL verification')

            def public_jwks(path, label):
                status, _, body = request(path)
                observation = dict(id=label, method='GET', path=path, status=status,
                                   expected=[200], passed=status == 200, positive=False)
                results['checks'].append(observation)
                check(status == 200, 'Public JWKS endpoint must return HTTP 200')
                keys = json.loads(body).get('keys', [])
                modulus = subprocess.run(['openssl', 'x509', '-in', str(cert), '-noout', '-modulus'],
                                         capture_output=True, check=True).stdout.decode().strip().split('=', 1)[1]
                expected_modulus = int(modulus, 16)
                matching_key = any(item.get('kty') == 'RSA' and item.get('n') and
                                   int.from_bytes(base64.urlsafe_b64decode(item['n']+'='*(-len(item['n']) % 4)), 'big') == expected_modulus
                                   for item in keys)
                check(matching_key, 'Published JWKS must contain the uploaded fixture certificate public key')
                check(all(not (set(item) & {'d', 'p', 'q', 'dp', 'dq', 'qi', 'oth', 'k'}) for item in keys),
                      'Public JWKS must never contain private or symmetric key material')
                observation['positive'] = True

            report('public client JWKS contains associated certificate RSA key only',
                   lambda: public_jwks('/oauth2/.well-known/'+client_id+'/jwks.json', 'configured client public JWKS'))
            report('public organization JWKS contains organization signing RSA key only',
                   lambda: public_jwks('/oauth2/.well-known/organizations/'+org['id']+'/jwks.json', 'organization public JWKS'))

            def assertion(**overrides):
                now = int(time.time())
                claims = dict(iss=client_id,sub=client_id,aud=base+'/oauth2/token',iat=now,exp=now+120,jti=secrets.token_hex(16)) | overrides
                def b64(value):
                    return base64.urlsafe_b64encode(value).decode().rstrip('=')
                signing = '.'.join(b64(json.dumps(value,separators=(',',':')).encode()) for value in ({'alg':'RS256','typ':'JWT'},claims)).encode()
                signature = subprocess.run(['openssl','dgst','-sha256','-sign',str(key)],input=signing,capture_output=True,check=True).stdout
                return signing.decode()+'.'+b64(signature)

            def token_request(expected, **overrides):
                status, _, body = request('/oauth2/token', dict(grant_type='client_credentials',client_id=client_id,client_assertion_type='urn:ietf:params:oauth:client-assertion-type:jwt-bearer',client_assertion=assertion(**overrides)))
                results['checks'].append(dict(id='RS256 assertion '+(','.join(overrides) or 'valid'), method='POST',path='/oauth2/token',status=status,expected=[expected],passed=status == expected,positive=expected == 200))
                check(status == expected, f'RSA assertion token request expected {expected}, got {status}: '+(body[:220] if status != 200 else '(access token omitted)'))
                result = json.loads(body)
                if expected == 200:
                    check(bool(result.get('access_token')) and result.get('token_type','').lower() == 'bearer', 'Successful request must return bearer token')
                else:
                    check(result.get('error') == 'invalid_client', 'Invalid assertion must return invalid_client')
            report('real RS256 private_key_jwt client_credentials issuance',lambda: token_request(200))
            report('private_key_jwt wrong audience rejected',lambda: token_request(401,aud='https://invalid.example/token'))
            report('private_key_jwt expired assertion rejected',lambda: token_request(401,exp=int(time.time())-300,iat=int(time.time())-600))
            report('private_key_jwt wrong subject rejected',lambda: token_request(401,sub='unrelated-client'))
            report('private_key_jwt wrong issuer rejected',lambda: token_request(401,iss='unrelated-client'))
            api(credential_path+'/'+credential_id, method='PUT',json_body=dict(status='Disabled'))
            report('disabled private-key credential rejected',lambda: token_request(401))
            api(credential_path+'/'+credential_id, method='PUT',json_body=dict(status='Active'))
            report('reactivated private-key credential permits issuance',lambda: token_request(200))
            sql(f"UPDATE Client.Credential SET ExpireOn=DATEADD(day,-1,SYSUTCDATETIME()) WHERE RowGuid='{credential_id}'")
            report('SQL-expired private-key credential rejects issuance',lambda: token_request(401))
            api(credential_path+'/'+credential_id, method='PUT',json_body=dict(status='Disabled'))
            report('expired credential cannot reactivate',lambda: api(credential_path+'/'+credential_id,expected=400,method='PUT',json_body=dict(status='Active')))
            sql(f"UPDATE Client.Credential SET ExpireOn=DATEADD(day,1,SYSUTCDATETIME()) WHERE RowGuid='{credential_id}'")
            api(credential_path+'/'+credential_id, method='PUT',json_body=dict(status='Active'))
            report('restored owned credential permits issuance',lambda: token_request(200))
            sql(f"UPDATE Partner.Certificate SET ValidFrom=DATEADD(day,1,SYSUTCDATETIME()) WHERE RowGuid='{public_id}'")
            report('future-valid certificate rejects private-key authentication',lambda: token_request(401))
            sql(f"UPDATE Partner.Certificate SET ValidFrom=DATEADD(day,-1,SYSUTCDATETIME()),ValidTo=DATEADD(day,-1,SYSUTCDATETIME()) WHERE RowGuid='{public_id}'")
            report('SQL-expired certificate rejects private-key authentication',lambda: token_request(401))
            sql(f"UPDATE Partner.Certificate SET ValidTo=DATEADD(day,2,SYSUTCDATETIME()) WHERE RowGuid='{public_id}'")
            report('restored owned certificate permits issuance',lambda: token_request(200))
            api(cert_path+'/'+public_id,expected=204,method='DELETE')
            report('deactivated certificate rejects private-key authentication',lambda: token_request(401))
        finally:
            if credential_path and credential_id:
                api(credential_path+'/'+credential_id, method='PUT',json_body=dict(status='Disabled'))
                api(credential_path+'/'+credential_id,expected=204,method='DELETE')
            for cert_id in created:
                api(cert_path+'/'+cert_id,expected=204,method='DELETE')
            if created:
                ids = ','.join("'"+item+"'" for item in created)
                check(sql(f'SELECT COUNT(*) FROM Partner.Certificate WHERE RowGuid IN ({ids}) AND Status=0') == str(len(created)), 'Fixture certificates must persist as inactive')
                print('PASS owned certificate fixtures deactivated and SQL verified')
    if failures:
        raise SystemExit('Failed checks: '+', '.join(failures))


if __name__ == '__main__':
    main()
