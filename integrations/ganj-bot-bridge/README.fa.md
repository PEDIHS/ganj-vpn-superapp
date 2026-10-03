# Ganj Bot Bridge — اتصال امن اپ به ربات موجود

این Bridge برای نصب **کنار Ganj Bot موجود** طراحی شده است و سورس ربات، Bot Token، Credential دیتابیس، کانفیگ VPN یا PasarGuard credential را داخل پروژه اپ کپی نمی‌کند. Installer فقط از CLI اجرا می‌شود، قبل از تغییر `index.php` Backup می‌سازد و Hook مدیریت‌شده با marker ثابت `GANJ_APP_BRIDGE_V1` اضافه می‌کند.

## خروجی نصب

- Telegram Bot Approval برای Login اپ با deep-link یک‌بارمصرف `ga_<opaque-token>`.
- نمایش دکمه‌های صریح «تأیید ورود» و «رد درخواست» داخل همان Ganj Bot.
- ارسال نتیجه Approval به Control API با HMAC-SHA256 + timestamp؛ Bot اجازه صدور Access Token اپ ندارد.
- Approval به user/device/state/PKCE همان درخواست App متصل است و replay/expiry/wrong-device به‌صورت fail-closed رد می‌شود.
- جدول افزایشی `ganj_app_ownership_journal` و Triggerهای Insert/Update/Delete فاکتور.
- Endpoint فقط‌خواندنی `/.ganj-app-bridge/projection.php` برای ownership sync؛ این Endpoint کانفیگ، UUID، رمز پنل، Subscription URL، `user_info` یا Bot Token را خروجی نمی‌دهد.
- نگاشت `Service_location` ربات به `connector_ref` پاسارگارد؛ وضعیت زنده، حجم، انقضا و node/config truth همچنان از PasarGuard می‌آید.

## پیش‌نیاز

- Ganj Bot با hook marker سازگار نسخه `0.1.5.4`.
- PHP 8.1+، PDO و cURL.
- MySQL privilege لازم برای `CREATE TABLE` و `CREATE TRIGGER`.
- HTTPS معتبر برای Control API.
- یک HMAC secret تصادفی حداقل 32 بایتی برای Bot Approval، در فایل `0600` خارج از public bot root.
- یک Bearer مستقل حداقل 32 بایتی برای Projection، در فایل `0600` خارج از public bot root.
- فایل JSON نگاشت panel name به PasarGuard connector ref، خارج از public root.
- دایرکتوری Backup خصوصی با permission `0700` خارج از public root.

## متغیرهای نصب

Secretها را داخل command line قرار ندهید. ابتدا فایل‌ها را بسازید و permission را محدود کنید، سپس فقط path آن‌ها را export کنید:

```bash
export GANJ_CONTROL_API_URL=https://control.example.com
export GANJ_BOT_APPROVAL_HMAC_SECRET_FILE=/secure/ganj/bot-approval-hmac-secret
export GANJ_BOT_PROJECTION_TOKEN_FILE=/secure/ganj/bot-projection-token
export GANJ_PASARGUARD_CONNECTOR_MAP_FILE=/secure/ganj/pasarguard-panel-map.json
export GANJ_BOT_BRIDGE_BACKUP_DIR=/secure/ganj/backups
php ganj-bot-bridge/install.php --bot-root=/path/to/bot-root
```

پس از verification، پوشه Installer آپلودشده `ganj-bot-bridge` را حذف کنید. پوشه نصب‌شده `.ganj-app-bridge` باید باقی بماند.

## تنظیم Control API

حداقل این متغیرها برای فعال‌شدن Primary Bot Approval لازم‌اند:

```text
GANJ_BOT_USERNAME=<bot username without @>
GANJ_BOT_APPROVAL_HMAC_SECRET=<same secret stored in GANJ_BOT_APPROVAL_HMAC_SECRET_FILE>
GANJ_BOT_APPROVAL_REDIRECT_URIS=https://auth.example.com/ganj/telegram/callback
```

`GANJ_BOT_APPROVAL_HMAC_SECRET` را از secret manager/runtime injection تأمین کنید؛ آن را commit نکنید. اگر این سه مقدار کامل نباشند، Bot Approval با `503 bot_approval_unavailable` fail-closed می‌شود ولی بقیه Control API از کار نمی‌افتد.

برای ownership projection نیز URL و token مربوط به projection را در adapter/reconciliation runtime تنظیم کنید. برای اتصال سرویس‌های Bot به PasarGuard، mapping فقط connector reference نگه می‌دارد و هیچ raw config یا provider credential به Android نمی‌رود.

## Flow ورود

1. Android یک Guest Device Session امن دارد.
2. App از `/v1/auth/telegram/bot/start` یک request کوتاه‌عمر می‌گیرد.
3. App لینک `https://t.me/<bot>?start=ga_<token>` را باز می‌کند.
4. Bridge داخل Bot به کاربر دکمه تأیید/رد نشان می‌دهد.
5. Bridge نتیجه را با `X-Ganj-Bot-Timestamp` و `X-Ganj-Bot-Signature` به `/v1/internal/telegram/bot-approval` می‌فرستد.
6. App پس از برگشت، status request را می‌خواند.
7. فقط در حالت `approved`، App با state + PKCE verifier همان device درخواست exchange می‌دهد.
8. Auth Session backend حساب Telegram را link و session جدید را صادر می‌کند؛ Bot هرگز App token صادر نمی‌کند.
9. Android My Services و Enterprise state را refresh می‌کند.

## Fail-closed / Security

Installer در شرایط زیر متوقف می‌شود: Bot marker ناسازگار، Secret داخل web-root، permission باز، URL غیر HTTPS، connector map نامعتبر، نبود Trigger privilege، index symlink یا نبود Backup directory امن.

Approval token در Bot DB ذخیره نمی‌شود و Bridge آن را log نمی‌کند. دیتابیس Control API نیز فقط SHA-256 approval token/state را نگه می‌دارد. Callback Bot دارای timestamp window محدود، HMAC و idempotent event id است. درخواست expired، replayed، wrong-device یا binding mismatch باید رد شود.

## Read-only subscription display metadata (Alpha 0.3.11)

The app uses `/api/internal/ganj-app/service-metadata-v1/` beside the existing private connection resolver. Deploy `service-metadata.php` as that directory's `index.php` and `service-metadata-core.php` alongside it. This deployment layout resolves `config.php`, `function.php` and `panels.php` from the bot root four directories above. The bridge installer does not install this optional endpoint automatically.

The endpoint accepts authenticated POST only with the existing `/etc/ganj-bot/app-projection-token`; keep that file outside the web root. Requests contain invoice/customer IDs and a boolean `include_usage`. Invoice IDs may be alphanumeric; customer IDs remain numeric. The current invoice owner is rechecked before returning username or querying the panel. SQL statements are prepared. Responses are `no-store` and contain only `service_username`, traffic fields, availability and the matching external IDs; provider objects/subscription links are never serialized.

The Control API automatically enables this display source when the private connection resolver is configured. It reads only the caller's projections, fetches cheap names first and uses at most three bounded usage batches. Unknown, slow or unsupported panel usage is represented by `traffic_usage_available: false`, not a fabricated zero. This display source does not write consumption/entitlement state or affect profile issuance. No database migration or bot restart is required.

Validation: `php -l service-metadata.php`, `php -l service-metadata-core.php`, `php service-metadata-core.test.php` and the Control API metadata/ownership unit tests. Deployment must preserve the independently installed connection parser hotfix and verify API health/readiness plus 401 for unauthenticated metadata POST and 405 for GET.
