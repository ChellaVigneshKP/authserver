# Recovery status — 15 October 2026

Branch: `fix/authserver-recovery`, based on main `bb73c66b6d5b256ce7b4b926c934e019da9111c6`.

The local development baseline now builds from real dependency source, installs a fresh SQL Server database, starts with Redis, and completes both public PKCE and confidential-client OAuth flows. These results qualify the tested development flows; they do not establish that every endpoint or external integration is defect-free.

## Repairs against the initial audit

| Findings | Repair | Evidence |
|---|---|---|
| F01, F09 | Pinned real custom-library sources, corrected crypto version, Java 25 compatible annotation processing and MapStruct generation; executable Maven wrapper | Source bootstrap and clean package succeed |
| F02, F03, F06 | Canonical versioned schema, reference data, procedures and Spring authorization storage; explicit subsequent contract corrections | Ten migrations install and validate on disposable SQL databases; rerun makes no changes; all 132 direct procedure names plus the application-detail helper have declarations |
| F04, F05, F07, F08 | Correct SQL argument names/order, schemas, scalar results, result projections, password bytes and UTC timestamps; propagate mapper errors in repaired classes | Real repository/settings/resource/user/branding/expiry/concurrency contracts pass |
| F10, F11 | Normalize to one encoder prefix; exact-prefix removal; validated keystore alias, constant-time comparison and fail-closed key loading | Actual encoder → delegating encoder → factory → stored password round-trip passes. See correction at the top of INITIAL-AUDIT.md |
| F12–F15, F24 | Consistent context URLs, default brand fallback, classpath CMS, complete referenced templates and form assets, static serving, controlled malformed-date responses and valid discovery | Java UI/CMS tests, five Node tests and packaged-app smoke pass |
| F16, F17 | Reject PRE_AUTH at authorization, persist completed MFA security context, reject inactive bearer tokens | Security/MFA regressions; real post-logout token rejection |
| F18, F19 | Standard Spring JDBC grant storage plus transactional custom state; preserve authorization request, scopes and refresh metadata; atomic consume and rollback behavior | OAuth regression suite and real confidential-client HTTP code/refresh/rotation/replay test |
| F20 | Remove caching of mutable app/org/settings/credential security reads and session/token activity | Mutation immediately visible in real HTTP organization update/revoke; logout invalidation passes |
| F21, F22 | Preserve custom client settings and use one Boot-managed Redis session repository with typed security serialization | Client/session/PRE_AUTH/profile DTO/Long metadata round trips; full login uses actual Redis |
| F23 | Scope fingerprint filter to API routes, tolerate unauthenticated requests, handle fingerprint bytes and Redis Base64 representation | Regression tests and fingerprint-bound API requests pass |
| F25 | Add unit, actual SQL, application context, confidential HTTP, browser-form and packaged smoke checks | 43 Java tests and five Node tests pass; complete smoke passes |

## Additional issues found and repaired during execution

- Bound authenticated branding to the login identity and SQL auth session, preventing a changed continuation parameter from selecting another brand's account with the same username. Metadata now uses the already brand-scoped login ID.
- Corrected Spring JDBC authorization text columns for SQL Server and removed anonymous default constraints before conversion.
- Completed missing credential-status enum projections and secret-creation projections. Repaired encrypted-secret decryption to use the secure-element key rather than the keystore password.
- Removed the hardcoded confidential-client secret fallback. Client-secret JWT defaults to HS256 and requires an active, unexpired configured secret; an absent retry slot returns `invalid_client`. Client assertion identity, audience and lifetime checks have regressions; private-key assertions use the same claims validator.
- Corrected the issuer to the actual server base. Removed the filter suppressing standard OIDC discovery. Discovery advertises the public key that signs ID tokens; the HTTP test verifies the issued token's signature with that advertised key.
- Persisted OIDC signing keys across restarts with a protected PKCS12 keystore and stable thumbprint key ID. Missing/wrong keys fail startup. A reload regression verifies the same public identity.
- Logout accepts a genuine ID-token hint and retains compatibility with the existing opaque proof token. Session/client/redirect binding is checked before browser logout. Access logs omit query strings containing codes and ID-token hints.
- Disabled duplicate servlet registration of security-chain filters and restored CSRF protection to browser forms. Unconfigured legacy password crypto fails closed rather than silently changing password format.

