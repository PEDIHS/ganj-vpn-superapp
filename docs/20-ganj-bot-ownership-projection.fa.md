# قرارداد Ownership Projection ربات Ganj → Control API

این سند قرارداد Canonical برای انتقال **مالکیت تجاری سرویس‌های موجود ربات** به Ganj VPN است. این مسیر جایگزین خواندن مستقیم دیتابیس ربات توسط Android یا Control API می‌شود و عمداً از Schema داخلی PHP/MySQL ربات مستقل است.

## اصل Source of Truth

- ربات Ganj: مالکیت تجاری، Telegram numeric ID، محصول/پلن خریداری‌شده و شناسه سرویس upstream.
- Control API: هویت مشترک Ganj، entitlement projection، conflict/replay/checkpoint و device binding.
- PasarGuard: وضعیت زنده سرویس، مصرف، انقضا، node inventory و connection material.

ربات نباید config زنده VPN را به این feed وارد کند. وجود فیلدهایی مانند `subscription_url`, `config`, `credential`, `password`, `uuid`, `proxy`, `token` یا URIهایی مثل `vless://` باعث رد شدن کل page در source adapter می‌شود.

## Endpoint ربات

پیشنهاد مسیر ثابت:

`GET /api/internal/ganj-app/ownership-v1`

الزامات:

- فقط HTTPS؛
- Bearer token اختصاصی حداقل 32 بایت؛
- token در فایل/secret store نگهداری شود، نه PHP public config یا Git؛
- پاسخ `application/json` و حداکثر 512 KiB؛
- redirect ممنوع؛
- endpoint فقط read-only؛
- queryها فقط `limit` و `cursor`؛
- `limit` حداکثر 500؛
- ترتیب نتایج deterministic و cursor monotonic باشد؛
- endpoint هیچ عملیات write روی دیتابیس Bot انجام ندهد.

## Envelope

```json
{
  "items": [],
  "next_cursor": null,
  "has_more": false
}
```

اگر `has_more=true` است، `next_cursor` باید non-empty باشد.

## هر ownership item

```json
{
  "source_key": "ganj-bot-primary",
  "event_id": "service:4127:rev:19",
  "external_customer_id": "123456789",
  "telegram_subject": "123456789",
  "external_service_id": "4127",
  "plan_code": "premium-30d",
  "display_name": "Premium 30 Days",
  "status": "active",
  "expires_at": "2026-09-30T20:30:00.000Z",
  "traffic_limit_bytes": 107374182400,
  "traffic_used_bytes": 0,
  "device_limit": 2,
  "allowed_protocols": ["vless", "trojan"],
  "source_updated_at": "2026-08-28T12:00:00.000Z",
  "upstream_binding": {
    "provider_type": "pasarguard",
    "external_service_username": "ganj_123456789",
    "connector_ref": "pg-main"
  }
}
```

### Telegram identity

`telegram_subject` باید همان Telegram numeric user ID به‌صورت decimal string باشد. Username تلگرام کلید مالکیت نیست؛ username قابل تغییر است.

### event_id

`event_id` باید برای یک revision منطقی ثابت باشد. Retry همان revision باید همان `event_id` و همان payload را بدهد. reuse کردن همان event ID با payload متفاوت توسط Control API conflict محسوب می‌شود.

الگوی مناسب:

`service:<stable-service-id>:rev:<monotonic-revision>`

اگر Bot revision column ندارد، bridge باید fingerprint/version deterministic از فیلدهای business-safe بسازد؛ timestamp لحظه request نباید event identity را تغییر دهد.

### plan_code

`plan_code` باید به کد Canonical پلن در `control_plans.code` map شود. اگر Bot محصولی دارد که mapping ندارد، adapter نباید حدس بزند؛ نتیجه به conflict queue می‌رود تا Admin mapping را اصلاح کند.

### status

مقادیر مجاز:

- `pending`
- `active`
- `disabled`
- `expired`
- `revoked`

این status مالکیت تجاری است. حتی اگر Bot `active` بفرستد، سرویس PasarGuard هنگام نمایش/اتصال مجدداً live check می‌شود و می‌تواند access را fail-closed کند.

### traffic/expiry

این فیلدها برای projection و history هستند و نباید به‌عنوان runtime truth برای سرویس PasarGuard استفاده شوند. Paid runtime مقدار زنده را از PasarGuard می‌گیرد.

