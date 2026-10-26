# Portal backend API contract

Approved design/plans: chat approval **2026-10-21**, following the original 2026-10-18 draft. This is the implemented B1–B7 frontend integration contract; release qualification is recorded separately in [ACCEPTANCE.md](ACCEPTANCE.md). Java remains the authorization authority.

## Transport and identity

All paths below are relative to the configured issuer context, normally `/services`. Management requests carry the current human's `Authorization: Bearer …`, `x-request-datetime` with an offset, stable `User-Agent`/`Accept-Language` fingerprint context, an optional safe `x-request-id`, and the exact-body `x-signature` required by the existing signature policy. HMAC is Base64 HmacSHA256 over raw UTF-8 body bytes (empty bytes for bodyless calls), using the issued `signing_key` token extension decoded from Base64. Preserve signed response bytes when verifying their HMAC. Multipart certificate uploads retain the existing signature exception. OAuth token response certificate signatures use a different organization certificate; never confuse them with API HMAC or ID-token JWKS verification.

Cookie-only sessions, pre-authentication/MFA sessions and unauthenticated requests cannot administer APIs. The portal must not substitute a machine token. Existing machine URL permissions are a separate legacy integration path; none conveys portal context or human role authority. Authorization reloads active memberships and permissions from SQL. Removed permissions affect the next protected request and queued writes recheck after acquiring the shared transaction lock. Selected organization is request scope, never a replacement actor identity.

Public role, actor, organization, assignment and user identifiers are GUIDs. User/member GUID and login GUID identify different records. An application GUID is different from OAuth `clientId`.

## Context and errors

`GET /api/v1/portal/context` → 200:

```json
{"subject":{"userId":"UUID","loginId":"UUID","username":"name","organizationId":"UUID"},"organizations":[{"id":"UUID","name":"Organization"}],"permissions":["idp-admin-app:read"],"capabilities":{"roleWrite":false,"userWrite":false,"resourceWrite":false,"providerMode":"unconfigured"}}
```

An account-only human can receive context with empty permissions. Organizations are accessible memberships for tenants, selectable organizations for platform actors. `providerMode` is configuration (`unconfigured|simulated|external`), never evidence of provider success. Capabilities are navigation hints; each action still needs its explicit permission. `GET /api/v1/portal/permissions` → 200 `[{key,description,platformOnly}]`, platform actor plus `idp-admin-permission:read` only.

`subject.loginId` is the exact authenticated credential, not an arbitrary credential belonging to the profile. Human introspection supplies reserved top-level `login_guid` from the exact SQL username/source row; custom claims cannot overwrite it. Context requires that trusted claim and correlates its SQL credential with the verified profile, organization, subject and active identity. Mutable metadata is never a fallback credential authority; token metadata reserves `uuid_member_id` and `uuid_login_id` for canonical SQL identity. Multiple distinct usernames under one profile/source remain supported. A biometric proof carrying only profile/source must resolve one active, unlocked eligible credential or be denied; general profile management lookups retain their profile-level semantics.

New context/catalog, role/assignment and v2 credential errors use `{code,message,requestId,retryable}` with `Cache-Control: no-store`; `fieldErrors` is reserved but currently absent. Request IDs are safe caller IDs or generated UUIDs. No SQL/upstream exception, secret or stack trace is returned. Legacy v1 APIs retain endpoint-specific empty/text/JSON errors and need bounded BFF normalization.

| Status | Frontend behavior |
|---|---|
| 400 | Show safe validation feedback; retain nonsecret form state |
| 401 | End the invalid portal session and offer sign-in; revoked/ambiguous active actor memberships also invalidate bearer introspection |
| 403 | Access denied; refresh context, do not retry the mutation |
| 404 | Missing/foreign scoped target; do not disclose existence |
| 409 | Refetch/review duplicate, stale revision, assigned role, ambiguous target memberships, reserved legacy role or final-admin conflict |
| 413 | `UPLOAD_TOO_LARGE`, no-store; show the 1 MiB inclusive file ceiling |
| 503 | Sanitized dependency/lock failure; lock timeout is retryable with `Retry-After: 5` |

A 401 caused by bad SQL identity relationships is not repaired merely by logging in again: an operator must deliberately correct the relationship. Target membership ambiguity is 409 `MEMBERSHIP_REQUIRES_REVIEW`. Other stable conflicts include `ROLE_ASSIGNED`, `ROLE_REQUIRES_REVIEW`, `ROLE_CONFLICT`, `PROFILE_EXISTS`, `LAST_PLATFORM_ADMIN`. Read structured `memberCount` after `ROLE_ASSIGNED`; never parse the message's count.

