# ممیزی وضع موجود

## دامنه بررسی

| ورودی | SHA-256 | کاربرد |
|---|---|---|
| GanjVPN 2.6.4 arm64 APK | `90f6d99f6344064c90843ab37fef845197ae01877ac550a8378f1fee206b8a49` | ممیزی کلاینت فعلی |
| v2rayNG 2.2.6 F-Droid arm64 APK | `117400b0bc4431f512bb9a23eae6a8ce43f74a4977596a8ae635a2894ceb42d5` | مقایسه ساختاری |
| Bot/Panel ZIP | `2a42de657a3a6ff2366b8e78ace0210fb56a36e4a7230f15bd19085edce3c81f` | کشف قابلیت‌ها و Contract فعلی |
| Firebase configuration | `8334f04e2663036210848191c6f549bd1a390580b892ed699c4cfe3e4822bae6` | بررسی Application ID؛ محتوا Commit نمی‌شود |

## APK فعلی

### مشخصات

- Package: `com.ganj.vpn.android`
- Version: `2.6.4` (`versionCode=5073604`)
- minSdk: 24
- targetSdk: 29
- compileSdk: 37
- ABI: فقط `arm64-v8a`
- App class و بیشتر Activity/Serviceها هنوز در Namespace `com.v2ray.ang` هستند.
- گواهی امضای Release با CN اختصاصی Ganj وجود دارد؛ کلید خصوصی در ورودی‌ها ارائه نشده و نباید بازتولید شود.

### نسبت با v2rayNG

هر دو APK دقیقاً 1118 مسیر غیر `META-INF` دارند. 1094 مسیر Byte-for-byte یکسان و فقط 24 مسیر متفاوت‌اند: Manifest، دو DEX، `resources.arsc` و منابع برند. نتیجه: کلاینت فعلی Fork توسعه‌یافته نیست؛ Rebrand کم‌عمق است.

### مشکلات Release و Security

| مشکل | شدت | اقدام |
|---|---|---|
| targetSdk 29 | Blocker | پروژه جدید از ابتدا API 36 را Target کند. |
| `usesCleartextTraffic=true` | High | Cleartext عمومی ممنوع؛ فقط Loopback به Core با استثنای محدود. |
| `allowBackup=true` | High | داده‌های Token/Config از Backup خارج و ترجیحاً Backup خاموش شود. |
| `QUERY_ALL_PACKAGES` | High/Policy | با `<queries>` محدود برای Split Tunneling جایگزین شود یا Declaration دقیق ارائه شود. |
| Package mismatch با Firebase | Blocker | Firebase App جدید برای Application ID نهایی صادر شود. |
| فقط arm64 | Medium | Play AAB و حداقل arm64 + armeabi-v7a بر اساس اندازه/تقاضا؛ x86_64 فقط QA. |
| وابستگی مستقیم به ساختار v2rayNG | Legal/High | کد v2rayNG کپی نشود؛ مجوز GPL-3.0 رعایت و Core مستقل ساخته شود. |

## Bot و Web موجود

ZIP شامل بیش از 1000 فایل و حدود 175 فایل Source غیر Vendor است. قابلیت‌های کشف‌شده:

- فروش محصول، پلن سفارشی، تمدید، ارتقا، تغییر لوکیشن و خرید انبوه؛
- کیف پول، پرداخت بانکی/رمزارز، رسید و Smart Receipt؛
- نمایندگی و Seller portal؛
- Affiliate، Lottery/Wheel، Gift، Loyalty/Missions؛
- Marketing links، Banner، Broadcast و Automation؛
- Telegram Mini App و Web Account؛
- Adapterهای Marzban، X-UI، Hiddify، MikroTik و IBSng؛
- پنل ادمین PHP و APIهای متعدد.

### نقاط مثبت قابل استفاده

- Telegram `initData` با HMAC سمت سرور اعتبارسنجی می‌شود.
- Login پنل از `password_hash`، CSRF و Rate Limit استفاده می‌کند.
- بسیاری از عملیات خرید دارای Idempotency Key هستند.
- Account link با Token Hash و تراکنش دیتابیس پیاده شده است.

### بدهی‌های معماری

- فایل‌های بسیار بزرگ و چندمسئولیتی (`admin.php` حدود 700KB و `index.php` حدود 489KB).
- SQL و Business Logic و Transport در یک فایل و بدون Contract نسخه‌دار.
- Migrationها در Runtime و داخل Functionهای کاربردی اجرا می‌شوند.
- نام‌گذاری/Collation/نوع ID یکنواخت نیست.
- عملیات فروش و Network Provider به Schema قدیمی و متغیرهای Global وابسته‌اند.

### مشکل نشست Mini App

`api/verify.php` پس از اعتبارسنجی Telegram یک Token تصادفی می‌سازد و مقدار خام آن را در ستون `user.token` جایگزین می‌کند. `api/miniapp.php` نیز `user_id` را از Body می‌گیرد و Bearer را با همان مقدار مقایسه می‌کند.

پیامدها:

- فقط یک Token برای هر کاربر؛ ورود جدید نشست قبلی را می‌شکند.
- Expiry، Refresh، Device Binding و Revocation مستقل وجود ندارد.
- Token خام در Database است.
- Scope/Audience و Rotation وجود ندارد.

این API نباید مستقیماً API اپ Native شود.

## Exposure داخل ZIP

Archive فعلی دارای Bot Token، Config عملیاتی، Error Log، Lock، Cache، Receipt image و فایل‌های Storage زمان اجرا است. این Archive فقط ورودی خصوصی Migration است. اقدامات اجباری:

1. Bot Token پیش از اولین استقرار CI/CD Rotate شود.
2. Secretها به Secret Manager/Environment منتقل شوند.
3. Runtime Data از Source Package جدا شود.
4. Git history با Secret Scanner کنترل شود.
5. Receipt و Logها با Retention و Access Control نگهداری شوند.

## Migration Mapping اولیه

| Legacy | مدل جدید |
|---|---|
| `user` | `users` + `user_identities` + `wallet_accounts` |
| `invoice` | `services` + `service_entitlements` |
| `product`, `category` | `plans` + `plan_prices` + `plan_server_rules` |
| `Payment_report` | `orders` + `payments` + `wallet_ledger_entries` |
| `marzban_panel` | `vpn_servers` + `provider_connections` |
| `ganj_web_notifications` | `notifications` + `notification_deliveries` |
| `ganj_web_banners` | `campaigns` + `campaign_creatives` |
| `user.token` | `auth_sessions` + hashed rotating refresh tokens |

