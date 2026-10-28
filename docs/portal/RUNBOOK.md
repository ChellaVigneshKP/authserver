# Portal runbook

The identity portal is a Next.js 16 server-side BFF in `web/`. It holds no
credential material of its own: it exchanges a public PKCE authorization code,
keeps the resulting session server-side in Redis, and calls the Java management
API with the signed-in user's bearer token. [API-CONTRACT.md](API-CONTRACT.md)
defines the request/response contract and [ACCEPTANCE.md](ACCEPTANCE.md) records
what has actually been verified.

## Runtime configuration

Configuration is read from the process environment at request time. Never bake
private values into the image, a `.env` file inside the build context, or a
`NEXT_PUBLIC_` variable. The portal has no client-side configuration variables.

| Variable | Required | Meaning |
|---|---|---|
| `PORTAL_ORIGIN` | yes | Exact public HTTPS origin (no path, query or credentials). It is also the registered callback host. |
| `AUTH_ISSUER` | yes | Public issuer URL of the auth server, used for discovery and OIDC validation. |
| `AUTH_CLIENT_ID` | yes | Registered public PKCE client id used by the portal. |
| `PORTAL_REDIS_URL` | yes | `redis:`/`rediss:` URL for the server-side session store. |
| `PORTAL_REDIS_NAMESPACE` | no | Prefix segment for portal keys; defaults to `default`. |
| `AUTH_TRANSPORT_ORIGIN` | no | Internal origin used to reach the same Java service when the public issuer is not routable from the container. Discovery must still name `AUTH_ISSUER`. |
| `AUTH_TOKEN_RESPONSE_CERT_PEM` | no | Pinned organization certificate for token-response signatures. Not a JWKS key. |
| `PORTAL_ALLOW_INSECURE_LOOPBACK` | no | `true` only for loopback HTTP development. Production must leave it unset/false. |

`PORTAL_ORIGIN` and `AUTH_ISSUER` must be HTTPS outside loopback development.
The portal refuses to start serving if either value is malformed, carries
credentials, or is plain HTTP without the explicit loopback opt-in.

The BFF session cookie (`portal-session`) is host-only for the portal origin,
`HttpOnly`, `SameSite=Lax`, and `Secure` whenever `PORTAL_ORIGIN` is HTTPS. No
access, refresh or ID token is ever placed in a cookie, URL, HTML/RSC payload,
browser storage, analytics event or log.

## Container build and run

`web/Dockerfile` produces a multi-stage standalone Node build. The runtime
stage contains only the standalone server, `.next/static` and production
dependencies, runs as the unprivileged `portal` user (uid/gid 10001), and
exposes port 3100. Both stages pin the same Node line the repository declares in
`web/package.json` engines, by index digest rather than by tag.

```sh
docker build -t identity-portal:local web
docker run --rm -p 127.0.0.1:3100:3100 \
  --env-file .local/portal-runtime.env \
  identity-portal:local
```

Pass configuration at run time only. Do not mount or copy a private env file
into the build context; `web/.dockerignore` excludes `.env*`, build output,
tests and browser artifacts from the image. If a secret is accidentally printed
or embedded, rotate it in Java/Redis before redeploying; rebuilding alone does
not revoke a leaked credential.

### Container networking

`AUTH_TRANSPORT_ORIGIN` accepts either an `https:` origin or a loopback
`http:` origin on `127.0.0.1`/`localhost`. Any other plain-HTTP value is
rejected at start; this is deliberate, so an internal hop can never silently
downgrade to plaintext across a network.

That rule constrains local container runs: inside the container the host's
loopback is not reachable, and the portal process cannot use the host's
`127.0.0.1` as its upstream. Give the container host-gateway aliases and reach
the fixture through the `localhost` alias, keeping the origin loopback-shaped:

```sh
docker run --rm -p 127.0.0.1:3100:3100 \
  --add-host host.docker.internal:host-gateway \
  --add-host localhost:host-gateway \
  -e PORTAL_ORIGIN=https://portal.example.internal \
  -e AUTH_ISSUER=https://issuer.example.internal/services \
  -e AUTH_TRANSPORT_ORIGIN=http://localhost:50139/services \
  -e AUTH_CLIENT_ID=<registered public client> \
  -e PORTAL_REDIS_URL=redis://host.docker.internal:16379/0 \
  identity-portal:local
```