## Roles and assignment

Base `G=/api/v1/organizations/{org}/groups`:

| Method/path | Request | Success |
|---|---|---|
| GET `G` | None | 200 legacy list `{id,name,description,status,revision,memberCount,readOnly}[]` |
| GET `G/{group}` | None | 200 RoleDto below |
| GET `G/{group}/permissions` | None | 200 existing permission list |
| POST `G` | `{name,description,permissionKeys:string[]}` | 201 RoleDto |
| PUT `G/{group}` | Same plus integer `revision` | 200 RoleDto; revision increments |
| DELETE `G/{group}` | None | 204 soft deactivation |
| PUT `/api/v1/organizations/{org}/users/{user}/group` | `{groupId:"UUID"}` or `{groupId:null}` | 204 replace/unassign one same-org role |

RoleDto is `{id:UUID,name:string,description:string|null,status:"Active"|"Inactive",permissionKeys:string[],revision:int,memberCount:int,readOnly:boolean}`. Legacy list `status` remains `ACTIVE|INACTIVE`; normalize explicitly. `readOnly` is additive in **both** contracts: true if `IsBuiltin`, non-null `GroupTemplateId`, or any active same-org grant classified platform-reserved by `ManagementAccessPolicy.platformReserved`. Detail and legacy reads use `RoleRepository.readOnly`; mutation enforcement consumes the same protection. Names and permission-set guesses are never editability metadata. False describes definition protection only; permissions, inactive status, stale revision and assigned-member constraints still apply. Inactive custom definitions are not thereby editable.

Definition writes require a platform actor and the matching `idp-admin-group:create|update|delete` action. Tenant reads require `idp-admin-group:read`. Names are trimmed, 1–100 characters, control-character-free, case-insensitively unique per org including inactive names; descriptions ≤1024. Keys are a nonempty, unique list of at most100 catalog keys, excluding platform-only grants and bounded by the actor's effective permissions. Assigned roles cannot deactivate. Built-ins return409; protected legacy reserved roles return409 `ROLE_REQUIRES_REVIEW`.

**Definition protection and new-assignment eligibility are distinct.** An immutable tenant template may be assigned if its grants satisfy delegation. New assignments carrying reserved platform grants require the designated **platform organization1 / template1** role and a platform actor; neither `readOnly:true`, `IsBuiltin` alone nor an imported switch grant establishes that eligibility. Assignment requires `idp-admin-group:assign`, an active same-org role/target, permission ceilings on current/next grants, no self elevation and no tenant delegation of reserved grants. Null removes the assignment and leaves the user active. Legacy multiple-role/active-organization ambiguity returns409 without collapsing records. The final effective platform administrator and platform organization remain active under concurrent changes.

## Clients and credentials

Base `A=/api/v1/organizations/{org}/applications`:

| Method/path | Request/behavior | Success |
|---|---|---|
| POST `A` | `name,description,type,authMethod`; supported PKCE or confidential pairing | 201 summary |
| GET `A` | Active-only list | 200 summaries |
| GET `A/{app}` | Scoped read including inactive client | 200 detail |
| PUT `A/{app}` | Mutable name/description/username settings only | 200 |
| DELETE `A/{app}` | Deactivate; existing bearer/new issuance invalidated | 200 |
| GET/PUT `A/{app}/urls` | Write `{applicationUrl,returnUrls,logoutUrls}`; read `{applicationUrl,redirectUrls,logoutRedirectUrls}` | 200 |
| PUT `A/{app}/settings` | Seconds: `authCodeTimeToLive,accessTokenTimeToLive,refreshTokenTimeToLive,deviceCodeTimeToLive,maxRequestTransitTime,reuseRefreshTokens` | 200 |
| GET/POST `A/{app}/endpoints` | Assign body `{resourceId:resourceLibraryGUID}` | 200 list / 201 |
| DELETE `A/{app}/endpoints/{resourceLibraryGUID}` | **Catalog GUID**, not assignment GUID; removes owned assignment row | 204 |
| GET `A/{app}/credentials` | Legacy safe metadata | 200 |
| GET `/api/v2/organizations/{org}/applications/{app}/credentials` | Preferred metadata contract below | 200 |
| POST `A/{app}/credentials` | `name,type,algorithm,expireOn?` and appropriate `certificateId` for private-key credentials | 201 creation response |
| PUT `A/{app}/credentials/{credential}` | `{status:"Active"|"Disabled"}` | 200 |
| DELETE `A/{app}/credentials/{credential}` | Disable before deletion | 204; active deletion400 |

