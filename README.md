# Ganj VPN Super App

مخزن مرجع محصول جدید **Ganj VPN**؛ یک کلاینت VPN اختصاصی Android به‌همراه API، پنل مدیریت، فروش اشتراک، همگام‌سازی Telegram Bot و زیرساخت مارکتینگ.

> وضعیت فعلی: **Phase 0 — Product & Architecture Baseline**  
> تاریخ مبنا: 2026-08-24  
> این شاخه هنوز شامل کد Production یا Secret نیست.

## تصمیم‌های قطعی فاز صفر

- کلاینت Android به‌صورت Native با Kotlin و Jetpack Compose ساخته می‌شود.
- ظاهر یا کد v2rayNG کپی نمی‌شود؛ هستهٔ Xray پشت یک رابط مستقل `VpnEngine` قرار می‌گیرد.
- Backend جدید یک Modular Monolith مبتنی بر Laravel و PostgreSQL است و با Adapter به ربات PHP/MariaDB فعلی متصل می‌شود.
- ورود Telegram از OIDC Authorization Code + PKCE استفاده می‌کند؛ Deep Link ربات مسیر جایگزین است.
- نسخه Google Play فقط از Play Billing یا برنامه‌های پرداخت جایگزین مجاز استفاده می‌کند؛ نسخه Direct می‌تواند Wallet/Telegram/Gateway داشته باشد.
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

فایل‌های ماشینی:

- [OpenAPI 3.1](api/openapi.yaml)
- [PostgreSQL baseline schema](database/schema.sql)
- [Design tokens](design/tokens.json)

## ساختار هدف مخزن

```text
apps/
  android/                  Native Android application
  admin-web/                React + TypeScript admin panel
services/
  control-api/              Laravel modular monolith
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

