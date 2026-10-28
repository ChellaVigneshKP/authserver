# Portal acceptance evidence

Design/plans were approved in chat on **2026-10-21**. This records the B1–B7 foundation and separately scoped frontend supplements below. It does not certify a deployment, every frontend workflow or genuine external providers. [API-CONTRACT.md](API-CONTRACT.md) and [UPGRADE.md](UPGRADE.md) define the frontend/operator contracts.

## Exact execution provenance

Reports in the original backend evidence table ran the same **clean implementation commit `93dc6048e13a0d76302be0d09b0a158d1ef56bd0`**, with source digest `a00ac05ecef5bc9ad2806ff5f38170196e94ad59fd35d72d630c83b23ab140f1` and `working_tree_dirty:false`. The final full gate, all-phase HTTP/SQL campaign and owned lifecycle launcher ran after the production fixes settled. Documentation/evidence committed afterward is not retroactively presented as the executed source. Previous B7 snapshots remain historical Git evidence and are superseded by these fresh results.

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


## F2 review fixes — 25 October 2026

The earlier 257-test/60-test evidence describes the pre-review implementation. Authentication now captures the exact username/source credential GUID with its password/version snapshot. Context correlates protected top-level login_guid with SQL/profile/organization/subject. Unresolved biometric profile/source ambiguity is denied without changing general profile CRUD. An upgrade exception aborts login before session creation, including a physical SQL rollback followed by coordinated password/revocation commit. Administration conceals stale server content and revalidates its explicit URL after context changes. Switch-only and individual-read revocations preserve surviving home access; the existing administrator-read grant is recognized.

Current Java source passed python3 scripts/local-dev.py test: **269 Java tests, zero failures/errors/skips**, 5 legacy JavaScript tests and 22 Python tests. The full frontend Redis-backed suite passed **63 tests in 9 files**; TypeScript and production build passed. Runner/cleanup Python checks repeated after the owned CMS fixture addition passed 5+10+7 tests. No Java production source changed after the full gate. Final runtime SHA256: 92713c6e47f36a6007c3b9ce0cd4d49ed8d64eba6429189bfa917360ed9d8d82.

| Current campaign | Result | Private report |
|---|---|---|
| Covering navigation, hosted forms, sibling credentials, partial revocation, keyboard/axe/native zoom | 125 checks; 8 tests passed; no blocked/skipped scenario | .local/portal-runs/3d9a2e4ec69a4376af3f0445074fc51a/report.json |
| Existing authentication/privacy regression | 43 checks; 3 tests passed | .local/portal-runs/8a7729b3a5ff4daf994559b8fb2a2975/report.json |

Both campaigns used copied immutable final JARs and checks enabled. Reports confirm owned processes stopped, UUID database dropped, Java Redis DB0/DB1 and portal prefixes empty, and temporary CMS copies removed. Eight navigation/form axe results have zero violations. Native zoom remains independently 2x, CSS640/scroll640, DPR2. Hosted privacy summaries have 19 clean responses; the single303 redirect body remains unavailable to Playwright, with headers checked.

The same-source sibling positive uses the shipped portal login and proves exact context, profile/password POSTs, sibling password preservation and changed-target reauthentication. The different-source browser positive is a protocol fixture: the shipped BFF still selects idp; the test retains real BFF state/PKCE/cookies, selects public portal-sibling branding, and navigates natively from the portal so the browser supplies Referer. Only that owned JVM receives copies of the existing local CMS content. This does not qualify a shipped multi-brand selector or an external provider. Separate real SQL tests cover other-source identity and hosted operations.

The old-version positive starts at the guarded post-authentication handler with a structurally valid v0 storage envelope, performs the real legacy SQL upgrade/current KPCV write and session creation, and verifies current-encoder usability. Genuine v0 password verification remains unqualified because its external CryptoWebClient is unavailable; no provider or algorithm was invented. Mandatory F4 legacy writer/audit/PasswordEncoderManager and username-reuse revocation work remains open. Production/provider/final visual-acceptance limits above still apply.


## F3 clients, credentials and resources — 26 October 2026

The settled client implementation uses Next16.4.2/React19.3.1/MUI9.5.0, isolated Node24.22.0 and **React Hook Form7.90.0+Zod** for all seven F3 form areas. The exact RHF pin was published11October, exceeded the seven-day release-age policy at installation, and supports React19/Node24. Only that dependency was added; the resolved lock audit reported0 vulnerabilities. Secret creation responses and the acknowledgement dialog remain outside RHF/defaultValues/form state. Unrelated saved-section refreshes preserve dirty drafts; successful own readback or explicitly labeled reload resets them. Guided ordered navigation reflects actual readback, marks PKCE credentials unnecessary, and shows the actual optional resource count.