Client summary adds authoritative `active:boolean`; existing `authFlow` remains. Detail adds `active:boolean` and authoritative nullable `reuseRefreshTokens` from persisted token settings (V16), and retains `authenticationMethod`, token lifetime fields, username/MFA settings. Both preserve existing fields. SERVER+PKCE is400. Type/auth method are immutable; the update DTO cannot change them. Inactive clients are absent from active lists but detail reports `active:false`; no reactivation operation exists. Exact duplicate redirect/logout entries fail400 before writes; existing URI validation and loopback exceptions remain. Creation/configuration consists of separate calls, with no transaction across onboarding. After timeout, refetch before retrying; never automatically create another client or assume a lost one-time secret can be read back.

v2 credential item: `{id,name,type:"SHARED_SECRET"|"PRIVATE_KEY",algorithm,status,expirationDate,isExpired}`. Legacy item is the same **without `type`**. Neither GET returns `value`, `secretValue`, key material or passwords. This removal from legacy GET is a deliberate breaking security change. Shared-secret creation alone returns `{id,secretValue}` with no-store. Private-key creation returns its existing ID contract. Maximum two enabled credentials; rotation creates/tests a second, disables old, then deletes old. No secret decrypt is performed in public metadata or credential-status readback: the scoped secret projection selects only `SecretId,ExpireOn` for that org/app.

Effective shared-secret expiry is the earliest non-null of credential and linked-secret bounds, enforced identically in metadata and internal confidential issuance. Offset strings are parsed as instants; naive SQL datetime2/legacy strings mean UTC; date-only means UTC midnight. Expiry is inclusive (`now >= expiry` is expired), never calendar-day-only. SQL precision remains datetime2(0) seconds; new writes explicitly bind UTC. Malformed imported model values fail closed without echoing their content. SQL datetime2 cannot store malformed dates, so malformed-value tests are helper/model evidence. Both-null metadata displays `expirationDate:""` and `isExpired:false`; internal issuance with both bounds missing remains401. **Null display is not proof that a key is usable.** Existing historical naive dates are preserved; see [UPGRADE.md](UPGRADE.md).

The inherited application-resource list returns numeric `resourceId`/`resourceLibraryId` for legacy consumers. Its existing **`id` is the catalog resource GUID**, as selected by `Resource.getAllResourcesByAppId` (`ResourceLibraryGuid`) and mapped by `ApplicationResource.fromResultSet`; it is not the assignment row GUID. Real HTTP plus distinct SQL row GUIDs qualify this wire meaning. The frontend adapter names it `catalogResourceId`, strips both numeric fields, and uses that catalog GUID for deletion. The SQL assignment row GUID remains a separate identity and is not exposed by this list. Earlier documentation claiming assignment-GUID semantics was incorrect; no wire meaning changed.

### Portal client adapter boundary

The portal uses explicit session/Origin/CSRF-guarded routes and the current human bearer; it never exposes a generic Java proxy. Identity onboarding stores only nonsecret attempt/identity/application correlation under that session for30minutes. Subsequent configuration stays authoritative in Java. A fresh read plus explicit user confirmation is required before retrying an uncertain create; elapsed time never automatically issues another POST. Lost creation secrets require rotation, not a GET recovery route.

The sole browser secret-display exception is one authenticated no-store shared-secret creation response and a client-memory acknowledgement dialog. It never enters SSR/RSC props, URLs, storage, query caches or React Hook Form state. Copy is an explicit clipboard action; dismissal/navigation removes application state without promising browser/clipboard zeroization. OAuth tokens and management signing keys have no such display exception. Current F3 form and issuance qualifications, including their limits and the historical test-diagnostic incident, are recorded in [ACCEPTANCE.md](ACCEPTANCE.md).

## Organizations, users, certificates and resource catalog

