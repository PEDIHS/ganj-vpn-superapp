# ADR-0004: Entitlement-only VPN Provisioning

- Status: Accepted
- Date: 2026-08-24

## Decision

Ganj VPN is a managed subscription client. The Android application does not expose manual config entry, URI/clipboard import, QR import, file import, config export, config sharing, or raw credential viewing. Every connection is derived from an active entitlement owned by the current user or from a server-issued free entitlement for a guest device.

## Connection Flow

1. The authenticated device selects one of the user's services.
2. The client requests a connection profile with `service_id`, `server_id`, device proof, and a fresh nonce.
3. The Control API verifies session, device binding, subscription state, device limit, server eligibility, abuse policy, and replay protection.
4. Config Broker returns a short-lived encrypted envelope bound to the device public key and request nonce.
5. The isolated VPN process decrypts the envelope in memory, starts the audited engine, and clears plaintext material after use.
6. Raw config and credentials are never written to logs, analytics, crash reports, clipboard, screenshots, backups, or general app storage.

## Purchase and Renewal

- The Play build uses Play Billing; the Direct build may use approved wallet, Telegram, or gateway channels.
- The client never activates a service based only on a local purchase callback.
- Backend verification, idempotent order fulfillment, immutable ledger entries, and reconciliation precede entitlement issuance.
- After successful purchase or renewal, the app refreshes `My Services` and can connect without exposing a config.

## Failure Rules

- Expired, suspended, refunded, chargeback, device-limit, or abuse-restricted entitlements cannot obtain a new envelope.
- An expired envelope cannot be replayed. Any reconnect grace policy must be signed, time-bounded, and revocable.
- Support diagnostics may include protocol, state, coarse server identifier, and sanitized error codes, never secrets or destinations.

## Consequences

- The product is intentionally not a general-purpose V2Ray client.
- Migration from the legacy bot maps existing paid services to entitlements instead of importing user-visible configs.
- Test tooling injects fixtures through instrumentation or staging APIs; it does not add a hidden production import screen.
