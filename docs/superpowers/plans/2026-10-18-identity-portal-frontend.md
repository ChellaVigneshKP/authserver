# Identity Portal Frontend and Acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the account portal and enterprise administration screens against the real Java service with server-held tokens and browser/API/database verification.

**Architecture:** A single Next.js application in `web/` has separate account/admin routes and allowlisted server route handlers. Redis holds opaque portal sessions; Java-hosted login/MFA/recovery/self-service and Java management APIs remain authoritative. Backend foundation contracts must pass before management screens ship.

**Tech Stack:** Node 24 LTS, patched stable Next.js App Router and compatible React, strict TypeScript, MUI community + official SSR adapter, React Hook Form/Zod, Vitest, Playwright, npm lockfile. Select exact compatible supported dependency versions at execution, record them and scan the resolved tree; do not blindly install `latest` or canary versions.

**Spec:** [2026-10-18-identity-portal-design.md](../specs/2026-10-18-identity-portal-design.md).

**Status:** Draft for approval; do not scaffold or install packages until the user approves the design and plans.

## Global Constraints

- Application code, dependencies, database state and running services stay unchanged until the user approves this design and execution plan.
- Existing database records, hashing/signing keys, clients and demo accounts must be preserved; only additive new migrations, never edits to V1–V11 or schema resets.
- OAuth access/refresh/ID tokens, signing material and client secrets must not be stored in browser localStorage/sessionStorage or emitted in URLs, HTML, client props, analytics, logs or screenshots; authorization-code and standard logout parameters are transient protocol exceptions and must be redacted.
- Java is the authorization authority; all human management API calls require a user bearer token and server-side tenant checks.
- Production sessions require HTTPS, HttpOnly, SameSite=Lax and Secure cookies; loopback HTTP development may disable Secure explicitly.
- Existing login, MFA, recovery and profile/password form CSRF and proof checks remain enforced; no global security toggle is weakened to integrate the portal.
- New lists use page size 25, allowed sizes 10/25/50/100, query length at most 100; existing legacy user search accepts at most 50 until the backend is deliberately expanded.
- New portal errors use `{code,message,fieldErrors?,requestId,retryable}`; secrets and upstream stack traces are never returned.
- Local simulated-provider success and real-provider success must be reported separately.

## Review Focus

- Two simultaneous login callbacks must not share state, swap subjects or expose tokens via RSC/HTML/cache (F1).
- Cookie-only forged cross-origin mutations and stale permission tabs must be denied by both BFF and Java (F1/F2/F5).
- Java create requests that time out after commit must yield recoverable partial configuration, not duplicate clients or lost one-time secrets (F3).
- Browsers in a non-UTC timezone and proxy/header changes must preserve the application's fingerprint contract with security toggles enabled (F1/F7).
- Redis/Java outages and provider failures must leave an actionable state without retrying destructive operations or claiming successful email delivery (F4/F7).

## File structure and shared interfaces

- `web/src/app/`: route pages/layouts and fixed auth/API handlers; no generic catch-all proxy.
- `web/src/server/config.ts`, `session-store.ts`, `auth.ts`, `csrf.ts`, `java-api.ts`, `portal-context.ts`, `errors.ts`: server-only configuration/session/protocol boundaries.
- `web/src/contracts/{portal,clients,users,roles,certificates,resources}.ts`: Zod validators and inferred TS types from backend contract; explicit legacy DTO mappings.
- `web/src/features/{account,clients,organizations,users,roles,certificates,resources}/`: feature form/table/actions, validation and tests together.
- `web/src/components/{PortalShell,DataTable,ConfirmAction,ErrorPanel,EmptyState}.tsx`; `web/src/theme/`: shared accessible layout/states/theme.
- `web/tests/{unit,integration,e2e}/`: session/API tests and browser flow acceptance. Integration tests can use a test HTTP server; acceptance never intercepts Java responses.
- `scripts/portal-dev.py`, `scripts/e2e-portal.py`: isolated fixture/start/report integration using existing local-dev/provider configuration, without printing secrets.
- `web/{package.json,package-lock.json,tsconfig.json,next.config.ts,vitest.config.ts,playwright.config.ts,Dockerfile,.dockerignore,.env.example}`; README and CI additions in F7.

Types defined in F1:

`BrowserContext={userAgent:string,acceptLanguage:string,externalOrigin:string}`; `PortalSession={id:string,subjectId:string,accessToken:string,idToken:string,signingKey:Uint8Array,expiresAt:number,createdAt:number,lastSeenAt:number,browserContext:BrowserContext,csrfSecret:string}` (server-only).

