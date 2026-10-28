# Identity Portal Backend Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Java management API safe and complete for human administration of clients, users, organizations, roles and certificates.

**Architecture:** Preserve Java/SQL/Redis authority and current hosted authentication. Repair bearer/tenant policies and secret responses, then add portal context and transactional role/membership contracts through existing JDBC patterns. Keep machine permissions explicit and separate.

**Tech Stack:** Existing Java 25 / Spring Boot / Spring Security / JDBC / Flyway / SQL Server / Redis; Python real-HTTP acceptance scripts.

**Spec:** [2026-10-18-identity-portal-design.md](../specs/2026-10-18-identity-portal-design.md). Review: [source findings](../specs/2026-10-18-identity-portal-review.md).

**Status:** Design and execution plan approved in chat2026-10-21. Original proposal history is preserved; completed backend contracts and evidence are published in `docs/portal/`.

## Global Constraints

- Application code, dependencies, database state and running services stay unchanged until the user approves this design and execution plan.
- Existing database records, hashing/signing keys, clients and demo accounts must be preserved; only additive new migrations, never edits to V1–V11 or schema resets.
- Java is the authorization authority; all human management API calls require a user bearer token and server-side tenant checks.
- Existing login, MFA, recovery and profile/password form CSRF and proof checks remain enforced; no global security toggle is weakened to integrate the portal.
- New portal errors use `{code,message,fieldErrors?,requestId,retryable}`; secrets and upstream stack traces are never returned.
- Local simulated-provider success and real-provider success must be reported separately.
- All additional constraints in the specification apply, including credential secrecy, role delegation and existing-data ambiguity checks.

## Review Focus

- A tenant read claim with an omitted or forged organization filter must never reveal another tenant's records or result totals (Task B1).
- A permission removal or concurrent last-admin operation must take effect before a stale browser can mutate protected data (Tasks B1/B5).
- Credential expiry later today in UTC and malformed imported dates must not hide an active key or crash metadata reads (Task B2).
- Multiple legacy role memberships must not be silently collapsed by the new assignment API (Tasks B4/B5).
- A timed-out client onboarding step must not be treated as success or automatically create a duplicate client (Task B6; frontend F3).

## File structure and interfaces

Existing source root abbreviated below as `J=src/main/java/com/chellavignesh/authserver`; test root `T=src/test/java/com/chellavignesh/authserver`. These are exact path prefixes, not new directories literally named J/T. Use existing controllers/services/repositories; do not move unrelated code.

New focused units:

- `J/security/{ManagementAccessPolicy,ActorContext}.java`: bearer actor record, organization, target and delegation policy.
- `J/adminportal/portal/{PortalContextController,PortalContextService}.java`, `dto/{PortalContextDto,PermissionCatalogEntryDto}.java`: authenticated context and catalog.
- `J/adminportal/credential/dto/CredentialMetadataDto.java`, `controller/CredentialMetadataController.java`: metadata-only v2 read.
- `J/adminportal/organization/role/{RoleController,RoleService,RoleRepository,RoleAssignmentController,RoleAssignmentService}.java`, `dto/{RoleDto,CreateRoleDto,UpdateRoleDto,AssignRoleDto}.java`: role CRUD and assignment.
- `J/adminportal/util/PortalProblem.java`: error record used by new contracts, matching spec envelope.
- Additive V12/V13 migrations in `src/main/resources/db/migration/`: role catalog/revision and membership mutations respectively, subject to the next unused migration numbers when execution begins.
- `T/security/ManagementAccessPolicyTest.java`, `T/adminportal/portal/PortalContextIntegrationTest.java`, `T/database/RoleContractTest.java`, `T/recovery/CredentialMetadataIntegrationTest.java`: security policy and real SQL/HTTP contracts.
- `scripts/e2e-admin-management.py`: reusable disposable two-tenant real-HTTP campaign; report `.local/e2e-admin-management-report.json` with no secrets.

Shared contract names: `ActorContext(userId:UUID,organizationId:UUID,platformAdmin:boolean,permissions:Set<String>)`; `RoleDto(id:UUID,name:String,description:String,status:String,permissionKeys:List<String>,revision:int,memberCount:int,readOnly:boolean)`; assignment body `{groupId:UUID|null}`. Existing public GUIDs are used; never expose numeric SQL IDs. A platform actor's selected organization is a request scope, not a replacement identity.

Use the existing `scripts/local-dev.py test` gate for Maven integration tests; extend its database-test selection only if needed, retaining disposable UUID database ownership. For focused Java tests, use the same private environment and Maven cache setup as that runner, rather than copying secrets into shell arguments. Name methods/classes as below so `-Dtest=<Class>` can select them when running under the configured environment.

## Task B1: Enforce bearer and tenant policy for management APIs

