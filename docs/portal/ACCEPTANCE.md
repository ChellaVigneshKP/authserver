# Portal backend acceptance evidence

Design/plans were approved in chat on **2026-10-21**. This records the B1–B7 foundation plus the bounded final review repair. It does not certify the frontend, a deployment or genuine external providers. [API-CONTRACT.md](API-CONTRACT.md) and [UPGRADE.md](UPGRADE.md) define the frontend/operator contracts.

## Exact execution provenance

All reports below ran the same **clean implementation commit `93dc6048e13a0d76302be0d09b0a158d1ef56bd0`**, with source digest `a00ac05ecef5bc9ad2806ff5f38170196e94ad59fd35d72d630c83b23ab140f1` and `working_tree_dirty:false`. The final full gate, all-phase HTTP/SQL campaign and owned lifecycle launcher ran after the production fixes settled. Documentation/evidence committed afterward is not retroactively presented as the executed source. Previous B7 snapshots remain historical Git evidence and are superseded by these fresh results.

`source_tree_sha256` hashes sorted tracked/nonignored file paths and bytes; it is not a Git tree object. The copied lifecycle JAR independently matches the full-gate artifact SHA256 `99384413a80545e8574457ef2ec73c92ee53ae7d2e48b335e76647e785b08623`. No production code or tests changed after these successful runs.

The standalone certificate/protocol runners do not emit source hashes themselves: their archive provenance is attached from the owned launcher after matching database/app_base and verifying their raw JSON content against its immutable run directory. Each archive records that provenance source and raw report SHA256.

Reports contain actual statuses and safe structural SQL outcomes, with no request/response bodies, headers, bearer tokens, passwords, private keys or client secrets. Private configuration/logs remain ignored under `.local`. JUnit names describe assertions and are never converted into invented endpoint observations.

## Reproduction and ownership

```sh
python3 scripts/local-dev.py test
python3 scripts/e2e-admin-management.py --phase all
SPRING_DATA_REDIS_DATABASE=2 SPRING_DATA_REDIS_CACHE_DATABASE=3 \
  python3 scripts/e2e-isolated-lifecycles.py
```

The full gate runs Maven clean verify/package, Node form tests and Python target/cleanup/observation checks. Java suites create owned UUID SQL databases and UUID Redis prefixes. The lifecycle launcher copies the artifact read-only, owns a new loopback JVM/database, verifies its SQL/HTTP identity and stops only that JVM. No shared database reset, Docker service restart or Redis flush occurs.

The launcher validates the effective Redis endpoint/environment and Docker published port before launch, then cleans only its UUID prefix in the effective databases. This run used `AuthserverLifecycle_74c78ab5fca64edfbf049a43aa931853` at `http://localhost:50139/services`. Cleanup verified: **DB2: 16 owned keys removed, 0 remaining, DB3: 1 owned keys removed, 0 remaining; owned JVM stopped; owned database dropped and absence verified**. The endpoint is the validated loopback Redis server at127.0.0.1:16379, not a guessed container/default database.

## Recorded results

| Command/campaign | Result | Sanitized evidence |
|---|---|---|
| `python3 scripts/local-dev.py test` | **219 Java**,0 failures/errors/skips; **5 Node**; **19 Python** (5 target,10 cleanup,4 observation); clean verify/package exit0 | [full gate](evidence/full-gate.json) |
| `python3 scripts/e2e-admin-management.py --phase all` | **144 tests;422 actual HTTP/SQL observations;32/32 mutation authentication denials with unchanged structural SQL**; exit0 | [management](evidence/admin-management.json) |
| Owned lifecycle launcher | Both campaigns and DB2/3 cleanup passed; immutable artifact matches full gate; exit0 | [launcher/cleanup](evidence/lifecycle-campaign.json) |
| Certificate campaign |38 HTTP checks;8 SQL checks;22 scenarios passed | [certificates](evidence/certificates.json) |
| Protocol campaign |51 HTTP checks;5 SQL checks; no failures | [protocol](evidence/protocol.json) |

The management campaign contains **208 positive** and **214 non-positive** actual transport results, including expected injected500/503 failures. **31/32** mutation variants have positive HTTP and observed structural SQL change. The sole remaining positive mutation gap is schema-keyed **POST `/api/v1/users/profile`**, which remains a separately qualified synchronization integration. Its authorization/ambiguity denials do not establish provider success.

