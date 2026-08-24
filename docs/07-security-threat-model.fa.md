# Security Architecture and Threat Model

## Assets

- VPN service credentials و Config payload؛
- Telegram identity و account-link tokens؛
- access/refresh sessions؛
- Wallet ledger، payment evidence و subscription entitlement؛
- server/provider admin credentials؛
- release signing keys؛
- user privacy و عدم افشای destination traffic.

## Trust Boundaries

1. Android UI ↔ VPN process؛
2. App ↔ Public API روی شبکه خصمانه؛
3. API ↔ Provider adapters؛
4. Backend ↔ Telegram/Google Play/Payment gateways؛
5. Admin browser ↔ Admin API؛
6. New system ↔ Legacy bot/database.

## Threat Register

| Threat | Control | Validation |
|---|---|---|
| MITM/API spoof | TLS 1.3، Network Security Config، optional SPKI pins با backup | proxy test و pin rotation drill |
| Token theft | کوتاه‌عمر، refresh rotation، Keystore، no-log | reuse test و device revoke |
| Deep-link hijack | App Links verified، state+PKCE، nonce یک‌بارمصرف | malicious app test |
| Config extraction | device-bound envelope، no disk plaintext، screen/log redaction | rooted lab و forensic check |
| Root instrumentation | integrity/risk signal، sensitive-action challenge | Frida/Magisk test؛ بدون false lockout |
| API abuse | rate limit per subject/device/IP، WAF، quotas | load/abuse test |
| Free-plan farming | anonymous device key، risk scoring، server-side quota | emulator farm simulation |
| Wallet race/double spend | double-entry، DB lock، idempotency، invariant | concurrent property tests |
| Payment webhook spoof | provider signature، replay guard، server verify | signed/unsigned fixtures |
| Admin takeover | phishing-resistant MFA، RBAC، re-auth، audit | quarterly access review |
| Provider compromise | per-provider secret، least privilege، circuit breaker | credential rotation exercise |
| Supply chain | pinned dependencies، SBOM، signed provenance، protected CI | dependency diff gate |
| Malicious update | Play App Signing، internal release key، signature verification | staged rollout/rollback |
| Traffic privacy leak | never collect destination/DNS/content؛ redacted telemetry | schema/log scanning |

## Android Security Baseline

- `android:allowBackup=false` یا backup rule صریح برای حذف DB/Token/Config؛
- `usesCleartextTraffic=false` با استثنای محدود loopback در صورت نیاز Core؛
- exported component فقط با ضرورت و permission؛
- PendingIntent immutable پیش‌فرض؛
- Clipboard هیچ‌گاه برای Config یا credential استفاده نمی‌شود؛ پروفایل فقط از Entitlement معتبر و به‌صورت device-bound صادر می‌شود؛
- screenshot protection روی Config/Recovery screen؛
- release `debuggable=false` و R8 full mode؛
- no WebView برای Auth اصلی؛ Custom Tabs + verified App Links؛
- biometric فقط برای local re-auth، نه جایگزین server auth.

## Certificate Pinning Policy

Pinning بدون برنامه Rotation باعث Bricking، مخصوصاً در شبکه محدود، می‌شود. بنابراین:

- pin روی SPKI intermediate/controlled endpoint؛
- حداقل primary + two backup pins؛
- overlap دو release؛
- signed remote pin set تنها با trust root آفلاین؛
- debug CA فقط در debug-overrides؛
- emergency runbook و expiration کنترل‌شده.

مرجع: [Android Network Security Configuration](https://developer.android.com/privacy-and-security/security-config)

## Secret Management

- هیچ Bot token، Firebase service account، provider credential یا keystore در Git نیست.
- GitHub OIDC به Cloud secret manager؛ long-lived cloud key در Actions ممنوع.
- Release keystore خارج CI artifact، با dual control و backup آفلاین.
- Firebase client configuration Secret محسوب نمی‌شود، اما per-flavor و خارج ورودی خصوصی مدیریت می‌شود؛ service account Secret است.
- Bot token موجود در ZIP باید Rotate شود.

## Privacy Model

### Collect

- account identifiers ضروری؛
- subscription/payment records؛
- aggregate connection health؛
- device/app version و security state حداقلی؛
- consented notification interactions.

### Never Collect

- browsing history، URL، destination IP/domain؛
- DNS query history؛
- packet payload؛
- installed app list برای Marketing؛
- precise location برای Smart Connect.

## Root Detection

Root detection دفاع قطعی نیست. نتیجه فقط Risk Score را تغییر می‌دهد. اتصال VPN برای کاربر Root شده لزوماً مسدود نمی‌شود؛ عملیات مالی/نمایش Config می‌تواند Step-up verification بخواهد. دلیل و مسیر Support باید واضح باشد.

## Incident Response

Severity، owner و Runbook برای موارد زیر قبل از Production آماده می‌شود:

- signing key compromise؛
- bot token leak؛
- provider credential compromise؛
- refresh token replay spike؛
- payment duplication؛
- config exposure in logs؛
- malicious server/end-point؛
- Play/Firebase service outage.

هر Incident شامل containment، revoke/rotation، customer impact، evidence preservation، notification decision و postmortem بدون سرزنش است.