**Files:** Create `J/security/ManagementAccessPolicy.java`, `T/security/ManagementAccessPolicyTest.java`; modify `J/security/{SpringSecurityConfig,PermissionsService}.java`, `J/adminportal/user/AdminOnlyFilterAspect.java`, `J/adminportal/controller/{UserController,OrganizationController}.java`; create initial `scripts/e2e-admin-management.py`.

**Interfaces:** Consumes existing opaque-token claims and UserDetails membership lookup. Produces `ActorContext requireHumanActor(Authentication)`; `boolean canReadUser(Authentication,UUID)`; `void requireUserMutation(Authentication,UUID,String)`; `UUID requireOrganizationScope(Authentication,UUID)`; explicit machine branch constrained to registered application scope. Methods throw controlled access-denied errors, never cast unauthenticated/session principals.

- [ ] Write failing `tenantReadWithoutFilterIsScoped`, `foreignUserMetadataIsDenied`, `orgAdminCanManageEligibleLocalUser`, `developerCannotManageUsers`, `forgedGroupDoesNotGrantAdmin`, `machineCannotTargetForeignOrg`, `cookieOnlyManagementRequestIsUnauthorized` tests. Use platform/admin/developer/viewer/account principals in org A/B; assert all foreign body/detail/list totals absent and no SQL mutation on denied requests. Include deactivated org and removed permission on next request.
- [ ] Run `./scripts/local-dev.py test` and the initial `python3 scripts/e2e-admin-management.py --phase policy`; expect failing authorization assertions before the patch, not unrelated setup failures. The new runner loads `.local/dev.json` internally, refuses non-loopback/unapproved DB names and cleans only UUID-tagged fixtures.
- [ ] Implement ManagementAccessPolicy and delegate existing helpers/annotations to it. Human org scope is mandatory/derived; reject conflicting query/body IDs. Preserve narrowly tested machine integration permissions; never give the portal a machine token. Split bearer-only management handling from Java form-session flows without altering OAuth chain ordering or static resources. Put organization status changes behind platform deactivate permission, separately from name/contact updates. Remove direct BearerTokenAuthentication casts from the aspect or retire the aspect by moving scope derivation to the policy/service.
- [ ] Run the full existing gate plus `--phase policy`; expect all existing scenarios and new positive/negative actor matrix to pass. Verify hosted login CSRF still rejects bad tokens and PRE_AUTH cannot administer.
- [ ] Commit only policy changes/tests: `fix: enforce tenant scoped management authorization`.

## Task B2: Make credential reads metadata-only and expiry correct

**Files:** Create `J/adminportal/credential/dto/CredentialMetadataDto.java`, `J/adminportal/controller/CredentialMetadataController.java`, `T/recovery/CredentialMetadataIntegrationTest.java`; modify `J/adminportal/credential/{CredentialService.java,dto/CredentialResponseDto.java}`, `J/adminportal/controller/CredentialController.java`; update tests/acceptance consumers that depended on secret rereads.

**Interfaces:** Produces `List<CredentialMetadataDto> listMetadata(UUID org,UUID app)` and GET v2 route from spec. DTO fields exactly `id,name,type,algorithm,status,expirationDate,isExpired`; no value. Existing POST returns `{id,secretValue}` only for shared-secret creation. Keep internal verification service's decrypted-secret access private to that use case.

- [ ] Write failing `viewerCannotReadSecretMaterial`, `secretOnlyReturnedOnCreation`, `expiryUsesUtcInstant`, `malformedExpiryFailsClosedWithout500`, `confidentialJwtStillAuthenticates` tests. Create a synthetic credential through HTTP; assert secret absence from legacy/v2 GET and metadata objects. Assert same-day before/after expiry boundary with an injected clock, not sleeps. For invalid stored dates, return `isExpired=true` and a sanitized warning without a 500.
- [ ] Run `./scripts/local-dev.py test` with these tests; expect secret-response/expiry assertions to fail on baseline.
- [ ] Implement metadata reads without decrypting shared secrets; apply `Cache-Control: no-store` to create and all credential responses. Remove `value` from legacy public reads as the documented breaking security change. Retain internal JWT verifier lookups and certificate public-download endpoints. Use UTC Instant parsing shared with CredentialService. Document migration for consumers that reread secrets.
- [ ] Run full gate and `python3 scripts/e2e-admin-management.py --phase credentials`; assert new credential creates once, list never reveals it, two-active limit and disable-before-delete behavior hold, old disabled credential cannot issue tokens and new active one can.
- [ ] Commit: `fix: restrict credential secrets to creation responses`.

## Task B3: Add human portal context and safe response contracts

