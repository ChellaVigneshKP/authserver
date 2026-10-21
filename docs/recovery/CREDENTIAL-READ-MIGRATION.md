# Credential read security migration

Credential GET responses no longer include `value`. This is an intentional breaking security change to the legacy v1 API. Shared secrets cannot be retrieved after creation, including by organization or platform administrators. Reads do not open encrypted secret stores.

Use `GET /api/v2/organizations/{org}/applications/{app}/credentials` for the metadata contract:

```json
[{"id":"<credential GUID>","name":"Rotation key","type":"SHARED_SECRET","algorithm":"HS256","status":"Active","expirationDate":"2030-06-01T18:00:00Z","isExpired":false}]
```

The v1 GET retains its other fields and also removes `value`. All credential responses use `Cache-Control: no-store`. Public certificate downloads continue through the existing certificate endpoints.

Shared-secret POST creation returns `{id,secretValue}` once. Consumers must capture that response and transfer the secret directly to their confidential client configuration. Do not log it, put it in a URL, or depend on GET to recover it. A lost secret requires rotation: create a second credential, verify token issuance with it, disable the old credential, then delete the old credential. There can be at most two active credentials.

Expiry preserves the full UTC instant, including SQL datetime2 time-of-day. An offset-free imported timestamp or date is interpreted as UTC; expiry at the current instant is expired. A missing expiry retains the service's existing non-expiring behavior. A malformed imported expiry is treated as expired with a warning that identifies the credential without logging the imported value or parser exception. Existing confidential JWT verification still uses its internal secret lookup.

No database migration, record rewrite or key replacement is required. Update consumers that read `value` before deploying this security change.
