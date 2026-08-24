# Ganj VPN Control API — subscription vertical slice

Executable, dependency-free Node.js reference service for the Android subscription flow. It implements the relevant `api/openapi.yaml` contracts and one status endpoint required by checkout orchestration. It intentionally has **no** manual URI, clipboard, QR, file, or arbitrary VPN configuration import route.

## Implemented endpoints

| Method | Path | Policy |
| --- | --- | --- |
| `GET` | `/healthz` | Process health only |
| `GET` | `/v1/store/plans?channel=play\|direct` | Public, channel-filtered product catalog |
| `GET` | `/v1/services` | Services owned by the authenticated user |
| `GET` | `/v1/services/{service_id}` | Ownership-scoped service details |
| `GET` | `/v1/servers` | Active servers filtered by usable entitlement, tier, country and protocol |
| `POST` | `/v1/orders` | Idempotent checkout creation |
| `GET` | `/v1/orders/{order_id}` | Ownership-scoped checkout status |
| `POST` | `/v1/billing/play/verify` | Idempotent, server-side Play verification and entitlement fulfillment |
| `POST` | `/v1/billing/play/rtdn` | Signed Pub/Sub signal; authoritative state is re-read from Android Publisher |
| `POST` | `/v1/auth/telegram/exchange` | One-time state + PKCE OIDC code exchange through the Telegram identity boundary |
| `POST` | `/v1/services/{service_id}/connection-profile` | Short-lived encrypted profile for an entitled device and server |

## Non-negotiable invariants

- Authentication comes from an injected boundary. This service does not fabricate JWT or Telegram identities.
- Production startup fails unless real data, authentication/device-crypto, and Play verification adapters are configured.
- Test headers and test purchase tokens are accepted only by the explicit `test` adapter mode; that mode is rejected when `NODE_ENV=production`.
- Order and purchase operations require an `Idempotency-Key`; reusing a key with different input returns `409`.
- Orders, services and renewal targets are always ownership-scoped.
- A connection profile requires an active, unexpired, non-exhausted service, a matching trusted device, valid device proof, available server, allowed tier/country/protocol, and an available device slot.
- Server endpoint and credential material is passed only to the device-sealing boundary. It is never returned as JSON or logged.
- Google Play purchase tokens are never stored; only a SHA-256 digest is retained for replay protection.
- `SUBSCRIPTION_STATE_PENDING` never grants an entitlement. RTDN payload claims never grant or revoke access by themselves.
- Initial purchases, new purchase tokens, plan changes and re-signups are acknowledged only after the entitlement transaction commits. A failed acknowledgement is safely retryable with the same idempotency key. Renewal RTDN processing never acknowledges again.
- Profile grants expire after five minutes, bind to one device and reject reuse of a client nonce. The VPN gateway must atomically call `consumeConnectionProfile()` and accept each profile ID once.
- Production data adapters must implement idempotency and purchase-token uniqueness with database unique constraints/transactions, not process-local locks.

## Adapter boundaries

Production modules are loaded from environment-provided ESM specifiers. Each factory receives `{ environment }`.

### Authentication and device cryptography

`CONTROL_API_AUTH_ADAPTER_MODULE` exports `createAuthAdapter()` and returns:

```js
{
  kind: 'oidc-jwt-device-v1',
  authenticate(request),
  verifyDeviceProof({ principal, payload, proof }),
  sealConnectionProfile({ principal, plaintext, associatedData })
}
```

`authenticate()` must validate the issuer, audience, signature, expiry, revocation/session state and device binding of a real access token. The returned principal must contain UUID `userId` and `deviceId`. `sealConnectionProfile()` must use the registered/attested device public key and authenticated associated data. Raw key material must not cross the boundary.

### Play verification

`CONTROL_API_PURCHASE_ADAPTER_MODULE` exports `createPurchaseVerifier()` with `verifyPlayPurchase()`. A production implementation must call Google Play Developer API, validate package/product/purchase state, acknowledgement and transaction identity, and return a stable external transaction ID. Client claims are never sufficient.

### Persistence

`CONTROL_API_DATA_ADAPTER_MODULE` exports `createDataAdapter()` implementing the repository methods asserted in `src/application.js`. Production implementations must make these operations durable and enforce:

- unique `(scope, user_id, idempotency_key)`;
- unique purchase-token digest;
- ownership predicates in the data query;
- atomic order fulfillment, renewal/new service creation and token binding;
- atomic device-slot reservation.

The included `InMemoryRepository` is only a local/test reference adapter.

## Production adapters included in this slice

### PostgreSQL persistence

`src/adapters/postgres.js` is an async PostgreSQL repository using parameterized queries, ownership predicates, row/advisory locks and transaction-scoped operations. Migrations under `migrations/` create users, devices, revocable sessions, plans, services, device bindings, servers, orders, idempotency keys, purchase-token digests, one-time profile grants, webhook inbox and Telegram PKCE state.

Run immutable/checksummed migrations before the API:

```bash
DATABASE_URL='postgres://…' node src/migrate.js
```

`002_seed_catalog_test.sql` is excluded unless `MIGRATIONS_INCLUDE_TEST_SEED=true`; never enable it in production. Server rows hold only `secret_ref`. `CONTROL_API_SERVER_SECRET_ADAPTER_MODULE` must resolve that reference from a KMS/Vault-backed provider and must not log or persist the resolved credential.

### Asymmetric JWT and device envelope