`PortalError={code:string,message:string,fieldErrors?:Record<string,string>,requestId:string,retryable:boolean}`; `Page<T>={items:T[],total:number,offset:number,limit:number}`; `PortalContext` matches B3.

Server interfaces: `requireSession(request:Request):Promise<PortalSession>`; `requireMutation(request:Request,session:PortalSession):Promise<void>`; `javaRequest<T>(session:PortalSession,operation:JavaOperation,input:unknown,schema:ZodType<T>):Promise<T>`; `getPortalContext(session:PortalSession):Promise<PortalContext>`. `JavaOperation` is a closed discriminated union of implemented route/method combinations, never arbitrary user URL/method. Feature handlers parse input then call these; pages receive sanitized feature DTOs only, never PortalSession.

## Task F1: Prove real login, server sessions and Java API integration

**Depends on:** B1–B3 passed. **Files:** New `web/` minimal package/config, server units above, contracts/portal.ts, `app/auth/{login,callback,logout}/route.ts`, `app/api/session/route.ts`, `app/api/context/route.ts`, minimal account page; `web/tests/integration/auth.test.ts`, `web/tests/e2e/auth.spec.ts`, `scripts/portal-dev.py` and initial `scripts/e2e-portal.py`.

**Interfaces:** Shared server types/functions above; GET `/api/session` returns only `{authenticated,expiresAt,csrfToken}` or401; context has B3 public shape. `createLoginTransaction(returnTo:string,context:BrowserContext)` → server-held one-use state/nonce/verifier; callback consumes it atomically. Redis functions `putSession/getSession/touchSession/deleteSession/consumeLoginTransaction` enforce design TTLs.

- [ ] Write failing tests: `rejectsWrongReplayedOrExpiredState`, `validatesIssuerAudienceNonceAndSignature`, `twoTabLoginRemainsIndependent`, `rejectsExternalReturnTo`, `sessionRotatesAfterLogin`, `expiredAccessTokenRequiresSignin`, `crossUserResponsesAreNotCached`, `sessionTokensAbsentFromBrowserArtifacts`, `mutationRequiresOriginAndCsrf`. Assert five-minute login transaction, 30-minute idle/eight-hour absolute limits capped by token expiry, one-use callback and logout invalidation. Use a test clock for TTL tests.
- [ ] Create only minimal approved scaffold/config and run `npm --prefix web run test -- auth`; expect the above tests fail because protocol/session functions are absent. Separate infrastructure/setup failures from behavior assertions.
- [ ] Implement functions with server-only imports, cryptographic random IDs, Redis atomic consumption and strict validated configuration (`PORTAL_ORIGIN`, `AUTH_ISSUER`, `AUTH_CLIENT_ID`, `PORTAL_REDIS_URL`, cookie settings and signature policy). Client registration uses existing WEB/PKCE; no new privileged machine credential. Read `signing_key` from `CustomTokenResponseSuccessHandler` token extension, supporting its actual JSON byte encoding; never forward the raw token response. Use established JOSE verification instead of hand-written JWT validation. Cap/disallow untrusted JWKS destinations and validate discovered issuer/endpoints against configured origins. Add no-store and redacted request IDs/errors; Origin+CSRF protect all mutation handlers including logout. Require B1 Java bearer policy to reject cookie-only calls.
- [ ] Implement exact-byte Base64 HMAC-SHA256 signing and verification of API response `x-signature` with decoded `signing_key`; reject missing/mismatched API signature on authenticated management responses. Capture browser headers and fixed external origin; generate consistent UTC timestamps; never allow caller-supplied arbitrary forwarding or host headers. Token body signatures use the separately pinned `AUTH_TOKEN_RESPONSE_CERT_PEM` certificate when configured: require RSA SHA256 or EC SHA256/P1363 verification before consuming the response. With no certificate configured, require HTTPS outside explicit loopback dev and validate the ID token via issuer JWKS, without claiming token-body-signature verification. Test wrong/missing HMAC and wrong pinned certificate separately.
- [ ] Run `npm --prefix web run test -- auth`, `npm --prefix web run typecheck`, then `python3 scripts/e2e-portal.py --phase auth`. Real Chrome: authorize/login/optional lab MFA/callback/context/client read/logout; keep fingerprint+referer+signature checks enabled. Verify direct cookie-only Java calls401, expired bearer rejected and subsequent Java SSO ended. Use a non-UTC browser timezone. If BFF fingerprint compatibility fails, stop and revise contract; no toggle workaround. Expect secret absence from storage/DOM/RSC/logs/normal JSON, clean callback URL and controlled Redis503.
- [ ] Commit: `feat: add secure portal session and Java integration`.