This campaign ran the feature worktree based on `bb9b2838c6ec94777599439c42a5eedbf76afb9c` before commit, not a claimed clean-commit snapshot. Its immutable JAR SHA256 is `125d83ff89510d033a0afab12b7171e48c32c01b28353626009c95a1ebd9fdc5`; embedded current application detail classes and V16 SQL were independently compared with compiled/source bytes. The per-file source manifest and final implementation report remain in ignored `.local/portal-f3-verified-source.json` and `.superpowers/sdd/2026-10-18-identity-portal-frontend/task-F3-report.md`. Qualification documentation was finalized afterward; no compiled source changed after the current passing gates.

| Gate | Result | Private evidence |
|---|---|---|
| Full enabled Java suite (`mvn test`, not mislabeled clean verify) |272 tests;0 failures/errors/skips |.local/portal-f3-backend-final2.log |
| Final RHF/catalog-source frontend |85 tests/14 files; TypeScript0errors; production build passed |.local/portal-f3-rhf-catalog-frontend.log; .local/portal-f3-rhf-catalog-types.log; .local/portal-f3-rhf-catalog-build.log |
| Complete current client/resource browser campaign |**118 checks/7 scenarios;0 failures/blocked** |.local/portal-runs/9d492b37584e4bfaae8b070bec966be5/report.json |
| Shared authentication regression |43 checks/3 scenarios passed |.local/portal-runs/7406942fa0c14042bb49774b91b82297/report.json |
| Shared navigation regression plus corrected owned-cache native zoom |120 functional checks/7 scenarios plus5 zoom checks/1 scenario passed; initial aggregate run failed before zoom launch because the cache environment was omitted |.local/portal-runs/c9781148163048f7a7ec0f1d4fa222ee/report.json; .local/portal-runs/0082eec6889a485ebdf3e1008f3c21b9/report.json |

Current F3 evidence covers actual browser/BFF/Java/SQL writes and independent Java+BFF viewer denials for all12 visible mutation variants; exact URLs, seconds and persisted refresh reuse; immutable type/auth pairing; two active credentials; disable/delete/deactivation; actual assignment/catalog GUID semantics and numeric-ID removal; normal metadata/HTML/RSC/storage/no-store privacy; guided keyboard/accessibility and dirty-form behavior. Real issuance qualified **WEB authorization-code+PKCE** and **SERVER CLIENT_SECRET_JWT/HS256 client credentials**. It does not qualify PRIVATE_KEY_JWT certificate-backed issuance, external providers, production TLS, token-response pinning, container execution, all browser engines or final user approval of every screen. These remain later F6/F7 gates. Mandatory F4 legacy writer/audit/PasswordEncoderManager and username-reuse revocation work remains open.

The controlled transport fixture forwards real exact signed requests to the owned Java process, confirms actual committed state and delays one armed response beyond the BFF deadline. It never fabricates a success response. Tests prove no automatic duplicate create, explicit ambiguous-candidate confirmation, lost-secret rotation without GET recovery, and SQL preservation under denials. Owned V15→V16 and repeat startup preserve existing client/token/username rows; the v2 DTO remains metadata-only. Browser timezone Asia/Kolkata and owned Next timezone UTC differ; issuerlocalhost/portal127.0.0.1 retain enabled fingerprint/Referer/signature checks.

The final report confirms owned JVM/Next/fault proxy stopped, CMS fixture removed, UUID database dropped, and Java Redis DB0/DB1 plus portal namespace remaining0. Six current runtime logs passed a bounded pattern scan with0 secret-input/raw-material/protocol-value matches; actual known-secret absence was also asserted as booleans in browser tests. Guarded current viewport PNGs are retained privately; they are consistency evidence, not final visual acceptance.

An earlier disposable test secret appeared in a Playwright strict-locator diagnostic and one tool-output read. Private failure artifacts were sanitized and that owned fixture was removed, but historical tool output cannot be withdrawn; the earlier run is not represented as secret-clean. Current tests avoid secret-bearing locator diagnostics, use boolean DOM checks, and disable traces, video, automatic failure screenshots and failure ARIA snapshots while secrets can appear. Current passing evidence does not erase that incident.


## F3 scoped review fix1 — definite identity rejection

