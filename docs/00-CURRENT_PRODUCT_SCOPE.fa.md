# Ganj VPN — Current Product Scope Lock

> **Status:** Canonical scope clarification for the current delivery cycle  
> **Purpose:** جلوگیری از ابهام بین Requirementهای واقعی پروژه، Roadmap تاریخی، Branchهای اجرایی و Post-MVP.

## 1. Scope فعلی: Android-first و Android-only برای Delivery جاری

در چرخه توسعه فعلی، **تنها Client محصولی که باید ساخته، تست و برای انتشار آماده شود Android است**.

- Android Native با Kotlin + Jetpack Compose در Scope فعلی است.
- iOS، Windows، macOS و Linux در Scope اجرایی فعلی نیستند.
- هر اشاره به iOS/Desktop در Roadmap یا Project Bible صرفاً **Post-MVP / Future Platform** است و نباید باعث ایجاد Workstream فعال قبل از بسته‌شدن Android Production Critical Path شود.
- الهام از Apple HIG/iOS به معنی ساخت iOS client در فاز فعلی نیست؛ فقط Design/Interaction reference است.

## 2. Product Type Lock

Ganj VPN یک VPN Super App مدیریت‌شده و Subscription-driven است، نه یک V2Ray/Xray config importer عمومی.

کاربر نهایی نباید بتواند URI، QR، Clipboard، فایل، Subscription URL یا Xray JSON خام وارد کند. اتصال فقط از سرویس/اشتراک خود کاربر و Entitlement معتبر صادرشده توسط Backend انجام می‌شود.

## 3. Commerce Lock

خرید و تمدید Subscription جزو Core Product است، نه Feature ثانویه.

- Play flavor: Google Play Billing + server-side verification + RTDN/reconciliation.
- Direct flavor: Wallet / Telegram / Gateway / روش‌های مستقیم مجاز.
- هیچ purchase success سمت Client به‌تنهایی Entitlement ایجاد نمی‌کند.
- سرویس‌های خریداری‌شده قبلی در Telegram Bot باید پس از Link شدن حساب داخل App قابل شناسایی و استفاده باشند؛ کاربر نباید مجبور به ورود کانفیگ یا خرید مجدد شود.

## 4. Telegram Integration Lock

Telegram integration فقط Login نیست. Target نهایی شامل:

- Telegram OIDC + PKCE؛
- Bot Deep-Link fallback امن؛
- Account mapping؛
- Legacy subscription/service sync؛
- renewal/expiry/revoke reconciliation؛
- My Services population؛
- audit و conflict handling.

## 5. UI/UX Lock

UI فعلی روی `main` Functional/Foundation UI است و **Final Product Design محسوب نمی‌شود**.

مرجع قطعی UI/UX و Visual Brand:

[`15-android-ui-ux-brand-system.fa.md`](15-android-ui-ux-brand-system.fa.md)

Final Android UI باید:

- فارسی و انگلیسی resource-based؛
- RTL/LTR واقعی؛
- Dark/Light/System؛
- Responsive؛
- Accessibility/TalkBack/Large Font؛
- Motion و micro-interaction حرفه‌ای؛
- پنج Tab اصلی Home / Servers / Connect / Store / Account؛
- Design System اختصاصی Ganj؛
- Material 3 / M3 Expressive behavior بومی Android؛
- Apple HIG-inspired clarity؛
- Liquid Glass / Glassmorphism به‌صورت کنترل‌شده و functional، نه افراطی؛
- ظاهر Premium، گرم و هویت‌دار، نه UI خام/Developer-like.

### 5.1 Visual Brand Lock

لوگوی رسمی Ganj VPN، با فرم G/Shield و Diamond مرکزی و ترکیب **Emerald Green + Metallic Gold**، منبع اصلی Visual DNA محصول است.

از این لحظه تصمیم قطعی Brand:

- **Emerald Green رنگ Primary برند و Action است.**
- **Gold رنگ Premium/Signature Accent است، نه رنگ غالب صفحه.**
- **Neutral Dark/Warm Light surfaces بخش غالب UI هستند.**
- **Blue به‌عنوان Brand Primary حذف است.**
- `#0A84FF`، `#007AFF` و Material/iOS blue نباید Primary Design Token Ganj باشند.
- Gold نباید تمام Buttonها، Cardها، Navbar یا Background را پر کند.
- Emerald نیز نباید کل صفحه را به سطح سبز اشباع تبدیل کند.
- Premium بودن باید از spacing، hierarchy، typography، material، motion و polish بیاید؛ نه از مصرف زیاد طلایی.

Representative palette لوگوی رسمی:

Emerald:

- `#012009`
- `#013A16`
- `#01481D`