The Redis URL follows the same rule: the session store must be reachable from
inside the container, so a compose service name or the host-gateway alias is
required rather than `127.0.0.1`. In production both the issuer origin and the
transport origin are typically routable HTTPS service origins and no aliases
are needed. Never enable `PORTAL_ALLOW_INSECURE_LOOPBACK` for a non-loopback
origin.

### Standalone server assets

The build uses `output: "standalone"`. `next start` still works for local
development but warns about the mismatch, and the generated `server.js` does
not serve `public/` or `.next/static` on its own. The image copies
`.next/static` next to the standalone tree, which is why the container renders
styled pages; a hand-rolled deployment of the standalone output must copy the
static assets the same way or pages will load without CSS and client chunks.

### Health and readiness

- `GET /healthz` is liveness only: 200 while the Node process serves requests.
- `GET /readyz` configures the runtime environment and performs one bounded
  Redis round trip. It returns 200 `{"status":"ready"}` or 503
  `{"status":"unavailable"}` and never returns URLs, credentials or stack
  traces.

Readiness intentionally does not probe the Java issuer. A short identity-service
or provider outage must not remove every portal replica from a load balancer;
individual management calls already return a retryable 503 envelope and the UI
keeps actionable state.

## Reverse proxy contract

The portal derives its public origin from `PORTAL_ORIGIN`, never from
`Host`/`X-Forwarded-Host`, so a proxy cannot widen the cookie domain or the
OIDC redirect target. The proxy must:

- terminate TLS for the exact configured origin and forward plain HTTP to the
  container port;
- pass `User-Agent` and `Accept-Language` through unchanged, because Java binds
  the portal session to the browser fingerprint captured at sign-in;
- preserve the request method, body and `Content-Type` (multipart uploads and
  binary downloads depend on them) and the `x-csrf-token` header;
- not add its own `Origin` rewrite: mutations require the browser `Origin` to
  equal `PORTAL_ORIGIN`;
- keep responses streaming for the certificate download; buffering is allowed
  but truncation is not.

## Local development runner

`python3 scripts/e2e-portal.py --phase <auth|navigation|clients|users|roles|certificates>`
starts an owned fixture: a UUID SQL Server database, UUID Redis prefixes, a
private loopback JVM and a Next server on a free loopback port (default 3100
selection). It registers the exact portal callback
`<portal>/auth/callback` and post-logout `<portal>/auth/logged-out`, and stops
only its own processes. Existing sample frontends on 3000/3001 and existing
clients are untouched. Evidence is written under `.local/portal-runs/<uuid>/`.

Pass `--container <image>` to serve the portal from an already built image
instead of `next start`. That mode publishes only the owned loopback port,
streams the container log into the run directory, performs the first-slice
smoke (liveness, readiness, sign-in start, account render), and skips the
outage sub-process probes, whose fault injection needs the host Next process.
The runner removes its own container by name; it never stops shared services.

## Failure behavior

- **Redis unavailable:** session reads fail closed with a retryable 503
  (`SESSION_UNAVAILABLE`); the portal never treats an unreadable session as
  anonymous success and never replays an uncertain write.
- **Java unavailable:** management calls return a retryable 503 envelope. No
  mutation is retried automatically; a lost response leaves the UI asking the
  operator to reload before retrying.
- **Session invalidated upstream:** the next verified response invalidates the
  local session and sends the browser to sign-in. Stale permission tabs are
  re-checked on every call by both the BFF and Java.
- **Server restart:** sessions survive in Redis; in-flight uploads do not. The
  import form reports an uncertain outcome rather than claiming failure.

## Logout limitations

Portal sign-out revokes the local session, redeems the Java logout transaction
and then redirects to the registered post-logout URI. If the identity service is
unreachable at that moment, the local session is still destroyed but upstream
SSO state and token revocation may remain pending; the operator must retry
sign-out once the service recovers. The portal does not claim successful SSO
logout when the upstream step failed.
