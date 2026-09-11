# Ganj VPN — Android Release / Publication Readiness

این سند مسیر انتشار امن Android را برای Ganj VPN مشخص می‌کند. Workflow موجود `.github/workflows/android-release.yml` از قبل build، تست، lint، SBOM، vulnerability gate، امضای AAB، provenance attestation و انتشار اختیاری در Google Play را انجام می‌دهد.

## وضعیت فعلی اپ

- Application ID: `com.ganj.vpn`
- Android namespace: `com.ganj.vpn`
- minSdk: 24
- targetSdk / compileSdk: 36
- versionName فعلی: `0.3.0`
- versionCode فعلی: `3`
- Release artifact اصلی: Signed Android App Bundle (`.aab`)
- مسیرهای Play مجاز در workflow: `internal` و `alpha` (closed beta)
- Production build باید فقط به HTTPS Control API production متصل شود.

## GitHub Environments مورد نیاز

### `android-candidate`
برای ساخت و امضای نسخه Candidate.

Secrets:
- `ANDROID_UPLOAD_KEYSTORE_B64`
- `ANDROID_UPLOAD_KEYSTORE_SHA256`
- `ANDROID_UPLOAD_STORE_PASSWORD`
- `ANDROID_UPLOAD_KEY_ALIAS`
- `ANDROID_UPLOAD_KEY_PASSWORD`

Variables:
- `ANDROID_UPLOAD_CERT_SHA256`

### `android-production`
همان کلیدها و fingerprint تأییدشده production. این Environment باید approval اجباری داشته باشد.

### `android-play-candidate`
Secret:
- `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`

### `android-play-production`
Secret:
- `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`

این Environment نیز باید approval اجباری داشته باشد تا upload به Play بدون تأیید انسانی انجام نشود.

## Repository Variables مورد نیاز

- `GANJ_CANDIDATE_CONTROL_API_BASE_URL`
- `GANJ_PRODUCTION_CONTROL_API_BASE_URL`

مقدار Production فقط باید origin/path امن HTTPS برای Control API واقعی باشد؛ هیچ credential، query-string یا fragment داخل URL قرار نگیرد.

## سیاست Tag

Workflow فقط Tagهای مطابق این الگو را می‌پذیرد:

- Release: `android-v1.2.3`
- Release Candidate: `android-v1.2.3-rc.1`

Tag باید annotated باشد، commit آن از `main` قابل دسترسی باشد و `versionName` داخل `apps/android/app/build.gradle.kts` دقیقاً با نسخه Tag یکی باشد.

## روند Candidate

1. Backend Candidate آماده و health check سبز باشد.
2. `GANJ_CANDIDATE_CONTROL_API_BASE_URL` تنظیم باشد.
3. versionName/versionCode افزایش داده شود.
4. تغییرات روی main پس از CI سبز merge شوند.
5. annotated tag از main ساخته شود.
6. `Android signed AAB release` با stage=`candidate` اجرا شود.
7. Signed AAB، checksum، mapping، SBOM و release evidence بررسی شوند.
8. در صورت نیاز `publish_to_play=true` و track=`internal` یا `alpha` انتخاب شود.
9. Login، My Services، خرید، تمدید، اتصال، قطع اتصال، تغییر شبکه، DNS/IPv6 leak و revoke روی دستگاه واقعی تست شوند.

## روند Production

1. Production Control API و Admin Console باید release-ready باشند.
2. Restore drill PostgreSQL و rollback اپلیکیشن انجام شده باشد.
3. PasarGuard live binding و legacy reconciliation conflictهای بحرانی نداشته باشند.
4. `GANJ_PRODUCTION_CONTROL_API_BASE_URL` روی API production قفل شود.
5. Release tag نهایی `android-vX.Y.Z` ساخته شود.
6. Workflow با stage=`production` اجرا و approval Environment داده شود.
7. ابتدا Play `internal` با status=`draft` توصیه می‌شود.
8. پس از smoke/E2E، track/status طبق برنامه انتشار جلو برود.

## Google Play Console checklist

- App با package `com.ganj.vpn` ایجاد/تأیید شده باشد.
- Play App Signing فعال باشد.
- Upload certificate fingerprint با `ANDROID_UPLOAD_CERT_SHA256` یکی باشد.
- Service Account فقط حداقل permission لازم برای release روی trackهای مجاز را داشته باشد.
- Privacy Policy، Data Safety، App Access و VPN declaration تکمیل شوند.
- Store listing، icon، feature graphic، screenshots و توضیحات fa/en آماده باشند.
- Test account / review instructions برای Login تلگرام و قابلیت VPN آماده باشند.

## Fail-closed rules

- بدون keystore یا fingerprint صحیح، release امضا نمی‌شود.
- بدون HTTPS Control API، release build رد می‌شود.
- vulnerability با severity بالا/بحرانی release را متوقف می‌کند.
- release commit باید از main باشد.
- upload به Play بدون Environment approval و Service Account معتبر انجام نمی‌شود.
- هیچ bot token، DB password، PasarGuard admin credential یا VPN subscription URL نباید داخل APK/AAB قرار گیرد.
