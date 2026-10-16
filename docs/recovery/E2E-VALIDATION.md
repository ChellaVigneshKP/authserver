# Real end-to-end validation

Executed against the packaged recovery application on 15 October 2026, with local SQL Server 2022 and Redis. The final campaign completed at 23:59 IST. An additional restart and the 21-request smoke passed on 16 October after the overnight session ended. The committed inventory includes that refreshed smoke evidence. The final clean Maven/Node gate passed before this campaign: **43 Java tests, zero failures/errors/skips, and five Node tests**. The Java gate includes actual SQL contracts, a running application context, confidential-client HTTP authorization/refresh rotation, and ID-token signature verification.

## Final HTTP and database campaign

| Runner | HTTP checks | SQL checks/operations | Result |
|---|---:|---:|---|
| `e2e-local.py` | 62 | 12 | Pass |
| `e2e-browser-flows.py` | 35 | 9 | Pass; 31 semantic assertions and five access-log correlations |
| `e2e-certificates.py` | 38 | 8 | Pass; 22 certificate/client-assertion scenarios |
| `e2e-protocol-variants.py` | 51 | 5 | Pass; 20 semantic assertions |
| `e2e-machine-users.py` | 27 | 4 | Pass; synchronized-profile success explicitly blocked |
| `e2e-boundaries.py` | 101 | 0 | Pass; unauthenticated boundary checks only |
| `smoke-local.py` | 21 | 0 | Pass |
| **Total** | **335** | **38** | **No failed checks; no HTTP 500 responses** |

Counts include setup, cleanup and intentional rejection requests, and include repeated requests to the same route. SQL counts include fixture-state operations in the certificate suite. They are not counts of distinct endpoints or independent business workflows.

The [source-derived coverage matrix](ENDPOINT-COVERAGE.md) inventories **101 mapping variants**: 85 MVC variants and 16 security-filter routes. Imported runtime evidence supplies positive scenarios for **69 variants**, semantic verification of **four intentional error views**, and boundary/error observations for the remaining **28 variants**. No mapping is unobserved. This denominator excludes implicit HEAD/OPTIONS/CORS, static assets, actuator selectors and servlet `/error`. It is not a claim of exhaustive application coverage.

## What was actually verified

- Actual cookie/CSRF password login, public PKCE code exchange, POST authorization continuation, state preservation, userinfo and metadata; replayed continuation returns a controlled OAuth error without extra SQL PKCE/code rows.
- Confidential-client JWT authentication, real encrypted shared-secret creation, machine-token endpoint permissions and password validation, including rejection of actual password reuse using SQL credential audit history. The Java integration gate additionally verifies refresh rotation/replay.
- Organization, contact, group/permission reads, application/settings/URL mutations, endpoint/resource assignment, secret credentials, user creation, metadata, profile, email, username, passwords, security settings, status and deletion, with selected SQL persistence assertions.
- Actual server-rendered self-service forms submitted over HTTP with real CSRF and HMAC signatures. First/last names, email and phone changes persist; a changed password permits a fresh login. Invalid signatures, missing CSRF and invalid encoding do not mutate the user.
- Generated RSA private/public certificate upload, exact DER-equivalent download, fingerprint and JWKS matching, real RS256 private-key client assertions. Wrong assertion claims, disabled/expired credentials and inactive/not-yet-valid/expired certificates are rejected, including immediately after a previously successful issuance. Public JWKS excludes private key material.
- Custom OAuth GET/POST logout and token revocation invalidate bearer access and SQL auth sessions. Standard Spring POST logout separately verifies cookie-session logout/redirect; bearer revocation evidence comes from the custom logout/revocation flows.
- Every inventoried route receives an unauthenticated probe. Rejections are counted as boundary checks, never successful business flows.

## Defects reproduced and repaired in this campaign

- SERVER application creation supplied a null non-nullable forgot-username setting; TTL validation rejected valid settings. Resource updates incorrectly treated changed identities as duplicates.
- Private key loading used the wrong keystore password; certificate download corrupted the final PEM chunk; certificate credential expiry parsing rejected SQL timestamp formats. Missing/malformed certificate requests now return controlled responses.
- Certificate lookup accepted inactive/expired credentials or certificates. Migration V9 enforces current active state, organization ownership and validity. A newly exposed client JWKS route also revealed that the default remote-JWKS authentication provider could accept cached revoked keys; JWT client authentication now uses the existing live-SQL providers.
- Editable self-service names were not persisted; migration V10 extends the procedure compatibly. Password validation must inspect decoded input while preserving the exact wire payload for HMAC. The password error retry used the wrong datetime query name.
- Missing authorization, malformed date/JSON/binding errors and missing error templates could cascade into HTTP 500 responses. Controlled error handling and signature-filter behavior now preserve the intended status.
- Token timestamp binding depended on the JVM timezone. Standard discovery advertised a logout URL different from the implemented handler. Repeated authorization continuation attempted duplicate SQL state; it now rolls back and returns an OAuth error. Invalid synchronized-profile schema payloads now return 400.
- Development startup loaded a mutable build JAR: a concurrent clean build invalidated classes used by a running process. Startup now uses a separate immutable JAR copy. Results from the earlier overwritten-JAR process were discarded; the table above comes from the final immutable build.

## Remaining qualification

The remaining 28 mappings comprise 23 activation/recovery/MFA variants, one biometric filter, three device-grant variants and one upstream synchronized-profile creation endpoint. They have no completed positive-flow evidence. Real MFA delivery/verification, notification and biometric integrations require provider configuration and eligible fixtures. Upstream synchronization needs its actual source schema, legacy password hash/cipher index and crypto provider; local IDP synchronization has no schema. Device filters exist, but the current application registration model does not register a device-grant client. The campaign records this as unsupported qualification rather than fabricating success.

This is HTTP/form testing, not a browser-engine visual/accessibility test. It does not establish production readiness, exhaustive authorization isolation, complete certificate rotation, all mapper/procedure semantics, concurrency/load behavior, dependency vulnerability clearance or existing-database upgrade safety. Local startup logs also report a macOS Netty native-DNS fallback; the tested local flows pass, but native resolver behavior and external provider connectivity remain unqualified. Deliberately rejected JWTs and malformed requests produce error-level logs; a quiet error log was not a pass condition.

## Reproduce and inspect

Start the app and development services as documented in [README](../../README.md), then run the seven scripts listed there, with `smoke-local.py` last. Tests create uniquely named fixtures and deactivate/delete their own objects where supported; some audit/resource rows remain. No pre-existing user or shared database is reset. Do not run these fixture-creating scripts against production.

Sanitized runtime reports live in ignored `.local/*report.json`; they omit credentials, tokens and private keys. Logs from the campaign live under `/tmp/authserver-recovery/` and are temporary local evidence. The committed inventory records report hashes, individual HTTP observations and database-check summaries, so the result can be reviewed without committing raw runtime logs.

Regenerate coverage after running all suites:

```sh
python3 scripts/endpoint-inventory.py \
  --results .local/e2e-report.json \
  --results .local/e2e-browser-report.json \
  --results .local/certificate-e2e-report.json \
  --results .local/e2e-protocol-report.json \
  --results .local/e2e-machine-report.json \
  --results .local/e2e-boundary-report.json \
  --results .local/e2e-smoke-report.json
```
