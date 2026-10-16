# Authserver

Spring Boot 3.5 / Java 25 OAuth2 and OpenID Connect authorization server, with SQL Server persistence, Redis HTTP sessions, branded Thymeleaf login/recovery pages, and administrative REST endpoints.

This recovery branch targets a **fresh development database**. Historical SQL under `db/v1.0` is reference material and is not part of the canonical Flyway stream. Do not point this branch at an existing shared database without a separately tested migration plan and backup.

## Local setup

Requirements: JDK 25 (`java`, `keytool`), Python 3, Git, Docker Compose, and Node for form tests. SQL Server's Linux image requires x86-64; on ARM the Compose file uses amd64 emulation. Development ports bind to loopback only: SQL Server 15433, Redis 16379, HTTP 9080.

```sh
# Build the real custom libraries from pinned upstream source revisions.
./scripts/bootstrap-dependencies.sh

# Choose a NEW local development password (prompted, not echoed).
./scripts/local-dev.py init
./scripts/local-dev.py up
./scripts/local-dev.py start
```

Configuration, generated password-hashing and OIDC-signing keys, and credentials are kept in ignored `.local/` with restricted file permissions. `init` preserves existing credentials and keys; it never resets them. The local bootstrap creates a missing `admin` user and a PKCE “Admin Portal” client only when explicitly enabled by the generated local configuration. It never overwrites existing user credentials. The newly created local fixture has no external MFA enrollment; ordinary enrolled users still require their configured MFA provider.

Start output prints the non-secret `client_id` and registered redirect URI. Keep the server running in one terminal and run:

```sh
LOCAL_CLIENT_ID=<client_id-from-startup> ./scripts/smoke-local.py
```

The smoke check performs CSRF-protected login, code+PKCE exchange, OIDC tokens, administrative organization listing, userinfo, replay rejection, logout and token rejection. It also checks admin organization create/update/revoke and malformed requests. Public PKCE clients receive access and ID tokens; refresh rotation is tested separately with a real authenticated client by the Java integration suite. It does not print passwords or token values. The callback URI is a registered test destination; the smoke client captures its redirect without needing a separate frontend. Real clients should use their own registered callback.

```sh
# Unit tests and actual SQL Server integration tests (unique disposable test DB).
./scripts/local-dev.py test

# Stop local services without deleting data.
./scripts/local-dev.py stop
```

`up` only creates AGSAuth if absent. Neither `up`, `init` nor `stop` deletes a database or Docker volume. Run `./mvnw -Dmaven.repo.local=.local/m2 test` for unit tests without external infrastructure; database tests run only when `AUTH_DB_IT_URL/USER/PASSWORD` are set, and the application context check requires `AUTH_APP_IT=true`.

## Real end-to-end tests

With the local app running and Docker Compose services healthy, run:

```sh
python3 scripts/e2e-local.py          # Admin CRUD, users, protocol helpers and SQL assertions
python3 scripts/e2e-browser-flows.py  # Real HTML forms, CSRF, SSO/HMAC, profile/password persistence
python3 scripts/e2e-certificates.py   # Real generated RSA certificates, JWKS and client assertions
python3 scripts/e2e-protocol-variants.py # Authorization continuation, revocation, logout and metadata
python3 scripts/e2e-machine-users.py # Machine-token password policy and invalid sync payloads
python3 scripts/e2e-boundaries.py     # Every inventoried route, without credentials
python3 scripts/smoke-local.py
```

These tests send real HTTP requests and inspect the local SQL Server. The form suite submits actual server-rendered forms over HTTP; it does not run a browser engine. Certificate tests require OpenSSL. They create uniquely named local fixtures, revoke/deactivate them where supported, and keep sanitized reports in ignored `.local/`. Some audit/resource fixture rows remain for inspection; no existing data is deleted. Provider-dependent MFA/recovery, notifications, biometric and legacy-crypto success paths need real provider configuration.

`start` runs an immutable copy of the packaged application under `.local/runs/`, so a concurrent clean build cannot replace its loaded JAR or classes. Restart the app to load changes. Stop that foreground process before starting another instance on the same port.

