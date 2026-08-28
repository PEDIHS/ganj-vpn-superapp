# Ganj VPN Super App

مخزن مرجع محصول **Ganj VPN**؛ یک VPN Super App اختصاصی Android به‌همراه Control API، فروش و مدیریت اشتراک، همگام‌سازی Telegram Bot، Admin Console، Enterprise Systems و زیرساخت Release/Operations.

> **مرجع Scope فعلی:** [`docs/00-CURRENT_PRODUCT_SCOPE.fa.md`](docs/00-CURRENT_PRODUCT_SCOPE.fa.md)  
> **مرجع UI/UX و Brand:** [`docs/15-android-ui-ux-brand-system.fa.md`](docs/15-android-ui-ux-brand-system.fa.md)  
> **مرجع مهندسی اصلی:** [`docs/PROJECT_BIBLE.fa.md`](docs/PROJECT_BIBLE.fa.md)  
> وضعیت Runtime پایدار: Phase 0 تا 6C روی `main` تحویل شده و Phase 7 در چند Track موازی برای Auth/Session، Billing E2E، VPN Resilience، UI/Accessibility، Admin Console و Staging/Operations در حال تکمیل است.  
> اصل پروژه: Production بدون Evidence واقعی، Provider واقعی، Secret واقعی، E2E و Release Gate کامل ادعا نمی‌شود.

## تصمیم‌های قطعی محصول

- **Scope اجرایی فعلی Android-only است.** iOS/Desktop فقط Post-MVP هستند و تا بسته‌شدن Android Production Critical Path نباید Workstream فعال شوند.
- Android به‌صورت Native با Kotlin و Jetpack Compose ساخته می‌شود.
- **هویت بصری رسمی Ganj VPN = Emerald Green + Refined Gold + Neutral surfaces است. Blue رنگ اصلی برند نیست و نباید به‌عنوان Primary UI استفاده شود.**
- Gold فقط Premium/Signature Accent است؛ کل App نباید فول طلایی یا فول سبز شود.
- Material 3 / M3 Expressive پایه رفتار Native Android است؛ Apple HIG/Liquid Glass فقط inspiration برای clarity/material/motion است.
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

1. **[Current Product Scope Lock](docs/00-CURRENT_PRODUCT_SCOPE.fa.md)** — Requirementهای قطعی فعلی، Android-only scope، Design/Commerce/Telegram locks و رفع ابهام شماره Phaseها.
2. **[Android UI/UX, Visual Brand & Interaction System](docs/15-android-ui-ux-brand-system.fa.md)** — مرجع Canonical رنگ، لوگو، Emerald/Gold/Neutral palette، Material/Liquid Glass، Typography، Components، Motion، RTL، Accessibility و تمام Screenها.
3. **[Project Bible & Engineering Specification](docs/PROJECT_BIBLE.fa.md)** — مرجع Canonical محصول، معماری، امنیت، Phaseها، Critical Path، Definition of Done و Release Blockers.
4. **[Delivery Ledger](docs/DELIVERY_LEDGER.fa.md)** — تاریخچه دقیق Phaseها، PRها و وضعیت Trackهای Phase 7.
5. **[Implementation Backlog](docs/IMPLEMENTATION_BACKLOG.fa.md)** — P0/P1/P2، Epicها و Acceptance Criteria اجرایی.
6. **[Release Readiness Matrix](docs/RELEASE_READINESS_MATRIX.fa.md)** — ماتریس Ready/In Progress/Blocker برای انتشار.
7. **[AI / Developer Engineering Handoff](docs/AI_ENGINEERING_HANDOFF.fa.md)** — قوانین ادامه پروژه توسط AI Agent یا Developer جدید.
8. [Master Project Plan](docs/MASTER_PROJECT_PLAN.fa.md) — نسخه جامع اولیه و تاریخچه محصول؛ در تعارض با Scope/Brand جاری، Scope Lock و سند 15 مقدم‌اند.
9. [خلاصه اجرایی](docs/00-phase-zero-executive-summary.fa.md)
10. [ممیزی وضع موجود](docs/01-current-state-audit.fa.md)
11. [تحلیل رقبا](docs/02-competitor-analysis.fa.md)
12. [معماری سامانه](docs/03-architecture.fa.md)
13. [Design System Summary](docs/04-design-system.fa.md)
14. [مدل داده](docs/05-database-schema.fa.md)
15. [طراحی API](docs/06-api-documentation.fa.md)
16. [امنیت و Threat Model](docs/07-security-threat-model.fa.md)
17. [Roadmap توسعه](docs/08-development-roadmap.fa.md)
18. [انتشار و انطباق Google Play](docs/09-play-release-compliance.fa.md)
19. [پلتفرم Enterprise](docs/10-enterprise-platform.fa.md)
20. [Observability، Bug Tracking و پشتیبانی](docs/11-observability-and-support-runbook.fa.md)
21. [Release، Update و Supply Chain](docs/12-release-and-update-runbook.fa.md)
22. [Incident Response و Disaster Recovery](docs/13-incident-response-runbook.fa.md)
23. [Observability اندروید با حفظ حریم خصوصی](docs/14-observability-privacy.fa.md)

