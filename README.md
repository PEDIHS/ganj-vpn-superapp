# Ganj VPN Super App

مخزن مرجع محصول **Ganj VPN**؛ یک VPN Super App اختصاصی Android به‌همراه Control API، فروش و مدیریت اشتراک، همگام‌سازی Telegram Bot، Admin Console، Enterprise Systems و زیرساخت Release/Operations.

> **🚨 مرجع اجباری پیشرفت UI برای تمام Agentها:** [`docs/ui/UI_100_PERCENT_PROGRESS.fa.md`](docs/ui/UI_100_PERCENT_PROGRESS.fa.md)  
> **هر Agent قبل از هر تغییر UI باید ابتدا این فایل را کامل بخواند، آیتم Scope خود را از checkboxهای باز انتخاب کند و پس از تکمیل واقعی همان checkboxها را در همان PR/commit تیک بزند. ادعای 100٪ UI تا زمانی که Final Gate این Ledger کامل نشده ممنوع است.**  
> **مرجع Scope فعلی:** [`docs/00-CURRENT_PRODUCT_SCOPE.fa.md`](docs/00-CURRENT_PRODUCT_SCOPE.fa.md)  
> **مرجع UI/UX و Brand:** [`docs/15-android-ui-ux-brand-system.fa.md`](docs/15-android-ui-ux-brand-system.fa.md)  
> **استاندارد اجباری Liquid Glass:** [`docs/16-liquid-glass-first-design-standard.fa.md`](docs/16-liquid-glass-first-design-standard.fa.md)  
> **مرجع مهندسی اصلی:** [`docs/PROJECT_BIBLE.fa.md`](docs/PROJECT_BIBLE.fa.md)  
> وضعیت Runtime پایدار: Phase 0 تا 6C روی `main` تحویل شده و Phase 7 در چند Track موازی برای Auth/Session، Billing E2E، VPN Resilience، UI/Accessibility، Admin Console و Staging/Operations در حال تکمیل است.  
> اصل پروژه: Production بدون Evidence واقعی، Provider واقعی، Secret واقعی، E2E و Release Gate کامل ادعا نمی‌شود.

# **تصمیم قطعی طراحی: Liquid Glass-First در کل اپلیکیشن**

## **تمام Screenها و Interactionهای Ganj VPN باید از یک زبان طراحی Liquid Glass مدرن، لایه‌ای و یکپارچه پیروی کنند.**

این Requirement فقط به معنی چند Card شیشه‌ای نیست. Navigation، Bottom Bar، Search، Filters، Sheets، Floating Controls، Connect state machine، Depth، Edge-to-Edge composition، Motion و Material hierarchy همگی باید به یک Liquid system واحد تعلق داشته باشند.

> **Liquid Glass-first ≠ Blur Everywhere.**  
> Content layer باید خوانا و پایدار بماند؛ Glass در لایه Functional و تعاملی استفاده می‌شود. جزئیات و Acceptance Criteria کامل در سند 16 ثبت شده است.

## قانون اجباری UI Agent

برای هر کار UI/UX، ترتیب زیر الزام‌آور است:

1. `docs/ui/UI_100_PERCENT_PROGRESS.fa.md` را کامل بخوان.
2. آخرین `main`، Branch فعلی و PRهای باز مرتبط را بررسی کن.
3. فقط آیتم‌های باز `[ ]` مرتبط با Scope خود را انتخاب کن؛ Scope موازی تکراری نساز.
4. صفحه اصلی به‌تنهایی Done نیست؛ تمام Subpage / Dialog / Bottom Sheet / Popup / Loading / Empty / Error / Offline / Permission / Success / Failure / Retry / Accessibility / Responsive stateهای آن بخش باید مطابق Ledger بررسی شوند.
5. Parent checkbox فقط وقتی `[x]` می‌شود که تمام Child itemهای آن واقعاً تکمیل شده باشند.
6. Mock/Placeholder/Fake telemetry یا action بدون Runtime contract هرگز Done محسوب نمی‌شود.
7. پس از هر Batch همان Ledger را در همان PR/commit به‌روز کن و Remaining Boundary را دقیق بنویس.
8. **100٪ فقط وقتی مجاز است که Final 100% Release Gate داخل Ledger کاملاً تیک خورده باشد.**

