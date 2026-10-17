# Identity portal: repository review

Date: 18 October 2026. Reviewed source: `2f90ecd5720bfbf3fa20e87d6e61f1e9a0c23b82`.
Status: read-only application review for a proposed frontend. No new runtime qualification is claimed.

## Conclusion

A useful enterprise portal is feasible, but a frontend alone cannot deliver the requested administration safely. Reuse the Java authentication server and its management APIs; first repair authorization boundaries and secret responses, then add the missing role/membership and portal-context contracts. Build the frontend against those contracts.

The existing `examples/test-client` is an acceptance-test client. It keeps tokens in sessionStorage and has no management screens. It is evidence that selected browser flows work, not the architecture for an administrative product.

## Evidence and limits

This review inspected controllers, DTOs, authorization helpers, resource-server claims, fingerprint/signature filters, client registration conversion, bootstrap, canonical SQL and existing acceptance documentation. It did not start services, call providers, inspect private credentials or rerun old tests. Findings below describe source behavior; exploitability and regression fixes need the live tests in the plans.

Historical qualification is recorded in `docs/recovery/{ENDPOINT-COVERAGE,LOCAL-PROVIDER-LAB,OUTAGE-RECOVERY}.md`: 101 route variants; 69 ordinary positive observations, 17 simulated-provider-only successes, four controlled views and 11 variants without completed positive flows. Those counts are imported scenario observations, not proof of all inputs or current service health. Real providers remain unavailable.

## Existing capability map

Paths below are relative to the configured servlet context, locally `/services`.

| Area | Existing contract | Frontend implication / gap |
|---|---|---|
| Login / MFA / recovery | Java-hosted `/login`, `/mfa/**`, `/forgot-password/**`, `/activate-account/**`; OIDC authorization/token/userinfo/logout | Preserve hosted flows in first delivery; no second password verifier in Next.js |
| Organizations | GET/POST `/api/v1/organizations`; GET/PUT `/{orgGuid}`; PUT primary/secondary contact | Create/read/edit/revoke exist; listing currently platform-only; no arbitrary hard-delete UI |
| Clients | GET/POST `/api/v1/organizations/{orgGuid}/applications`; GET/PUT/DELETE `/{appGuid}` | UI calls these “Clients”; DELETE inactivates; create does not complete URLs/credentials/settings in one operation |
| Client configuration | GET/PUT `/{appGuid}/urls`; PUT `/{appGuid}/settings`; GET/POST `/{appGuid}/endpoints`; DELETE endpoint assignment | Multi-step onboarding and resumable partial configuration required |
| Credentials | GET/POST client `credentials`; PUT/DELETE `credentials/{credGuid}` | Shared-secret and private-key JWT supported; list currently returns values; maximum two active credentials; delete requires disabled/expired credential |
| Certificates | GET/POST org `certificates`; GET/DELETE `/{certId}`; GET `/{certId}/download` | Multipart PEM import; ORGANIZATION requires certificate plus private key; download returns public certificate only |
| Users | GET/POST `/api/v1/users`; GET/DELETE `/{userGuid}`; PUT profile/email/password/username/security-settings/metadata/status; GET metadata | Search/pagination exist; authorization and tenancy require repair; user response lacks organization/group identifiers needed by admin editing |
| Roles | GET org `groups`; GET `groups/{groupGuid}/permissions` | Existing groups/templates are a useful data model; no role write/catalog/assignment management API found |
| Resource catalog | GET/POST `/api/v1/endpoints`; GET/PUT `/{resourceGuid}` | Platform-level resource management and client assignments exist; not arbitrary OAuth scope creation |
| Portal session/context | OAuth userinfo and introspection claims; no purpose-built browser admin context | Need authenticated subject, accessible orgs, effective permissions and capabilities without privileged service credentials |
| Audit | SQL temporal histories and credential audit | No unified actor-attributed administrative audit API; propose a separately gated addition |

## Findings

### R01 — P1: credential reads expose shared-secret material

