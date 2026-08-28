# Ganj VPN Super App

مخزن مرجع محصول **Ganj VPN**؛ یک VPN Super App اختصاصی Android به‌همراه Control API، فروش و مدیریت اشتراک، همگام‌سازی Telegram Bot، Admin Console، Enterprise Systems و زیرساخت Release/Operations.

> **مرجع اصلی ادامه پروژه:** [`docs/PROJECT_BIBLE.fa.md`](docs/PROJECT_BIBLE.fa.md)  
> وضعیت فعلی: Phase 0 تا 6C روی `main` تحویل شده و Phase 7 در چند Track موازی برای Auth/Session، Billing E2E، VPN Resilience، UI/Accessibility، Admin Console و Staging/Operations در حال تکمیل است.  
> اصل پروژه: Production بدون Evidence واقعی، Provider واقعی، Secret واقعی، E2E و Release Gate کامل ادعا نمی‌شود.

## تصمیم‌های قطعی محصول

- Android به‌صورت Native با Kotlin و Jetpack Compose ساخته می‌شود.
- Xray پشت یک رابط مستقل `VpnEngine` قرار می‌گیرد و app یک V2Ray config importer عمومی نیست.
- Backend یک Modular Monolith مبتنی بر Node.js و PostgreSQL است.
- ورود Telegram بر OIDC Authorization Code + PKCE و fallback امن Bot Deep-Link بنا می‌شود.
- Play flavor از Google Play Billing و server-side verification استفاده می‌کند؛ Direct flavor می‌تواند Wallet/Telegram/Gateway داشته باشد.
- هیچ URI/QR/Clipboard/File/Subscription-URL manual config import در Production مجاز نیست.
- اتصال فقط از `Account → Entitlement → Eligible Server → Device-bound encrypted profile → VpnService/Xray` انجام می‌شود.
- خرید فقط پس از Server-side verification به Entitlement تبدیل می‌شود.
- مقصد، DNS history، traffic content، credential، Token و private key وارد Analytics/Logs نمی‌شوند.
- Bot Token، Keystore، Play Service Account، DB password و Config واقعی هرگز Commit نمی‌شوند.

## مستندات اصلی پروژه

1. **[Project Bible & Engineering Specification](docs/PROJECT_BIBLE.fa.md)** — مرجع Canonical محصول، معماری، امنیت، Phaseها، Critical Path، Definition of Done و Release Blockers.
2. **[Delivery Ledger](docs/DELIVERY_LEDGER.fa.md)** — تاریخچه دقیق Phaseها، PRها و وضعیت Trackهای Phase 7.
3. **[Implementation Backlog](docs/IMPLEMENTATION_BACKLOG.fa.md)** — P0/P1/P2، Epicها و Acceptance Criteria اجرایی.
4. **[Release Readiness Matrix](docs/RELEASE_READINESS_MATRIX.fa.md)** — ماتریس Ready/In Progress/Blocker برای انتشار.
5. **[AI / Developer Engineering Handoff](docs/AI_ENGINEERING_HANDOFF.fa.md)** — قوانین ادامه پروژه توسط AI Agent یا Developer جدید.
6. [Master Project Plan](docs/MASTER_PROJECT_PLAN.fa.md) — نسخه جامع اولیه و تاریخچه محصول.
7. [خلاصه اجرایی](docs/00-phase-zero-executive-summary.fa.md)
8. [ممیزی وضع موجود](docs/01-current-state-audit.fa.md)
9. [تحلیل رقبا](docs/02-competitor-analysis.fa.md)
10. [معماری سامانه](docs/03-architecture.fa.md)
11. [Design System](docs/04-design-system.fa.md)
12. [مدل داده](docs/05-database-schema.fa.md)
13. [طراحی API](docs/06-api-documentation.fa.md)
14. [امنیت و Threat Model](docs/07-security-threat-model.fa.md)
15. [Roadmap توسعه](docs/08-development-roadmap.fa.md)
16. [انتشار و انطباق Google Play](docs/09-play-release-compliance.fa.md)
17. [پلتفرم Enterprise](docs/10-enterprise-platform.fa.md)
18. [Observability، Bug Tracking و پشتیبانی](docs/11-observability-and-support-runbook.fa.md)
19. [Release، Update و Supply Chain](docs/12-release-and-update-runbook.fa.md)
20. [Incident Response و Disaster Recovery](docs/13-incident-response-runbook.fa.md)
21. [Observability اندروید با حفظ حریم خصوصی](docs/14-observability-privacy.fa.md)

## فایل‌های ماشینی

- [OpenAPI 3.1](api/openapi.yaml)
- [PostgreSQL baseline schema](database/schema.sql)
- [Design tokens](design/tokens.json)

## اجزای اجرایی فعلی

- `apps/android`: Android native application، UI، VPN orchestration و enterprise client integration.
- `services/control-api`: Catalog، Services، Orders، Billing verification، Telegram boundary، Profile Broker، Remote Config، Analytics، Support و Audit.
- `apps/android/core/control-api`: HTTPS client و profile envelope handling.
- `apps/android/core/billing`: provider-neutral purchase/entitlement state machine.
- `apps/android/core/play-billing`: Google Play Billing adapter و secure purchase proof boundary.
- `apps/android/core/observability`: privacy-safe diagnostics/analytics/bug boundaries.
- `apps/android/core/subscription`: entitlement و service policy.
- Phase 7 branches: production auth/session، VPN resilience، UI/accessibility، Admin Console و staging operations.

## مدل توسعه

کارها می‌توانند موازی اجرا شوند، ولی dependencyهای امنیتی و Release Gate حذف نمی‌شوند. Trackهای اصلی:

1. **Identity & Commerce:** Auth/Session، Telegram، Billing، Entitlement، Legacy Sync.
2. **VPN & Android Product:** Xray/VpnService، Resilience، Smart Connect، UI/Accessibility.
3. **Enterprise & Operations:** Admin، Remote Config، Analytics، Support، Monitoring، Backup، Release.

هر Track روی Branch جدا، با PR، تست و Quality Gate وارد `main` می‌شود.

## Quality Gates

- Android: unit, lint, instrumentation, VPN smoke, accessibility, benchmark/device evidence.
- Backend: unit, integration, PostgreSQL migrations, contracts, ownership, idempotency, concurrency.
- Security: secret scan, SAST/CodeQL, dependency review, SBOM/provenance, replay/tamper tests, penetration testing before Production.
- Release: signed AAB/APK، artifact checksum، mapping، Internal/Closed Beta، metrics، rollback.

## شروع کار برای Developer/AI جدید

قبل از تغییر کد، به‌ترتیب بخوان:

`PROJECT_BIBLE.fa.md` → `DELIVERY_LEDGER.fa.md` → `IMPLEMENTATION_BACKLOG.fa.md` → `RELEASE_READINESS_MATRIX.fa.md` → `AI_ENGINEERING_HANDOFF.fa.md` → latest `main`/Branch/PR/code/tests.

مستندات Context هستند؛ برای تصمیم نهایی، کد، Migration، OpenAPI و CI Evidence روی baseline جاری Source of Truth هستند.
