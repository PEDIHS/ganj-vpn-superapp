# Release، Update و Supply-Chain Runbook

هدف این Runbook تولید Build قابل ردیابی، قابل Rollback و بدون Secret برای Android است. iOS و Desktop فعلاً خارج از دامنه‌اند.

## محیط‌ها و کانال‌ها

| Environment | داده | Signing | توزیع | هدف |
|---|---|---|---|---|
| Development | synthetic/local | debug | توسعه‌دهنده | توسعه سریع |
| Testing | synthetic/shared | debug/internal | CI artifact | تست خودکار و QA |
| Staging | anonymized/synthetic | staging | Internal testing | E2E و rehearsal |
| Production | production | Play App Signing/release | Play + Direct flavor مجاز | کاربران واقعی |

هیچ Endpoint یا Credential بین محیط‌ها مشترک نیست. Production Build فقط از Tag محافظت‌شده و GitHub Environment `production` ساخته می‌شود.

## Versioning

- نسخهٔ کاربر از Semantic Versioning به شکل `major.minor.patch` استفاده می‌کند.
- `versionCode` Android همیشه افزایشی و غیرقابل استفادهٔ مجدد است.
- هر Artifact به Commit SHA، workflow run، dependency lock/SBOM، mapping file و signing certificate fingerprint نگاشت می‌شود.
- Tag انتشار به شکل `android-vX.Y.Z` و Release Candidate به شکل `android-vX.Y.Z-rc.N` است.

## Branch و Approval

تغییر از Branch کوتاه‌عمر و Pull Request وارد `main` می‌شود. Branch protection باید review، conversation resolution، branch up-to-date و checks زیر را اجباری کند:

- Documentation/OpenAPI validation؛
- repository guard و secret scan؛
- Android unit test، lint و build؛
- dependency review و CodeQL؛
- تست‌های contract/integration مرتبط؛
- review امنیتی برای auth، payment، VPN Core، crypto و CI.

هیچ مدیر یا Automation حق bypass برای release عادی ندارد. Emergency bypass با Incident ID، دو تأییدکننده و postmortem ثبت می‌شود.

در مخزن Private، CodeQL و GitHub Dependency Review به GitHub Code Security وابسته‌اند. پس از فعال‌سازی آن، Repository Variable به نام `GHAS_ENABLED=true` تنظیم می‌شود. تا قبل از آن، workflowها به‌جای fail شدن بی‌دلیل، Android Lint و اسکن محلی SBOM/Grype را اجرا می‌کنند؛ Repository guard و Gitleaks در هر دو حالت اجباری‌اند.

## CI Artifact Policy

Debug APK فقط Artifact موقت CI با retention هفت روز است و Release محسوب نمی‌شود. Upload باید APK غیرخالی و ZIP-valid را کنترل و SHA-256 تولید کند. Quality evidence شامل test/lint reports و SPDX SBOM برای 14 روز نگهداری می‌شود.

Release artifact شامل موارد زیر است:

- signed AAB برای Google Play؛
- در صورت نیاز، signed APK برای کانال Direct مجاز؛
- SHA-256 checksum؛
- SPDX یا CycloneDX SBOM؛
- R8 mapping/native symbols؛
- provenance/attestation و workflow run؛
- release notes و migration/rollback notes؛
- privacy/Data Safety delta و dependency/license report.

Artifact یا Log عمومی نباید Config، Endpoint خصوصی، Token، Keystore، `google-services.json` یا دادهٔ کاربر داشته باشد.

## Signing

1. Play App Signing مالک کلید توزیع Play است؛ Upload key جدا و قابل Rotation است.
2. Direct flavor کلید مستقل دارد؛ کلید و backup در Secret manager خارج از GitHub Artifact نگهداری می‌شوند.
3. GitHub Environment دسترسی release secret را به protected tag و reviewer محدود می‌کند.
4. Keystore هرگز Base64 شده داخل Repository، Issue یا Log ذخیره نمی‌شود.
5. fingerprint مورد انتظار قبل و بعد از Signing کنترل می‌شود.
6. بازیابی کلید هر شش ماه در محیط جدا rehearsal و ثبت می‌شود.

## Release Candidate Gate

