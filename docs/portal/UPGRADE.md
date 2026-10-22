# Portal backend upgrade and rollback

Approved implementation scope: chat **2026-10-21**. This runbook preserves existing records, IDs, hashing/signing keys, clients, accounts and migration history. It does not authorize a production rollout. See [API-CONTRACT.md](API-CONTRACT.md) for tightened legacy behavior and [ACCEPTANCE.md](ACCEPTANCE.md) for exactly what was exercised.

## Compatibility changes to prepare

- Legacy credential GET no longer returns secret values. Migrate consumers to v2 metadata and retain a creation secret only in the approved secret store. There is no read-secret fallback. Both contracts are no-store; ordinary metadata/status reads never decrypt a secret.
- Tenant filters are enforced server-side, including omitted organization filters, rows and totals. Raw machine URL grants do not replace human portal permissions. Resource catalog reads/writes and role-definition writes require platform authority plus explicit actions.
- Revoked or ambiguous active actor membership invalidates the bearer at introspection401, including context and raw-evaluator routes. Target ambiguity yields409. Refreshing/relogging does not correct inconsistent SQL relationships; deliberately repair them using reviewed operational procedures.
- User DTOs add `organizationId`, nullable `groupId`, `syncMode`; role DTO/list add `revision`, `memberCount`, authoritative `readOnly`; application summary/detail add `active:boolean`. Legacy group status casing and list/detail auth-method names differ, as documented in the API contract. New assignment requires the designated organization1/template1 role for reserved authority; readOnly is definition protection, not assignment eligibility.
- Inactive client detail can be read for readback but list remains active-only and reactivation is unsupported. Client URLs reject exact duplicates. Expired/malformed certificate imports fail400; files over1MiB fail413 with a2MiB multipart request budget.
- Effective confidential secret expiry is the earliest non-null of linked credential/secret expiries, inclusive at the UTC instant. Missing-both metadata display is not proof of usable authentication; issuance remains fail-closed. Existing stored second precision is unchanged.

## Before deployment

1. Record the deployed binary/version, database migration history and configuration references. Take and restore-test an approved database backup in isolation. Preserve all signing/encryption/password-hasher stores and their existing secret-manager references; never print keys/passwords into evidence. A database-only restore without the matching key material is not a valid recovery plan.
2. Inventory dependent clients and their secret-reading behavior; prepare consumer changes before turning on the UI. Identify active platform administrators with usable, unlocked credentials, unexpired profiles, an active organization membership and current platform grants. Arrange a tested independent recovery operator; do not disable the final effective administrator.
3. On an isolated copy of the existing database, run Flyway validation against the unchanged V1–V11 checksums, then the additive V12/V13 migrations. Never use `clean`, schema reset, checksum repair or baseline to hide drift. B7 adds **no V14**; all V1–V13 files remain untouched.
4. Review existing-data ambiguities before enabling writes. Before V12, use read-only checks below; after migration use both issue views. Capture only safe counts/codes in public evidence, keeping identifiers/private details in approved operator records.

```sql
-- These work before V12 and never modify existing data.
SELECT OrganizationId,LOWER(LTRIM(RTRIM(GroupName))) COLLATE Latin1_General_100_CI_AS AS NormalizedName,COUNT(*) AS RoleCount
FROM Partner.OrganizationGroup
GROUP BY OrganizationId,LOWER(LTRIM(RTRIM(GroupName))) COLLATE Latin1_General_100_CI_AS
HAVING COUNT(*)>1;
SELECT ProfileId,COUNT(*) AS ActiveOrganizationRows
FROM Person.ProfileOrganization WHERE Status=1
GROUP BY ProfileId HAVING COUNT(*)>1;
SELECT pg.ProfileId,g.OrganizationId,COUNT(*) AS GroupRows
FROM Person.ProfileGroup pg JOIN Partner.OrganizationGroup g
ON g.OrganizationGroupId=pg.OrganizationGroupId
GROUP BY pg.ProfileId,g.OrganizationId HAVING COUNT(*)>1;
SELECT pg.ProfileId,g.OrganizationId
FROM Person.ProfileGroup pg JOIN Partner.OrganizationGroup g
ON g.OrganizationGroupId=pg.OrganizationGroupId
WHERE g.Status=1 AND NOT EXISTS (
  SELECT 1 FROM Person.ProfileOrganization po
  WHERE po.ProfileId=pg.ProfileId AND po.OrganizationId=g.OrganizationId AND po.Status=1);

-- Authoritative post-migration reports:
SELECT OrganizationId,Code,Detail,ProfileId
FROM Partner.RolePreflightIssues ORDER BY OrganizationId,Code,ProfileId;
SELECT ProfileId,Code FROM Person.ManagementMembershipIssues ORDER BY ProfileId,Code;
SELECT COUNT(DISTINCT ProfileId) AS EffectivePlatformAdministrators
FROM Person.EffectivePlatformAdministrator;
```

Duplicate names include inactive roles. The V12 report also flags broader legacy group ambiguity; V13 supplies precise active-identity eligibility. No migration silently renames roles, merges groups, removes membership history or rewrites profiles. Affected mutations fail409; ineligible actors fail401. Review intended ownership and resolve only with a separately reviewed, backed-up corrective change. Do not convert ambiguous memberships into a default role.

## Historical UTC provenance audit

New credential/secret writes explicitly bind `Date` as UTC `LocalDateTime`; reads interpret datetime2 as UTC. **Existing datetime2 rows are preserved.** Historical writers running a non-UTC JVM may have stored local wall time instead of the intended UTC instant. A naive SQL datetime2 value contains no timezone provenance, so neither a date comparison nor this upgrade can reconstruct intent.

