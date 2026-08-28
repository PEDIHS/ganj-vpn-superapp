# Ganj VPN Control API — Auth Session V1

This document describes the executable Phase 7 authentication/session contract implemented by `src/application.js` and `src/adapters/auth-session.js`.

> Security note: the older Auth section in `api/openapi.yaml` predates GDP1 device identity. It must not be used to weaken the implementation. The executable contract below is the migration target for the next OpenAPI revision.

## Trust chain

```text
Android Keystore P-256 signing identity
        +
wrapped X25519 encryption identity
        ↓
GDP1 proof of the exact registration/refresh body
        ↓
Control API guest/device registration
        ↓
Ed25519-signed short-lived access JWT
        +
opaque rotating refresh token
        ↓
Telegram OIDC + PKCE account link (optional)
        ↓
Telegram-linked Ganj user/session
        ↓
owned services / entitlement / device-bound VPN profile
```

The access token is never an entitlement. Connection authorization still revalidates the authenticated device, owned service, expiry/quota, device slot, server eligibility and one-time profile grant.

## Public routes

### `GET /.well-known/jwks.json`

Returns the public Ed25519 verification JWK set for Ganj-issued access tokens. Private key material is never exposed.

### `POST /v1/auth/guest`

Creates or reconciles a guest account bound to an exact device key identity.

Request:

```json
{
  "device_id": "uuid",
  "key_version": "v1",
  "signing_public_key_spki": "base64url DER SPKI for P-256",
  "encryption_public_key_raw": "43-char base64url X25519 public key",
  "device_proof": "gdp1.<timestamp>.<nonce>.<body-hash>.<key-version>.<P1363-signature>"
}
```

GDP1 canonical body intentionally excludes `device_proof` itself and includes the other four fields. The proof is bound to method `POST` and path `/v1/auth/guest`, has a 90-second clock window and a one-time nonce stored server-side.

Response `201` data:

```json
{
  "user_id": "uuid",
  "device_id": "uuid",
  "access_token": "JWT",
  "access_token_expires_at": "RFC3339",
  "refresh_token": "opaque-base64url",
  "refresh_token_expires_at": "RFC3339",
  "token_type": "Bearer"
}
```

### `POST /v1/auth/refresh`

Rotates both access and refresh credentials. The refresh token is also bound to the same registered device through GDP1.

Request:

```json
{
  "refresh_token": "43-char opaque token",
  "device_id": "uuid",
  "device_proof": "GDP1 proof of the exact refresh body"
}
```

A refresh token is one-time. Reuse causes the whole refresh family/session family to be revoked.

### `POST /v1/auth/telegram/exchange`

Public OIDC callback exchange. Existing Telegram OIDC adapter consumes hashed one-time `state`, verifies S256 PKCE, exchanges the code over HTTPS, verifies the Telegram ID token, then calls the account broker to link the device/user and issue a new Ganj session.

Request:

```json
{
  "code": "provider authorization code",
  "state": "opaque one-time state",
  "code_verifier": "PKCE verifier"
}
```

## Authenticated routes

### `POST /v1/auth/telegram/start`

Requires a valid Ganj access token. This is intentionally **not** a public device-registration endpoint.

Request:

```json
{
  "code_challenge": "43-char S256 challenge",
  "redirect_uri": "allowlisted app redirect URI"
}
```

The server persists a digest of a random state tied to the authenticated `user_id`, `device_id`, redirect URI and PKCE challenge. This prevents an unauthenticated caller from creating a Telegram state for another device.

### `POST /v1/auth/logout`

Requires a valid Ganj access token and revokes the session's complete refresh family. Response is a normal success envelope with `{"logged_out": true}`.

## Access JWT

Issuer implementation:

- algorithm: `EdDSA`
- key type: Ed25519
- default TTL: 15 minutes
- required claims: `iss`, `aud`, `sub`, `device_id`, `jti`, `token_use=access`, `auth_method`, `iat`, `nbf`, `exp`

