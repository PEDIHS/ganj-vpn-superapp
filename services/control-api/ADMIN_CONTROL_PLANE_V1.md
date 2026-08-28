# Admin Control Plane V1

This contract defines the first operator-facing API slice for Ganj VPN. It manages the same `control_servers` registry used by the Android discovery/profile path. It does not create a separate Free-server catalog.

## Authentication and authorization

All routes are mounted under `/v1/admin/control-plane/*` and use the normal Control API authentication boundary. The authenticated principal must additionally contain scope:

`admin:control-plane`

Missing/invalid authentication returns `401`. Missing administrative scope returns `403 admin_scope_required`.

## Server representation

Responses contain safe operational metadata only:

```json
{
  "id": "uuid",
  "code": "de-premium-01",
  "name": "Germany Premium 01",
  "country_code": "DE",
  "city": "Frankfurt",
  "tier": "premium",
  "status": "active",
  "load_ratio": 0.2,
  "latency_hint_ms": 61,
  "protocols": ["vless", "trojan"],
  "secret_configured": true,
  "updated_at": "2026-08-28T00:00:00.000Z"
}
```

The API MUST NOT return the server secret reference itself or any resolved connection material.

## Routes

### `GET /v1/admin/control-plane/servers`
Returns the registry including active, busy, maintenance and disabled entries.

### `POST /v1/admin/control-plane/servers`
Creates a registry entry. Required fields are `code`, `name`, `country_code`, `tier`, `protocols`, and `secret_reference`. `secret_reference` is an opaque server-side secret-store locator; it is accepted on creation but is never echoed back.

Raw reusable VPN material is forbidden. VLESS/VMess/Trojan/Shadowsocks URIs, subscription URLs, UUID/password/token parameters or Xray JSON must not be accepted as the secret reference.

New servers should normally start in `maintenance` until the operator has separately validated the referenced secret/provider binding.

### `GET /v1/admin/control-plane/servers/:serverId`
Returns one safe registry representation.

### `PATCH /v1/admin/control-plane/servers/:serverId`
Mutable fields: `name`, `country_code`, `city`, `tier`, `status`, `load_ratio`, `latency_hint_ms`, `protocols`.

The route deliberately does not mutate `code` or `secret_reference`. Secret rotation belongs to a separate privileged secret-management ceremony so a normal metadata edit cannot silently redirect connection credentials.

## Status semantics

- `active`: eligible for normal discovery when entitlement rules match.
- `busy`: kept in registry but not currently eligible through the user-facing discovery path.
- `maintenance`: intentionally unavailable while being operated/tested.
- `disabled`: emergency/operator shutdown. It remains visible to administrators but is unavailable to users.

## Audit

Successful create/update operations append the existing immutable admin audit stream. Audit entries contain actor subject, request id, resource id, action and before/after digests. They do not contain VPN credentials or secret references.

## Shared-source rule

The future Admin Console, Telegram administrator actions and automated health/reconciliation workers must mutate this same Control Plane contract/repository. They must not maintain independent server lists.
