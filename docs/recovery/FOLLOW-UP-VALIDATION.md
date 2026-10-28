# Follow-up validation — 16 October 2026

Subsequent results: [the isolated local-provider lab](LOCAL-PROVIDER-LAB.md) adds 17 simulated-provider route variants and three reproduced fixes. Counts below describe the earlier campaign.

This campaign addresses the remaining verification work and the reported dependency alerts on branch `fix/authserver-recovery`. The user confirmed that no provider test environment is available. Genuine external-provider success paths therefore remain unqualified.

## Actual browser and sample frontend

`examples/test-client/` is a small local relying-party frontend. `scripts/sample-frontend.py` registers a public PKCE client and test user through real admin endpoints, writes test-user credentials to a mode-600 ignored local file, and serves only the frontend assets and public client configuration on loopback port 3000. It does not serve administrator credentials. Ctrl-C deletes its test user and deactivates its own client and organization.

The Chrome campaign does not intercept or mock any network request. It executes the sample frontend's own authorization-state validation, PKCE generation/exchange, cross-origin token/userinfo fetches and callback handling. It submits the auth server's actual login/profile/password forms, including its Base64 and WebCrypto signing JavaScript.

`python3 scripts/e2e-browser-engine.py` passed **15 browser assertions**, observed **53 real browser HTTP responses**, and completed **four SQL checks** including fixture checks. It verifies:

- Sign-in through actual browser forms, code exchange and userinfo through the frontend. The callback code is removed from the address bar.
- Unicode profile names and email/phone changes persist in SQL.
- Mismatched passwords display the actual client error without submitting a password POST, and preserve input for retry.
- A valid signed password change updates the SQL binary hash; a fresh cookie session accepts the new password.
- Frontend logout clears its stored tokens and makes the old bearer fail at `/userinfo`.
- No uncaught page JavaScript errors, no observed browser HTTP 500s, and no raw token displayed in the page.

Reports and the synthetic-account screenshot are under ignored `.local/`; credentials and token-bearing callback URLs are not committed. Automation starts its own frontend and requires port 3000 to be free. The independent CLI sample server was also started and opened in the browser for manual inspection. Only installed Chrome was qualified; other engines, full accessibility, visual regression and production-client security are not claimed.

## Missing-provider failure paths

`python3 scripts/e2e-provider-unavailable.py` passed **30 HTTP checks**, **four SQL checks** and **seven semantic assertions**. It enables MFA only on its own disposable user, performs correct-password login and confirms:

- No authorization code is issued before MFA. Missing factor-provider configuration produces a controlled 503.
- OTP generation cannot advance the flow when the provider is missing; a synthetic PIN cannot complete authentication.
- An authorization continuation from PRE_AUTH is rejected and no SSO access token is returned. SQL access-token counts do not increase.
- A synthetic biometric proof with no configured provider is rejected, gives no SSO token, and creates no SQL access token.

These tests exercise the real application with absent providers. They do not simulate delivery, approve biometric authentication, verify actual expired/reused provider PINs or qualify provider timeout/recovery behavior against a configured remote service. The synchronized-profile negative checks remain in `e2e-machine-users.py`.

## Dependency remediation

The six open GitHub alerts all concerned the two direct Bouncy Castle dependencies, previously pinned to 1.82. Both direct artifacts now use one `bouncycastle.version` property set to **1.85**. The resolved Maven tree and packaged JAR contain **bcprov-jdk18on, bcpkix-jdk18on and bcutil-jdk18on all at 1.85**.

`python3 scripts/check-bouncycastle-alerts.py` fetched the current open repository alerts and compared their affected ranges with the resolved dependency tree. All six reported ranges excluded the installed versions:

| Alert | Advisory | Package | Reported first patch |
|---|---|---|---|
| 13 | GHSA-qp49-qgx5-5m26 | bcprov-jdk18on | 1.85 |
| 12 | GHSA-9pwp-9qqc-pr26 | bcprov-jdk18on | 1.85 |
| 11 | GHSA-574f-3g2m-x479 | bcprov-jdk18on | 1.84 |
| 10 | GHSA-p93r-85wp-75v3 | bcprov-jdk18on | 1.84 |
| 8 | GHSA-c3fc-8qff-9hwx | bcprov-jdk18on | 1.84 |
| 7 | GHSA-wg6q-6289-32hp | bcpkix-jdk18on | 1.84 |

This establishes version-range remediation for those six alerts on this branch. It is not a full dependency vulnerability scan or an exploitability assessment. Alerts on the default branch remain until that branch is updated and GitHub reevaluates it; none were manually dismissed. Source: the repository's authenticated GitHub Dependabot alerts API, with sanitized results in `.local/dependency-alert-report.json`.

## Full rerun on the patched build

`./scripts/local-dev.py test` passed **43 Java tests, zero failures/errors/skips, plus five Node tests**. Database contracts and confidential-client HTTP/OIDC/refresh tests were enabled.

After restarting an immutable copy of the patched JAR, all nine acceptance runners passed:

| Runner | Recorded HTTP checks | SQL checks/operations |
|---|---:|---:|
| e2e-local | 62 | 12 |
| e2e-browser-flows | 35 | 9 |
| e2e-certificates | 38 | 8 |
| e2e-protocol-variants | 51 | 5 |
| e2e-machine-users | 27 | 4 |
| e2e-boundaries | 101 | 0 |
| e2e-browser-engine | 21 | 4 |
| e2e-provider-unavailable | 30 | 4 |
| smoke-local | 21 | 0 |
| **Total** | **386** | **46** |

The 53 Chrome network observations are additional to the browser runner's 21 recorded setup/cleanup HTTP checks and are kept separate to avoid confusing network traffic with semantic assertions. HTTP checks include setup, cleanup, expected rejection responses and repeated routes; they are not distinct business workflows. Certificate SQL counts include fixture-state operations. Expected missing-provider 503s are verified rejection behavior. No runner recorded a failed assertion or an unexpected server response.

The regenerated [coverage matrix](ENDPOINT-COVERAGE.md) still has 101 mapping variants: 69 with positive-flow observations, four controlled error views, and 28 without positive-flow qualification. A new rejection test does not convert a provider-dependent route into a verified success path.

## What still needs an environment

- **23 activation/recovery/MFA variants:** configured SecureAuth realm credentials, test factor enrollment, test email/phone and notification service; then complete actual delivered-PIN flows and test wrong/expired/reused codes and session state.
- **One biometric filter:** a genuine enrolled device/user and verification service, followed by valid, invalid, expired and replayed proof tests.
- **One synchronized-profile endpoint:** actual upstream schema, a synthetic legacy credential/hash/cipher index and working crypto/provider configuration; then validate persistence and login compatibility.
- **Three device-grant variants:** implement supported device-grant client registration before qualifying issuance, approval, polling, denial, expiry and replay. No feature support was invented as part of this test campaign.

The repository has no available provider credentials for those success tests, and the user confirmed no test environment exists. These remain explicit limits rather than passing mocks. README contains reproduction commands; detailed session logs are temporary files under `/tmp/authserver-recovery/`.