Baseline فعلی Ledger در زمان ایجاد آن **54٪ UI کل محصول** است؛ این عدد پنج تب اصلی را به‌تنهایی معیار قرار نمی‌دهد و تمام Flowها و micro-surfaceهای محصول را حساب می‌کند.

## تصمیم‌های قطعی محصول

- **Scope اجرایی فعلی Android-only است.** iOS/Desktop فقط Post-MVP هستند و تا بسته‌شدن Android Production Critical Path نباید Workstream فعال شوند.
- Android به‌صورت Native با Kotlin و Jetpack Compose ساخته می‌شود.
- **هویت بصری رسمی Ganj VPN = Emerald Green + Refined Gold + Neutral surfaces است. Blue رنگ اصلی برند نیست و نباید به‌عنوان Primary UI استفاده شود.**
- **Liquid Glass-first زبان طراحی سراسری و غیرقابل‌حذف محصول است.**
- Gold فقط Premium/Signature Accent است؛ کل App نباید فول طلایی یا فول سبز شود.
- Material 3 / M3 Expressive پایه رفتار Native Android است؛ Liquid Glass اصول Material/Depth/Motion را روی Brand Ganj سوار می‌کند.
- Xray پشت یک رابط مستقل `VpnEngine` قرار می‌گیرد و app یک V2Ray config importer عمومی نیست.
- Backend یک Modular Monolith مبتنی بر Node.js و PostgreSQL است.
- ورود Telegram بر OIDC Authorization Code + PKCE و fallback امن Bot Deep-Link بنا می‌شود.
- Telegram integration علاوه بر Login شامل Account Mapping، Legacy Subscription Sync و نمایش سرویس‌های قبلی در My Services است.
- Play flavor از Google Play Billing و server-side verification استفاده می‌کند؛ Direct flavor می‌تواند Wallet/Telegram/Gateway داشته باشد.
- خرید/تمدید Subscription یک Core Product Flow است، نه Feature جانبی.
- هیچ URI/QR/Clipboard/File/Subscription-URL manual config import در Production مجاز نیست.
- اتصال فقط از `Account → Entitlement → Eligible Server → Device-bound encrypted profile → VpnService/Xray` انجام می‌شود.
- خرید فقط پس از Server-side verification به Entitlement تبدیل می‌شود.
- مقصد، DNS history، traffic content، credential، Token و private key وارد Analytics/Logs نمی‌شوند.
- Bot Token، Keystore، Play Service Account، DB password و Config واقعی هرگز Commit نمی‌شوند.
- Smart Banking/receipt verification در Direct Commerce یک قابلیت برنامه‌ریزی‌شده و جدا از VPN Data Plane است؛ هیچ permission بانکی اضافه بدون Policy/Privacy review وارد Play VPN build نمی‌شود.

## مستندات اصلی پروژه

