# API Documentation

Contract قابل پردازش: [`api/openapi.yaml`](../api/openapi.yaml)

## Conventions

- Base: `/v1`
- JSON UTF-8، snake_case روی Wire؛
- Auth: `Authorization: Bearer <access_token>`؛
- Trace: `X-Request-Id`؛
- Mutations مالی: `Idempotency-Key` اجباری؛
- Version negotiation با URL major و additive schema change؛
- تمام زمان‌ها RFC 3339 UTC.

## Standard Envelope

```json
{
  "data": {},
  "meta": { "request_id": "...", "server_time": "..." },
  "error": null
}
```

Error:

```json
{
  "data": null,
  "meta": { "request_id": "..." },
  "error": {
    "code": "ENTITLEMENT_EXPIRED",
    "message": "اشتراک این سرویس تمام شده است.",
    "retryable": false,
    "details": {}
  }
}
```

## Endpoint Groups

### Bootstrap

- `GET /app/bootstrap`: remote flags، minimum version، maintenance، public plan summary و signed cache policy.

### Auth

- `POST /auth/guest`
- `POST /auth/telegram/start`
- `POST /auth/telegram/exchange`
- `POST /auth/bot-link/start`
- `GET /auth/bot-link/{id}`
- `POST /auth/refresh`
- `POST /auth/logout`

### Account

- `GET /me`
- `PATCH /me/preferences`
- `GET /me/devices`
- `DELETE /me/devices/{id}`
- `GET /me/sessions`
- `DELETE /me/sessions/{id}`

### VPN and Services

- `GET /services`
- `GET /services/{id}`
- `POST /services/{id}/connection-profile`
- `GET /servers`
- `POST /servers/recommend`
- `POST /connections/start`
- `POST /connections/{id}/heartbeat`
- `POST /connections/{id}/end`

Profile endpoint فقط ciphertext دستگاه‌محور و TTL کوتاه می‌دهد؛ URI خام در endpointهای دیگر ممنوع است.

### Commerce

- `GET /store/plans`
- `POST /orders`
- `GET /orders/{id}`
- `POST /billing/play/verify`
- `POST /billing/direct/session` فقط در Direct channel
- `GET /wallet`
- `GET /wallet/transactions`

### Messaging

- `PUT /devices/{id}/push-token`
- `GET /notifications`
- `POST /notifications/{id}/read`
- `GET /campaign-surfaces`

## Authorization Scopes

| Scope | Use |
|---|---|
| `profile:read` | Account summary |
| `service:read` | My Services |
| `vpn:connect` | Config envelope and session |
| `wallet:read` | Balance and ledger view |
| `commerce:write` | Order and purchase |
| `device:manage` | Revoke devices/sessions |
| `admin:*` | فقط Admin audience و MFA |

## Idempotency

- Key حداقل 128-bit random؛
- scope روی `(user, endpoint, key)`؛
- request hash ذخیره می‌شود؛ reuse با payload متفاوت `409`؛
- پاسخ نهایی 24 ساعت Replay می‌شود؛
- timeout client به معنی retry با همان key است.

## Pagination

Cursor-based:

```json
{
  "data": [...],
  "meta": { "next_cursor": "opaque", "has_more": true }
}
```

## Webhook Ingestion

Provider webhookها زیر `/v1/webhooks/{provider}` هستند:

- signature verification قبل از parse business payload؛
- replay window و event ID unique؛
- queue-first، پاسخ سریع؛
- raw payload رمزگذاری‌شده و با retention محدود؛
- entitlement فقط پس از server-side verification صادر می‌شود.

## API Compatibility with Bot

Bot جدید JWT کاربر دریافت نمی‌کند. Bot با service credential محدود و signed request به API متصل است و Telegram user ID را به‌عنوان subject خارجی می‌فرستد. API مسئول mapping و permission است.