## Task F2: Build the enterprise shell and account portal

**Files:** `web/src/components/*` above, `web/src/theme/{tokens,theme}.ts`, `app/layout.tsx`, `app/account/{page,security/page}.tsx`, `app/admin/layout.tsx`, `app/{forbidden,unavailable}/page.tsx`, `features/account/{AccountSummary,HostedActionLink}.tsx`; `tests/e2e/navigation.spec.ts`, `tests/unit/permissions.test.ts`.

**Interfaces:** `PortalShell({context:PortalContext,children:ReactNode})`; `canShow(context:PortalContext,action:string,orgId:string):boolean` controls UI only; `buildHostedAction(action:'change-profile'|'change-password'|'recovery',returnTo:string):URL` generates safe continuation without tokens. Layout server checks repeat at API entry points; no middleware-only security.

- [ ] Write failing keyboard/navigation/accessibility tests: account user sees no admin areas, viewer has no mutation actions, tenant has no global-org switch, platform switch changes explicit scope, deep foreign URL is denied, late permission revocation removes access, account links use only allowlisted return URLs. Include 200% zoom, narrow layout and focus restoration on confirmation dialogs.
- [ ] Run `npm --prefix web run test -- permissions` and `python3 scripts/e2e-portal.py --phase navigation`; expect missing routes/components.
- [ ] Implement MUI SSR adapter and proposed theme, page title/breadcrumb/organization indicator, consistent loading/empty/error/denied states. Shell shows real information only. Account pages navigate to actual Java-hosted forms/recovery; retain existing SSO/CSRF/HMAC flow and avoid rendering secrets. No iframe or replacement password collection.
- [ ] Run typecheck, targeted tests and real Playwright navigation. Produce sanitized screenshots for user visual approval before expanding screens; verify manual keyboard order and automated accessibility checks, not screenshots alone. Adapt visual details within approved scope; substantial redesign goes back for review.
- [ ] Commit: `feat: add enterprise portal shell and account screens`.

## Task F3: Deliver clients, credentials and resource assignments

**Depends on:** B2/B6; F1/F2. **Files:** `contracts/{clients,resources}.ts`, `features/clients/{ClientList,ClientWizard,ClientDetails,UrlSettingsForm,TokenSettingsForm,CredentialPanel,SecretOnceDialog,ResourceAssignments}.tsx`, `features/clients/api.ts`; corresponding explicit `app/api/admin/organizations/[orgId]/clients/.../route.ts` handlers and `/admin/organizations/[orgId]/clients/...` pages; `tests/e2e/clients.spec.ts`, `tests/integration/client-errors.test.ts`.

**Interfaces:** `createClient(orgId,input):Promise<ClientSummary>` maps to existing application DTO; `getClientReadiness(detail,urls,credentials):{ready:boolean,missing:string[]}` derives actual state. UI keeps application GUID distinct from OAuth clientId. Credentials use B2 v2 metadata only. Creation result type has ephemeral secret only in the single acknowledgement response; no query cache/persistence for it.

- [ ] Write failing browser tests for wizard valid/invalid type/auth combinations, immutable fields, exact redirect validation, token seconds, no arbitrary grant/scope controls, successful PKCE/confidential issuance after configuration, two-active credential limit, secret once then absent on reload, disable/delete/revoke effects, assigned resources. Add timeout-after-commit fixture: no automatic second POST, show uncertain step, reconcile list/detail by created identifier or candidate identity and require user confirmation before retrying if ambiguous. Lost secret creation response must instruct rotation; cannot recover via GET.
- [ ] Run `npm --prefix web run test -- client-errors` and `python3 scripts/e2e-portal.py --phase clients`; expect missing client flows.
- [ ] Implement API routes with BFF session/CSRF guards, Zod input/response mapping and closed JavaOperation. Wizard persists non-secret step IDs/configuration only server-side under session with a 30-minute TTL; access tokens and secrets never enter wizard storage. Read after each operation, show partial progress and resume; disable double-submit while pending. No automatic non-idempotent retries. Lists search complete bounded datasets locally only when backend returns a full array; make that behavior explicit rather than fake server pagination.
- [ ] Implement one-time-secret dialog with copy acknowledgement, no download or analytics, erase on dismissal/navigation and no capture in screenshots/traces. Platform resource catalog uses existing create/update only; client assignment actions respect actual permissions. Client deactivation confirmation uses its name and explains token impact.
- [ ] Run unit/integration checks and actual browser/API/SQL client campaign. Each visible mutation asserts SQL and permissions denial; issuance uses disposable credentials and the actual token endpoint, never an intercepted response. Assert disabled client token no longer authenticates.
- [ ] Commit: `feat: add client onboarding and credential management`.