| Routes | Permission/contract boundary |
|---|---|
| `/api/v1/organizations` POST201 / GET200 | Platform create/list actions; context supplies tenant memberships |
| `/api/v1/organizations/{org}` GET200 / PUT200 | Authorized own read/details update; platform scope supported; status changes additionally require platform deactivate authority; platform org cannot revoke |
| Same base `/primary-contact`, `/secondary-contact` PUT200 | `{name,email,phoneNumber}`, own org update permission |
| `/api/v1/users` GET200 | `resultsPerPage` (1–1000), `offset`, `type`, `search`, `organizationId`, `status`; `{offset,resultsPerPage,results,users}` totals and rows scoped identically even when org omitted |
| `/api/v1/users/{user}` GET200 / DELETE204 | Target-scope user/admin action and delegation ceiling; delete is lifecycle deactivation, not promised erasure |
| `/api/v1/users` POST201; `/profile` POST201 | Local-create DTO / legacy account-sync envelope respectively; creation authority checked before provider work |
| `/api/v1/users/{user}/profile`, `/email` PUT200 | Existing brand/intent/metadata DTO and provider mode; target checks before external work |
| Same `/password`, `/username` PUT204 | Existing validation/history/sync policies; Base64 password is transport convention over TLS, not encryption |
| Same `/security-settings`, `/metadata` PUT200 | `{twoFactorEnabled:boolean}` / `{metaData:object}`; matching explicit user/admin update authority |
| Same `/status` PUT204 | `{status:"Active"|"Inactive"}`, lifecycle/final-admin guards |
| Same `/metadata` GET200 | Authorized metadata read |
| `/api/v1/organizations/{org}/certificates` POST201 / GET200; `/{certificate}` GET200 / DELETE204; `/{certificate}/download` GET200 | Type-specific create/delete authority; ORGANIZATION writes require effective platform authority and the matching org-cert action; PUBLIC_KEY writes require the matching cred-cert action; public metadata/download only |
| `/api/v1/endpoints` POST201 / GET200; `/{resource}` GET200 / PUT200 | Platform plus explicit resource action, including read; no catalog DELETE API |

User lists include only active organization memberships and eligible active same-organization roles. Preserved inactive/foreign role history does not add rows or change user-class filtering/totals. Profiles reported by `Person.ManagementMembershipIssues` are omitted from lists, including totals, until deliberately reviewed; list reads never repair or collapse ambiguous memberships.

User list/detail add `organizationId:UUID`, explicit nullable `groupId:UUID|null`, and `syncMode:"local"|"sync"|"async"` from SQL sync flags and deployment mode. Keep `id`, `memberId`, `loginId`, `userName`, status and existing profile fields distinct. An assignment change refetches context and affected user/role records. Provider-backed mutation/delivery success needs separate configured qualification; a validated boundary error does not prove a successful provider flow. `/api/v1/users/validate-password` is an existing echo/validation mapping, not password verification or a management-state mutation; the portal must not offer it as password validation.

Certificate multipart fields: `name,password,type:ORGANIZATION|PUBLIC_KEY,certificate` file. File ceiling **1,048,576 bytes inclusive**, request budget **2,097,152 bytes**. PEM validation checks current X509 validity/type; ORGANIZATION requires matching private material; public-key import may use the public certificate. DELETE resolves the stored certificate type within the authorized organization before choosing its action permission. Create/delete acquire the shared management transaction lock before reloading current actor grants. Oversize413 is PortalProblem/no-store; legacy malformed/expired PEM failures remain400. Download contains public certificate only, with sanitized filename; no private download/preview. UI must enforce the same file ceiling. No affected-credential count is promised without a separate dependency contract.

Legacy adapters additionally retain these exact DTO spellings:

- Local user creation body: required `firstName,lastName,username,email,password,phoneNumber,orgGuid,branding`; optional `groupGuid,metaData,memberId,loginId,secondaryPhoneNumber,syncFlag`. `password` is UTF-8 Base64 transport text and the server applies its existing password policy. Do not send internal `orgId`. Prefer fresh server-assigned member/login IDs; supplying an existing member ID conflicts. Creation returns201 `{id:UUID}`; no delivery success is implied. Legacy registered-machine provisioning retains its URL POST grant and organization scope, but cannot create users with any platform-reserved human authority, including the designated platform role.
- Profile update uses `branding,intent,metadata,firstName,lastName,title,middleInitial,phoneNumber,suffix,secondaryPhoneNumber` as supported; email/password/username bodies use `branding,intent,metadata` plus their named field. Provider/brand validation is authoritative. `/users/profile` is the existing schema-keyed account-sync envelope, not the portal local-create form; it remains a separately qualified integration.
- User search defaults to `resultsPerPage:10,offset:0`; the legacy maximum is1000 (including the frontend 100-row option). The SQL `search` parameter is separately limited to50 characters. Organization response fields are `id,name,status,description,primaryContact,secondaryContact`, with `ACTIVE|INACTIVE`; contacts contain `name,email,phoneNumber`.
- Application creation additionally supports `usernameType,allowForgotUsername,forgotUsernameSettings,jwkSetUrl` through its existing validator. URL GET also retains `appGuid,orgGuid`; client summary uses `authFlow`, detail uses `authenticationMethod`. Do not map these to a user-selectable grant array.
- Certificate list retains `id,name,type,status,validTO,isExpired`; detail additionally returns `fingerprint,thumbprint,certificate` (public DER encoded as Base64). `isExpired` compares the full UTC validity timestamp using the injected server clock, identically for list and detail. Naive SQL timestamps mean UTC. The X509 boundary accepts `notAfter` itself and expires after it (millisecond precision, matching `X509Certificate.checkValidity(Date)`); this differs from the inclusive credential expiry rule above. Explicit EXPIRED status, missing or malformed validity fail closed. The flag alone does not establish usability: inactive/suspended status, notBefore, linked credentials and operation checks still apply.