## Reproducible validation

From the repository root:

```sh
./scripts/bootstrap-dependencies.sh
./scripts/local-dev.py init
./scripts/local-dev.py up
./scripts/local-dev.py test
./scripts/local-dev.py start
# In another terminal, use the client_id printed at startup:
LOCAL_CLIENT_ID=<client-id> ./scripts/smoke-local.py
```

The dependency bootstrap was executed against the pinned upstream commits and succeeded. The exact Compose `up` command was executed with a new task-owned volume; both services became healthy and AGSAuth was created without resetting existing databases. The exact test command performs a clean Maven verification and the Node tests. Database contract tests create and drop only UUID-named test databases. The opt-in HTTP test creates a uniquely named client and deactivates it afterward. The smoke creates and revokes a uniquely named organization; it never deletes existing data.

Packaged-app smoke checks public discovery/JWKS, CSRF-protected password login, state preservation, PKCE exchange, OIDC tokens, administrator reads and organization create/update/revoke, missing/malformed timestamps, invalid bearer tokens, userinfo, logout and token/code replay rejection. Public clients intentionally receive no refresh token; the confidential HTTP test covers refresh issuance, rotation and replay with a real encrypted secret and JWT assertion.

Detailed session logs are under `/tmp/authserver-recovery/` on the development machine; they are temporary evidence, not required project inputs. Credentials, downloaded tools/dependencies and generated keys are ignored under `.local/`, with restricted permissions on credentials and keys. No credentials or dependency JARs are committed.

Final execution evidence: `./scripts/local-dev.py test` passed **43 Java tests (zero failures, errors or skips)** and **five Node tests**. The packaged application then passed every smoke check, including standard public discovery and JWKS. Its public key ID was unchanged after restart, and the JWKS response contained no private RSA key. `git diff --check` passed. The local app and task-owned Compose services were left running for inspection.

## Limits and remaining qualification

- Real SecureAuth MFA delivery/verification, notification delivery, external account synchronization, biometric authentication and legacy remote crypto require working provider configuration and credentials. Their live success paths have not been qualified. Local development does not fake successful provider responses; newly created local-only fixtures have no MFA enrollment.
- The follow-up real certificate/private-key acceptance run now passes upload/list/download, advertised JWKS signature matching, RS256 issuance and immediate rejection after credential/certificate revocation or expiry. See E2E-VALIDATION.md; this does not establish complete certificate rotation/load qualification.
- Not every administrative endpoint, failure mode, browser or concurrency/load scenario has an end-to-end test. The SQL inventory establishes object coverage, not semantic correctness of every procedure. A browser-engine accessibility/visual review and deployment/load qualification remain separate work.
- This migration stream targets the user-approved fresh development database. An existing shared database needs its own tested upgrade/backfill and backup plan; baseline/clean shortcuts are disabled.
- The upstream libraries' optional legacy analysis plugins were skipped for source packaging; this work does not claim their vulnerability or quality scans passed. Dependency bootstrap was tested with the session's populated public-dependency cache, not an empty offline machine.
- Final whitespace cleanup of imported migrations was followed by checksum alignment only in the task-created local development database; no schema or user data was changed by that repair. No candidate branch was merged wholesale and no shared database was reset. The recovery branch was subsequently pushed at the user's request. The task's initial temporary SQL/Redis containers were stopped when the documented Compose services replaced them.

## Follow-up end-to-end campaign

The recovery branch was pushed to `origin/fix/authserver-recovery` at the user's request. The expanded live test campaign, reproduced defects, exact coverage counts and remaining flow gaps are recorded in [E2E-VALIDATION.md](E2E-VALIDATION.md) and [ENDPOINT-COVERAGE.md](ENDPOINT-COVERAGE.md). New migrations V9/V10 repair active certificate selection and editable profile-name persistence without changing already-applied migrations.

The [16 October follow-up](FOLLOW-UP-VALIDATION.md) adds the sample frontend, actual Chrome execution, missing-provider failure checks and a patched Bouncy Castle dependency tree. All nine acceptance runners passed on that build; genuine external-provider success remains unqualified because no provider test environment is available.
