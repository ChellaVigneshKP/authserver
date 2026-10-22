# Portal backend acceptance evidence

Design/plans were approved in chat on **2026-10-21**. This document records backend B1–B7 qualification before frontend work; it does not certify a deployed portal or genuine external providers. [API-CONTRACT.md](API-CONTRACT.md) and [UPGRADE.md](UPGRADE.md) define the frontend and operator boundaries.

## Evidence provenance

Runs use the existing ignored private `.local/dev.json`; no password, bearer, signing key, authorization code, client secret, private key or SQL connection credential is copied into reports. Test identities are synthetic and owned. Actual observation files contain a redacted route, method, fixture actor class, HTTP status, safe SQL projection outcome and execution-time source commit/tree information. They never contain request/response bodies or headers. JUnit names are assertion metadata only and are not converted into route results.

The original full203-test gate records baseline `0aebe70096b11a9003b93bedaac1f832f412743e` **with dirty B7 changes**. Fresh review-fix management and lifecycle reports record `212e059c44999554a67bddeedffb68a0b78cde8b` **with dirty fix changes**, execution tree `6fe43cd1a47ce0e660225ed180fc469e41eaea3a2cf6dc776f5532d516eb9f94`. Each report retains its actual execution snapshot/diff digest and `working_tree_dirty:true`. Commits created afterward are never retroactively presented as execution SHAs. Later documentation and pure test-harness changes naturally change the worktree digest. The copied JAR hash independently identifies the lifecycle runtime; no production Java changed for this review fix.

`source_tree_sha256` hashes sorted tracked/nonignored untracked file paths and bytes before execution. The digest identifies that observed worktree snapshot; it is not a Git commit/tree object or a claim that the dirty execution ran a later commit. The suite result and lifecycle binary evidence remain distinct.

## Reproduction and isolation

From this worktree with private local configuration already present:

```sh
./scripts/local-dev.py test
python3 scripts/e2e-admin-management.py --phase all
SPRING_DATA_REDIS_DATABASE=2 SPRING_DATA_REDIS_CACHE_DATABASE=3 \
  python3 scripts/e2e-isolated-lifecycles.py
```

The first command performs Maven clean verify/package, five Node form tests and Python target/cleanup/observation checks (currently5/10/4). The preserved full-gate report predates the expanded cleanup tests; fresh focused Python results are recorded below. It never uses shared AGSAuth as an integration-test fixture database. Management tests create independently owned `AuthserverManagement_<uuid>`, `AuthserverPolicy_<uuid>` or migration-test databases and UUID Redis prefixes. They drop their own database and remove their own session/cache keys.

The lifecycle launcher copies the built JAR read-only, chooses a free loopback port, creates `AuthserverLifecycle_<uuid>`, and runs both existing certificate and protocol campaigns against that same database/JVM. It validates private SQL/HTTP target correspondence using DB_NAME, discovery issuer and the unique synthetic bootstrap identity. Shared AGSAuth is only the administrative connection for creating/dropping the separate database; its fixtures/accounts/keys are not mutated. No Docker service or shared JVM is restarted/reset.

## Qualification semantics

A passing HTTP2xx/3xx check is positive observation of that scenario only. Expected401/403/404/409 probes are negative observations and never positive coverage. The dedicated32-route matrix's401 checks are **authentication-denial** evidence: the security filter can reject before controller dispatch or even before404, so these do not establish route existence, permission denial, tenant isolation, or a successful handler flow. Those claims require separate signed-bearer/positive tests and source mappings.

Each automatic SQL snapshot compares exact ordered explicit structural/public projections within the owned database before/after the actual request. Reports retain only checked-table row counts and changed-table names. They exclude secret stores, password bytes, keys and token columns. Therefore an unchanged structural projection does not prove byte identity of passwords/keys/tokens or absence of changes in omitted columns. Stronger existing migration, B5 membership/rollback and B6 issuance/UTC assertions separately read their relevant state inside their tests. No dirty-read/NOLOCK snapshot is used. Concurrent tests can observe another request's committed changes; those aggregate snapshots are supporting readbacks, not sole atomicity proof. The dedicated denial matrix is serial and independently asserts unchanged structural state.