## Task F4: Deliver organization and user administration

**Files:** `contracts/users.ts`, `features/organizations/{OrganizationList,OrganizationForm,ContactForm}.tsx`, `features/users/{UserList,CreateUserForm,UserDetails,UserProfileForm,UserSecurityForm,UserMetadataForm}.tsx`, `features/users/api.ts`; explicit organization/user pages and handlers; `tests/e2e/{organizations,users}.spec.ts`.

**Interfaces:** `listUsers(orgId,{offset,limit,search,status,type}):Promise<Page<UserSummary>>` maps existing results envelope and enforces legacy search≤50, GET with no body. `UserSummary` includes B3 org/group/syncMode identifiers. Mutations map exact DTO keys and branding; Base64 password encoding happens on server and is not treated as encryption.

- [ ] Write failing tests for platform org create/contacts/revoke, tenant own-org edit but denied status/global list; user create/edit/email/username/password/status/metadata; server pagination/search, empty results and field validation. Test foreign org/user URL tampering, wrong body branding, duplicate submit, externally synchronized account provider failure and stale list after deletion. Assert no misleading invitation or notification success message. Password/history tests use real backend policy, and no password appears in rendered props/errors.
- [ ] Run `python3 scripts/e2e-portal.py --phase users`; expect missing screens/contracts rather than mocking provider success.
- [ ] Implement feature forms/handlers with explicit action permissions; sanitize and size-limit JSON metadata editing (64 KiB portal limit) and treat it as untrusted data, never permissions. Keep selected org server-validated on each operation. Map existing backend errors into field/global messages; on uncertain mutation reload target before offering retry. No permanent-erasure claims for lifecycle actions and no synthesized MFA enrollment. Local user creation does not call upstream profile-sync import.
- [ ] Run focused tests/typecheck and real browser/API/SQL campaign including old-password rejection/new-password acceptance on a disposable account and a genuine provider-unavailable503/error case. Reload shows persisted values. Denials leave SQL unchanged.
- [ ] Commit: `feat: add organization and user administration`.

## Task F5: Deliver role definitions and assignment

**Depends on:** B4/B5. **Files:** `contracts/roles.ts`, `features/roles/{RoleList,RoleEditor,PermissionMatrix,RoleAssignment}.tsx`, `features/roles/api.ts`; explicit org roles/group assignment route handlers/pages; `tests/e2e/roles.spec.ts`, `tests/integration/role-conflict.test.ts`.

**Interfaces:** RoleDto and bodies exactly backend plan; editor carries revision; assignment sends `{groupId:UUID|null}` to org-scoped endpoint. No client-generated permission catalog and no arbitrary role keys.

- [ ] Write failing tests for platform custom role CRUD and assignment, built-in immutability, tenant read/eligible assignment, unavailable foreign role, grant ceiling, stale409, assigned-role409 and ambiguous legacy membership409. Two browsers with stale revisions cannot overwrite; permission removal makes an already logged-in tab lose the next protected action. Last-platform-admin action displays actual backend409 rather than optimistic success.
- [ ] Run integration tests and `python3 scripts/e2e-portal.py --phase roles`; expect missing screens.
- [ ] Implement server catalog-driven matrix, immutable built-ins, platform-only definition actions, eligible same-org assignment selector. Never optimistically display a successful permission change; refetch context and affected detail. Stale update shows reload/review action, no force-overwrite. Error messages do not reveal foreign tenant role details.
- [ ] Run browser campaign with real SQL and direct Java denials; test missing backend enforcement by attempting requests independent of disabled UI controls. Assert no use of metadata to grant roles.
- [ ] Commit: `feat: add protected role and membership screens`.

## Task F6: Deliver certificate lifecycle screens

**Files:** `contracts/certificates.ts`, `features/certificates/{CertificateList,CertificateImportForm,CertificateDetails}.tsx`, `features/certificates/api.ts`; explicit certificate upload/detail/download/deactivate handlers/pages; `tests/e2e/certificates.spec.ts`.

**Interfaces:** Certificate type `ORGANIZATION|PUBLIC_KEY`, server metadata DTO, multipart form with name/type/password/certificate. Enforce 1 MiB both BFF streaming request bound and Java contract. Download route streams validated public certificate bytes and sanitized filename only, no-store.

