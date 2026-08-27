# Android Signed AAB و Release Evidence

این سند روش اجرای Workflow محافظت‌شدهٔ `.github/workflows/android-release.yml` را تعریف می‌کند. خروجی آن یک AAB امضاشده برای Upload به Google Play و بستهٔ شواهد قابل ممیزی است؛ Workflow هیچ انتشار خودکار در Play Console انجام نمی‌دهد و داشتن Artifact به معنی تأیید Rollout نیست.

## مدل اعتماد

```text
Protected tag -> pinned official libXray rebuild -> source/quality gate -> unsigned AAB
              -> protected environment approval
              -> ephemeral keystore -> signed AAB -> fingerprint/checksum/SBOM/provenance
              -> human Play upload -> staged rollout
```

- Source فقط از Tag به شکل `android-vX.Y.Z` یا `android-vX.Y.Z-rc.N` پذیرفته می‌شود.
- Tag انتخاب‌شده در `workflow_dispatch` باید دقیقاً با ref اجرا برابر باشد و Commit آن در تاریخچهٔ `main` وجود داشته باشد.
- `versionName` باید با نسخهٔ Tag برابر باشد و `versionCode` عدد مثبت باشد؛ mismatch قبل از دسترسی به Signing secret متوقف می‌شود.
- Quality job هیچ Secret و Permission نوشتن ندارد. Signing فقط بعد از موفقیت Gate و Approval محیط اجرا می‌شود.
- libXray فقط از مخزن رسمی `XTLS/libXray`، Tag `v26.7.28` و Commit
  `80263da83e96b2972455b0a94b13ee1a10e51391` بازسازی می‌شود. HEAD، Source hash، License،
  Go/NDK، Go module checksum، ABI و 16 KiB alignment با
  [`UPSTREAM.lock.json`](../UPSTREAM.lock.json) تطبیق داده می‌شوند.
- Dependency موقت Debug منبع اعتماد Release نیست. یک Repository محلی و exclusive فقط برای Build
  همان Run تزریق می‌شود و checksum باینری `libgojni.so` برای تمام ABIها بین AAR رسمی و AAB نهایی
  byte-for-byte برابر می‌شود؛ نبود یا mismatch باعث توقف قبل از Signing است.
- Keystore فقط در `$RUNNER_TEMP` با Permission `0600` بازسازی، checksum آن کنترل و در Trap به‌صورت قطعی حذف می‌شود.
- Passwordها با `keytool`/`jarsigner` از Environment خوانده می‌شوند و در command line یا Log چاپ نمی‌شوند.
- Production entitlement یا Firebase credential بخشی از Signing نیست و نباید به این Workflow اضافه شود. Public Control API URL یک Build variable جدا و اجباری است، نه Signing secret.

## GitHub Environments

دو Environment محافظت‌شده ایجاد شود:

| Stage | Environment | کاربرد | Approval |
|---|---|---|---|
| Candidate | `android-candidate` | Internal/Closed testing | Release Owner |
| Production | `android-production` | Artifact نهایی Play | Release Owner + Security |

قواعد هر Environment:

- Required reviewer و جلوگیری از self-approval؛
- Deployment branch/tag فقط Tagهای محافظت‌شدهٔ Android؛
- Secrets فقط در همان Environment؛
- Admin bypass خاموش؛ Emergency bypass فقط با Incident ID؛
- Audit دوره‌ای reviewerها و آخرین استفاده از Secret.

### Secrets الزامی

| نام | محتوا | قاعده |
|---|---|---|
| `ANDROID_UPLOAD_KEYSTORE_B64` | Base64 یک Upload keystore اختصاصی Play | بدون newline اضافی، هرگز Artifact نشود |
| `ANDROID_UPLOAD_KEYSTORE_SHA256` | SHA-256 فایل Keystore قبل از Base64 | ۶۴ hex، comparison بدون چاپ مقدار |
| `ANDROID_UPLOAD_STORE_PASSWORD` | Store password | فقط Environment secret |
| `ANDROID_UPLOAD_KEY_ALIAS` | Alias کلید Upload | فقط Environment secret |
| `ANDROID_UPLOAD_KEY_PASSWORD` | Key password | فقط Environment secret |

### Variableهای الزامی

- `ANDROID_UPLOAD_CERT_SHA256` در هر Environment Variable قرار می‌گیرد. این مقدار fingerprint عمومی Certificate است، Secret نیست، ولی باید توسط دو نفر از منبع trusted تأیید شود. Workflow Certificate داخل Keystore را Export و fingerprint مشتق‌شده را دقیقاً با این مقدار مقایسه می‌کند.
- `GANJ_CANDIDATE_CONTROL_API_BASE_URL` و `GANJ_PRODUCTION_CONTROL_API_BASE_URL` به‌عنوان Repository Variable تعریف می‌شوند. این URL عمومی API است، Credential نیست. Workflow بر اساس Stage دقیقاً یکی را انتخاب و HTTPS/no-userinfo/no-query بودن آن را کنترل می‌کند؛ مقدار داخل Evidence یا Log چاپ نمی‌شود.

نبودن هر Secret/Variable یا ناسازگاری checksum/fingerprint باعث Fail بسته می‌شود؛ Endpoint خالی، Debug key یا Keystore ساختگی fallback نیست.

## Quality Gate پیش از Signing

Job بدون Secret این موارد را اجرا می‌کند:

