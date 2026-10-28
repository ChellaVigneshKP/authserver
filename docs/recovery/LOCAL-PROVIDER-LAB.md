# Local provider lab — 16 October 2026

This is a Mac-runnable development lab for actual application HTTP/SQL/browser journeys against explicitly **synthetic provider contracts**. The user has no real provider test environment. Passing this lab does not establish compatibility with SecureAuth, a biometric vendor, real notification delivery or legacy upstream crypto.

## Isolation and components

- Normal app/sample remain on 9080/3000, using the existing local `AGSAuth` database.
- The lab launches an immutable copy of the built app on 9082, creates a fresh uniquely named `AGSAuthProviders_<suffix>` database and uses a separate Redis session/cache namespace. It reuses the existing local SQL/Redis containers and protected development crypto keys.
- The Python standard-library provider binds only to loopback on 9191. MFA endpoints validate the actual application's SecureAuth-style HMAC and request timestamp. Random six-digit codes expire after 120 seconds and are consumed once. Their local delivery records appear in a loopback inbox, with available/used/expired/superseded status.
- Synthetic biometric proofs are random, expiring, one-use and bound to a fixture user and device identifier. Their results exercise the app's real provider client and authentication handlers. They are not a device fingerprint or Touch ID assertion.
- Test controls require a generated private bearer secret; the frontend receives only public config and an inbox link. No production code accepts a magic PIN or fabricated successful provider response.
- The provider demo on 3001 has a disposable MFA-enabled user and PKCE client. Credentials are mode 600 under ignored `.local/providers/`; Ctrl-C deletes/deactivates its own fixtures. A second lab startup is rejected before changing active configuration. The macOS port check permits recently closed sockets.

See README for commands. Start SQL/Redis, build the app, start the lab, run the automated campaign, then start the interactive demo. Port 3001 must be free for the lab's browser test. Stop the interactive demo before stopping the lab. Browser cookies on localhost are not isolated by port; use separate browser profiles/incognito sessions when manually switching between the normal app and lab. Automation uses separate cookie jars/contexts. Lab databases are intentionally retained for inspection; repeated starts create additional databases, with no automatic shared-database cleanup.

## What passed

The final `e2e-local-providers.py` run passed **104 application HTTP checks, nine SQL checks/operations and 84 semantic assertions**, including **17 actual Chrome assertions**. The Chrome journey observed 69 network responses. Three additional direct provider checks verified rejection of unsigned MFA and unauthorized control requests. Semantic assertions and network responses are not added to the HTTP-check count.

The test creates its own user/organization and exercises:

1. Password login followed by actual factor selection and a signed code request. Wrong and expired codes cannot finish MFA; a fresh valid code completes PKCE and userinfo; replay is rejected.
2. Password recovery, activation and mobile recovery. Every alias rejects password changes before MFA, without changing SQL. Each completes password setup after valid verification and persists a new binary hash.
3. Synthetic biometric success through the real authentication filter/provider and subsequent OAuth exchange. Reused, expired and wrong-device proofs are rejected and yield no SSO token.
4. Actual Chrome and sample-frontend login with MFA, browser factor/code forms, WebCrypto profile/password changes, logout and fresh MFA login with the changed password.

These tests uncovered and repaired three defects, rather than merely exercising simulated service responses.

## Reproduced defects and repairs

**Password reset before MFA.** On the original build, identifying an existing disposable username created a recovery session, and directly posting the reset form changed its SQL password without verifying a PIN. All recovery aliases shared this handler. The new `RecoveryVerificationService` issues a five-minute Redis proof only after successful provider verification and binds it to the browser session, SQL auth-session ID and flow. Reset requires this proof and consumes it atomically with GETDEL before mutation. A concurrent second reset cannot consume the same proof. If mutation fails after consumption, repeat verification is required.

Real-Redis integration checks verify absence/replay rejection, wrong browser/session/flow rejection and exactly one successful consumer among 12 concurrent requests. The full HTTP campaign verifies pre-MFA denial for all three recovery aliases and successful verified changes. This is a confirmed security repair; the previous branch build did not prevent the bypass.

**Biometric Redis persistence.** A provider-approved synthetic biometric login failed with a Redis `InvalidTypeIdException` for the custom authentication token. After the proof has been verified, the success handler now persists a standard Spring username/password authentication token with null credentials. It preserves the existing biometric-versus-MFA decision while avoiding storage of the provider proof or unsupported custom token. Subsequent HTTP/OAuth requests and replay rejection pass.

**Intermittent token signature rejection.** A full rerun caught a valid token HMAC failing immediately after issuance. `Token.Token.CreatedOn` was `DATETIME2(0)`, which rounded fractional issuance time into the next second; a lookup before that rounded time returned no token. A deterministic disposable-SQL test reproduced issuance at `.900` and lookup at `.901` failing on the old schema. Migration V11 widens creation time to `DATETIME2(7)`; that regression and the actual HTTP signature check now pass. No retry or delay masks the defect.

## Final broader verification

After the fixes and V11, `./scripts/local-dev.py test` passed **46 Java tests, no failures/errors/skips, plus five Node tests**. This includes 11-migration install/idempotence, real SQL/OAuth tests, Redis proof binding/concurrency and the precision regression.

The normal app was restarted from the verified immutable JAR and all nine prior HTTP campaigns passed again. Its Chrome runner used a separate callback on 3002, preserving the user's manual sample on 3000; that origin was enabled only in the local runtime configuration. The isolated provider campaign then passed against the same final application build and latest service source. Across all ten campaigns: **490 recorded HTTP checks and 55 SQL checks/operations**, zero failed recorded assertions. Counts include setup/cleanup and intended rejections, not unique endpoints. The final provider campaign's 17 browser assertions are additional to the ordinary browser campaign's 15.

The [coverage inventory](ENDPOINT-COVERAGE.md) explicitly separates **69 mapping variants with positive observations outside the provider simulation**, **17 additional variants with simulated-provider success**, **four verified error views**, and **11 variants still lacking a completed positive scenario**. There are 101 variants in total. Original boundary probes remain separate from successful business behavior. The JSON schema is version 2 to carry simulation provenance; each imported observation records its provider mode.

## Limits

- The inbox captures local synthetic MFA deliveries. It is not SMTP delivery, and the application's separate `NotificationEmailServiceClient`/forgot-username notification path has not been successfully exercised here. That path also needs configured upstream username lookup and SDK authentication/certificate fixtures.
- Upstream synchronized-profile import still lacks a genuine legacy password hash/cipher index, configured schema and crypto service. A fake compatible hash was not introduced. Source/account synchronization and notification success remain unqualified.
- Device-grant client registration remains unsupported by the current application model. Legacy MFA and remaining username-recovery variants do not gain positive coverage from this campaign.
- Touch ID/WebAuthn is a new authentication feature, not a substitute for the app's existing biometric-provider contract. It has not been implemented or claimed as tested.
- No actual email/SMS leaves the machine. Genuine-provider errors, outages, rate limits, credentials and vendor compatibility require separate verification.

The app and provider/demo processes were left running for inspection. Secrets, synthetic PINs, token-bearing URLs, provider-control responses and raw logs stay in ignored/private local artifacts. The committed coverage report contains only sanitized HTTP observations, assertion summaries and provenance. Stop the lab after finishing; it is a development simulator rather than a deployment component.