See [end-to-end validation](docs/recovery/E2E-VALIDATION.md) and the [endpoint coverage matrix](docs/recovery/ENDPOINT-COVERAGE.md). Boundary rejection is recorded separately from successful business-flow coverage.

## Sample frontend and real Chrome tests

Start the auth server, then run:

```sh
python3 scripts/sample-frontend.py
```

Open <http://localhost:3000/>. The server registers a disposable public PKCE client through the real admin API and creates a test user. Credentials are in private, ignored `.local/sample-frontend-account.json`; no administrator secret is served to the browser. Use the buttons to sign in, inspect userinfo, change profile/password and sign out. Ctrl-C deletes its test user and deactivates its client and organization. Abrupt termination can leave disposable fixture rows.

The frontend uses real cross-origin token/userinfo requests, browser session storage and actual auth-server forms/WebCrypto. No endpoint responses are mocked. It is a local testing sample, not a production client or an admin portal. Provider-dependent flows remain explicitly unavailable.

To run the automated browser campaign, stop the sample server first so port 3000 is free. Install the pinned runner in ignored tooling; Google Chrome must be installed:

```sh
npm install --prefix .local/browser --save-exact playwright@1.56.1
python3 scripts/e2e-browser-engine.py
python3 scripts/e2e-provider-unavailable.py
```

The browser campaign starts its own sample frontend and disposable client/user, executes the real JavaScript, checks SQL and cleans up. Its report and synthetic-account screenshot stay under `.local/`. The provider-unavailable campaign enables MFA only on its own test user and verifies that missing MFA/biometric providers cannot issue authorization codes or tokens. Those rejection checks do not qualify real provider success.

Bouncy Castle modules are pinned together at 1.85. To recheck the repository's current open Bouncy Castle alerts against resolved Maven versions (requires authenticated `gh`):

```sh
MAVEN_USER_HOME="$PWD/.local/maven" ./mvnw -B -ntp \
  -Dmaven.repo.local="$PWD/.local/m2" dependency:tree \
  -Dincludes=org.bouncycastle -DoutputFile="$PWD/.local/bouncycastle-tree.txt"
python3 scripts/check-bouncycastle-alerts.py
```

This is a targeted advisory-range check; it does not clear all dependencies or establish exploit reachability. See [follow-up validation](docs/recovery/FOLLOW-UP-VALIDATION.md) for the executed results and remaining provider requirements.

## Local provider lab on macOS

The lab runs a separate auth instance, database and Redis namespace, alongside the normal sample app. It uses an explicit synthetic MFA/biometric service; it does not add a production authentication bypass.

```sh
# SQL/Redis must already be up. Build and verify first:
./scripts/local-dev.py test
# Terminal 1: separate auth server on 9082 and local provider on 9191
python3 scripts/local-providers.py start
# Terminal 2: automated HTTP/SQL and real Chrome MFA campaign
python3 scripts/e2e-local-providers.py
# After the test completes, start the interactive sample on 3001:
python3 scripts/local-provider-demo.py
```

Open <http://localhost:3001/>. Read the disposable account from private `.local/providers/sample-account.json`. Sign in, choose the email factor, click **Send code**, and read the newest available code at <http://localhost:9191/inbox>. Codes expire after two minutes and can be used once. This inbox captures synthetic MFA delivery; it does not send real email/SMS or exercise the app's separate notification-service API.

Use a separate browser profile/incognito window for the lab when also using the normal app: localhost cookies are shared across ports. Automated tests use isolated browser contexts. Port 3001 must be free for automated lab browser tests; stop the interactive provider demo with Ctrl-C before running them. The ordinary sample on port 3000 can remain running. Stop the demo before stopping its lab. Lab startup creates a uniquely named `AGSAuthProviders_<suffix>` database; Ctrl-C stops its own processes and retains that disposable database for inspection. It never resets the normal `AGSAuth` database. Repeated starts create additional lab databases. Credentials, logs and runtime JARs remain ignored under `.local/providers/`.