1. **[UI 100% Completion Progress Ledger](docs/ui/UI_100_PERCENT_PROGRESS.fa.md)** — **مرجع اجرایی اجباری تمام Agentها برای تکمیل دانه‌به‌دانه Screen/Subpage/Dialog/Sheet/Stateها و تنها مبنای ادعای 100٪ UI.**
2. **[Current Product Scope Lock](docs/00-CURRENT_PRODUCT_SCOPE.fa.md)** — Requirementهای قطعی فعلی، Android-only scope، Design/Commerce/Telegram locks و رفع ابهام شماره Phaseها.
3. **[Android UI/UX, Visual Brand & Interaction System](docs/15-android-ui-ux-brand-system.fa.md)** — مرجع Canonical رنگ، لوگو، Emerald/Gold/Neutral palette، Typography، Components، Motion، RTL، Accessibility و تمام Screenها.
4. **[Liquid Glass-First Android Design Standard](docs/16-liquid-glass-first-design-standard.fa.md)** — **استاندارد اجباری Material، Depth، Glass Roles، Edge-to-Edge، Motion، Performance، Accessibility و Liquid coverage تمام اپ.**
5. **[Project Bible & Engineering Specification](docs/PROJECT_BIBLE.fa.md)** — مرجع Canonical محصول، معماری، امنیت، Phaseها، Critical Path، Definition of Done و Release Blockers.
6. **[Delivery Ledger](docs/DELIVERY_LEDGER.fa.md)** — تاریخچه دقیق Phaseها، PRها و وضعیت Trackهای Phase 7.
7. **[Implementation Backlog](docs/IMPLEMENTATION_BACKLOG.fa.md)** — P0/P1/P2، Epicها و Acceptance Criteria اجرایی.
8. **[Release Readiness Matrix](docs/RELEASE_READINESS_MATRIX.fa.md)** — ماتریس Ready/In Progress/Blocker برای انتشار.
9. **[AI / Developer Engineering Handoff](docs/AI_ENGINEERING_HANDOFF.fa.md)** — قوانین ادامه پروژه توسط AI Agent یا Developer جدید.
10. [Master Project Plan](docs/MASTER_PROJECT_PLAN.fa.md) — نسخه جامع اولیه و تاریخچه محصول؛ در تعارض با Scope/Brand جاری، Scope Lock و اسناد 15/16 مقدم‌اند.
11. [خلاصه اجرایی](docs/00-phase-zero-executive-summary.fa.md)
12. [ممیزی وضع موجود](docs/01-current-state-audit.fa.md)
13. [تحلیل رقبا](docs/02-competitor-analysis.fa.md)
14. [معماری سامانه](docs/03-architecture.fa.md)
15. [Design System Summary](docs/04-design-system.fa.md)
16. [مدل داده](docs/05-database-schema.fa.md)
17. [طراحی API](docs/06-api-documentation.fa.md)
18. [امنیت و Threat Model](docs/07-security-threat-model.fa.md)
19. [Roadmap توسعه](docs/08-development-roadmap.fa.md)
20. [انتشار و انطباق Google Play](docs/09-play-release-compliance.fa.md)
21. [پلتفرم Enterprise](docs/10-enterprise-platform.fa.md)
22. [Observability، Bug Tracking و پشتیبانی](docs/11-observability-and-support-runbook.fa.md)
23. [Release، Update و Supply Chain](docs/12-release-and-update-runbook.fa.md)
24. [Incident Response و Disaster Recovery](docs/13-incident-response-runbook.fa.md)
25. [Observability اندروید با حفظ حریم خصوصی](docs/14-observability-privacy.fa.md)

## فایل‌های ماشینی

- [OpenAPI 3.1](api/openapi.yaml)
- [PostgreSQL baseline schema](database/schema.sql)
- [Design tokens](design/tokens.json) — نسخه `0.3.0+` شامل Brand + semantic Liquid Glass roles است؛ Blue legacy token و raw one-off glass values ممنوع است.

## اجزای اجرایی فعلی

- `apps/android`: Android native application، UI، VPN orchestration و enterprise client integration.
- `services/control-api`: Catalog، Services، Orders، Billing verification، Telegram boundary، Profile Broker، Remote Config، Analytics، Support و Audit.
- `apps/android/core/control-api`: HTTPS client و profile envelope handling.
- `apps/android/core/billing`: provider-neutral purchase/entitlement state machine.
- `apps/android/core/play-billing`: Google Play Billing adapter و secure purchase proof boundary.
- `apps/android/core/observability`: privacy-safe diagnostics/analytics/bug boundaries.
- `apps/android/core/subscription`: entitlement و service policy.
- Phase 7 branches: production auth/session، VPN resilience، UI/accessibility، Admin Console و staging operations.

## وضعیت UI و VPN