### upstream_binding

فقط locator metadata:

- `provider_type` فعلاً فقط `pasarguard`؛
- `external_service_username`؛
- `connector_ref` که به connector server-side Control API اشاره می‌کند.

ممنوع:

- PasarGuard admin username/password؛
- panel token؛
- base URL محرمانه در feed؛
- subscription URL؛
- VLESS/VMess/Trojan/Shadowsocks URI؛
- Xray JSON؛
- credential/UUID/password.

بعد از reconcile موفق، `LegacyProjectionPipeline` همین locator را به endpoint داخلی `/v1/internal/bot/service-upstream` در vertical مربوط به PasarGuard می‌فرستد. اگر binding fail شود، item failed حساب می‌شود و checkpoint page جلو نمی‌رود؛ retry بعدی entitlement را idempotent replay می‌کند و binding دوباره تلاش می‌شود.

## Pagination و checkpoint

Control API cursor را در PostgreSQL خودش ذخیره می‌کند. Bot endpoint نباید cursor مصرف‌شده را destructive mark کند.

ترتیب پیشنهادی:

1. stable update revision/time؛
2. stable service primary key به‌عنوان tie-breaker.

Cursor opaque است و می‌تواند base64url از tuple بالا باشد؛ اما نباید credential یا PII اضافه در cursor قرار گیرد.

## حذف / revoke

Service حذف‌شده از Bot نباید صرفاً از feed ناپدید شود. Bridge باید یک revision `revoked` یا `expired` تولید کند تا Control API entitlement قدیمی را فعال رها نکند.

برای hard-deleteهای قدیمی که event قابل تولید نیست، یک reconciliation/full-snapshot policy جدا لازم است؛ حذف بر اساس «در page نبود» ممنوع است.

## اجرای Worker

در Control API image:

```text
npm run legacy:sync
```

Environment اصلی:

```text
LEGACY_SOURCE_KEY=ganj-bot-primary
GANJ_BOT_PROJECTION_URL=https://bot.example/api/internal/ganj-app/ownership-v1
GANJ_BOT_PROJECTION_TOKEN_FILE=/run/secrets/ganj/bot-projection-token
LEGACY_SYNC_MODE=once
LEGACY_SYNC_BATCH_SIZE=100
LEGACY_SYNC_MAX_PAGES=20
LEGACY_UPSTREAM_BINDING_URL=https://control.example/v1/internal/bot/service-upstream
LEGACY_UPSTREAM_BINDING_TOKEN_FILE=/run/secrets/ganj/bot-sync-token
```

برای cron، `LEGACY_SYNC_MODE=once` ترجیح دارد. برای worker دائم، `loop` با interval حداقل 60 ثانیه مجاز است.

## Logging

Runner فقط آمار page/count را log می‌کند:

- received
- applied
- unchanged
- conflict
- replay
- failed

Telegram ID، external service ID، username، payload و secret نباید در log worker چاپ شوند.

## پیاده‌سازی سمت Bot

Endpoint PHP باید با queryهای واقعی نسخه فعلی Bot نوشته شود. نام جدول/ستون نباید در Control API hardcode شود. Bridge داخل Bot مسئول map کردن schema جاری (`service_other`/محصول/کاربر/panel binding مطابق نسخه نصب‌شده) به Contract بالا است.

اگر schema در یک نسخه قدیمی متفاوت است، Bridge باید آن نسخه را در Bot تشخیص دهد یا fail-closed شود؛ Control API نباید با حدس نام column به MySQL Bot وصل شود.

## Definition of Done

1. حساب قدیمی Bot با Telegram numeric ID به Ganj account لینک می‌شود.
2. active service یک بار در Control API ساخته می‌شود.
3. replay هیچ duplicate service نمی‌سازد.
4. renewal همان service را update می‌کند.
5. ownership collision به conflict queue می‌رود.
6. plan نامشخص conflict می‌شود، نه guess.
7. PasarGuard locator بعد از reconcile به service bind می‌شود.
8. `My Services` بعد از account link همان entitlement را نمایش می‌دهد.
9. status/traffic/expiry/node در استفاده واقعی از PasarGuard live می‌آید.
10. هیچ raw VPN material در Bot feed، reconciliation DB، log یا Android دیده نمی‌شود.
