# Login outage and recovery — 16 October 2026

The user reported HTTP 500 after clicking **Sign in** on the local MFA demo. The lab log recorded Redis query/command timeouts during `/oauth2/authorize`, before the login form could load. A direct host Redis PING timed out, and the Docker engine socket/Compose commands also timed out although Docker Desktop's control command reported it running. The user was running Docker builds and asked to pause; no Docker Desktop restart was performed.

After the user resumed work, the Docker engine responded but this repository's development containers were not running and host Redis refused connections. `./scripts/local-dev.py up` started only the repo's SQL/Redis services using their existing volumes. Redis returned PONG. A fresh request then passed authorization redirect (302) and login-form rendering (200). These observations establish dependency unavailability; they do not prove which build or Docker internals caused it.

## Repairs

`InfrastructureAvailabilityFilter` runs before Spring's session/security processing, where Redis failures can occur before MVC exception handling. Typed resource failures/timeouts, including wrapped Redis failures, return a generic 503 response, `Retry-After: 5` and `Cache-Control: no-store` when the response has not been committed. Internal details and partial page output are removed. Unrelated application exceptions continue through normal error handling. MVC resource failures/timeouts receive the same 503 behavior through the controller advice. An already committed response cannot be replaced.

The lab launcher now accepts `resume` to reuse the existing isolated database, credentials and demo client while loading a new immutable application JAR. `start` continues to create a fresh isolated lab. The availability fix was loaded into both the normal app and lab. The existing manual demo account and public client were preserved and used for the final verification.

## Verification

- `./scripts/local-dev.py test`: **49 Java tests, zero failures/errors/skips; five Node tests passed**. Three focused filter regressions verify wrapped session/cache errors, timeout handling and propagation of unrelated programming errors.
- `scripts/e2e-infrastructure-recovery.py`: a genuine HTTP test app and its own disposable Redis container. Healthy cache returned a 302 authorization redirect; pausing only the test Redis returned a controlled **503** and retry message; resuming it restored **302 without restarting the app**. Three recorded HTTP checks and five assertions passed. The test container/process were removed afterward; ordinary SQL/Redis were never paused.
- The exact existing manual demo client/account passed login-form loading, password login, synthetic MFA code delivery/verification, PKCE exchange and `/userinfo` with real HTTP. No profile or password was changed. Sanitized evidence is in ignored `.local/demo-signin-check.json`.
- Clicking **Sign in** in the opened real browser reached `/services/login`, with the username/password fields visible.

The test's initial script issues (a Python module-variable collision and missing OAuth headers in a recovery probe) were corrected before the passing final run. They were test-runner issues, and no passing application-recovery claim relies on those failed attempts.

The isolated outage report is `.local/e2e-infrastructure-report.json`; private app logs remain local. These tests qualify a recoverable Redis outage, not Docker build performance, every distributed outage mode or failures after a response has already been sent. No containers, volumes or databases outside the task's own test resources were deleted or reset.

## Operating the demo

1. Ensure Docker responds and run `./scripts/local-dev.py up` if this repo's services are stopped.
2. Confirm `docker compose exec -T redis redis-cli PING` returns PONG.
3. Retry **Sign in** from `http://localhost:3001/`.
4. If the lab has stopped, run `python3 scripts/local-providers.py resume` after stopping any existing instance. Keep using the current `.local/providers/sample-account.json` credentials.

The scripts do not restart Docker Desktop. A Desktop restart affects unrelated containers and must be coordinated with other Docker work. Heavy builds can coincide with dependency timeouts; this change provides a controlled response and tested recovery rather than allowing authentication without its required infrastructure.
