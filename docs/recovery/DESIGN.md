# Authserver recovery design

The user approved fixing the audited repository on a new branch and confirmed a fresh development database is acceptable. Preserve the existing application and HTTP API; selectively port code only after review. Do not merge candidate branches wholesale or import the unconditional password reset.

Success requires a clean Maven build, reproducible isolated SQL Server/Redis setup, fresh migrations, usable branded OAuth login and recovery pages, correct repository/mapper contracts, password round trips, MFA enforcement, active-token rejection, persisted code/refresh state and consistent session/cache behavior. External integrations must fail explicitly when unconfigured; do not pretend an unavailable remote service worked.

Keep current Java 25/Spring Boot 3.5 API compatibility. Keep the custom-library APIs and recover their real artifacts/source rather than replacing them with security stubs. Use one canonical migration location; fresh initialization must not require the historical scripts. Never run clean/reset against an existing database, deploy, or overwrite a shared admin password.

Each repair gets behavioral regression coverage where practical. SQL Server integration and complete browser/OAuth flow checks are required before claiming the app works. Missing dependencies or external access are validation blockers, not successful checks.