Review I1 found that a definite duplicate-name409 was converted to uncertainty and hid editable input until the30-minute TTL. The fix separates create-call rejection from later Redis/readback errors. Only a create-origin400/403/404/409 after response-HMAC verification can retire its own initial unresolved attempt. An explicit retry rejection restores the captured prior genuine uncertainty and fixed expiry, with guidance to reconcile it. Unverified early401, signature/transport errors and post-success failures never gain noncommit authority; stale rejected attempts cannot clear newer attempts or selected application IDs. No Java, dependency or migration change is included.

The necessary fix gates have actual pre/post source-byte archives and complete per-file SHA256 manifests under ignored `.local/portal-f3-fix1/<gate>/{pre,post}/`, using the exact existing `scripts/e2e_provenance.py` sorted-path/NUL/file-bytes/NUL algorithm. Each command receipt records actual UTC start/end, argv, output hash and exit status. These captures independently establish the fix1 execution source; the missing original executed-snapshot capture and original installation-time registry capture remain historical attestation limits, not retroactively verified facts. The new typecheck receipt explicitly records exit0 despite normal empty output. Fresh filtered official npm metadata is archived separately in `.local/portal-f3-fix1/rhf-registry-current.json`, with source URL, current capture time,7.90.0 publication/peers/engines and current seven-day age eligibility; it does not reconstruct the original registry response.

| Necessary fix1 gate | Result | Private evidence |
|---|---|---|
| Verified-create classification and atomic attempt settlement |6 tests passed |.local/portal-f3-fix1/green-unit-rejections/receipt.json |
| Covering frontend |89 tests/14 files passed |.local/portal-f3-fix1/frontend/receipt.json |
| Current typecheck and production build |Both exit0 |.local/portal-f3-fix1/typecheck/receipt.json; .local/portal-f3-fix1/build/receipt.json |
| Real affected browser/BFF/Java/SQL |18 checks/3 scenarios passed;0blocked |.local/portal-runs/23fbc96ca6e54e6d8b82aad6094d31ca/report.json; .local/portal-f3-fix1/green-browser-affected/receipt.json |

The real regressions establish existing-name409/no new SQL row, editable correction followed by exactly one successful creation; retry409 preserving an earlier possibly committed application and later explicit reconciliation; and unchanged actual committed-response loss, ambiguity and lost-secret rotation. Fourteen pre/post archives and their receipt output hashes were mechanically verified against manifests. All passing fix gates used unchanged source digest `3752735e5c1c67383ec630aaf5c3a031cd96ec7f4e8406938d92097510961da5` over836 nonignored files. This documentation was finalized afterward without changing executed code/tests.

The owned runtime still uses immutable Java artifact `125d83ff89510d033a0afab12b7171e48c32c01b28353626009c95a1ebd9fdc5`. Java272 and the earlier broader118-check campaign remain their recorded evidence, not newly rerun results for this TypeScript-only fix. Current affected-fixture cleanup confirms owned processes/proxy stopped, CMSremoved, database dropped, and both Java Redis namespaces plus portal namespace remaining0. Six current runtime logs have0 secret-input/raw-material/protocol-value pattern matches. Historical secret-diagnostic incident and F4/F6/F7 qualification limits above remain unchanged. Browser color/toolchain and inherited Java/security-negative warnings are retained; M1 warning cleanup is deferred separately and passing output is not represented as pristine.

## F7 current qualification — browser journeys, outages and container

This section is the **current** F7 record. Rows above are historical: they were
executed at their own commits and are not re-claimed for the F7 tree. What
follows is branch `feat/identity-portal` at head `6457566` plus the uncommitted
F7 changes; the gate wrapper recorded tree digest `20fd6904` over 925
nonignored files for every gate below. Documentation was finalized after the
code gates, in line with the provenance rule above.

Toolchain: Node 24.22.0; Playwright 1.64.1 with Chromium revision 1248,
Firefox 157.0 (revision 1555) and WebKit 2370, each executed for this record.
Migrations are unchanged by F7: 18 additive `V1`–`V18` files, no edits to
published files and no schema reset. Runtime configuration category is
server-only process environment, documented in [RUNBOOK.md](RUNBOOK.md); the
portal defines no `NEXT_PUBLIC_` variable.