**Files:** Create portal classes/DTOs and `J/adminportal/util/PortalProblem.java` listed above; create `T/adminportal/portal/PortalContextIntegrationTest.java`; modify `J/adminportal/user/dto/UserResponseDto.java` and its mapper, relevant SQL projection only through new migration if required.

**Interfaces:** GET `/api/v1/portal/context` → exact subject/organizations/permissions/capabilities shape in spec. `PortalContextDto getContext(Authentication)` derives permissions from current introspection. Extend user DTO additively with `organizationId:UUID,groupId:UUID|null,syncMode:String`. `syncMode` is `local|sync|async`, derived from actual source/toggle settings. Existing DTO consumers retain fields.

- [ ] Write failing `contextUsesHumanPermissions`, `contextListsOnlyAccessibleOrganizations`, `accountUserHasNoAdminCapabilities`, `permissionRemovalVisibleOnNextRequest`, `contextContainsNoSecrets`, `userDtoIncludesAuthorizedOrgAndGroup`, `contextRejectsClientCredentialActor` tests. Assert providerMode distinguishes configuration only, and invalid bearer gives401 with no context.
- [ ] Run the full gate; expect missing endpoint/contract failures.
- [ ] Implement context using existing organization/user/external-source services and B1 policy; no new passwords, token minting or privileged impersonation. Include own org for tenant principals without granting global org:list. Use no-store on all context/error responses and generate/propagate validated request IDs. New errors conform to PortalProblem; leave unrelated legacy response shapes to BFF normalization.
- [ ] Run full gate and `python3 scripts/e2e-admin-management.py --phase context`; assert context differs for every role and SQL assignment changes appear without re-login.
- [ ] Commit: `feat: expose authenticated portal context`.

## Task B4: Add protected role definitions and permission catalog

**Files:** Create role controller/service/repository and RoleDto/CreateRoleDto/UpdateRoleDto; create V12 role revision/catalog migration and `T/database/RoleContractTest.java`; extend portal catalog controller and existing group DTO/list with additive revision/member count as needed.

**Interfaces:** `RoleDto create(UUID org,CreateRoleDto,ActorContext)`; `RoleDto update(UUID org,UUID group,UpdateRoleDto,ActorContext)`; `RoleDto get(UUID org,UUID group,ActorContext)`; `void deactivate(UUID org,UUID group,ActorContext)`. Paths/body/status exactly spec; GET catalog platform-only. Existing group/permission reads remain compatible.

- [ ] Write failing SQL+HTTP `roleCreateRejectsUnknownOrDuplicateKeys`, `customRolesCannotContainPlatformReservedActions`, `customRoleNamesAreCaseInsensitivePerOrg`, `builtinRolesAreImmutable`, `tenantCannotEditRoleDefinitions`, `staleRoleRevisionReturns409`, `assignedRoleCannotBeDeactivated`, `foreignRoleIsNotFound`, `roleChangeUpdatesExistingTokens` tests. Concurrent PUTs with the same revision: exactly one success and one409. Test malformed/oversized names, empty role permission set, and transaction rollback.
- [ ] Run full gate and `--phase roles`; expect missing role API/schema failures.
- [ ] Implement additive schema for revision/built-in markers and catalog without renumbering historical migrations or role IDs. Seed only specified permission grants from the design; catalog limits platform reserved actions. Validate names/keys, transactionally replace role permissions with revision compare-and-swap, preserve built-ins. Use role row locks consistently with assignment. Existing duplicate names/multi-memberships produce a preflight report and stop affected mutations; never auto-repair user data. Update DatabaseContractTest's expected migration list/count deliberately.
- [ ] Run full gate, migrate a fresh disposable DB and an eleven-migration populated fixture, rerun migrations and assert stable rows/IDs and no duplicate reference grants. Run `--phase roles` to prove real introspection sees removals.
- [ ] Commit: `feat: add protected organization role management`.

## Task B5: Add safe role assignment and last-administrator protection

**Files:** Create RoleAssignmentController/Service/AssignRoleDto; extend RoleRepository; create V13 assignment procedures/indexes guarded by preflight; extend B1 user/status/org policy and RoleContractTest.

**Interfaces:** `void assign(UUID org,UUID user,UUID groupOrNull,ActorContext)` backing PUT org `/users/{user}/group`. Introduce `void requireAdminSurvives(ChangeIntent)` used in assignment, user deactivation/delete and relevant role/organization changes. One active same-org group maximum for new writes; imported ambiguity yields409.