- [ ] Write failing tests for genuine test PEM import/list/detail/public download/deactivation; expired/invalid/oversized PEM, missing private key for ORGANIZATION, denied tenant organization-key import, wrong target org. Assert no private key/password persists in browser state/server logs/traces; secret fixtures never appear in screenshots. Verify later-today UTC expiry display and issuance behavior.
- [ ] Run `python3 scripts/e2e-portal.py --phase certificates`; expect missing UI/routes.
- [ ] Implement explicit type selection, content validation errors and UTC/local expiry with type/status text. Stream bounded multipart; never buffer arbitrary upload sizes. Explain downstream impact before deactivate without inventing usage counts. Drop password/file form state after submission/navigation. Public download is distinct from private-key export, which has no UI.
- [ ] Run browser/API/SQL certificate campaign and `scripts/e2e-certificates.py` against isolated fixture app. Verify actual private-key JWT issuance and immediate rejection after revocation, signature matches advertised certificate and downloaded PEM parses to the expected public fingerprint.
- [ ] Commit: `feat: add certificate lifecycle management`.

## Task F7: Qualify deployment, outages and complete browser journeys

**Files:** `scripts/e2e-portal.py`, `web/playwright.config.ts`, `web/tests/e2e/{journeys,outages,security}.spec.ts`, `web/{Dockerfile,.dockerignore,.env.example}`, `docs/portal/{RUNBOOK,ACCEPTANCE}.md`, root README and existing CI workflow if one exists (otherwise create a dedicated portal workflow).

**Interfaces:** `python3 scripts/e2e-portal.py --phase all --browsers chromium,firefox,webkit` creates unique two-tenant fixtures with role actors, optional isolated local-provider lab, and sanitizes its `.local/e2e-portal-report.json`. No raw tokens/secrets/codes or live PII. Java/Redis fault tests affect owned app/proxy/containers only, never shared Docker Desktop.

- [ ] Add failing whole-journey tests: platform configures client and delegates user; tenant manages local user but cannot cross tenant; viewer reads metadata without secret; account user completes hosted profile/password/recovery and signout. Add Redis/Java pause/recovery, missing real provider, non-UTC browser, referer/fingerprint enforced, browser back after logout, two-tab callback/revocation, expired session and server restart. Record real provider checks as blocked unless configured; simulation label cannot be dropped by test success.
- [ ] Run all browser journeys before any release claim; resolve genuine bugs and rerun only affected gates then final campaign. Verify each mutation's SQL readback and denied case. Use no network interception for acceptance; UI component tests may use fixtures but cannot contribute to end-to-end evidence.
- [ ] Provide production standalone Node Docker build, non-root runtime, runtime-only config/secrets, health/readiness endpoints that expose no credentials, and reverse-proxy requirements for fixed HTTPS origin/trusted headers. No build-time embedding of private env files; secret env keys never use `NEXT_PUBLIC_`. Dev runner allocates a free portal port (default3100) and registers exact callback/logout URLs with a private loopback fixture; preserves existing3000/3001 demos and existing clients. Document Java public origin versus BFF internal origin, cookie hostname behavior and SSO logout outage limitations.
- [ ] Run `npm --prefix web ci`, `npm --prefix web run lint`, `npm --prefix web run typecheck`, `npm --prefix web test`, `npm --prefix web run build`, dependency audit of resolved tree, `./scripts/local-dev.py test`, and full three-browser runner. Build/run the actual frontend container and repeat first-slice smoke. A clean build alone is insufficient. Pin tested versions/image digest and document unresolved advisories with explicit scope; no blanket scan-clean claim.
- [ ] Run accessibility checks and manual keyboard/focus/zoom review of complete journeys. Obtain user visual acceptance of the final screens. Record source commit, migrations, browser versions, runtime configuration category and scenario-level results; separate historical reports, current tests and external blockers. Run `git diff --check` and review tracked files for secrets before commit.
- [ ] Commit: `test: qualify portal browser and management journeys`. Publish/push/deploy only within the user's explicit authorization for the implementation stage; do not merge or deploy merely because local tests pass.

## Acceptance and execution handoff

Both plans plus the specification require user approval before execution. Recommended execution is subagent-driven with fresh task reviews because changes span authorization, secrets and browser sessions. Native execution is also possible with the same task gates and a fresh whole-branch review. Backend B1–B3 precede F1; B4/B5 precede F5; B6 precedes F3/F6 release. No phase may advertise functionality backed by unimplemented APIs.

Completion means the documented local workflows and denial/outage cases pass against real services with preserved user data. External provider certification, production load sizing and deployment acceptance remain explicit independent gates; do not claim perfect or exhaustive verification.