| Static/unit gate | Result | Private evidence |
|---|---|---|
| `npm --prefix web ci` and resolved-tree audit | `npm audit --audit-level=high` reported **0 vulnerabilities** in the resolved tree, exit 0 | `.local/portal-f4/f7-audit-6/receipt.json` |
| `npm --prefix web run typecheck` | exit 0, tree `20fd6904`/925 files | `.local/portal-f4/f7-tsc-5/receipt.json` |
| `npm --prefix web test` | **23 files/143 tests** passed | `.local/portal-f4/f7-vitest-5/receipt.json` |
| `npm --prefix web run build` | exit 0 production standalone build, BUILD_ID `skOSCRRX9Nm-sarXxLHz1` | `.local/portal-f4/f7-next-build-5/receipt.json` |
| `./scripts/local-dev.py test` | BUILD SUCCESS; **297 Java tests**, 0 failures/errors/skips; 5 Node checks; 22 Python checks | `.local/portal-f4/f7-local-dev-test-1/receipt.json` |
| Browser campaign `--phase all --browsers chromium,firefox,webkit` | **2082 checks across three isolated fixtures; 0 failed**; only the external-provider block remains | `.local/portal-campaigns/7312620f4ce04cf6b0bd15e43b711bd2/campaign.json` |

The audit statement is scoped to the resolved tree at this lockfile. It is not
a claim that the dependency graph is advisory-free, and it does not replace the
separate Bouncy Castle advisory check. No ESLint configuration exists in `web/`,
so typecheck is the enforced static gate; the CI step runs `npm run lint` only
if such a script is added later.

### Current browser scenarios (three engines, one campaign)

| Engine | Result | Run |
|---|---|---|
| Chromium 1248 | **694 checks**, 0 failed; 1 expected block | `.local/portal-runs/2891a276c71546479120fb2c1ad90e67/report.json` |
| Firefox 157.0 | **694 checks**, 0 failed; 1 expected block | `.local/portal-runs/15c0464b464a4984836ffe53234a5214/report.json` |
| WebKit 2370 | **694 checks**, 0 failed; 1 expected block | `.local/portal-runs/f21888672f8d49988db3145a50b3ff2d/report.json` |

Each engine ran all nine specs against its own owned fixture: clients 131,
users 151, navigation 125, certificates 73, roles 68, journeys 51, security 19,
outages 15 checks, and the auth/session spec contributes the remaining 61. The
one expected block per engine is `real account recovery delivery` (issuer
provider unconfigured, see below), so the campaign reports `BLOCKED` and exits
1 while every executed check passed — a blocked external item is not recorded
as a pass.

Every engine recorded `security_checks_enabled:true` with fingerprint, referer
and signature toggles on, provider `unconfigured; no provider success claimed`,
and cleanup with `owned_processes_stopped`, `database_dropped`,
`cms_fixture_removed`, `restart_replacement_stopped` and
`portal_port_released` all true. Journeys prove a persisted profile change,
sign-out followed by browser back denial, password change revoking other human
sessions, the new password working and the old one rejected, and recovery
leaving the portal for the issuer route without claiming delivery.

### Accessibility, reflow and keyboard

The navigation and journeys phases run axe and geometry checks on every
management and account screen: **14 automated accessibility checks**, **6 reflow
assertions** (narrow viewport without horizontal scroll, 200 % equivalent and
native 200 % zoom, and 320 CSS pixels on the client, tenant-user and account
detail screens) and **7 keyboard/focus assertions** (skip link to main on the
default and native-zoom layouts, confirmation-dialog focus trap and trigger
restore, labelled-field tab order on the hosted profile and password forms, and
the configuration link focusing the actual URL section). All passed in Chromium,
Firefox and WebKit; assertion ids and PNG evidence are in the run directories
above. Manual visual acceptance of the final screens remains the user's gate.

### Fixes proven by this qualification

1. **Report merge.** Nine specs each overwrote `browser-report.json`, so a
   multi-spec run published only the last spec's checks. `web/tests/e2e/report.ts`
   now merges by check id; the campaign total (2082) reconciles exactly with
   three engines of 694.
2. **Portal port leak.** The outage restart watcher started its replacement Next
   process outside the fixture's process group, so a finished run could leave
   port 3100 occupied. Restart replacements and both outage servers now start in
   their own session, every stop targets the group, and `run_browser` stops a
   leftover replacement after joining the watcher. Proven by
   `restart_replacement_stopped`/`portal_port_released` in all three legs and a
   free port after the campaign.
3. **WebKit credential-form race.** `CredentialPanel`'s submit control is a
   native `type="submit"`, so a click before React hydration submitted the form
   natively and navigated away instead of posting; the fault control stayed
   armed and no `/credentials` request was ever sent. The two affected
   scenarios now wait for the `#client-credentials` subtree to hydrate, matching
   the existing precedent in the client-settings and certificate tests. The
   isolated WebKit reproducer was re-run twice after the change and the full
   campaign passed in all three engines; no product change was required.