Supported local **POST `/users`**, profile/email/password/username changes now have successful real HTTP/SQL evidence using valid IDP branding in an owned database. Each has a permission-denial case with unchanged structural state; password bytes are independently checked for unchanged denial. Real hosted form/CSRF login issues authorization codes with the initially created credentials and the renamed/new-password credentials; the previous password is rejected. Creation still defaults MFA on. The test selects the supported password-only local user through the existing authorized per-user security-settings API; global proof, CSRF and MFA policies remain enabled. This proves local credential usability, not external notification delivery. These browser-form assertions are reported as test assertions; they are not synthesized into route observations.

Final repair coverage also establishes: inactive tenant history and administrator-role history cannot leak/duplicate user-list rows or totals; ambiguous memberships are omitted without repair; machine provisioning cannot assign reserved human authority; certificate create/delete enforce current type-specific platform/tenant authority after the shared lock; list/detail certificate expiry matches full UTC X509 boundaries; v2 credential errors retain safe envelopes, validated request IDs, no-store and exact-byte signatures; queued role writes are otherwise authorized before selective revocation. Populated **V11/V12/V13→V14** upgrades preserve IDs, credential bytes and history, then validate and migrate again without changes. V1–V13 are immutable.

## Meaning and limits of evidence

A passing2xx/3xx observation qualifies that scenario only. The32-route unauthenticated matrix proves rejection without structural writes, not handler existence, permission denial or successful mutation by itself. Separate authenticated positive/negative tests supply those claims.

Automatic SQL snapshots compare explicit ordered public structural columns, including credential version/modified timestamps but excluding password/secret/keystore/token bytes. A structural change alone does not prove credential correctness; independent password-byte assertions and real authentication do. Unchanged snapshots do not certify omitted columns. Concurrent tests may observe another request's commit; deterministic barrier and explicit target readbacks establish the corresponding concurrency assertions.

Fixture actor labels are descriptive: organization1 membership alone is not proof of effective platform authority. Configuration `providerMode` is not a delivery/connectivity certification. No fresh provider simulation is represented as genuine external success.

Remaining release boundaries:

- Genuine external MFA/recovery/delivery/synchronized-provider success remains unconfigured and unqualified; the schema-keyed synchronization create endpoint is separate from the supported local-create form.
- Device authorization remains unsupported by the approved client model; negative probes do not establish a working device flow.
- Historical naive SQL timestamp intent cannot be reconstructed; no timestamp data is rewritten. Follow the upgrade provenance/rotation guidance.
- Frontend/BFF sessions, CSRF/Origin, two tabs, real browser layout/accessibility, non-UTC/browser-host continuity, timeouts/refetch and deployed-container behavior remain future frontend/F7 qualification. Fingerprint/proof checks must stay enabled.
- Certificate `isExpired` now uses the full UTC injected-clock instant with the X509 inclusive-notAfter boundary; status/notBefore/credential policy still determine usability. Credential expiry separately uses its documented inclusive-expired boundary.
- Complete dependency/runtime/vulnerability/license/supply-chain audits remain later gates. Earlier targeted BouncyCastle-alert analysis is not a fresh complete audit.
- Inherited duplicate org.json test-classpath, JVM/Mockito/native-access, optional Netty macOS DNS, Flyway forward-reference and owned cleanup warnings remain. Expected negative tests log denied requests and injected failures; these are not assertion failures or a claim of a warning-free runtime.

## Inventory regeneration

```sh
python3 scripts/endpoint-inventory.py \
  --results docs/portal/evidence/admin-management.json \
  --results docs/portal/evidence/certificates.json \
  --results docs/portal/evidence/protocol.json
```

The schema2 inventory has **109 source variants**: **61 positive**, **4 controlled views**, **3 exercised without positive completion**, and **41 unqualified**. It imports only the three fresh reports and retains their execution provenance/hashes. It does not infer qualification from test names or conflate backend acceptance with product readiness. See [inventory JSON](../recovery/ENDPOINT-INVENTORY.json) and [coverage table](../recovery/ENDPOINT-COVERAGE.md).