Confirmed source path: `CredentialController.getCredentials` → `CredentialService.getAll` (lines 142–159) → `SecretRepository.getById` → `SecretRowMapper`/`Secret.fromResult` → `CredentialResponseDto.value` (lines 10–20). The service assigns the decrypted secret to the same field returned by an ordinary credential GET. `idp-admin-app:read` permits this GET; mutation requires update permission. A read-only client viewer can therefore receive secret material through the contract.

Required remediation: metadata-only credential reads; secret returned only in the authenticated creation response, with no-store headers. Keep the internal secret lookup needed for JWT verification. Introduce a versioned public DTO if existing integrations depend on `value`; never silently replace values with asterisks. Test real HTTP read with a viewer and prove absence of the secret and `value` field, then prove confidential JWT authentication still works. Migration risk: API consumers relying on rereading secrets must rotate or use their original secret storage.

### R02 — P1: normal organization administrator mutation helpers cannot succeed

Confirmed logical defect in `security/PermissionsService.java:89–132`: `isAdmin` requires the same platform `switch` action as `isAscensusAdmin`. `canCreateUser` and `canEditOrDeleteUser` first allow platform admins, then depend on `isAdmin` for the organization-admin branch. For a non-platform principal that branch is false, regardless of its organization-admin CRUD claims. The branch also tests non-null groups rather than an explicit administrator policy.

Required remediation: explicit actor/target organization and permission policy; distinguish platform, org admin, developer and viewer. Test real bearer principals for both organizations and every allowed/denied action. Migration risk: new grants must be deliberate; do not “fix” this by treating every grouped user as an administrator.

### R03 — P1: user-read claims do not consistently bind targets to an organization

Confirmed source gap: `UserController.java:336–376` allows `idp-admin-user:read` plus `isSubjectNonAdminUser` for detail/metadata without an organization check. In `AdminOnlyFilterAspect.java:29–58`, that same read claim disables the forced organization filter; the organizationId query remains optional. `Person.GetUsers` in V3 applies tenancy only when its organization argument is supplied. Seeded tenant roles do not currently get this read claim, so this is a latent exposure when adding the requested delegated user management, not a reproduced exploit against every current role. Machine `url-permissions` deliberately provides an alternate path and needs a separately documented policy.

Required remediation: scope every human list/detail/metadata path on the server; reject foreign organization filters; constrain machine access to explicitly registered application organizations/targets. Test foreign GUIDs, omitted and forged organization filters, duplicate usernames/brands and pagination totals. A frontend organization selector cannot repair this boundary.

### R04 — P1 qualification gate: cookie authentication and API CSRF policy must be separated

`SpringSecurityConfig.java:90–139` enables form sessions and opaque bearer auth in the same chain, authenticates `/api/**`, and ignores CSRF there. Method permissions generally expect bearer principals; `AdminOnlyFilterAspect` casts directly to `BearerTokenAuthentication`. This is unsuitable as the browser session API for a new portal without an explicit contract. A complete cookie-only mutation exploit is not established by this review.

Required remediation: portal BFF accepts its own CSRF-protected cookie; Java management APIs require bearer authentication consistently. Preserve Java-hosted form CSRF. Test cookie-only, missing/invalid bearer, cross-origin, expired and PRE_AUTH cases, including controlled 401/403 instead of a cast failure. Do not globally disable CSRF to integrate the UI.

### R05 — P2: role and membership writes are absent

`OrganizationController.java:153–178` exposes only group/permission reads. `Partner.OrganizationGroup`, `Partner.OrganizationGroupPermission`, template tables and `Person.ProfileGroup` exist; `CreateUserDto.groupGuid` can assign a group when creating a user. There is no reviewed API for role CRUD, permission catalog, or changing an existing user's group. The physical model can contain multiple profile groups, while public claims/DTOs select a single group. Define cardinality and privilege-delegation rules before writing screens.

Required remediation: additive APIs/migrations, built-in role protections, transactional role replacement, grant ceilings, last-platform-admin protection and real SQL race tests. Do not implement roles as frontend-only labels or put role keys into arbitrary metadata.