4. **Firefox on macOS 27.** Firefox 157 launched by direct `exec` cannot read
   `~/Library/Application Support/Firefox` (Mozilla 2069536/2078511, fixed in
   Firefox 158), so `web/playwright.config.ts` points `MOZ_APP_DATA` and
   `MOZ_LOCAL_APP_DATA` at private temporary directories. The host has no
   system Firefox profile root at all, so a bare manual or headed start of that
   build fails with "Your Firefox profile cannot be loaded"; that is an
   environment property, not a portal defect. `scripts/portal-firefox.sh`
   applies the same override for manual launches, prints `Mozilla Firefox 157.0`
   and exits 0, and leaves no profile directory or system profile behind.
5. **Certificate tooling.** The certificate scenarios mint certificates with
   explicit validity windows, which macOS LibreSSL cannot express. The spec
   probes `PATH` and Homebrew's `openssl@3` locations and honours
   `PORTAL_OPENSSL`; fingerprints and expiry instants are parsed with Node's
   `X509Certificate`.

### Container

Container qualification passed for this tree with `web/Dockerfile` unchanged.
`docker build -t identity-portal:f7c web` resolved both pinned references from
the local store and produced image id `sha256:f144f623…` with production build
`AerWvr-ofs5QQsZnHx-9J` (`.local/portal-f4/f7-container-build-2/receipt.json`,
exit 0, tree `4cf2f77b`/925 files).

| Container check | Result | Private evidence |
|---|---|---|
| Runtime identity | `Config.User` `portal`; `id -u`/`id -un` inside the running container report `10001`/`portal` | `.local/portal-f4/f7-container-health-1/output.log` |
| Image metadata | healthcheck set; env limited to `PATH`, `NODE_VERSION`, `YARN_VERSION`, `NODE_ENV`, `NEXT_TELEMETRY_DISABLED`, `PORT`, `HOSTNAME`; zero `NEXT_PUBLIC_` variables; `3100/tcp` exposed | same receipt |
| Liveness | `/healthz` **200** with a single `status` field and no credentials; Docker health status `healthy` on its first probe (exit 0) | same receipt |
| Readiness | `/readyz` **503** with a single `status` field when no Java/Redis configuration is supplied — the documented unconfigured state, not a credential leak | same receipt |
| First-slice smoke | `scripts/e2e-portal.py --phase auth --browsers chromium --container identity-portal:f7c` → **PASS: 44 browser/API checks, SQL verified**, run `.local/portal-runs/5529922e3f7344e996d357f1e70c3157/report.json` (`security_checks_enabled=true`, `container_smoke.non_root_user=portal`, `image_id sha256:f144f623…`, provider unconfigured) | `.local/portal-f4/f7-container-smoke-1/receipt.json` |
| Cleanup | owned container removed by name; JVM/Next stopped; fixture database dropped; Java Redis DB0 23 and DB1 1 removed with 0 remaining; portal Redis remaining 0 | run report `cleanup` |

The build and smoke ran at tree `4cf2f77b`/925 files, which differs from the
static-gate tree `20fd6904` only in this document. Earlier in the same session
every Docker Hub request from the daemon failed with `EOF`. The cause was IPv6:
the daemon dialled `registry-1.docker.io` over IPv6 on a host whose IPv6 path
is dead (`write tcp [2406:…]->[2600:…]:443: socket is not connected`), while
the same URLs answer 401/200 over IPv4 from both the host and containers in the
same engine. The two referenced images were therefore fetched inside a container
(IPv4-only) and imported locally, so BuildKit satisfied the pinned digests
without network access; no Dockerfile, tag or digest was changed. The procedure
is recorded in [RUNBOOK.md](RUNBOOK.md) under "Container build and run". This is
a property of the local engine's registry path, not a portal defect. The
historical container run `.local/portal-runs/eb31a42b…` (image `sha256:33d385…`)
remains historical.

### Pending and externally blocked

- **Blocked (external):** real recovery delivery needs a configured provider;
  visual acceptance of the final screens is the user's, not the campaign's.
- **Not claimed:** production load sizing, deployment acceptance and external
  provider certification remain independent gates.
- **Resolved:** container build, health and first-slice smoke for this tree;
  the interrupted attempt's orphan owned database `AuthserverLifecycle_d170a2ea…`
  and container `portal-f7-20d26aa4…`; and an orphan Next process plus one owned
  fixture database from an aborted run. Two fixture databases (`c6337930…` from
  27 October, `fffe794c…` from the morning of 28 October) predate this session
  and were left untouched.