Actor classes describe fixtures (`human_tenant`, `human_platform_organization`, `machine`, `unauthenticated`, or explicitly unregistered bearer); organization1 membership alone is not proof of effective platform permissions. Java policy tests establish authority separately. Configured provider labels and positive observations are separate: synthetic local identities with real Java/SQL/crypto do not constitute an external-provider simulation or genuine delivery success.

The inventory remains schema_version2. Its importer accepts actual checks with `path` (existing runners) or `url` (redacted management route); reports contain actual status fields, never statuses synthesized from test names. Imported source commit/tree, actor and SQL outcomes remain attached. Only fresh B7 management/certificate/protocol observations are imported for this snapshot. Previously imported simulated-provider/historical observations are not silently represented as fresh qualification.

## Remaining release boundaries

- Genuine MFA/recovery/delivery/synchronized-provider positive flows are unconfigured/unexercised in these campaigns; a boundary/outage/invalid-provider test is not positive provider qualification. `providerMode` is configuration only. Existing local simulation, if separately run, must retain its distinct simulation label and does not certify external providers.
- Device authorization remains unsupported/unconfigured by the current client model; device-negative checks do not qualify a working device flow.
- Legacy `CertificateResponseDto.isExpired` remains date-derived/local display logic; exact UTC credential/token checks are independently verified. Do not claim exact-instant certificate-display normalization; retain this for broad backend review.
- Historical naive datetime2 intent cannot be reconstructed from SQL alone. Existing timestamps are not rewritten; the operator provenance audit/deliberate rotation in UPGRADE is still required.
- Browser/BFF first-slice fingerprint/referer continuity needs actual hosted browser verification, including a non-UTC browser and separately configured portal/issuer hosts. Current fingerprint fields are zoneOffset, Accept-Language, User-Agent and refererDomain, with no remote-IP hash. Do not disable checks to integrate the portal.
- Frontend F3 must implement timeout/refetch/partial-onboarding UX. These backend tests prove failed later steps preserve a single partial client, not network-timeout UI handling or multi-call atomicity.
- Frontend adapters must consume authoritative readOnly, preserve actor401/target409 distinction, strip legacy numeric resource IDs and resolve the public catalog GUID for resource removal.
- Dependency boundary inherited from B6: the earlier targeted six-alert assessment found configured BouncyCastle1.85 outside those six affected ranges. That is not a fresh complete vulnerability/license/supply-chain audit. F7 still audits the resolved backend/new frontend/runtime dependencies.
- Inherited local warnings include duplicate org.json classes, JVM/Mockito/native-access deprecations, missing optional Netty macOS DNS native library, Flyway forward-reference creation warnings, loopback Redis without authentication and closed-pool warnings while owned databases are torn down. These are tracked separately from assertion failures and are not claimed remediated by B7. Dense inherited guard/assignment formatting remains a reviewable style carryover.

## Recorded results

The preserved full required gate, before review-fix changes to fixtures/runner, passed: **203 Java tests (198 baseline +5 new), zero failures/errors/skips;5 Node tests;14 Python checks (5 target +5 cleanup +4 observation).** Maven clean verify and packaged JAR succeeded. Private execution log: `.local/b7-full-gate.log`; execution provenance is retained in the sanitized evidence bundle. No production Java was changed after this gate. Fresh review-fix verification is the corrected focused Java test, final observation-enabled all-phase run,19 Python checks and the owned nondefault-DB lifecycle campaign. The full gate is not claimed rerun after these test/runner changes.


| Command | Final result | Sanitized evidence |
|---|---|---|
| `./scripts/local-dev.py test` (preserved pre-fix run) |203 Java;5 Node;14 Python; clean verify/package exit0 | [full gate](evidence/full-gate.json) |
| `python3 scripts/e2e-admin-management.py --phase all` |131 tests;361 actual HTTP/SQL observations;32/32 source-derived mutation variants with authentication denial and unchanged structural SQL; exit0 | [management report](evidence/admin-management.json) |
| `SPRING_DATA_REDIS_DATABASE=2 SPRING_DATA_REDIS_CACHE_DATABASE=3 python3 scripts/e2e-isolated-lifecycles.py` | Both campaigns and same-endpoint DB2/3 owned cleanup passed; exit0 | [launcher/cleanup](evidence/lifecycle-campaign.json) |
| Certificate campaign (inside owned launcher) |38 HTTP checks;8 SQL checks;22 scenarios passed | [certificate report](evidence/certificates.json) |
| Protocol campaign (same owned app/DB) |51 HTTP checks;5 SQL checks; no failures | [protocol report](evidence/protocol.json) |