- VPN Runtime واقعی و secure profile-to-tunnel orchestration روی `main` وجود دارد؛ اما Production E2E، real server deployment، resilience/leak/device evidence هنوز Release Blocker هستند.
- پنج مقصد اصلی UI روی branch `ui/stitch-persian-liquid-v1` به Stitch Persian Liquid منتقل شده‌اند، اما این به معنی تکمیل کل UI محصول نیست.
- **وضعیت دقیق و ریزدانه کل UI فقط از `docs/ui/UI_100_PERCENT_PROGRESS.fa.md` خوانده شود.**
- **Final UI باید Liquid Glass Maturity Level 3 / Production طبق سند 16 باشد.**
- **Brand Lock:** Neutral surfaces غالب + Emerald برای Brand/Action + Gold محدود برای Premium. آبی Primary حذف است.
- Bottom Navigation، Search/Filter، Sheets، floating controls و Connect باید semantic Glass role داشته باشند.
- Content rows/cards به‌طور پیش‌فرض Glass نیستند؛ Nested Glass و Blur Everywhere ممنوع است.

## مدل توسعه

کارها می‌توانند موازی اجرا شوند، ولی dependencyهای امنیتی و Release Gate حذف نمی‌شوند. Trackهای اصلی:

1. **Identity & Commerce:** Auth/Session، Telegram، Billing، Entitlement، Legacy Sync.
2. **VPN & Android Product:** Xray/VpnService، Resilience، Smart Connect، Liquid UI/Accessibility.
3. **Enterprise & Operations:** Admin، Remote Config، Analytics، Support، Monitoring، Backup، Release.

هر Track روی Branch جدا، با PR، تست و Quality Gate وارد `main` می‌شود.

### رفع ابهام Phaseها

`docs/08-development-roadmap.fa.md` از Phase 0–5 به‌عنوان **Product Roadmap** استفاده می‌کند. نام‌های Phase 6A/6B/6C و Phase 7 در PRها **Engineering Delivery Increments** هستند؛ تفاوت شماره‌گذاری به معنی تکمیل Roadmap Release Phase یا آمادگی Production نیست.

## Quality Gates

- Android: unit, lint, instrumentation, VPN smoke, accessibility, benchmark/device evidence.
- UI/UX: Liquid Glass Level 3، semantic Glass roles، Dark/Light/System، fa/en یا Scope قطعی locale، RTL/LTR، font scale تا 200%، TalkBack، Reduce Transparency/Motion، 48dp touch targets، screenshot/device review، frame/jank evidence، no dominant blue brand surface.
- Backend: unit, integration, PostgreSQL migrations, contracts, ownership, idempotency, concurrency.
- Security: secret scan, SAST/CodeQL, dependency review, SBOM/provenance, replay/tamper tests, penetration testing before Production.
- Release: signed AAB/APK، artifact checksum، mapping، Internal/Closed Beta، metrics، rollback.

## شروع کار برای Developer/AI جدید

قبل از تغییر کد، به‌ترتیب بخوان:

**`docs/ui/UI_100_PERCENT_PROGRESS.fa.md`** → `00-CURRENT_PRODUCT_SCOPE.fa.md` → `15-android-ui-ux-brand-system.fa.md` → **`16-liquid-glass-first-design-standard.fa.md`** → `04-design-system.fa.md` → `design/tokens.json` → `PROJECT_BIBLE.fa.md` → `DELIVERY_LEDGER.fa.md` → `IMPLEMENTATION_BACKLOG.fa.md` → `RELEASE_READINESS_MATRIX.fa.md` → `AI_ENGINEERING_HANDOFF.fa.md` → latest `main`/Branch/PR/code/tests.

مستندات Context هستند؛ برای تصمیم نهایی runtime، کد، Migration، OpenAPI و CI Evidence روی baseline جاری Source of Truth هستند. برای UI/UX، **UI 100% Ledger + Scope Lock + اسناد 15/16 + Design Tokens** مراجع قطعی‌اند.