Gold:

- `#B3710D`
- `#CF9221`
- `#D0B348`
- `#F8D162`
- `#FCF4B3`

UI-adjusted semantic tokens در `design/tokens.json` و سند 15 تعریف می‌شوند. رنگ‌های خام Logo نباید بدون Contrast/Accessibility review روی text/control استفاده شوند.

### 5.2 Material / Glass Lock

- Material 3 / M3 Expressive پایه component behavior و interaction Android است، نه palette برند.
- Dynamic Color برای Brand Core در MVP به‌صورت پیش‌فرض خاموش است تا هویت Ganj با wallpaper کاربر به آبی/بنفش/رنگ تصادفی تبدیل نشود.
- Liquid Glass/Glassmorphism فقط برای navigation/control/transient layers استفاده می‌شود؛ body content باید عمدتاً opaque/readable باشد.
- fallback بدون blur برای accessibility و low-performance device الزامی است.

### 5.3 Phase 7 UI Migration

Branch `phase-7/android-ui-accessibility-v2` که هنوز از `GanjBlue`/palette آبی استفاده می‌کند، قبل از merge نهایی باید به Emerald/Gold/Neutral Design Tokens migration شود.

## 6. Smart Banking / Receipt Verification

Smart Banking verification یک قابلیت برنامه‌ریزی‌شده برای **Direct Commerce** است و باید از VPN Data Plane جدا بماند.

Target:

- integration با سیستم تایید هوشمند پرداخت/رسید؛
- پشتیبانی از signalهای بانکی مجاز در Android در صورت فعال‌شدن این Flow؛
- امکان سازگاری با مسیرهای موجود دیگر مانند iPhone Shortcuts/receipt verification در سطح Backend؛
- هیچ دسترسی بانکی یا permission اضافی نباید به Play VPN build اضافه شود مگر Requirement، Policy و Privacy review آن صریحاً تصویب شده باشد؛
- نتیجه verification فقط payment/order را تغییر می‌دهد و Entitlement همچنان باید از transaction معتبر Backend ایجاد شود.

این قابلیت P1/Direct Commerce است و نباید Critical Path اتصال VPN، Auth/Telegram یا Play Billing را عقب بیندازد.

## 7. Parallel Delivery Lock

توسعه می‌تواند در چند Track موازی جلو برود، اما dependencyهای امنیتی حذف نمی‌شوند.

Trackهای فعلی:

1. Auth / Telegram / Legacy Sync؛
2. Billing / Entitlement / Commerce؛
3. VPN Runtime / Resilience؛
4. Android UI / Localization / Accessibility؛
5. Admin Console؛
6. Staging / Operations / Release.

Parallel بودن به معنی Merge کردن Feature ناقص یا Production mock نیست.

## 8. Phase Numbering Clarification

دو نوع Phase در مستندات دیده می‌شود:

### Product Roadmap Phases

`docs/08-development-roadmap.fa.md` از Phase 0 تا Phase 5 برای برنامه محصول استفاده می‌کند: Baseline → Foundations → VPN MVP → Commerce/Sync → Enterprise/Quality → Release.

### Engineering Delivery Increments

PRها بعداً با نام‌های Phase 6A / 6B / 6C و Phase 7 Trackها ادامه یافته‌اند. این شماره‌ها **engineering increments** هستند و جایگزین معنای Product Roadmap Phaseها نمی‌شوند.

بنابراین AI/Developer نباید از تفاوت شماره‌ها نتیجه بگیرد که Roadmap Phase 5 کامل شده یا Production آماده است.

## 9. Current Critical Path

ترتیب اولویت تا نسخه واقعاً قابل فروش/اتصال:

1. Production Auth/Session؛
2. Telegram account linking + legacy subscription sync؛
3. Production-like staging Control API؛
4. Real server secret resolver و real VPN servers؛
5. Real VPN E2E + resilience/leak/device tests؛
6. Google Play Billing E2E + reconciliation؛
7. Subscription lifecycle + multi-device؛
8. Final localization/UI/accessibility طبق Visual Brand Lock؛
9. Minimum operational Admin Console؛
10. Internal/Closed Beta؛
11. Security/compliance remediation؛
12. staged Production rollout.

## 10. Source-of-Truth Rule

اگر این Scope Lock با سند قدیمی تعارض داشت:

1. code/tests/CI روی `main` برای runtime behavior؛
2. OpenAPI/migrations؛
3. این Scope Lock برای Requirementهای Product فعلی؛
4. `15-android-ui-ux-brand-system.fa.md` برای UI/UX/Visual Brand؛
5. Project Bible؛
6. Roadmap/old Master Plan؛
7. historical PR text

ملاک تفسیر باشد.
