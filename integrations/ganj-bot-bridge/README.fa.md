# نصب Bridge اپلیکیشن کنار ربات Ganj

این بسته **هیچ بخشی از سورس ربات، توکن ربات، دیتابیس یا کانفیگ VPN را داخل مخزن اپ کپی نمی‌کند**. پوشه را کنار روت ربات Ganj نسخه 0.1.5.4 قرار دهید و Installer را فقط از CLI اجرا کنید. Installer قبل از تغییر `index.php` یک Backup می‌سازد و فقط یک Hook مدیریت‌شده با نشانگر `GANJ_APP_BRIDGE_V1` اضافه می‌کند.

## خروجی نصب

- تأیید ورود اپ در همان ربات با Deep Link از نوع `APP-<one-time-token>`؛ رفتار آن با ورود فعلی سایت هم‌راستا است.
- جدول افزایشی `ganj_app_ownership_journal` و سه Trigger برای Insert/Update/Delete فاکتور.
- Endpoint فقط‌خواندنی `/.ganj-app-bridge/projection.php` برای همگام‌سازی مالکیت؛ این Endpoint کانفیگ، UUID، رمز پنل، Subscription URL، `user_info` و توکن ربات را خروجی نمی‌دهد.
- نگاشت `Service_location` ربات به `connector_ref` پاسارگارد؛ وضعیت زنده، حجم، انقضا و نودها همچنان از PasarGuard خوانده می‌شوند.
- اتصال `invoice.id_user` به Telegram subject و `invoice.id_invoice` به شناسه پایدار سرویس.

## پیش‌نیاز

- PHP 8.1 یا جدیدتر، PDO و cURL.
- دسترسی MySQL برای `CREATE TABLE` و `CREATE TRIGGER`.
- دو Bearer مستقل حداقل 32 کاراکتری در فایل‌های `0600` خارج از public bot root: یکی برای Bot Approval و یکی برای Projection.
- فایل JSON نگاشت نام پنل ربات به connector reference پاسارگارد. این فایل Credential ندارد، اما باید خارج از روت عمومی نگهداری شود.

## اجرا

پوشه `ganj-bot-bridge` را داخل روت ربات آپلود کنید و متغیرهای زیر را در Session شل تنظیم کنید؛ مقدار Secret را در Command line قرار ندهید:

```bash
export GANJ_CONTROL_API_URL=https://control.example.com
export GANJ_BOT_APPROVAL_TOKEN_FILE=/secure/ganj/bot-approval-token
export GANJ_BOT_PROJECTION_TOKEN_FILE=/secure/ganj/bot-projection-token
export GANJ_PASARGUARD_CONNECTOR_MAP_FILE=/secure/ganj/pasarguard-panel-map.json
php ganj-bot-bridge/install.php --bot-root=/path/to/bot-root
```

پس از تست، پوشه آپلودشده `ganj-bot-bridge` را حذف کنید. پوشه نصب‌شده `.ganj-app-bridge` باید باقی بماند.

## تنظیم Control API

- `TELEGRAM_BOT_APPROVAL_INTERNAL_TOKEN_FILE` باید به Secret همسان با Bot Approval اشاره کند.
- `GANJ_BOT_PROJECTION_URL` برابر URL کامل Projection و `GANJ_BOT_PROJECTION_TOKEN_FILE` برابر Secret همسان Projection است.
- `LEGACY_SOURCE_KEY=ganj-bot-primary` یا یک نام ثابت دیگر.
- برای اتصال سرویس به PasarGuard، `LEGACY_UPSTREAM_BINDING_URL` باید به `/v1/internal/bot/service-upstream` ختم شود و Token آن با `GANJ_BOT_SYNC_INTERNAL_TOKEN_FILE` یکسان باشد.

## Fail-closed

Installer روی نسخه‌ای که Marker مورد انتظار Ganj 0.1.5.4 را نداشته باشد، روی Secret داخل Web root، روی Permission باز، URL غیر HTTPS، Map نامعتبر یا نبود Trigger privilege متوقف می‌شود. حذف دستی فایل اصلی ربات، تغییر کانفیگ کاربر یا خواندن مستقیم PasarGuard از Android انجام نمی‌شود.