Sources: controller/DTO types under `src/main/java/com/chellavignesh/authserver/adminportal`, `ManagementAccessPolicy`, `ManagementLifecycleGuard`, `RoleRepository`, and `CredentialExpiration`. Migration/grant details are in [UPGRADE.md](UPGRADE.md); actual observed coverage and remaining qualification are in [ACCEPTANCE.md](ACCEPTANCE.md).

## Additive issuer-hosted local account forms

The approved [hosted account addendum](../superpowers/specs/2026-10-24-hosted-account-forms-addendum.md) adds these exact issuer routes. Legacy `/user/change-*`, `/oauth2/sso`, bearer/HMAC APIs, CSRF exclusions, and Strict SSO cookie policy retain their existing contracts. The portal never proxies account passwords.

| Route | Contract |
|---|---|
| GET `/portal/account-action` | Anonymous transition with exact `action=profile|password`, public `clientId`, stable credential UUID `expectedLoginId`, `returnTo`, and UTC `x-request-datetime`. Manual issuer-origin Continue; no mutation or grant. |
| GET `/portal/account/profile`, `/portal/account/password` | Current active SSO session, completed active human grant, current local SQL identity, exact expected login/client/callback, issuer Referer, and original full UA/language/UTC fingerprint. Issues transient CSRF and one-use form nonce. |
| POST same two form paths | Exact issuer Origin plus the GET authority, Spring CSRF and bound nonce. Form-urlencoded UTF-8 only; 64 KiB request ceiling and individual field bounds, duplicate/unknown fields rejected. Safe 400/403/413/415/503 errors do not echo rejected form data. |

Only active WEB registrations with a clean registered `/auth/callback` authorize fixed same-origin `/account` or `/account/security` returns. The callback origin comes from registration, issuer origin from configured `AuthorizationServerSettings`; Host/forwarded/query origins provide no authority. Portal A versus issuer B rejects before personal details. A shared portal client may belong to the platform organization while its user belongs to an active tenant organization: client ownership does not constrain self-service to that organization. Roleless local users need no management permission. IDP source type and explicitly false credential/profile sync flags are required on GET and under the mutation lock on POST; synchronized and unknown modes fail closed. Recovery retains only the existing `/forgot-password` contract, with no invented return/delivery guarantee.

A nonce is random 256-bit form proof, expires after at most five minutes and has one outstanding slot per browser HttpSession/action. A fresh GET replaces only that action's previous slot. Redis stores hashes/binding/expiry, never account form data. Binding covers browser, current auth session, stable login/member/source/client/callback/action/return. Compare-and-consume permits exactly one concurrent winner and preserves a valid slot after a wrong proof. Redis operations have a two-second total bound and fail closed without automatic write retries. OAuth access/refresh/ID tokens and management signing keys never enter these forms, browser JSON/storage/URLs or logs.

Profile update reuses existing validated local profile semantics. A successful POST uses a bounded standard-Base64 `p` navigation continuation to the registered callback; the portal accepts it only with its existing valid opaque session and no mixed OAuth fields. An unsigned continuation is navigation information, never proof that a save succeeded. Cancel performs no write.

Password change verifies current password, confirmation, existing policy/semantic rules and history, then V15 writes and audits exactly the stable login/member/source credential. Password mutation and every matching human login's SQL auth session/token/code/PKCE/SSO/Spring grant revocation commit atomically. Other branding and machine grants survive. Issuer cookie/HttpSession are cleared after commit; BFF invalidates its own opaque Redis session only after confirmed Java401. Reauthentication is required. A transaction-owned per-credential SQL lock fences queued old authenticated snapshots and human code/refresh saves; management mutation lock precedes credential lock, while human issuance takes credential only. No global machine-grant serialization or auth/KDF change is introduced. Any password-version upgrade failure aborts the guarded login with controlled503 before session creation; an upgrade that rolls back the physical SQL transaction is never swallowed or retried.

Administration checks current context on focus/visibility/pageshow. If that context changes, stale server content is concealed while the same explicit URL is reloaded through its server authorization checks. Losing only organization-switch authority denies an already-selected foreign scope while preserving valid home reads; losing one read grant removes that row while other reads remain. The existing `idp-admin-admin:read` grant exposes the Administrators read entry without implying ordinary-user read or mutation authority.
