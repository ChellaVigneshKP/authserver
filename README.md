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

## Configuration

The context root is `/services`; `SERVER_BASE_PATH` must match the externally reachable scheme, host and path. Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `KEY_STORE_PASSWORD`, `AUTH_HASHER_KEYSTORE`, `AUTH_HASHER_PASSWORD` and `AUTH_HASHER_PEPPER`. The pepper alias is a UUID identifying a matching AES secret in the hashing keystore. Preserve this key to preserve existing passwords. OIDC signing uses a persistent PKCS12 file at `AUTH_SIGNING_KEYSTORE` (local default `.local/signing.p12`), alias `AUTH_SIGNING_ALIAS` (default `authserver-signing`), protected by `KEY_STORE_PASSWORD`. Preserve and back up this signing key too; missing or incorrect keys fail startup. Production SQL Server TLS certificate verification is enabled by default; local trust bypass is confined to the local profile.

Do not use the local bootstrap/profile for deployment. Configure external account synchronization, notifications, legacy crypto, biometric and SecureAuth MFA integrations before using those flows. MFA credentials use `secureauth.api.access.<realm-id>.clientId/clientSecret`; no real provider credentials are bundled. An unavailable provider is not replaced with a successful mock.

API calls use bearer tokens and `x-request-datetime` in ISO-8601 with timezone. PKCE authorization additionally uses `branding`, `x-request-datetime`, `Accept-Language` and `User-Agent` to establish the configured fingerprint. The same fingerprint headers must be used on subsequent calls. Request body signatures are configurable; the profile/password browser forms use the session SSO token and WebCrypto HMAC. Shared-secret JWT clients use HS256 and an active encrypted credential; private-key JWT clients need their registered verification certificates. Standard OIDC discovery is `/.well-known/openid-configuration` under the configured context root and publishes `/oauth2/jwks`.

The app uses one Boot-managed Redis session repository with typed security serialization. Mutable authorization settings, session status and tokens are read from SQL rather than a TTL cache, so logout, credential changes and application deactivation can take effect immediately.

The two source-built libraries are pinned in `scripts/bootstrap-dependencies.sh`. `lib-crypto`'s published source declares `2.0`, not the unavailable `2.0.2` originally requested by this repository. The script upgrades only its old Lombok annotation processor in a disposable checkout and skips upstream optional legacy analysis plugins; those plugins are not claimed to pass on JDK 25.

See [recovery progress](docs/recovery/PROGRESS.md), [design](docs/recovery/DESIGN.md), and [initial audit with correction](docs/recovery/INITIAL-AUDIT.md). Validation results distinguish isolated checks from complete flows and external integrations.
