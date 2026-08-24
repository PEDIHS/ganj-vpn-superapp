# Ganj VPN Super App

مخزن مرجع محصول جدید **Ganj VPN**؛ یک کلاینت VPN اختصاصی Android به‌همراه API، پنل مدیریت، فروش اشتراک، همگام‌سازی Telegram Bot و زیرساخت مارکتینگ.

> وضعیت فعلی: **Phase 1–2 — Android, Subscription & Control API Foundations**
> تاریخ مبنا: 2026-08-24  
> این مخزن شامل Vertical Slice تست‌شده است؛ Adapterهای Production و Secretها عمداً خارج مخزن می‌مانند.

## تصمیم‌های قطعی فاز صفر

- کلاینت Android به‌صورت Native با Kotlin و Jetpack Compose ساخته می‌شود.
- ظاهر یا کد v2rayNG کپی نمی‌شود؛ هستهٔ Xray پشت یک رابط مستقل `VpnEngine` قرار می‌گیرد.
- Backend به‌صورت Modular Monolith مبتنی بر Node.js و PostgreSQL طراحی می‌شود و با Adapter به ربات PHP/MariaDB فعلی متصل خواهد شد.
- ورود Telegram از OIDC Authorization Code + PKCE استفاده می‌کند؛ Deep Link ربات مسیر جایگزین است.
- نسخه Google Play فقط از Play Billing یا برنامه‌های پرداخت جایگزین مجاز استفاده می‌کند؛ نسخه Direct می‌تواند Wallet/Telegram/Gateway داشته باشد.
- اپ هیچ ورودی دستی، QR import، Clipboard import یا نمایش/Export کانفیگ ندارد؛ اتصال فقط از سرویس و Entitlement متعلق به کاربر انجام می‌شود.
- خرید، تمدید و فعال‌سازی اشتراک یک جریان هسته‌ای محصول است و فقط پس از تأیید Server-side پرداخت، Entitlement صادر می‌شود.
- اطلاعات مقصد، DNS، محتوای ترافیک و تاریخچهٔ مرور جمع‌آوری یا ثبت نمی‌شود.
- فایل‌های APK، ZIP سرور، `google-services.json`، Bot Token، Keystore و Config واقعی هرگز Commit نمی‌شوند.

## مستندات

1. [خلاصه اجرایی](docs/00-phase-zero-executive-summary.fa.md)
2. [ممیزی وضع موجود](docs/01-current-state-audit.fa.md)
3. [تحلیل رقبا](docs/02-competitor-analysis.fa.md)
4. [معماری سامانه](docs/03-architecture.fa.md)
5. [Design System](docs/04-design-system.fa.md)
6. [مدل داده](docs/05-database-schema.fa.md)
7. [طراحی API](docs/06-api-documentation.fa.md)
8. [امنیت و Threat Model](docs/07-security-threat-model.fa.md)
9. [Roadmap توسعه](docs/08-development-roadmap.fa.md)
10. [انتشار و انطباق Google Play](docs/09-play-release-compliance.fa.md)
11. [پلتفرم Enterprise](docs/10-enterprise-platform.fa.md)
12. [Observability، Bug Tracking و پشتیبانی](docs/11-observability-and-support-runbook.fa.md)
13. [Release، Update و Supply Chain](docs/12-release-and-update-runbook.fa.md)
14. [Incident Response و Disaster Recovery](docs/13-incident-response-runbook.fa.md)

فایل‌های ماشینی:

- [OpenAPI 3.1](api/openapi.yaml)
- [PostgreSQL baseline schema](database/schema.sql)
- [Design tokens](design/tokens.json)

اجزای اجرایی فعلی:

- [`services/control-api`](services/control-api/README.md): Catalog، My Services، Checkout، Play verification و صدور Profile رمز‌شده
- `apps/android/core/control-api`: کلاینت HTTPS و Vault یک‌بارمصرف envelope
- `apps/android/core/billing`: state machine خرید/بازیابی/Refund با verification سروری
- `apps/android/core/subscription`: سیاست Entitlement و state فروشگاه/اتصال

## ساختار هدف مخزن

```text
apps/
  android/                  Native Android application
  admin-web/                React + TypeScript admin panel
services/
  control-api/              Node.js control-plane vertical slice
  telemetry-worker/         Queue consumers and server probes
packages/
  contracts/                OpenAPI-generated DTOs and SDKs
  design-tokens/            Shared semantic tokens
vpn-core/
  xray-wrapper/             Thin, audited Xray binding
docs/
api/
database/
infra/
```

## مدل توسعه موازی

توسعه Android در سه Track هم‌زمان انجام می‌شود:

1. **Product & Subscription:** حساب، سرویس‌های من، Store، خرید/تمدید و Entitlement.
2. **VPN & Reliability:** Config Broker، Xray wrapper، Smart Connect، Kill Switch و Diagnostics.
3. **Enterprise & Operations:** Bug/Crash، Analytics، Feature Flag، Support، Monitoring، SOC و Release Automation.

هر Track روی شاخه جدا، با Pull Request و Quality Gate وارد `main` می‌شود؛ قابلیت وابسته تا زمانی که قرارداد امنیتی و تست آن کامل نشده در Production فعال نمی‌شود.

## Quality Gates برنامه‌ریزی‌شده

- Android: unit, instrumentation, VPN smoke, macrobenchmark, lint, detekt, dependency verification.
- Backend: unit, integration, contract, permission, idempotency and migration tests.
- Security: secret scanning, SAST, dependency/SBOM scan, signed artifacts and provenance.
- Release: signed AAB/APK, Play Integrity integration, staged rollout and rollback runbook.

## وضعیت ورودی‌های بررسی‌شده

- `GanjVPN-2.6.4-arm64-v8a-fixed.apk`: فقط برای ممیزی؛ وارد مخزن نمی‌شود.
- `v2rayNG_2.2.6-fdroid_arm64-v8a.apk`: فقط مرجع مقایسه و مجوز؛ وارد مخزن نمی‌شود.
- `well-known (26).zip`: کد ربات/پنل فعلی؛ دارای Secret و Runtime Data و خارج از Git است.
- `google-services.json`: شناسهٔ بستهٔ آن با APK فعلی همخوان نیست و باید برای Application ID نهایی دوباره تولید شود.