The lab verifies signed MFA requests, wrong/expired/replayed codes, recovery/activation/mobile recovery, rejection of pre-MFA resets, synthetic biometric proofs and actual Chrome forms. It does not qualify SecureAuth, real notification delivery, upstream synchronization, legacy crypto or Touch ID. See [local provider validation](docs/recovery/LOCAL-PROVIDER-LAB.md).

## Recovering local sign-in after Docker is unavailable

Login depends on the SQL/Redis containers. If Docker was stopped or became unresponsive during builds, first restore Docker responsiveness, then start this repo's existing services:

```sh
./scripts/local-dev.py up
docker compose exec -T redis redis-cli PING
```

Expect `PONG`. Retry **Sign in** from the sample frontend. If the provider lab process stopped, reuse its existing database, credentials and demo client with:

```sh
python3 scripts/local-providers.py resume
```

`resume` requires the lab's existing private configuration and the built JAR; stop an existing lab before invoking it. `start` creates a new lab database, while `resume` reuses the previous one. Neither operation resets the normal database. Docker Desktop itself is shared with other work and is not restarted by these scripts.

A cache/database outage now returns HTTP 503 with a retry message where the response can still be replaced. Unexpected application exceptions remain errors. Verify real outage handling independently:

```sh
python3 scripts/e2e-infrastructure-recovery.py
```

That test starts its own small Redis container and app on 16380/9084, pauses only its own Redis, verifies 503, resumes it and verifies recovery without restarting the test app. It cleans up its own container/process and leaves normal development services alone. See [outage validation](docs/recovery/OUTAGE-RECOVERY.md).

## Configuration

The context root is `/services`; `SERVER_BASE_PATH` must match the externally reachable scheme, host and path. Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `KEY_STORE_PASSWORD`, `AUTH_HASHER_KEYSTORE`, `AUTH_HASHER_PASSWORD` and `AUTH_HASHER_PEPPER`. The pepper alias is a UUID identifying a matching AES secret in the hashing keystore. Preserve this key to preserve existing passwords. OIDC signing uses a persistent PKCS12 file at `AUTH_SIGNING_KEYSTORE` (local default `.local/signing.p12`), alias `AUTH_SIGNING_ALIAS` (default `authserver-signing`), protected by `KEY_STORE_PASSWORD`. Preserve and back up this signing key too; missing or incorrect keys fail startup. Production SQL Server TLS certificate verification is enabled by default; local trust bypass is confined to the local profile.

Do not use the local bootstrap/profile for deployment. Configure external account synchronization, notifications, legacy crypto, biometric and SecureAuth MFA integrations before using those flows. MFA credentials use `secureauth.api.access.<realm-id>.clientId/clientSecret`; no real provider credentials are bundled. An unavailable provider is not replaced with a successful mock.

API calls use bearer tokens and `x-request-datetime` in ISO-8601 with timezone. PKCE authorization additionally uses `branding`, `x-request-datetime`, `Accept-Language` and `User-Agent` to establish the configured fingerprint. The same fingerprint headers must be used on subsequent calls. Request body signatures are configurable; the profile/password browser forms use the session SSO token and WebCrypto HMAC. Shared-secret JWT clients use HS256 and an active encrypted credential; private-key JWT clients need their registered verification certificates. Standard OIDC discovery is `/.well-known/openid-configuration` under the configured context root and publishes `/oauth2/jwks`.

The app uses one Boot-managed Redis session repository with typed security serialization. Mutable authorization settings, session status and tokens are read from SQL rather than a TTL cache, so logout, credential changes and application deactivation can take effect immediately.

The two source-built libraries are pinned in `scripts/bootstrap-dependencies.sh`. `lib-crypto`'s published source declares `2.0`, not the unavailable `2.0.2` originally requested by this repository. The script upgrades only its old Lombok annotation processor in a disposable checkout and skips upstream optional legacy analysis plugins; those plugins are not claimed to pass on JDK 25.

See [recovery progress](docs/recovery/PROGRESS.md), [design](docs/recovery/DESIGN.md), and [initial audit with correction](docs/recovery/INITIAL-AUDIT.md). Validation results distinguish isolated checks from complete flows and external integrations.
