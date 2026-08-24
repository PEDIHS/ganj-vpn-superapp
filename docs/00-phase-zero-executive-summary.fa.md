# خلاصه اجرایی فاز صفر

## مسئله

Ganj VPN امروز دو دارایی جدا دارد:

1. ربات و پنل PHP بسیار پرقابلیت برای فروش، کیف پول، سرویس، پرداخت، نمایندگی و Mini App؛
2. یک APK برندشده که از نظر ساختار هنوز تقریباً همان v2rayNG است و با ربات همگام نیست.

هدف محصول جدید، وصل‌کردن این دو با یک API امن و ساخت یک تجربهٔ واقعاً اختصاصی است؛ نه ادامهٔ Repackaging.

## نتیجه ممیزی

- 1094 مورد از 1118 ورودی غیرامضایی APK فعلی با APK مرجع v2rayNG یکسان است.
- APK فعلی `targetSdkVersion=29` دارد و برای انتشار جدید Google Play در سال 2026 مناسب نیست.
- `usesCleartextTraffic=true`، `allowBackup=true` و `QUERY_ALL_PACKAGES` فعال‌اند و باید حذف یا محدود شوند.
- Application ID فعلی `com.ganj.vpn.android` است، اما فایل Firebase برای `com.ganj.vpn` صادر شده است.
- ربات فعلی API و Telegram Mini App دارد، ولی نشست موبایل به‌صورت یک Token خام، بدون Expiry و تک‌دستگاهی ذخیره می‌شود.
- ZIP شامل Bot Token، Log و Runtime Artifact است؛ Commit مستقیم آن ممنوع است.

## محصول پیشنهادی

Ganj VPN به سه سطح تقسیم می‌شود:

### Data Plane

هسته Xray، Android `VpnService`، TUN، DNS، Kill Switch، مسیرها و آمار محلی اتصال. این لایه بدون دسترسی به Wallet یا داده‌های مارکتینگ کار می‌کند.

### Control Plane

API نسخه‌دار برای حساب، سرویس‌ها، سرورها، Entitlement، Config Envelope، پرداخت، اعلان و تنظیمات. این لایه هیچ داده‌ای از مقصدهای مرور دریافت نمی‌کند.

### Experience Plane

اپ Android، پنل React، Telegram Bot و Mini App. تمام این کلاینت‌ها از قرارداد مشترک API استفاده می‌کنند.

## اصول غیرقابل مذاکره

- اتصال VPN حتی در خرابی سیستم مارکتینگ یا پنل ادمین نباید مختل شود.
- Config خام در API List، Log، Analytics، Push یا Crash Report ظاهر نمی‌شود.
- Guest بدون شماره/Telegram ممکن است و فقط Free Entitlement می‌گیرد.
- حساب Telegram با OIDC + PKCE متصل می‌شود و لینک ربات فقط توکن یک‌بارمصرف 5 دقیقه‌ای دارد.
- Sessionها چنددستگاهی، قابل لغو و دارای Refresh Token چرخشی هستند.
- تبلیغات داخل رابط نمایش داده می‌شوند؛ هیچ Traffic Injection یا تحلیل ترافیک برای تبلیغ مجاز نیست.
- پرداخت نسخه Play از Play Billing تبعیت می‌کند؛ روش‌های خارج Play در Flavor مجزا قرار می‌گیرند.

## انتخاب تکنولوژی

| لایه | انتخاب | دلیل |
|---|---|---|
| Android | Kotlin + Jetpack Compose | کنترل مستقیم `VpnService`، عملکرد، تست و UX Native |
| Architecture | Modular Clean + MVI | جداسازی VPN Core از Store/Account و کاهش ریسک |
| Local Data | Room + DataStore + Android Keystore | Cache ساختاریافته و نگهداری امن Token/Key |
| Backend | Node.js Modular Monolith | Control API کم‌وابستگی، مرزهای تست‌پذیر و اتصال Adapterمحور به PHP/MariaDB فعلی |
| Database | PostgreSQL | Ledger، Concurrency، JSONB، Constraints و گزارش‌گیری |
| Cache/Queue | Redis | Rate Limit، Job، Lock و Outbox delivery |
| Admin | React + TypeScript + Tailwind | پنل سریع، Type-safe و Design Token مشترک |
| Push | FCM HTTP v1 | ارسال کم‌مصرف و هدف‌گیری سمت Backend |
| Observability | OpenTelemetry + Sentry-compatible collector | Trace بدون ثبت Traffic Content |

## Migration Strategy

بازنویسی Big Bang انجام نمی‌شود:

1. API جدید در حالت Read-through به دیتابیس/پنل‌های فعلی متصل می‌شود.
2. جدول نگاشت `legacy_links` هویت Telegram، User و Service قدیمی را به ID جدید وصل می‌کند.
3. خریدها ابتدا Dual-write با Transactional Outbox هستند.
4. پس از تطبیق گزارش و Balance، مالکیت هر دامنه به Backend جدید منتقل می‌شود.
5. Bot و Mini App به Clientهای همان API تبدیل می‌شوند.

## تعریف موفقیت MVP

- Guest در کمتر از 20 ثانیه به Free Server متصل شود.
- کاربر Telegram در کمتر از 45 ثانیه حساب و سرویس‌های فعلی خود را Sync کند.
- اتصال موفق P50 کمتر از 1.8 ثانیه و P95 کمتر از 4 ثانیه روی شبکه سالم باشد.
- Crash-free users حداقل 99.7% و ANR کمتر از 0.25% باشد.
- هیچ Secret یا Config در Log/Crash/Analytics مشاهده نشود.
- خرید Play، Wallet Direct و Sync ربات Idempotent و قابل Audit باشند.