- [ ] Write failing `assignmentCannotCrossOrg`, `tenantCannotGrantBeyondOwnPermissions`, `selfElevationDenied`, `platformGroupCannotBeAssignedByTenant`, `unassignLeavesUserActive`, `legacyMultipleGroupsReturns409`, `lastPlatformAdminCannotBeRemoved`, `concurrentAdminDisableLeavesOneActive`, `assignmentAndRoleUpdateSerialize` tests with real SQL barriers/transactions. Cover user status/delete, group replacement/deactivation and platform-org revoke; protect every path capable of removing the final admin.
- [ ] Run full gate and `--phase assignments`; expect absent assignment/guard behavior.
- [ ] Implement transactionally locked user/group/org changes with consistent lock ordering and a stable platform-admin guard lock. Count effective active platform administrators using active profile/credential/org/group/permission predicates, not a cached UI count. Enforce actor permission ceiling on both new assignment and later role permission edits. Preserve membership in other orgs; never modify ambiguous legacy records automatically.
- [ ] Run SQL concurrency tests repeatedly only to resolve actual race uncertainty; full gate and `--phase assignments` must show exactly one surviving platform admin and no privilege escalation. Verify old bearer permissions reflect new assignment on next call.
- [ ] Commit: `feat: enforce safe organization role assignment`.

## Task B6: Validate portal-facing client, certificate and lifecycle behavior

**Files:** Modify `J/adminportal/application/dto/{ApplicationDtoValidator,ApplicationResponseDto,ApplicationDetailResponseDto}.java`, relevant controllers/services only for reproduced gaps; `J/adminportal/certificate/dto/CreateCertificateRequest.java` and upload validation; create `T/recovery/AdminLifecycleIntegrationTest.java`; extend `scripts/e2e-admin-management.py`.

**Interfaces:** Existing create/settings/URLs/credential/resource/certificate contracts, no invented grant array. Add `active:boolean` to application summary/detail from the entity; retain active-only listing and no reactivation action. Reject SERVER+PKCE with400. Certificate upload ceiling 1 MiB at both Java multipart/request validation and later BFF. Apply scope/status policy from B1 and no-store to sensitive responses.

- [ ] Write failing tests for SERVER+PKCE, duplicate/invalid redirect URLs, partial onboarding interrupted after client create, immutable type/auth method, org revoke/client deactivate invalidation, malformed/oversized PEM, ORGANIZATION missing private key, expired certificate and disabled/expired credential issuance. Assert partial readback reflects actual state and every denied operation leaves SQL unchanged.
- [ ] Run full gate and `python3 scripts/e2e-admin-management.py --phase lifecycle`; establish failing assertions only for reproduced gaps, not new behavior unsupported by the spec.
- [ ] Implement required compatible validation/error handling; preserve existing URI policy including loopback development exceptions. Do not add arbitrary grants or silently change stored settings. Map duplicate and validation errors predictably for BFF; do not make multi-call onboarding appear atomic. Do not implement retries of non-idempotent creation.
- [ ] Run full gate, new lifecycle phase and existing `e2e-certificates.py`/`e2e-protocol-variants.py` against an isolated app/database. Verify real PKCE and confidential issuance plus SQL persistence, not just201 responses.
- [ ] Commit: `fix: validate portal management lifecycles`.

## Task B7: Publish contracts, migration notes and backend acceptance evidence

**Files:** Create `docs/portal/API-CONTRACT.md`, `docs/portal/UPGRADE.md`, `docs/portal/ACCEPTANCE.md`; finalize `scripts/e2e-admin-management.py`. Update existing coverage inventory only from fresh sanitized observations with correct schema.

**Interfaces:** Stable contracts consumed by frontend F1–F6; `.local/e2e-admin-management-report.json` has source commit, scenario, actor class, redacted route, status, SQL outcome and simulation qualification, never raw tokens/credentials/codes.

- [ ] Add acceptance assertions that every implemented route mutation has SQL readback and a denial case; assert old/v2 credential responses never include the synthetic secret, final admin cannot be removed, and legacy consumers receive documented contract changes.
- [ ] Run `./scripts/local-dev.py test`, `python3 scripts/e2e-admin-management.py --phase all`, and relevant existing acceptance runners using isolated fixture ownership. Expected all assertions pass; preserve external-provider limitations in report. Do not restart shared Docker or reset AGSAuth.
- [ ] Record exact new contracts, permission seeds, preflight/upgrade procedure and rollback boundaries. Rollback means stop new UI and redeploy compatible binaries; never drop role tables or restore permissive credential APIs. Reversion of a deployed schema requires a separately tested migration.
- [ ] Run `git diff --check` and review changed file list for private files and unrelated edits; expected clean whitespace and only intended source/test/documentation changes.
- [ ] Commit: `docs: publish portal backend contracts and acceptance evidence`.

## Handoff

Backend plan complete only when the source findings have regression evidence, all new contracts are exercised with real SQL/HTTP and frontend F1's integration assumptions are documented. Then execute the [frontend plan](2026-10-18-identity-portal-frontend.md). Do not report genuine external-provider success from synthetic lab runs.