### R06 — P2: client options are constrained and onboarding is partial

`ApplicationService.java:230–321` derives grants from `ApplicationTypeEnum`: SERVER gets client_credentials, WEB/MOBILE get authorization_code and refresh_token registration. `AuthFlowEnum` supports PKCE, CLIENT_SECRET_JWT and PRIVATE_KEY_JWT. Authorized scopes come from a shared constant. Public clients do not currently receive refresh tokens. `CreateApplicationDto` has no configurable grant list; edits cannot change type/auth method. URLs, token settings, credentials and resource assignments use subsequent calls. Do not advertise device grant or arbitrary scopes; don't present a newly created database row as an OAuth-ready client.

Required remediation: compatible choices only, explicit onboarding state, retry/readback after uncertain writes and real issuance tests. Deactivation must show its effect; replacement is the supported way to change an immutable client type/auth method unless separately designed.

### R07 — P2: nonstandard integration details require an early BFF proof

API calls require `x-request-datetime`; optional HMAC enforcement signs exact bytes with per-token signing material. Fingerprints include timestamp offset, browser language, user agent and optionally referer host (`ClientFingerprintValidator.java:26–54`). Java-hosted self-service uses SSO cookies, CSRF, metadata and signed forms. The sample does top-level navigation for these forms. A generic fetch proxy or an unrelated machine-admin token will fail or lose user attribution.

Required remediation: prove the real browser → Next.js → Java path with fingerprint/signature enforcement on before building management pages. Preserve a captured browser context server-side and validate all configured destinations. Never trust client-supplied arbitrary proxy headers/URLs. Keep tokens out of browser storage and callback error telemetry.

### R08 — P2: errors, list shapes and expired-credential dates need an adapter

Existing controllers return a mix of arrays, user paging records, plain strings and empty errors. `CredentialResponseDto.isExpired` parses only `yyyy-MM-dd` in the default timezone and compares against now; malformed non-null dates can reach a null comparison. Client and organization lists are unpaginated; user lists default to 10, SQL accepts 1–1000. User list accepts an optional GET body, which browser fetch cannot supply.

Required remediation: normalize BFF errors and page envelopes; never send GET bodies; initially filter only the complete small unpaginated collections, with explicit limits/scale qualification. Use the existing Instant-based credential service logic for metadata expiry. Test same-day UTC expiry and invalid legacy dates. No unsupported backend sorting or pagination controls.

## Proposed design decisions and tradeoffs

1. Next.js App Router + TypeScript, one application with account and admin route areas; Java remains the identity/authorization authority. Compared with static React/Vite this adds a Node service, but gives a contained server-side token boundary.
2. MUI community components and a custom enterprise theme; no paid template or grid dependency. A bespoke headless design has more flexibility but raises delivery and accessibility work. This is a recommendation for approval, not an existing user-selected library.
3. Preserve Java-hosted login/MFA/recovery/profile/password pages initially. Rebuilding their workflows into JSON APIs is a separate project; visual consistency can follow after security parity is proven.
4. Fix the blocking API contracts before portal management screens. Ship in vertical slices rather than a dashboard of disabled controls.
5. Admin audit is proposed as a later foundation extension. Temporal row history alone does not prove who performed an operation.

## References checked for the proposed stack

- [Next.js authentication](https://nextjs.org/docs/app/guides/authentication): authorize at server entry points and data access.
- [Next.js backend for frontend](https://nextjs.org/docs/app/guides/backend-for-frontend): server route integration; still requires application authorization.
- [MUI Next.js integration](https://mui.com/material-ui/integrations/nextjs/): official App Router/SSR cache adapter.
- [Node.js releases](https://nodejs.org/en/about/previous-releases): Node 24 LTS is the proposed runtime line. Pin supported security patches and matching Next/React/MUI versions at implementation time.

This review is not a replacement for a complete security assessment, dependency scan or external-provider qualification.