The final evidence validation checked JSON structure, report links/hashes, unique observation IDs, matching source provenance, completed readbacks, actual status classification and obvious secret/private-key/JWT/body/header fields. This is a mechanical evidence/privacy check, not an external-provider or deployment audit.

## F2 shell and additive local account forms — 24 October 2026

This supplements earlier backend evidence; it does not rewrite the historical inventory campaign or claim F3–F7 completion. User approved the slate/light enterprise direction and reviewed hosted account security addendum. Final complete-screen visual acceptance remains F7.

The settled source passed `python3 scripts/local-dev.py test`:257 Java tests,5 legacy JavaScript tests and19 Python tests, zero failures/skips. Frontend:60 unit/integration tests in9 files, TypeScript and production Next16.4.2 build passed; exact pinned dependency audit reports0 vulnerabilities. Private full gate log: `.local/portal-f2-full-backend-final.log`. Runtime artifact SHA256: `3b5b3bbae7ddd9a6c84c6a73944b0e54ecdea58583455b031fdf7d35fdb904b2`.

Real loopback Chrome campaigns used separate portal127.0.0.1/issuerlocalhost, non-UTC Asia/Kolkata, fingerprint/Referer/signature checks enabled, owned UUID SQL databases/Redis prefixes and copied immutable JARs. No Java response interception or skipped hosted scenario was used. Each report confirms owned processes stopped, both configured Java Redis namespaces empty, portal Redis empty and owned database dropped.

| Campaign | Actual result | Private report |
|---|---|---|
| Complete navigation |76 checks;4 tests; zero blocked/skipped | `.local/portal-runs/afe3b2073879462f95bd87b3c98de79b/report.json` |
| New issuer forms plus their axe/keyboard qualification |37 checks; profile/password axe0 violations | `.local/portal-runs/3fdad7e9a71f4042acd0d5c03fdea0ed/report.json` |
| Corrected shell/zoom capture |24 checks; actual chrome.tabs.getZoom2; CSS640/scroll640, devicePixelRatio2;6 axe reports0 violations | `.local/portal-runs/2f91ab8850c84775b4b039683e9c0074/report.json` |
| Final F1 auth/privacy regression |43 checks | `.local/portal-runs/d8046affef4e4518800ec16047d8c7ab/report.json` |

Hosted qualification includes manual Strict-cookie transition, roleless tenant using a platform-owned shared WEB client, portal A/issuer B mismatch (including same client), cancel with unchanged SQL, profile POST with changed SQL, wrong-current-password400 without reflection, password POST200, all affected human SQL sessions inactive, two real PKCE clients' old access tokens401, issuer SSO clearing, BFF opaque Redis cleanup only after Java401, unrelated admin preservation, old password rejection and new password login. HTML/script bodies, redirect headers, browser storage/cookies and application logs contained none of the actual OAuth/key/password material under test. The one303 redirect body is unavailable through Playwright and is explicitly recorded as such; its headers were checked.

Focused Java/SQL tests additionally prove CSRF/Origin/session/fingerprint/identity/continuation negatives, nonce compare-and-consume and HTTP concurrency, five-minute expiry and bounded actual stalled Redis failure, provider-mode GET/POST denial, policy/history validation, exact same-profile same-source credential audit/history preservation, separate-brand same-username and machine preservation, rollback with original token still active, queued old-password login and deleted-human-grant fencing, and unrelated human/machine persistence while another credential lock is held. V14→V15 preserves business rows and repeat migration performs zero work; V1–V14 are unchanged.

Sanitized shell images in the corrected capture directory above: `account-desktop.png`, `security-desktop.png`, `administration-desktop.png`, `account-narrow.png`, `account-200-percent.png` (640 CSS equivalent), and `account-native-200-percent.png` (actual native2x viewport after keyboard skip). Earlier full-page zoom images in afe3... were found cropped during image review and are superseded; runtime zoom/reflow checks in that campaign remain valid. New forms have separate axe evidence in3fdad...; Java credential-entry screens were not captured.

Limits: no production TLS/container/deployment, external provider/MFA/recovery delivery, Firefox/WebKit or final all-screen visual acceptance is claimed. Existing legacy password writer ambiguity remains a mandatory F4 follow-up, recorded in the approved plan/spec and carryovers. Future feature pages are absent; only Account, Security and functional Administration overview navigation are included. Parent reviews the entire F2 range before pushing.
