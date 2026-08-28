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

Final Android UI باید:

- فارسی و انگلیسی resource-based؛
- RTL/LTR واقعی؛
- Dark/Light/System؛
- Responsive؛
- Accessibility/TalkBack/Large Font؛
- Motion و micro-interaction حرفه‌ای؛
- پنج Tab اصلی Home / Servers / Connect / Store / Account؛
- Design System اختصاصی Ganj؛
- Apple HIG-inspired clarity؛
- Liquid Glass / Glassmorphism به‌صورت کنترل‌شده و functional، نه افراطی؛
- ظاهر Premium، گرم و هویت‌دار، نه UI خام/Developer-like.

### 5.1 Visual Brand Clarification

Tokenهای فعلی در `docs/04-design-system.fa.md` و implementationهای Phase 7 **baseline طراحی هستند، نه Brand Lock غیرقابل تغییر**.

Final palette باید با Brand assets تأییدشده Ganj تطبیق داده شود. Marketing assets فعلی جهت Premium dark با accentهای سبز/teal/gold را نشان می‌دهند؛ در عین حال انتخاب رنگ نهایی UI باید با تست Contrast، accessibility، dark/light و consistency انجام شود. بنابراین هیچ AI/Developer نباید صرفاً به دلیل وجود token آبی فعلی، آن را «رنگ نهایی قطعی برند» فرض کند.

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
8. Final localization/UI/accessibility؛
9. Minimum operational Admin Console؛
10. Internal/Closed Beta؛
11. Security/compliance remediation؛
12. staged Production rollout.

## 10. Source-of-Truth Rule

اگر این Scope Lock با سند قدیمی تعارض داشت:

1. code/tests/CI روی `main`؛
2. OpenAPI/migrations؛
3. این Scope Lock برای Requirementهای Product فعلی؛
4. Project Bible؛
5. Roadmap/old Master Plan؛
6. historical PR text

ملاک تفسیر باشد.