`src/adapters/jwks-auth.js` defaults its allow-list to `EdDSA` so the verifier and issuer cannot silently disagree. Production should still configure the algorithm explicitly.

## Refresh token security

- 32 random bytes encoded base64url;
- only SHA-256 token digests are persisted;
- family and parent/replacement relationships are persisted;
- each use consumes the current token and creates a new session/token atomically;
- reuse revokes all sessions and refresh tokens in the family;
- expired, revoked, wrong-device, blocked-user and revoked-device tokens fail closed.

## Telegram relink / merge safety

When the verified Telegram subject already belongs to another Ganj user:

- if the current guest account has services/orders, the server returns `telegram_account_merge_required`; it does not silently merge financial/service ownership;
- if the guest has no holdings, existing guest sessions are revoked, the device is reassigned to the Telegram user and the obsolete guest user is retired;
- linking a currently Telegram-linked account to a different Telegram identity returns a conflict;
- all resulting access is still device-bound.

## Required production wiring

```text
CONTROL_API_ADAPTER_MODE=production
CONTROL_API_DATA_ADAPTER_MODULE=./src/adapters/postgres.js
CONTROL_API_AUTH_ADAPTER_MODULE=./src/adapters/jwks-auth.js
CONTROL_API_AUTH_SESSION_ADAPTER_MODULE=./src/adapters/auth-session.js
CONTROL_API_TELEGRAM_AUTH_ADAPTER_MODULE=./src/adapters/telegram-oidc.js
CONTROL_API_TELEGRAM_ACCOUNT_BROKER_MODULE=./src/adapters/auth-session.js

DATABASE_URL=<secret-injected PostgreSQL URL>
DATABASE_SSL=require

AUTH_SESSION_SIGNING_PRIVATE_JWK=<secret-injected Ed25519 private JWK>
AUTH_SESSION_SIGNING_KEY_ID=ganj-auth-ed25519-v1
AUTH_ISSUER=https://<ganj-auth-host>
AUTH_AUDIENCE=ganj-control-api
AUTH_JWKS_URI=https://<same-public-api-host>/.well-known/jwks.json
AUTH_ALLOWED_ALGORITHMS=EdDSA
AUTH_TOKEN_USE=access

TELEGRAM_OIDC_AUTHORIZATION_ENDPOINT=https://<telegram-authorization-endpoint>
TELEGRAM_OIDC_TOKEN_ENDPOINT=https://<telegram-token-endpoint>
TELEGRAM_OIDC_JWKS_URI=https://<telegram-jwks-endpoint>
TELEGRAM_OIDC_ISSUER=<exact-provider-issuer>
TELEGRAM_OIDC_CLIENT_ID=<secret/config>
TELEGRAM_OIDC_CLIENT_SECRET=<secret if provider requires it>
TELEGRAM_OIDC_REDIRECT_URIS=ganjvpn://oauth/telegram
TELEGRAM_OIDC_ALLOWED_ALGORITHMS=<provider-approved algorithms>
```

Do not reuse the internal `AUTH_ALLOWED_ALGORITHMS=EdDSA` policy as the Telegram provider algorithm policy; the two trust domains have separate JWKS and algorithm allow-lists.

## Migration

`migrations/005_auth_sessions.sql` adds:

- stable session ID / refresh family / auth method fields;
- hashed rotating refresh-token store;
- Telegram login-state ownership by user;
- dedicated one-time auth GDP1 nonce table.

Migrations are forward-only and must be exercised against real PostgreSQL in CI/staging before production promotion.

## Current completion boundary

This backend slice closes the server-side guest/session and Telegram-link foundation. It does **not** by itself make the Android login journey production-complete. The Android client still needs guest registration, secure token vault/refresh, OIDC launch/callback/PKCE handling, linked-account UI and post-link My Services reconciliation.