- تمام Required Checkها سبز و Source tree clean است؛
- API compatibility و database migration روی staging تست شده؛
- purchase → verify → entitlement → connect → revoke E2E موفق است؛
- no manual/QR/clipboard/file config import/export تأیید شده؛
- VPN leak، reconnect، kill switch و Android 8–16 matrix موفق است؛
- Crash-free، ANR، battery و startup در محدودهٔ budget است؛
- dependency/license/security scan finding بحرانی یا بالا ندارد؛
- Backup/restore و feature kill switch آزمایش شده؛
- Support، Operations و Product release notes را پذیرفته‌اند.

## Rollout

```text
Internal -> Closed beta -> 5% -> 20% -> 50% -> 100%
```

هر مرحله حداقل یک بازهٔ مشاهده بر مبنای حجم واقعی دارد. Release Dashboard با previous stable مقایسه می‌شود. معیار توقف:

- Critical security/privacy/payment incident؛
- افت crash-free زیر 99.2% یا جهش ANR؛
- افت connect success بیش از 5%؛
- duplicate entitlement، double charge یا widespread purchase mismatch؛
- API/server error budget burn شدید؛
- traffic/DNS leak یا Kill Switch regression.

در توقف، rollout freeze، Feature Flag disable و در صورت امکان Play halt انجام می‌شود. Downgrade اجباری مجاز نیست؛ Rollback باید Server-compatible و به صورت نسخهٔ اصلاحی با `versionCode` بالاتر منتشر شود.

## Remote Config و Force Update

Remote Config امضاشده، schema-versioned و دارای TTL است. App روی مقدار cache شدهٔ امن کار می‌کند و Config نامعتبر را رد می‌کند. Flagها Owner، audience، درصد rollout، start/end و default دارند.

- **Optional update:** قابلیت یا بهبود عادی؛ dismiss و remind-later دارد.
- **Recommended update:** مشکل مهم با grace period؛ مسیر اتصال همچنان روشن است.
- **Forced update:** فقط آسیب‌پذیری بحرانی یا ناسازگاری قطعی؛ نسخهٔ حداقل از Backend، امضاشده و با fallback تعیین می‌شود.

Force update نباید کاربر را بدون مسیر Support، توضیح و دسترسی به الزامات قانونی مسدود کند. Kill switch قابلیت، اولویت بیشتری از Force update دارد.

## Migration و Compatibility

- Backend حداقل current و previous stable app را پشتیبانی می‌کند؛
- API change ابتدا additive و سپس بعد از telemetry/deprecation حذف می‌شود؛
- migration دیتابیس forward-only و expand/migrate/contract است؛
- Entitlement و payment idempotency در rollback حفظ می‌شود؛
- پروفایل VPN فقط Server-side و device-bound refresh می‌شود و Export ندارد.

## Hotfix

Hotfix از Tag تولیدی Branch می‌گیرد، فقط کمترین اصلاح و تست regression را شامل می‌شود و دوباره از همان Gateهای signing/SBOM/provenance عبور می‌کند. Skip کردن تست فقط با SEV-0/1، تأیید Incident Commander و ثبت دلیل مجاز است. اصلاح ظرف 48 ساعت به `main` بازگردانده می‌شود.

## Release Checklist

### قبل از Build

- [ ] نسخه، `versionCode`، Commit و Changelog نهایی‌اند.
- [ ] Secret/Dependency/CodeQL/License findings بررسی شده‌اند.
- [ ] Feature Flagها owner و expiry دارند.
- [ ] Privacy Policy، Data Safety و VpnService declaration delta بررسی شده است.
- [ ] Rollback owner و incident channel مشخص‌اند.

### پس از Build

- [ ] signature fingerprint، checksum، SBOM و provenance ثبت شد.
- [ ] AAB/APK روی clean device و upgrade path نصب شد.
- [ ] purchase/restore/connect/reconnect/logout/delete account smoke موفق بود.
- [ ] mapping/native symbols در crash system بارگذاری شد.
- [ ] Artifact دسترسی محدود و retention صحیح دارد.

### پس از Rollout

- [ ] Dashboard و Alertها برای release dimension فعال‌اند.
- [ ] crash-free، ANR، connect و commerce با stable مقایسه شد.
- [ ] Support macro/FAQ و known issues به‌روز شد.
- [ ] در 100%، Flagهای موقت و release branch cleanup برنامه‌ریزی شد.

## Disaster Recovery Evidence

هر فصل Restore نقطه‌ای Database، بازیابی Signing access، بازسازی Artifact از Tag و بازیابی Remote Config آزمایش می‌شود. گزارش شامل RPO/RTO واقعی، انحراف، Owner و موعد اصلاح است. Backup بدون تست Restore قابل اتکا نیست.
