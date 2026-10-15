# Authserver recovery implementation plan

Goal: repair the 25 audited findings and qualify the existing app on a fresh development database.
Spec: DESIGN.md. Evidence baseline: INITIAL-AUDIT.md. Branch: fix/authserver-recovery.

- [x] Build: recover real custom artifacts/source, fix Maven processors/wrapper, compile cleanly; resolve compiler errors without substituting fake security libraries.
- [x] Database: reconcile base schema/seeds and full procedures into canonical versioned migrations; fix schema/argument/result contracts, UTC types, nullable consumption and atomic single-use codes. Add SQL contract tests and run SQL Server fresh install.
- [x] Repository contracts: fix the five named-binding errors, MFA argument order, missing schemas/procedures and explicit result projections. Add tests against actual repository invocations and row mappers.
- [x] UI/startup: align context/public URLs, branding defaults, resource-based CMS, complete templates/forms, static assets and safe local configuration. Do not import unprofiled development seeders.
- [x] Security/state: repair password prefix/pepper, enforce pending MFA and active tokens, transactional code/refresh persistence, grant reconstruction, expiry/replay checks, serializer and invalidation consistency.
- [x] Qualification: run clean build/tests, fresh DB migrations, start app with SQL Server/Redis, exercise login/code/refresh/logout/admin operations and malformed inputs. Review full diff independently and fix substantive findings.

Review focus: partial database writes, cross-brand usernames, stale cache entries after security mutations, null/malformed headers, pending MFA attempting direct authorization. Use red-green regression tests for behavioral fixes and preserve detailed test/blocked status in PROGRESS.md.

Execution: subagent-driven development for the database/repository and UI/startup work, with root handling build/security integration and a whole-change review. Ownership boundaries avoid simultaneous edits to the same files.

Qualification is complete for the tested local development flows. Provider-dependent acceptance, exhaustive endpoint/load qualification and existing-database upgrades remain explicitly outside the validated claim; see PROGRESS.md.
