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
| `POST` | `/v1/services/{service_id}/connection-profile` | Short-lived encrypted profile for an entitled device and server |

`GET /v1/orders/{order_id}` is the checkout-status addition requested by the Android vertical slice. The OpenAPI file should add the same operation when its next contract version is published.

## Non-negotiable invariants

- Authentication comes from an injected boundary. This service does not fabricate JWT or Telegram identities.
- Production startup fails unless real data, authentication/device-crypto, and Play verification adapters are configured.
- Test headers and test purchase tokens are accepted only by the explicit `test` adapter mode; that mode is rejected when `NODE_ENV=production`.
- Order and purchase operations require an `Idempotency-Key`; reusing a key with different input returns `409`.
- Orders, services and renewal targets are always ownership-scoped.
- A connection profile requires an active, unexpired, non-exhausted service, a matching trusted device, valid device proof, available server, allowed tier/country/protocol, and an available device slot.
- Server endpoint and credential material is passed only to the device-sealing boundary. It is never returned as JSON or logged.
- Google Play purchase tokens are never stored; only a SHA-256 digest is retained for replay protection.
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

## Local execution

Requires Node.js 22 or later and no package installation.

```bash
cd services/control-api
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

The built-in test runner covers catalog filtering, ownership, expiry, traffic, tier, protocol, server state, device proof/limit, manual-config rejection, idempotency conflicts, server-side purchase verification, token replay, renewals, production fail-closed behavior and the HTTP boundary.