Fresh review-fix checks passed: the focused security-settings Java test1/1 and19 Python checks (10 cleanup/environment/endpoint,5 target,4 observation). The security fixture now explicitly sets/asserts false before the actual PUT, then asserts true plus a changed public profile projection. The prior archive had24 positive-with-SQL-change variants, contrary to its prose claim of25; the refreshed archive now measures25, with `management-109` recording `Person.Profile` changed.

Management observations contain171 positive HTTP results and190 non-positive observations (including deliberately injected500/503 rollback/outage checks). Their statuses are actual transport results. Twenty-five of the32 management mutation variants have a positive HTTP observation **and** an observed structural SQL change in this report. Certificate DELETE is additionally qualified by the separate certificate campaign. The six remaining positive gaps are provider/brand-dependent user creation (both legacy entrypoints), profile/email/password/username changes; their negative/ambiguity guards are exercised, and their successful provider flows remain unqualified here.

Separate existing assertions cover: tenant omission/forged filters and totals; revoked actor membership401 and target ambiguity409; current permission removal and queued/concurrent writes; final administrator/unassignment races; V11→V13 and V12→V13 populated additive upgrades with stable IDs/profiles/credentials/grants/membership history and no duplicate seed additions; builtin/custom/protected-legacy readOnly metadata; stale role revisions and assigned-role conflicts; old/v2 secret absence and one-time creation; exact UTC write/metadata/actual confidential issuance before/at/after restrictive expiry; inactive client readback and retained-token invalidation; real RSA private_key_jwt, PKCE exchange, logout/revocation and public-only certificate/key advertisement. These assertions are not converted into invented endpoint observations.

The retained immutable JAR SHA256 is `a713db4373145060b19b13d2717adadcd0156ee3f97bf93c282215a17f8f4fd8` (unchanged production artifact from the preserved full gate). It ran against `AuthserverLifecycle_251795a2aab6455298834893c698d7d6` at `http://localhost:61345/services`. The launcher resolves `os.environ | private_config | owned_overrides` once, validates effective Redis settings, and passes that exact environment to the JVM. Before database creation/JVM launch, Docker inspection proves that the running Redis container publishes `127.0.0.1:16379` from its6379 port. Cleanup uses that verified immutable container ID, port and effective DBs; arbitrary loopback ports, remote hosts, Redis URL/auth/TLS/sentinel/cluster and unsupported JVM/config transport overrides are refused.

The fresh inherited-environment proof selected sessionDB2/cacheDB3. Cleanup stopped only the owned JVM, removed17 owned keys from DB2 and1 from DB3, verified0 remaining in each, dropped the UUID database and verified absence. No shared flush/reset or unrelated-key cleanup occurred. Earlier defaultDB0/1 B7 runs and DB0-only B6 evidence retain their historical scope; they are not proof of the later inherited-environment fix.

Fresh inventory regeneration:

```sh
python3 scripts/endpoint-inventory.py \
  --results docs/portal/evidence/admin-management.json \
  --results docs/portal/evidence/certificates.json \
  --results docs/portal/evidence/protocol.json
```

The schema2 inventory has109 source variants:57 with positive HTTP observations,4 controlled views,7 exercised without positive completion and41 unqualified. There are no fresh simulated-provider positives in these three reports. These counts deliberately differ from the older historical/simulated snapshot: removing old imports is not evidence of a product regression, and the new counts are not release-completeness claims. See [inventory JSON](../recovery/ENDPOINT-INVENTORY.json) and [coverage table](../recovery/ENDPOINT-COVERAGE.md).

The full203-test gate preceded the final source-inventory completeness assertion in the Python wrapper; no production Java changed. The subsequent observation-enabled all-phase run executed that final wrapper, all131 selected tests and coordinated races successfully. Python checks, compilation, source-to-observation route matching, secret/private-key/JWT evidence scanning and `git diff --check` also passed. Public reports retain the precise execution hashes; no full gate is falsely attributed to the later documentation commit.