## فایل‌های ماشینی

- [OpenAPI 3.1](api/openapi.yaml)
- [PostgreSQL baseline schema](database/schema.sql)
- [Design tokens](design/tokens.json) — نسخه 0.2+ باید با سند Brand همگام باشد؛ Blue legacy token ممنوع است.

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
- UI فعلی Functional/Foundation است و Final Design محسوب نمی‌شود. Final pass شامل localization فارسی/English، RTL/LTR، Light/Dark/System، accessibility، responsive layouts، motion و brand polish است.
- **Brand Lock فعلی:** Neutral surfaces غالب + Emerald برای Brand/Action + Gold محدود برای Premium. آبی Primary حذف است.
- Liquid Glass/Glassmorphism باید functional و کنترل‌شده باشد، نه skin کل App.
- Phase 7 UI branch که هنوز `GanjBlue` دارد باید قبل از merge نهایی به Emerald/Gold tokens migration شود.

## مدل توسعه

کارها می‌توانند موازی اجرا شوند، ولی dependencyهای امنیتی و Release Gate حذف نمی‌شوند. Trackهای اصلی:

1. **Identity & Commerce:** Auth/Session، Telegram، Billing، Entitlement، Legacy Sync.
2. **VPN & Android Product:** Xray/VpnService، Resilience، Smart Connect، UI/Accessibility.
3. **Enterprise & Operations:** Admin، Remote Config، Analytics، Support، Monitoring، Backup، Release.

هر Track روی Branch جدا، با PR، تست و Quality Gate وارد `main` می‌شود.

### رفع ابهام Phaseها

`docs/08-development-roadmap.fa.md` از Phase 0–5 به‌عنوان **Product Roadmap** استفاده می‌کند. نام‌های Phase 6A/6B/6C و Phase 7 در PRها **Engineering Delivery Increments** هستند؛ تفاوت شماره‌گذاری به معنی تکمیل Roadmap Release Phase یا آمادگی Production نیست.

## Quality Gates

- Android: unit, lint, instrumentation, VPN smoke, accessibility, benchmark/device evidence.
- UI/UX: Dark/Light/System، fa/en، RTL/LTR، font scale تا 200%، TalkBack، 48dp touch targets، screenshot/device review، no dominant blue brand surface.
- Backend: unit, integration, PostgreSQL migrations, contracts, ownership, idempotency, concurrency.
- Security: secret scan, SAST/CodeQL, dependency review, SBOM/provenance, replay/tamper tests, penetration testing before Production.
- Release: signed AAB/APK، artifact checksum، mapping، Internal/Closed Beta، metrics، rollback.

## شروع کار برای Developer/AI جدید

قبل از تغییر کد، به‌ترتیب بخوان:

`00-CURRENT_PRODUCT_SCOPE.fa.md` → `15-android-ui-ux-brand-system.fa.md` (برای هر کار UI) → `PROJECT_BIBLE.fa.md` → `DELIVERY_LEDGER.fa.md` → `IMPLEMENTATION_BACKLOG.fa.md` → `RELEASE_READINESS_MATRIX.fa.md` → `AI_ENGINEERING_HANDOFF.fa.md` → latest `main`/Branch/PR/code/tests.

مستندات Context هستند؛ برای تصمیم نهایی runtime، کد، Migration، OpenAPI و CI Evidence روی baseline جاری Source of Truth هستند. برای تصمیم‌های Visual Brand، سند 15 مرجع قطعی است.
