# Google Play Release and Compliance Plan

## Target SDK

از 2026-08-31، App و Update جدید باید Android 16 / API 36 یا بالاتر را Target کند. پروژه با `compileSdk=36+` و `targetSdk=36` شروع می‌شود و رفتارهای Android 16 در CI تست می‌شوند.

مرجع: [Google Play target API requirements](https://developer.android.com/google/play/requirements/target-sdk)

## VpnService

Ganj VPN مجاز است چون VPN کارکرد اصلی آن است، ولی باید:

- استفاده از VpnService در Listing توضیح داده شود؛
- داده از Device تا Tunnel endpoint رمز شود؛
- جمع‌آوری Sensitive Data با prominent disclosure و consent باشد؛
- Traffic برای Monetization redirect/manipulate نشود؛
- Play Console declaration و demo قابل بررسی ارائه شود.

مرجع: [Google Play — Permissions and APIs, VPN Service](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en#vpn_service)

## Payments

VPN subscription یک Digital Service است. بنابراین:

- Play flavor از Google Play Billing و server-side verification استفاده می‌کند؛
- Play Billing Library روی `9.1.0` پین شده و ارتقا فقط همراه Release Notes review و تست Sandbox انجام می‌شود؛
- Wallet/Telegram/Gateway فقط در صورتی داخل Play build نمایش داده می‌شود که برنامه/منطقه مجوز آن را بدهد؛
- Direct APK flavor می‌تواند روش‌های فعلی را داشته باشد؛
- Entitlement backend واحد است ولی `purchase_channel` و policy جدا هستند؛
- لینک خارجی پرداخت در Play build بدون eligibility ممنوع است.
- `PENDING` هیچ Entitlement فعال نمی‌کند؛ Purchase Token به Backend می‌رود و وضعیت از Android Publisher API دوباره خوانده می‌شود؛
- RTDN فقط trigger همگام‌سازی است و به‌تنهایی منبع حقیقت خرید نیست؛
- خرید اولیه، Token جدید، تغییر Plan و ثبت‌نام مجدد پس از Grant در Backend acknowledge می‌شوند؛ Renewal با همان Token دوباره acknowledge نمی‌شود.

مراجع: [Google Play Billing integration](https://developer.android.com/google/play/billing/integrate)، [Play Billing release notes](https://developer.android.com/google/play/billing/release-notes)، [Android Publisher subscriptions v2](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2)

## Permission Budget

| Permission/API | تصمیم |
|---|---|
| INTERNET | Required |
| ACCESS_NETWORK_STATE | Required |
| CHANGE_NETWORK_STATE | فقط اگر API واقعاً لازم باشد |
| POST_NOTIFICATIONS | runtime و بعد از توضیح value |
| FOREGROUND_SERVICE / SPECIAL_USE | برای VPN با subtype/description دقیق |
| RECEIVE_BOOT_COMPLETED | فقط اگر Auto Connect روشن باشد |
| CAMERA | حذف؛ اپ هیچ QR import یا مسیر ورود دستی کانفیگ ندارد |
| QUERY_ALL_PACKAGES | حذف؛ targeted `<queries>` یا declaration مستدل |
| Location | ممنوع؛ Smart Connect از coarse server-side region استفاده می‌کند |
| SMS/Call Log | ممنوع در این اپ |
| Storage broad access | ممنوع؛ SAF/Photo Picker |

## Data Safety Draft Categories

احتمالاً Collect می‌شود:

- account/profile و Telegram identity؛
- purchase/transaction data؛
- app interactions و crash diagnostics با consent/necessity؛
- device identifiers محدود برای fraud/security؛
- aggregate VPN connection diagnostics.

Collect نمی‌شود:

- browsing history؛
- web destination/DNS query؛
- traffic content؛
- precise location؛
- contacts، SMS یا call logs.

فرم نهایی باید دقیقاً با SDKها و رفتار build نهایی تطبیق داده شود؛ این سند جایگزین Declaration واقعی نیست.

## Ads in Free Plan

- Ad فقط داخل UI؛ نه tunnel injection، DNS rewrite یا proxy monetization؛
- network traffic/destination برای ad targeting استفاده نمی‌شود؛
- در Connect transition و system VPN permission dialog interstitial نمایش داده نمی‌شود؛
- consent و age/region rules رعایت می‌شود؛
- Ad SDK باید جداگانه از نظر permissions/data safety ممیزی شود.

## Signing

- Play App Signing فعال؛ upload key و release control جدا؛
- Direct APK با release key پایدار و قابل backup؛
- secret material داخل GitHub Actions قرار نمی‌گیرد مگر به‌شکل Secret encrypted و با access محدود؛ ترجیح OIDC/HSM؛
- artifact شامل provenance، SBOM، mapping file و checksum؛
- applicationId پس از Production تغییر نمی‌کند.

## Store Listing Evidence

- یک ویدیو کوتاه: launch → disclosure → VPN permission → connect → disconnect؛
- screenshots واقعی از Home/Servers/Connect/Store/Account؛
- Privacy Policy عمومی با deletion path؛
- support email/domain؛
- explanation روشن Free limitations و ads؛
- Subscription price، renewal، cancellation و refund قبل از purchase.

## Pre-submission Gate

- [ ] target API 36+
- [ ] VpnService declaration approved
- [ ] Play Billing server verification
- [ ] Data Safety matches binary/SDK scan
- [ ] privacy policy and in-app account deletion
- [ ] no broad SMS/storage/location permissions
- [ ] cleartext disabled
- [ ] exported components reviewed
- [ ] AAB signed and Play pre-launch report clean
- [ ] crash/ANR/performance thresholds met
- [ ] closed-track test account and server available for reviewer