Audit writer versions/JVM timezones, request/configuration records and certificate NotAfter values where independently available. Record which active credentials have confirmed UTC provenance and which remain uncertain. Deliberately rotate uncertain active secrets/credentials/certificates with owner coordination and validate issuance before disabling old material. Do not blindly add/subtract the current timezone offset, bulk shift old expiries or claim automatic repair. Historical ambiguity is an operator acceptance prerequisite, not an implemented migration feature.

## Additive schema and deliberate grants

`V12__Protected_role_catalog.sql` adds `Revision` (default1), `IsBuiltin` (default0; backfilled for template groups), `dbo.PermissionCatalog`, `Partner.RolePreflightIssues`, compatible live-member/revision procedure projections and protected new-organization template creation. Existing IDs, GUIDs and permission rows remain. `V13__Safe_role_assignment.sql` adds membership/effective-identity/effective-platform-admin views, nonunique supporting indexes, protected assignment and compatible active-relationship projections.

New seeded actions are exact and limited:

| Target | Added actions |
|---|---|
| Administrators template1 and its existing groups in every org | `idp-admin-user:create`, `:read`, `:update`, `:delete`; `idp-admin-group:assign` |
| Platform-only template entries (GroupId NULL), template1 groups in organization1 only | `idp-admin-group:create`, `:update`, `:delete`; `idp-admin-permission:read` |
| Developers/Viewers | No blanket additions |

Catalog classification has37 explicit keys, listed below. An action's presence in the catalog is not proof it has a route (for example resource delete) or that every actor holds it. Existing grants, including the historical duplicate builtin credential-certificate create grant, are preserved; migrations/tests do not silently deduplicate them.

| Permission key | Platform only |
|---|---|
| `idp-admin-admin:create` | no |
| `idp-admin-admin:delete` | no |
| `idp-admin-admin:read` | no |
| `idp-admin-admin:update` | no |
| `idp-admin-app:assign-resource` | yes |
| `idp-admin-app:create` | no |
| `idp-admin-app:delete` | no |
| `idp-admin-app:read` | no |
| `idp-admin-app:update` | no |
| `idp-admin-cred-cert:create` | no |
| `idp-admin-cred-cert:delete` | no |
| `idp-admin-cred-cert:read` | no |
| `idp-admin-cred-cert:update` | no |
| `idp-admin-group:assign` | no |
| `idp-admin-group:create` | yes |
| `idp-admin-group:delete` | yes |
| `idp-admin-group:read` | no |
| `idp-admin-group:update` | yes |
| `idp-admin-org-cert:create` | yes |
| `idp-admin-org-cert:delete` | yes |
| `idp-admin-org-cert:read` | no |
| `idp-admin-org-cert:update` | yes |
| `idp-admin-org:create` | yes |
| `idp-admin-org:deactivate` | yes |
| `idp-admin-org:list` | yes |
| `idp-admin-org:read` | no |
| `idp-admin-org:switch` | yes |
| `idp-admin-org:update` | no |
| `idp-admin-permission:read` | yes |
| `idp-admin-resource:create` | yes |
| `idp-admin-resource:delete` | yes |
| `idp-admin-resource:read` | yes |
| `idp-admin-resource:update` | yes |
| `idp-admin-user:create` | no |
| `idp-admin-user:delete` | no |
| `idp-admin-user:read` | no |
| `idp-admin-user:update` | no |

## Rollout verification and recovery boundary

Deploy first to the validated isolated upgrade copy, then use the site's approved rollout procedure. Confirm Flyway validation/history, issue counts and effective-admin count; exercise permitted and denied tenant/platform reads and mutations with disposable operator-owned fixtures. Check context, readOnly metadata, UTC expiry, two-key rotation, actual PKCE/confidential issuance, certificate public download and retained-token rejection after deactivation. Preserve signature, fingerprint, referer, CSRF and proof policies throughout. Provider/browser/production checks remain separate from the local backend evidence.

Role/user/assignment mutations share a transaction-owned SQL application lock `authserver:identity-management:v1`, Exclusive. Order is transaction → lock → live actor/organization → role → profile/membership/invariant → write. Acquisition timeout is5seconds, mapped to sanitized retryable503/Retry-After5. Do not replace it with a JVM mutex or independently scoped lock. No automatic retry of management writes is allowed in the portal.

If the UI misbehaves, stop new portal traffic and redeploy a **compatible hardened binary** supporting the deployed schema and safe credential/tenant contracts. Verify that binary against V12/V13 before selecting it as a rollback target. Preserve role tables, assignments, revisions, keys and all existing records. Never restore permissive secret GET behavior or bypass actor/membership enforcement to make old consumers work. Schema reversion requires a separately tested migration; dropping role tables or restoring an old whole database over newer data is not the default rollback. Historical key rotation and deliberate membership corrections have their own recovery plans.

The acceptance harness uses UUID-owned SQL databases, unique Redis session/cache prefixes and a copied immutable JAR/owned JVM. It cleans only its own prefix in **each configured Redis database** (normally sessionDB0/cacheDB1), records per-DB removed/remaining counts and verifies the owned database is absent. No `FLUSHDB`, `FLUSHALL`, shared-container restart, volume deletion or shared AGSAuth reset is part of verification. Earlier B6 cleanup evidence covered DB0 only; it must not be relabeled as DB1 cleanup.
