# راهنمای انتشار Android در Google Play — Internal و Closed Beta

این مسیر فقط برای بسته‌ای است که Workflow امضای Android آن را از Tag محافظت‌شده ساخته، بررسی، امضا و Attest کرده است. هیچ AAB محلی یا فایل امضانشده مستقیماً به Play Console فرستاده نمی‌شود.

## پیش‌نیازهای بیرون از مخزن

1. برنامه با Package ID برابر `com.ganj.vpn` در Play Console ایجاد شده باشد.
2. Play App Signing فعال و Upload Certificate ثبت شده باشد.
3. Google Play Android Developer API در Google Cloud فعال باشد.
4. یک Service Account با حداقل دسترسی لازم به همین برنامه متصل باشد.
5. JSON همان Service Account فقط در Secret محافظت‌شدهٔ `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` قرار گیرد.
6. Environmentهای `android-play-candidate` و `android-play-production` دارای Required Reviewer باشند.
7. متغیرهای URL و Secrets امضای Workflow اصلی مطابق Runbook انتشار تنظیم شده باشند.
8. Privacy Policy، Data Safety، VPN Service declaration، Store Listing، گروه تست و کشورهای مجاز در Console تکمیل شده باشند.

Service Account، Keystore، Password، Private Key یا فایل JSON نباید در Git، Artifact عمومی، Log یا Issue قرار گیرد.

## ترتیب انتشار

1. `versionName` و `versionCode` را افزایش بده.
2. همهٔ CIهای `main` و تست دستگاه واقعی را سبز کن.
3. یک Annotated Tag مانند `android-v0.4.0-rc.1` روی Commit قابل‌دسترسی از `main` بساز.
4. Workflow `Android signed AAB release` را دقیقاً از همان Tag اجرا کن.
5. برای اولین تحویل:
   - `stage = candidate`
   - `publish_to_play = true`
   - `play_track = internal`
   - `play_status = draft`
6. پس از بررسی Draft در Console، اجرای بعدی برای تسترهای داخلی می‌تواند `completed` باشد.
7. بعد از تست واقعی خرید، ورود، دریافت Profile، اتصال، Reconnect و لغو اشتراک، Track را به `alpha` تغییر بده؛ این Track در این پروژه Closed Beta است.
8. انتشار Production از این Workflow عمداً پشتیبانی نمی‌شود. Promotion عمومی فقط بعد از Go/No-Go امنیت، Crash/Vitals، Data Safety و بازبینی انسانی Play Console انجام می‌شود.

## کنترل‌های Fail-closed

Workflow پیش از ارسال موارد زیر را بررسی می‌کند:

- دقیقاً یک Signed AAB موجود باشد.
- SHA-256 فایل با Release Evidence یکسان باشد.
- Commit داخل Evidence با Tag انتخاب‌شده یکسان باشد.
- Artifact Guard دوباره اجرا شود.
- Mapping فایل موجود باشد.
- Track فقط `internal` یا `alpha` باشد.
- Status فقط `draft` یا `completed` باشد.
- JSON حساب سرویس وجود داشته و ساختار Service Account داشته باشد.
- Environment مربوطه تأیید انسانی گرفته باشد.

Action انتشار با Commit کامل و تغییرناپذیر pin شده است. خروجی موفق یک `play-publication-evidence.json` با Commit، Version، Track، Status، Run و Edit ID تولید می‌کند.

## Gateهای Closed Beta

قبل از `alpha/completed`:

- ورود Guest و Telegram روی Backend واقعی
- ثبت دو کلید مستقل Device و چرخش Session
- خرید Google Play در License Tester و RTDN
- اتصال واقعی VLESS/Trojan روی Wi-Fi و Cellular
- عدم DNS leak و پاک‌سازی Profile پس از Disconnect
- Reconnect پس از تغییر شبکه و Doze
- Refund، Cancel، Expire و Restore Purchase
- Android 8، 10، 12، 14، 15 و 16
- TalkBack، RTL، Large Font و Dark Mode
- Crash-free session و ANR قابل‌قبول
- Backup/Restore دیتابیس و Rollback نسخه
- Privacy Policy و Data Safety دقیقاً مطابق Telemetry واقعی

## Rollback

- اگر Build هنوز Draft است، آن را از Play Console منتشر نکن.
- اگر Internal/Alpha منتشر شده، Rollout را متوقف و نسخهٔ اصلاحی با `versionCode` بالاتر بساز.
- AAB قبلی را دوباره با همان Version Code آپلود نکن.
- Release Evidence، Mapping، SBOM و Log رخداد را برای Incident نگه دار.