1. Clone و بازسازی libXray رسمی از Source pin و Toolchain pin؛
2. تست upstream، license/module verification، SPDX SBOM و اسکن libXray؛
3. Repository و subscription-only guard؛
4. Android release unit tests و `lintRelease`؛
5. `bundleRelease` با Repository exclusive مربوط به AAR بازسازی‌شده و minify/shrink واقعی؛
6. کنترل ساختار ZIP/AAB، سقف ۲۰۰ MiB، نبود Credential و تطبیق تمام باینری‌های native؛
7. dependency tree برای `releaseRuntimeClasspath`؛
8. SPDX SBOM و اسکن High/Critical vulnerability؛
9. Upload موقت unsigned AAB و evidence با retention یک روز.

Intermediate unsigned AAB قابل انتشار نیست. Signing job همان Artifact را از GitHub Artifact service دریافت می‌کند، امضا را با `jarsigner -verify -strict` کنترل و فقط بستهٔ Signed نهایی را با retention محدود Upload می‌کند.

## محتوای Release Evidence

Artifact نهایی باید حداقل شامل موارد زیر باشد:

- `ganj-vpn-<version>-signed.aab`؛
- SHA-256 همان AAB؛
- SPDX JSON SBOM؛
- R8 `mapping.txt` در صورت تولید؛
- dependency report؛
- `UPSTREAM.lock.json`، provenance و Licenseهای MIT/MPL-2.0 مربوط به libXray/Xray-core؛
- Go module manifest/checksums و SPDX SBOM مستقل libXray؛
- `release-evidence.json` شامل Tag، Commit، Run ID/attempt، stage، versionName/versionCode، certificate fingerprint، checksum، actor و timestamp UTC؛
- GitHub build provenance attestation برای Signed AAB.

Evidence نباید Secret، مسیر Keystore، password، Token، User/Payment data، Server profile، URL خصوصی یا unsigned debug artifact داشته باشد. دسترسی Artifact به اعضای Release محدود و دانلود آن Audit می‌شود.

## روش اجرا

1. تمام Required Checkها روی Commit سبز باشد.
2. Tag محافظت‌شده ایجاد و Workflow از همان Tag با `release_tag` یکسان Dispatch شود.
3. برای RC، stage برابر `candidate` و برای انتشار نهایی `production` انتخاب شود.
4. Reviewer تغییرات، SBOM findings، privacy delta و rollback owner را بررسی و Environment را Approve کند.
5. پس از موفقیت، checksum و attestation دانلود و مستقل بررسی شود.
6. AAB در Play Internal track Upload و Pre-launch report بررسی شود؛ سپس طبق [Runbook انتشار](12-release-and-update-runbook.fa.md) Rollout ادامه پیدا کند.

## Checklist امضاشده

### پیش از Approval

- [ ] Tag/version/versionCode و Commit مورد انتظارند.
- [ ] CodeQL/Dependency/Secret/Unit/Lint/Release build سبز است.
- [ ] SBOM دارای finding حل‌نشدهٔ High/Critical نیست.
- [ ] libXray Commit/AAR checksum با Pin رسمی و `release-evidence.json` برابر است.
- [ ] Notice و تعهدات MIT/MPL-2.0 طبق [Provenance Notice](../third_party/libxray/NOTICE.md) تأیید شده‌اند.
- [ ] Data Safety، Permission و VpnService declaration delta بررسی شده است.
- [ ] خرید Play sandbox، restore، pending و revoke روی Candidate موفق است.
- [ ] Rollback owner، Incident channel و release monitoring آماده‌اند.

### پس از Signing

- [ ] `jarsigner -verify -strict` موفق است.
- [ ] Certificate fingerprint با baseline دو نفره برابر است.
- [ ] SHA-256 محلی با evidence برابر است.
- [ ] Artifact guard و provenance موفق‌اند.
- [ ] Mapping/SBOM/dependency report قابل بازیابی‌اند.
- [ ] هیچ Secret در Log/Artifact مشاهده نمی‌شود.

### پیش از Rollout هر Stage

- [ ] Pre-launch report و device smoke بدون regression است.
- [ ] Dashboard به `app_version` و `release_channel` تفکیک شده است.
- [ ] crash-free، ANR، connect و commerce alertها Armed هستند.
- [ ] معیار توقف و فرد مجاز به Halt کردن Rollout مشخص‌اند.

## Failure و Recovery

- Missing secret، checksum یا fingerprint mismatch: Workflow متوقف؛ مقدار در Log چاپ نشود؛ Release Security منبع Secret و Audit را بررسی کند.
- Vulnerability High/Critical: انتشار متوقف؛ Exception فقط time-bound، با Owner، compensating control و تأیید Security ثبت شود.
- Signature verification failure: Artifact حذف و Keystore access freeze شود؛ Incident supply-chain بررسی شود.
- Artifact/attestation mismatch پس از دانلود: Upload به Play ممنوع و طبق [Incident Response](13-incident-response-runbook.fa.md) SEV-0/1 triage شود.
- libXray Source/Tag/toolchain/license/ABI/native mismatch: Release بسته می‌ماند؛ fallback به Maven Debug،
  prebuilt ناشناس، stub یا حذف VPN Engine ممنوع است.
- Bad release پس از Play upload با Downgrade اصلاح نمی‌شود؛ Rollout halt و Hotfix با `versionCode` بالاتر از همین Pipeline عبور می‌کند.

## Rotation Drill

هر شش ماه یک Candidate غیرتولیدی برای بازیابی/Rotation Upload key ساخته می‌شود. Evidence شامل زمان، افراد، fingerprint قبل/بعد، نتیجهٔ Play validation و cleanup است. Backup بدون rehearsal قابل اتکا نیست.