`src/adapters/jwks-auth.js` validates bearer access-token signature, exact issuer/audience, algorithm allow-list, expiry/not-before/issued-at, token use, JTI session revocation, active user/device and trusted attestation. JWKS retrieval is HTTPS-only, bounded, cached and fail-closed. Device proof uses the registered signing JWK. Profiles use ephemeral X25519, HKDF-SHA256, AES-256-GCM and authenticated metadata; the response contains no raw endpoint or credential.

Required configuration includes:

```text
CONTROL_API_AUTH_ADAPTER_MODULE=./src/adapters/jwks-auth.js
AUTH_JWKS_URI=https://identity.example/.well-known/jwks.json
AUTH_ISSUER=https://identity.example
AUTH_AUDIENCE=ganj-control-api
AUTH_ALLOWED_ALGORITHMS=RS256
DATABASE_URL=<secret reference injected by the platform>
```

### Telegram code exchange

`src/adapters/telegram-oidc.js` consumes a hashed one-time login state under a database lock, validates S256 PKCE, exchanges the code over HTTPS, verifies the signed ID token through JWKS and delegates account linking/session issuance to `CONTROL_API_TELEGRAM_ACCOUNT_BROKER_MODULE`. Client secrets and provider tokens are never logged.

### Google Play and RTDN

`src/adapters/google-play.js` uses `purchases.subscriptionsv2.get` as the source of truth. It also models post-grant acknowledgement and v2 cancel/revoke operations. Service-account OAuth is signed locally; credentials must arrive through `GOOGLE_PLAY_SERVICE_ACCOUNT_FILE` or a secret-injected JSON environment variable and are never written or logged.

`src/adapters/google-play-rtdn.js` verifies the Pub/Sub OIDC bearer JWT, audience, Google issuer and exact push service-account email. The notification is an idempotent signal only: the API hashes the purchase token, finds its owned order, calls Android Publisher again, then reconciles service status.

Production runtime additionally requires:

```text
CONTROL_API_DATA_ADAPTER_MODULE=./src/adapters/postgres.js
CONTROL_API_PURCHASE_ADAPTER_MODULE=./src/adapters/google-play.js
CONTROL_API_PLAY_NOTIFICATIONS_ADAPTER_MODULE=./src/adapters/google-play-rtdn.js
CONTROL_API_TELEGRAM_AUTH_ADAPTER_MODULE=./src/adapters/telegram-oidc.js
CONTROL_API_TELEGRAM_ACCOUNT_BROKER_MODULE=<session issuer module>
CONTROL_API_SERVER_SECRET_ADAPTER_MODULE=<KMS/Vault resolver module>
GOOGLE_PLAY_PACKAGE_NAME=com.ganj.vpn
PLAY_RTDN_AUDIENCE=https://api.example/v1/billing/play/rtdn
PLAY_RTDN_SERVICE_ACCOUNT=<dedicated Pub/Sub push identity>
PLAY_RTDN_SUBSCRIPTION=<exact Pub/Sub subscription resource>
```

No default production adapter, token, key, database password or service-account secret is embedded in this repository. Missing or test-only adapters stop startup.

## Local execution

Requires Node.js 22 or later. Install the pinned PostgreSQL client before starting the production adapter; the explicit in-memory test adapter itself has no database requirement.

```bash
cd services/control-api
npm install --ignore-scripts
CONTROL_API_ADAPTER_MODE=test npm start
```

The explicit local adapter seeds these test identities:

```text
user:   10000000-0000-4000-8000-000000000001
device: 20000000-0000-4000-8000-000000000001
plan:   30000000-0000-4000-8000-000000000002
```

Example catalog and checkout:

```bash
curl 'http://127.0.0.1:8080/v1/store/plans?channel=play'

curl -X POST 'http://127.0.0.1:8080/v1/orders' \
  -H 'Content-Type: application/json' \
  -H 'X-Test-User-Id: 10000000-0000-4000-8000-000000000001' \
  -H 'X-Test-Device-Id: 20000000-0000-4000-8000-000000000001' \
  -H 'Idempotency-Key: local-checkout-00000001' \
  --data '{"plan_id":"30000000-0000-4000-8000-000000000002","channel":"play"}'
```

The local-only approved proof is the repeated test value `aaaaaaaaaaaaaaaa`. Never deploy it or test adapter mode. Override local secrets with `CONTROL_API_TEST_DEVICE_SECRET`, `CONTROL_API_TEST_SECOND_DEVICE_SECRET`, and `CONTROL_API_TEST_PURCHASE_TOKEN` when exercising device proof or purchase flows.

## Verification

```bash
npm run check
npm test
npm run test:coverage
```

PostgreSQL integration tests run in an isolated tmpfs database:

```bash
docker compose -f docker-compose.integration.yml up --build --abort-on-container-exit --exit-code-from integration
docker compose -f docker-compose.integration.yml down --volumes
```

If Docker is unavailable, `postgres.integration.test.js` is explicitly skipped while repository, cryptographic, JWT/JWKS, Telegram, Publisher API and signed-callback contracts still run as unit/integration tests. PostgreSQL production code is excluded from the local coverage gate because it has a separate containerized gate.

The built-in test runner covers catalog filtering, ownership, expiry, traffic, tier, protocol, server state, device proof/limit, manual-config rejection, idempotency conflicts, server-side purchase verification, token replay, renewals, production fail-closed behavior and the HTTP boundary.
