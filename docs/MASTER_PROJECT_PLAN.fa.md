# Ganj VPN — Master Project Plan, Architecture, Delivery History & Release Roadmap

> **وضعیت سند:** مرجع اصلی پروژه برای ادامه توسعه توسط انسان یا AI  
> **مخزن:** `PEDIHS/ganj-vpn-superapp`  
> **شاخه مرجع پایدار هنگام ایجاد سند:** `main` روی Phase 6C  
> **هدف:** ثبت یکپارچه‌ی محصول، معماری، تصمیم‌های قطعی، کارهای انجام‌شده، کارهای در حال انجام، کارهای باقی‌مانده، معیار Done، مسیر انتشار و Post-MVP.

---

## 0. نحوه استفاده از این سند

این فایل باید به‌عنوان **Single Source of Product Delivery Context** خوانده شود، نه جایگزین کد، OpenAPI، Migration یا Runbookهای تخصصی. برای تصمیم فنی، ترتیب اعتبار منابع چنین است:

1. کد و Migrationهای روی `main`؛
2. قرارداد `api/openapi.yaml`؛
3. تست‌های خودکار و CI؛
4. Runbookها و اسناد امنیت/انتشار؛
5. این Master Plan برای Context، Scope، ترتیب کار و وضعیت کلی.

هر توسعه‌دهنده یا AI قبل از ایجاد قابلیت جدید باید:

- محدودیت‌های Product/Security این سند را رعایت کند؛
- بررسی کند قابلیت مشابه قبلاً در شاخه یا ماژول دیگری ایجاد نشده باشد؛
- هیچ مسیر manual config به اپ اضافه نکند؛
- هیچ Secret، Bot Token، Keystore، Purchase Token، VPN credential یا production profile را Commit نکند؛
- قابلیت وابسته به Backend را تا کامل‌شدن زنجیره اعتماد به‌صورت fail-closed نگه دارد؛
- تست، migration و rollback impact را بخشی از خود Feature بداند.

---

# 1. تعریف محصول

## 1.1 Ganj VPN چیست؟

Ganj VPN یک **VPN Super App اشتراکی** است، نه یک V2Ray client عمومی. محصول باید تجربه‌ای در سطح محصولات جهانی مثل NordVPN، Proton VPN، Surfshark و ExpressVPN ارائه کند اما با برند، زیرساخت، مدل فروش و اکوسیستم اختصاصی Ganj.

محصول نهایی شامل این لایه‌هاست:

- Android VPN Client؛
- Xray-based VPN Runtime؛
- Control API و Subscription Platform؛
- Telegram Authentication و اتصال به ربات فروش فعلی؛
- خرید اشتراک در Google Play و Direct channel؛
- My Services و مدیریت سرویس‌های کاربر؛
- Device Binding و Multi-device policy؛
- Server Catalog و Smart Connect؛
- Web Admin Console؛
- Enterprise Remote Config / Feature Flags / Audit؛
- Bug Tracking / Diagnostics / Support؛
- Analytics با Consent؛
- Notification / Marketing / Referral؛
- Observability / Security / Backup / Disaster Recovery؛
- Release automation و Google Play staged rollout.

## 1.2 اصل غیرقابل مذاکره محصول

Ganj VPN **نباید مانند v2rayNG یک ابزار واردکردن کانفیگ باشد**.

کاربر نباید بتواند:

- VLESS/VMess/Trojan/Shadowsocks URI وارد کند؛
- QR کانفیگ اسکن کند؛
- کانفیگ را از Clipboard وارد کند؛
- فایل کانفیگ Import کند؛
- Xray JSON خام وارد کند؛
- Subscription URL دستی بدهد؛
- Credential سرور را ببیند یا Export کند.

تمام دسترسی VPN باید از این زنجیره به‌دست آید:

`Account → Entitlement → Owned Service → Eligible Server → Device-bound short-lived encrypted profile → VpnService/Xray`

## 1.3 کانال‌های محصول

### Google Play flavor

- خرید Digital Subscription از Google Play Billing؛
- Server-side verification؛
- RTDN؛
- رعایت کامل Play Billing و VPN Service policy؛
- عدم نمایش روش پرداخت خارجی مگر در برنامه/کشور مجاز.

### Direct APK flavor

در نسخه Direct می‌توان علاوه بر Entitlement مشترک، از موارد زیر پشتیبانی کرد:

- Wallet؛
- Telegram checkout؛
- Gateway / direct payment؛
- روش‌های پرداخت منطقه‌ای مجاز؛
- کمپین‌های فروش خارج از Play.

Backend entitlement باید مشترک بماند اما `purchase_channel` و policy هر کانال جدا باشد.

---

# 2. اهداف تجربه کاربری و طراحی

## 2.1 Design Direction

رابط باید:

- Native Android با Kotlin + Jetpack Compose باشد؛
- از Apple HIG/iOS modern interaction principles الهام بگیرد ولی کپی iOS نباشد؛
- ظاهر سرد، خام و Developer-like نداشته باشد؛
- هویت مستقل Ganj VPN داشته باشد؛
- روی موبایل اول طراحی شود؛
- RTL و فارسی را first-class در نظر بگیرد؛
- Dark/Light mode کامل داشته باشد؛
- animationها نرم، purposeful و performance-safe باشند؛
- تمام touch targetها، spacing، typography و stateها استاندارد باشند.

## 2.2 Navigation هدف

ساختار پنج تب اصلی:

1. Home
2. Servers
3. Connect
4. Store
5. Account / My Services

نسخه نهایی باید به‌جای متن‌های hardcoded فعلی از Resource/Localization واقعی استفاده کند.

## 2.3 صفحه Connect

هدف نهایی:

- مرکز بصری واضح برای اتصال؛
- حالت‌های `Disconnected / Preparing / Connecting / Connected / Reconnecting / Failed`؛
- انیمیشن تغییر حالت؛
- نمایش کشور/سرور یا Smart Connect؛
- وضعیت سرویس فعال؛
- latency خلاصه؛
- elapsed session time؛
- اختیاری: local traffic stats بدون ثبت مقصد/ترافیک؛
- disconnect واقعی از runtime؛
- عدم نمایش credential/profile خام.

## 2.4 Servers

- Countries؛
- Free/Premium segmentation؛
- Search؛
- Favorites؛
- latency؛
- health؛
- capacity/availability؛
- supported protocols؛
- Smart Connect؛
- sort/filter؛
- server eligibility بر اساس entitlement.

## 2.5 Store

- Plan cards حرفه‌ای؛
- monthly/yearly یا مدل قیمت‌گذاری نهایی؛
- trial/free state در صورت سیاست محصول؛
- تفاوت Free/Premium؛
- renewal information؛
- cancellation/refund disclosure؛
- checkout recovery؛
- pending purchase؛
- restore purchase؛
- upgrade/downgrade؛
- خطاهای Provider با UX مناسب.

## 2.6 Account / My Services

- حساب Telegram/Guest؛
- شناسه حساب؛
- سرویس‌های فعال/منقضی؛
- روز/زمان باقی‌مانده؛
- device slots؛
- revoke device؛
- renew/upgrade؛
- purchase history؛
- support؛
- bug report؛
- diagnostics؛
- privacy/analytics consent؛
- notification preferences؛
- delete account؛
- logout/session management.

---

# 3. معماری کلان

## 3.1 Android

هدف معماری:

- Native Kotlin؛
- Jetpack Compose؛
- ماژولار؛
- Presentation state بدون Secret؛
- Networking HTTPS-only؛
- Android Keystore؛
- Device-bound identity؛
- VpnService در process امن/کنترل‌شده؛
- Xray behind `VpnEngine` abstraction؛
- provider-specific purchase logic پشت Adapter؛
- enterprise telemetry privacy-safe.

### Boundaries مهم Android

- UI هرگز profile خام را نمی‌بیند؛
- Purchase Token مستقیم وارد state نمایشی نمی‌شود؛
- encrypted profile فقط در مسیر یک‌بارمصرف runtime حرکت می‌کند؛
- Intent/Bundle/Log/SavedState محل Secret نیست؛
- VPN permission قبل از مصرف one-time connection action گرفته می‌شود؛
- Connected state فقط بعد از پذیرش profile توسط Xray فعال می‌شود.

## 3.2 Control API

Backend هدف یک Modular Monolith مبتنی بر Node.js + PostgreSQL است.

دامنه‌ها:

- Identity/Auth؛
- Users/Devices/Sessions؛
- Store؛
- Orders؛
- Billing؛
- Entitlements/Services؛
- Server Catalog؛
- Connection Profile Broker؛
- Telegram linking؛
- Runtime Config؛
- Feature Flags؛
- Analytics؛
- Bug Reports؛
- Diagnostics؛
- Support؛
- Admin/Audit؛
- RTDN/Reconciliation؛
- future Wallet/Direct payment.

## 3.3 PostgreSQL

Production data must be durable and transactional.

اصول:

- ownership predicates در query؛
- unique idempotency constraints؛
- purchase token digest uniqueness؛
- append-only audit/consent where required؛
- revocable sessions؛
- device bindings؛
- one-time profile grants؛
- replay nonce storage؛
- migrations immutable/checksummed؛
- backup/restore testable.

## 3.4 Secrets

Secretها نباید در Git باشند.

موارد حساس:

- DB password؛
- Bot token؛
- Telegram OIDC secret؛
- Play service account؛
- runtime config signing private key؛
- HMAC cohort secret؛
- server credentials؛
- Xray Reality private material؛
- Android release keystore؛
- Direct payment credentials.

Production باید از Secret Manager/KMS/Vault یا secret-mounted files استفاده کند.

---

# 4. مدل امنیتی

## 4.1 Device Identity

Android از دو هویت کلیدی مجزا استفاده می‌کند:

- P-256 signing key؛
- X25519 encryption identity.

Signing key غیرقابل export در Android Keystore نگهداری می‌شود. X25519 private material به‌صورت AES-256-GCM با key داخل Android Keystore wrap می‌شود.

## 4.2 Device Proof

Proof درخواست باید bind شود به:

- method؛
- canonical path/query؛
- body SHA-256؛
- timestamp؛
- nonce؛
- key version.

Backend:

- ES256/P-256 verification؛
- P1363 signature format؛
- clock window محدود؛
- key version validation؛
- nonce replay prevention؛
- atomic nonce consume در PostgreSQL.

## 4.3 Profile Encryption

GVP1:

- X25519؛
- HKDF-SHA256؛
- AES-256-GCM؛
- authenticated associated data؛
- one-time grant؛
- device-bound؛
- short-lived؛
- no raw endpoint/credential JSON response.

## 4.4 Auth

Production access token باید:

- signed؛
- issuer validated؛
- audience validated؛
- expiry/not-before checked؛
- session revocation checked؛
- device binding checked؛
- user/device active state checked؛
- trusted device key checked.

## 4.5 Privacy

نباید جمع‌آوری شود:

- browsing history؛
- destination domains/IP history؛
- DNS queries؛
- traffic content؛
- precise location؛
- contacts؛
- SMS؛
- call logs.

Telemetry باید allowlisted و consent-governed باشد.

---

# 5. تاریخچه تحویل — Phase 0 تا Phase 6C

## Phase 0 — Product & Architecture Baseline — DONE

### انجام‌شده

- تعریف Product Scope؛
- competitor analysis؛
- architecture docs؛
- API baseline؛
- database baseline؛
- security threat model؛
- Design System direction؛
- Play compliance plan؛
- enterprise platform plan؛
- observability/support runbooks؛
- release/update runbook؛
- incident response / DR docs؛
- تصمیم licensing و Xray integration boundary؛
- ممنوعیت manual config.

### خروجی اصلی

فاز صفر پایه قراردادها را تثبیت کرد تا Android، Backend و Enterprise بتوانند موازی توسعه پیدا کنند.

---

## Phase 1 — Android Foundation — DONE / MERGED

### Android foundation

- پروژه Native Android؛
- `com.ganj.vpn`؛
- Compose shell؛
- five-tab navigation foundation؛
- `VpnEngine` boundary؛
- Smart Connect weighted ranker foundation؛
- secure manifest defaults؛
- cleartext disabled؛
- backup disabled؛
- VPN service non-exported؛
- `BIND_VPN_SERVICE` protection؛
- CI build/test/lint؛
- APK artifact؛
- checksum؛
- design token baseline.

### کیفیت

- Android compilation in CI؛
- unit tests؛
- OpenAPI lint؛
- repository hygiene.

---

## Phase 1–4 Foundation Increment — DONE / MERGED

این Increment سه مسیر را جلو برد:

### Subscription product

- product/service/purchase/entitlement models؛
- Home؛
- Servers؛
- Connect؛
- Store؛
- Account/My Services؛
- purchase verification policies؛
- entitlement connection policy.

### VPN trust boundary

- connection only from owned service/server؛
- reject wrong owner؛
- reject expired؛
- reject exhausted؛
- reject suspended؛
- enforce device limit؛
- enforce allowed plan/protocol؛
- no manual import.

### Enterprise foundation

- reliability contracts؛
- telemetry؛
- support؛
- privacy؛
- monitoring؛
- release/SOC/anti-abuse contracts؛
- enterprise database entities؛
- issue templates؛
- SBOM/CodeQL/Gitleaks/Dependabot foundations.

---

## Phase 2 — Control API + Verified Checkout Vertical Slice — DONE / MERGED

### Backend

- Store catalog؛
- channel filtering؛
- My Services؛
- server catalog؛
- idempotent order creation؛
- order recovery؛
- Play verification boundary؛
- entitlement creation/renewal؛
- connection profile endpoint؛
- production startup fail-closed without real adapters.

### Android networking

- HTTPS-only transport؛
- redirect/origin escape prevention؛
- response limits؛
- strict JSON/UTF-8/UUID/Base64 validation؛
- typed provider/backend failures؛
- one-time profile envelope vault.

### Billing abstraction

- Play/Wallet/Telegram/Gateway provider-neutral boundaries؛
- pending/cancel/refund/restore/reconciliation state machine؛
- server verification required before entitlement.

---

## Phase 3 — Production Commerce, Persistence & Observability Wiring — DONE / MERGED

### Android commerce

- real Catalog/My Services/Checkout presentation flow؛
- Google Play Billing 9.1.0 adapter؛
- purchase Proof Vault؛
- pending handling؛
- restore handling؛
- acknowledgement handling؛
- Play-only production wiring؛
- production mock guard.

### Backend production boundaries

- PostgreSQL repository؛
- migrations؛
- JWKS auth foundation؛
- device proof boundary؛
- X25519/HKDF/AES-GCM envelope؛
- Telegram OIDC+PKCE exchange boundary؛
- Play subscriptions v2؛
- acknowledge/cancel/revoke models؛
- RTDN verification/reconciliation.

### Privacy/Enterprise

- bug report envelope؛
- secret redaction؛
- analytics/log allowlists؛
- attachment policy؛
- RTDN contracts.

---

## Phase 4 — Xray Runtime + Enterprise Control Plane — DONE / MERGED

### VPN runtime

- typed provisioned profile model؛
- VLESS؛
- VMess؛
- Trojan؛
- Shadowsocks؛
- real Android `VpnService` lifecycle؛
- TUN lifecycle؛
- foreground notification/service؛
- socket protection؛
- runtime cleanup؛
- in-memory Xray config compilation؛
- pinned official libXray؛
- reject expired/malformed/replayed/insecure profiles.

### Control plane

- GVP1 envelope handling؛
- entitlement-aware protocol selection؛
- signed Remote Config؛
- Feature Flags؛
- gradual rollout؛
- analytics؛
- bug reports؛
- support tickets؛
- diagnostics؛
- immutable audit؛
- dual-control production config publish.

### Supply chain

- pinned Xray source؛
- source rebuild path؛
- native embedding verification؛
- SBOM؛
- vulnerability/provenance gates؛
- signed AAB workflow foundation؛
- alert policy.

---

## Phase 5 — Secure Android Profile-to-Tunnel Orchestration — DONE / MERGED

### انجام‌شده

- opaque one-time connection action؛
- binding validation؛
- user/service/server/profile consistency؛
- Android VPN permission handling؛
- foreground `VpnService` binding؛
- real tunnel start؛
- Connected UI only after runtime acceptance؛
- real disconnect؛
- replay tests؛
- auth failure tests؛
- zeroization behavior؛
- tunnel failure handling.

### نتیجه

فاصله بین UI و VPN runtime از حالت mock به orchestration واقعی و امن منتقل شد.

---

## Phase 6A — Device-bound Android Identity + GVP1 Crypto — DONE / MERGED

### انجام‌شده

- P-256 signing key؛
- Android Keystore protection؛
- X25519 encryption identity؛
- AES-GCM-wrapped X25519 private material؛
- separate/versioned key pairs؛
- canonical request proof؛
- P1363 signature؛
- GVP1 decrypt؛
- tamper tests؛
- deterministic Base64URL/proof tests؛
- fail-closed key rotation/error behavior.

### مرز باقی‌مانده در زمان این Phase

Session registration/login هنوز کامل نبود و عمداً fail-closed باقی ماند؛ این مورد به Phase 7 Auth/Session منتقل شد.

---

## Phase 6B — Replay-resistant Backend Device Proof — DONE / MERGED

### انجام‌شده

- GDP1 proof verification؛
- ES256/P-256؛
- P1363؛
- method/path/query/body binding؛
- key-version check؛
- 90-second clock window؛
- SHA-256 nonce digest storage؛
- atomic nonce consume per device/key؛
- PostgreSQL migration؛
- replay/body mutation/stale/future proof tests.

### نتیجه

Android device proof و Backend verification با یک قرارداد cryptographic هماهنگ شدند.

---

## Phase 6C — Approval-gated Google Play Test Publication — DONE / MERGED

### انجام‌شده

- opt-in publication job؛
- Internal track؛
- Closed Beta/alpha track؛
- protected environment؛
- reviewer gate؛
- service-account required؛
- signed AAB artifact revalidation؛
- SHA-256 verification؛
- release evidence validation؛
- mapping upload؛
- pinned Play upload action؛
- publication evidence artifact؛
- closed beta runbook.

### محدودیت عمدی

Production track عمداً تا عبور از beta/vitals/compliance Go-No-Go اتومات نشده است.

### هنوز انجام نشده

- release tag واقعی؛
- signed production candidate execution؛
- Internal Play upload واقعی؛
- Closed Beta واقعی؛
- Public production rollout.

---

# 6. Phase 7 — Production Closure Tracks — IN PROGRESS / NOT MERGED TO MAIN

> Phase 7 به‌صورت چند Track موازی اجرا می‌شود. وجود کد در branch به معنی Done یا Production-ready بودن نیست؛ تنها Merge به `main` پس از CI/Review معیار تثبیت است.

---

## 6.1 Track A — Auth & Session Vertical

### Branch مرجع فعلی

`phase-7/auth-session-vertical-v2`

### وضعیت هنگام ایجاد این سند

این Branch چند Commit از `main` جلوتر است و شامل migration و adapterهای Auth/Session است.

### هدف

بستن مهم‌ترین gap محصول: تبدیل Telegram identity + Device Identity به session واقعی و revocable.

### Scope

- user session issuance؛
- device registration؛
- device signing/encryption public key registration؛
- session refresh؛
- session revocation؛
- JTI/session state؛
- account/device active checks؛
- Telegram-linked identity؛
- logout؛
- remote revoke؛
- rotation policy؛
- trusted device state؛
- session persistence در PostgreSQL؛
- audit relevant events.

### Telegram Login flow هدف

1. اپ state + PKCE ایجاد می‌کند؛
2. Telegram/OIDC authorization آغاز می‌شود؛
3. callback/deep link به اپ؛
4. backend code exchange؛
5. Telegram identity verify؛
6. account lookup/create/link؛
7. device public identity register؛
8. access/refresh session issue؛
9. app session را امن ذخیره می‌کند؛
10. subsequent API calls bearer + GDP1 proof دارند.

### Deep Link Bot fallback

در صورت استفاده از bot-assisted linking:

- token باید one-time؛
- short-lived؛
- audience-bound؛
- account-bound؛
- replay-safe؛
- بدون Bot Token روی client باشد.

### Done Criteria

- Guest + Telegram login end-to-end روی staging؛
- device registration؛
- access refresh؛
- revoke؛
- expired/revoked session fail closed؛
- device mismatch rejected؛
- replay rejected؛
- unit/integration/security tests green؛
- Android UI flow بدون mock.

---

## 6.2 Track B — Telegram Bot / Legacy Subscription Sync

### هدف

کاربری که قبلاً از ربات Ganj VPN سرویس خریده باید پس از Login در اپ، بدون واردکردن Config، سرویس خود را ببیند.

### Scope

- Legacy Bot Adapter؛
- Telegram user ID → canonical Ganj user mapping؛
- old customer identity linking؛
- existing subscription import/read؛
- entitlement normalization؛
- renewal sync؛
- expiration sync؛
- revoke sync؛
- multi-device compatibility؛
- reconciliation job؛
- duplicate account prevention؛
- conflict resolution؛
- audit trail؛
- idempotency.

### قوانین

- Bot DB نباید client source of truth مستقیم باشد؛
- adapter باید data contract روشن داشته باشد؛
- sync باید idempotent باشد؛
- race بین bot renewal و app refresh نباید entitlement duplicate بسازد؛
- migration به backend جدید باید مرحله‌ای باشد.

### Done Criteria

- test user خرید قدیمی در Bot → login app → service visible؛
- bot renewal → app reflects؛
- expiration/revoke → app disconnect access removed؛
- no duplicate entitlement؛
- reconciliation report.

---

## 6.3 Track C — Play Billing End-to-End

### Branch مرجع فعلی

`phase-7/play-billing-e2e-v2`

### هدف

تبدیل adapterهای نوشته‌شده به زنجیره واقعی Google Play sandbox/internal test.

### Scope

- Play Console app setup؛
- products/base plans/offers؛
- license tester؛
- service account؛
- Android Publisher API؛
- BillingClient purchase؛
- purchase token vault؛
- server verification؛
- entitlement transaction؛
- acknowledgement؛
- RTDN؛
- renewal؛
- cancel؛
- expire؛
- grace period؛
- account hold؛
- pause/resume در صورت استفاده؛
- revoke/refund؛
- restore؛
- plan change؛
- duplicate callback/idempotency؛
- reconciliation.

### Done Criteria

Sandbox/License Tester باید این chain را پاس کند:

`Buy → Verify → Grant → Connect → Renew → Cancel/Expire/Refund → Revoke → Restore/new purchase`

بدون duplicate fulfillment.

---

## 6.4 Track D — Android VPN Resilience

### Branch مرجع فعلی

`phase-7/android-vpn-resilience-v2`

### وضعیت فعلی Branch

شامل توسعه محسوس روی:

- `GanjVpnService`؛
- secure profile recovery؛
- reconnect backoff؛
- Xray engine behavior؛
- profile model؛
- recovery codec tests.

### هدف

VPN باید فقط «وصل شود» کافی نیست؛ باید در شرایط واقعی موبایل پایدار بماند.

### Scope

- network loss؛
- Wi-Fi → Cellular؛
- Cellular → Wi-Fi؛
- captive portal transition؛
- airplane mode؛
- app process recreation؛
- service process restart؛
- OS reclaim؛
- Doze؛
- battery saver؛
- screen off؛
- reboot/autoconnect در صورت فعال بودن؛
- exponential backoff؛
- bounded retry؛
- stale profile rejection؛
- secure recoverable session state؛
- no credential persistence outside approved encrypted store؛
- clean disconnect.

### Kill Switch

باید تصمیم نهایی Android behavior گرفته و تست شود:

- Always-on VPN compatibility؛
- block connections without VPN؛
- app-level state explanation؛
- no false claim if OS policy not enabled؛
- reconnect + leak behavior.

### DNS Leak Protection

- DNS routing through tunnel؛
- prevent cleartext DNS fallback where applicable؛
- IPv4/IPv6 behavior؛
- leak lab tests.

### Done Criteria

- real-device network switching tests؛
- reconnect success target ≥95%؛
- no credential leak؛
- no stuck foreground service؛
- no phantom Connected UI؛
- Android 8–16 matrix relevant cases.

---

## 6.5 Track E — Android UI, Localization & Accessibility

### Branch مرجع فعلی

`phase-7/android-ui-accessibility-v2`

### وضعیت Branch

شروع شده و Theme + accessibility policy به کد اضافه شده است، اما **UI نهایی محصول هنوز Done نیست**.

### Scope کامل باقی‌مانده

#### Localization

- تمام stringهای hardcoded خارج شوند؛
- `values/strings.xml`؛
- `values-fa/strings.xml`؛
- locale switch policy؛
- Persian RTL؛
- English LTR؛
- number/date formatting؛
- pluralization؛
- error localization.

#### Visual System

- brand colors semantic tokens؛
- typography scale؛
- spacing system؛
- corner radius system؛
- elevation/glass rules؛
- icon system؛
- dark/light palettes؛
- contrast compliance؛
- loading skeletons؛
- empty/error/success states.

#### Motion

- connect animation؛
- state transition؛
- tab transitions؛
- server selection feedback؛
- checkout state؛
- reduced-motion accessibility policy؛
- no expensive infinite animations.

#### Accessibility

- TalkBack labels؛
- semantics؛
- minimum touch target؛
- dynamic/large font؛
- contrast؛
- focus order؛
- RTL layout validation؛
- screen reader purchase flow؛
- motion sensitivity.

#### Responsive

- compact phones؛
- tall phones؛
- different density؛
- edge-to-edge؛
- navigation gesture/insets؛
- foldable/tablet optional post-MVP optimization.

### Done Criteria

- no user-facing hardcoded English-only strings؛
- Persian RTL screenshot suite؛
- TalkBack audit؛
- large font usable؛
- dark/light verified؛
- final Product review.

---

## 6.6 Track F — Admin Console Foundation

### Branch مرجع فعلی

`phase-7/admin-console-foundation-v2`

### وضعیت Branch

Foundation شروع شده و `web/admin` شامل config/security/RBAC پایه شده است؛ اما پنل کامل نیست.

### هدف Admin Console

اپراتور بتواند بدون دستکاری DB یا Server CLI، عملیات محصول را امن انجام دهد.

### Modules موردنیاز

#### Dashboard

- active users؛
- paid users؛
- active subscriptions؛
- MRR/Revenue where applicable؛
- connect success؛
- server health؛
- crash/ANR summary؛
- open tickets؛
- purchase failure rate.

#### Users

- search؛
- user detail؛
- Telegram identity؛
- devices؛
- sessions؛
- services؛
- orders؛
- consent state؛
- tickets؛
- audit history؛
- safe revoke/disable.

#### Plans

- plan CRUD with versioning؛
- channel availability؛
- limits؛
- device slots؛
- server tiers؛
- Free/Premium rules؛
- price reference mapping؛
- activation window.

#### Servers

- add/edit logical server؛
- secret reference only؛
- country؛
- protocol؛
- tier؛
- enabled/draining/maintenance؛
- health؛
- capacity؛
- weight؛
- rollout؛
- no raw secret display.

#### Orders/Billing

- order search؛
- provider status؛
- reconciliation؛
- refund/revoke visibility؛
- no manual fake paid button in production؛
- controlled admin adjustment with audit where business requires.

#### Support

- tickets؛
- status؛
- internal notes؛
- assignment؛
- user-safe replies؛
- diagnostics linking.

#### Reliability

- bug reports؛
- diagnostic reports؛
- app version؛
- device/app metadata؛
- duplicate grouping؛
- severity؛
- resolution workflow.

#### Enterprise

- Remote Config؛
- Feature Flags؛
- gradual rollout؛
- target cohorts؛
- dual control publish؛
- immutable Audit Log.

#### Marketing

- announcements؛
- banners؛
- popups؛
- campaigns؛
- audience filters؛
- push scheduling؛
- referral configuration.

### Security

- Admin RBAC؛
- MFA؛
- short sessions؛
- CSRF؛
- secure cookies؛
- CSP؛
- audit every mutation؛
- separate permissions for financial/security operations؛
- dual control for high-risk production actions.

### Done Criteria

- no direct DB requirement for normal operations؛
- RBAC tested؛
- audit tested؛
- high-risk actions protected؛
- production API only؛
- responsive admin UI.

---

## 6.7 Track G — Staging / Production Operations

### Branch مرجع فعلی

`phase-7/staging-ops-v2`

### وضعیت Branch

Foundation شامل مواردی مثل:

- production compose؛
- Nginx config؛
- production env example؛
- database backup/restore scripts؛
- smoke test؛
- env validation؛
- server secret adapter؛
- Telegram account broker adapter؛
- production Dockerfile changes.

### Scope کامل

- staging environment؛
- production environment؛
- TLS؛
- reverse proxy؛
- PostgreSQL؛
- migration job؛
- secret injection؛
- health checks؛
- readiness؛
- resource limits؛
- backup؛
- restore verification؛
- log routing؛
- metrics؛
- alerts؛
- deployment rollback؛
- disaster recovery؛
- least privilege؛
- firewall/network segmentation؛
- rate limiting/WAF where applicable.

### Done Criteria

- clean staging deploy from empty host؛
- migrations apply؛
- smoke test pass؛
- backup created؛
- restore into clean DB pass؛
- secret absent from image/repo/log؛
- rollback drill documented؛
- monitoring active.

---

# 7. کارهای محصول باقی‌مانده پس از Phase 7 Core Closure

## 7.1 Free Plan

### نیازها

- Free entitlement؛
- allowed free servers؛
- quota policy؛
- speed policy در صورت نیاز؛
- device limit؛
- upgrade prompts؛
- abuse prevention؛
- campaign/offer eligibility؛
- ads فقط اگر تصمیم محصول نهایی شود.

### ممنوع

Free policy نباید با دستکاری tunnel traffic برای monetization اجرا شود.

---

## 7.2 Favorites / Search / Filter / Sort

- favorite countries/servers؛
- search country/city؛
- latency sort؛
- recommended؛
- available only؛
- protocol filter if exposed safely؛
- entitlement-aware results.

---

## 7.3 Smart Connect v1 Production

Ranking inputها می‌تواند شامل:

- server health؛
- latency؛
- capacity؛
- region؛
- user plan؛
- server tier؛
- protocol compatibility؛
- maintenance/draining؛
- failure history aggregate.

نباید از browsing destination استفاده کند.

### Metrics

- P50 connect <1.8s target؛
- P95 connect <4s target؛
- reconnect success ≥95%.

---

## 7.4 Speed Test

- latency test؛
- controlled download/upload test؛
- user initiated؛
- data consumption warning؛
- no background abusive bandwidth؛
- local result display؛
- optional aggregate telemetry only with policy/consent.

---

## 7.5 Notifications / FCM

- transactional notifications؛
- subscription expiry؛
- renewal reminder؛
- purchase status؛
- service issue؛
- security notice؛
- support reply؛
- marketing opt-in؛
- notification preferences؛
- quiet behavior؛
- deep link safe handling.

---

## 7.6 Marketing Platform

- announcements؛
- in-app banners؛
- popup؛
- offer cards؛
- audience by plan/app version/locale؛
- campaign start/end؛
- frequency cap؛
- analytics funnel؛
- A/B only for UX/offer, not security behavior.

---

## 7.7 Referral

- referral code/link؛
- anti-self-referral؛
- fraud limits؛
- attribution window؛
- reward rule؛
- entitlement extension or wallet credit policy؛
- audit؛
- admin controls.

---

## 7.8 Wallet / Direct Payments

برای Direct APK:

- unified wallet؛
- transaction ledger؛
- top-up؛
- gateway provider adapter؛
- Telegram purchase flow؛
- idempotent payment callbacks؛
- fraud controls؛
- balance atomicity؛
- refund adjustment؛
- invoice/receipt؛
- reconciliation؛
- admin audit.

**اصل:** ledger باید append-oriented و مالیات/حسابداری policy-aware باشد؛ balance صرفاً یک عدد قابل overwrite نباشد.

---

## 7.9 Multi-device Management

- device list؛
- friendly name؛
- last seen محدود و privacy-safe؛
- trusted/revoked؛
- plan slot count؛
- revoke other device؛
- session revoke؛
- graceful replacement؛
- anti-sharing policy.

---

# 8. Enterprise Systems

## 8.1 Bug Tracking

### User Bug Report

اپ باید بتواند با رضایت/اطلاع مناسب ارسال کند:

- app version؛
- Android version؛
- device model؛
- locale؛
- network type coarse؛
- current product state؛
- selected server ID غیرحساس؛
- error code؛
- timestamp؛
- bounded sanitized logs؛
- optional screenshot/attachment در صورت policy.

نباید ارسال شود:

- VPN credentials؛
- raw profile؛
- private key؛
- access token؛
- destination history؛
- traffic content.

## 8.2 Diagnostics

- explicit action/consent؛
- bounded collection؛
- redaction؛
- retention policy؛
- support reference؛
- no silent full-device dump.

## 8.3 Support Tickets

- create؛
- category؛
- status؛
- replies؛
- attachments policy؛
- diagnostic link؛
- ownership enforcement؛
- admin assignment.

## 8.4 Remote Config

- Ed25519 signed؛
- public behavior/presentation values only؛
- key version؛
- cached validation؛
- fail-safe defaults؛
- no secret in config؛
- dual-control production publish.

## 8.5 Feature Flags

- deterministic cohort؛
- tier؛
- locale؛
- app version؛
- Android version/platform where needed؛
- gradual rollout؛
- kill switch for non-security feature؛
- security invariants cannot be disabled by ordinary feature flag.

## 8.6 Analytics

- explicit purpose registry؛
- consent state؛
- allowlisted events/properties؛
- idempotent batch؛
- withdrawal honored؛
- no destination/DNS/IP/payload/ad-id by default؛
- funnel for onboarding/login/store/purchase/connect/support.

## 8.7 Audit Log

High-value actions:

- admin login/security changes؛
- user disable؛
- service adjustment؛
- server change؛
- plan change؛
- feature flag؛
- remote config publish؛
- financial adjustment؛
- role change؛
- secret reference rotation event؛
- production release.

Audit باید immutable/append-only و sanitized باشد.

---

# 9. Observability & SRE

## 9.1 Metrics

### API

- request rate؛
- latency؛
- 4xx/5xx؛
- auth failures؛
- profile issuance؛
- purchase verification latency؛
- DB pool؛
- migration status.

### VPN Product

Privacy-safe aggregate metrics:

- connect attempt؛
- connect success/failure reason code؛
- reconnect؛
- duration buckets؛
- server health؛
- no traffic destination.

### Commerce

- checkout initiated؛
- provider launched؛
- purchase pending؛
- verification pass/fail؛
- acknowledgement failure؛
- RTDN lag؛
- duplicate fulfillment MUST remain zero.

## 9.2 Alerts

- API unavailable؛
- high 5xx؛
- DB unavailable؛
- migration failure؛
- purchase verification spike؛
- RTDN failure؛
- server pool unhealthy؛
- connect failure spike؛
- secret/key expiry/rotation issue؛
- backup failure؛
- no-data fail-closed cases where appropriate.

---

# 10. Backup, Restore & Disaster Recovery

## 10.1 Database Backup

- automated schedule؛
- encrypted storage؛
- retention policy؛
- checksum؛
- restore test؛
- off-host copy؛
- secrets backup policy separate.

## 10.2 Restore Drill

معیار واقعی Backup وجود فایل نیست؛ باید:

1. clean DB ایجاد شود؛
2. backup restore شود؛
3. migrations/schema validated؛
4. core queries pass؛
5. smoke test pass؛
6. evidence ثبت شود.

## 10.3 Release rollback

- application rollback؛
- schema forward-compatible strategy؛
- config rollback؛
- feature flag emergency disable؛
- Play staged rollout halt؛
- server drain.

---

# 11. Google Play Release Requirements

## 11.1 Technical

- target API 36+ طبق policy زمان انتشار؛
- signed AAB؛
- Play App Signing؛
- stable applicationId؛
- mapping file؛
- SBOM؛
- provenance/checksum؛
- pre-launch report؛
- crash/ANR thresholds.

## 11.2 VPN Service Compliance

- VPN core functionality واضح در listing؛
- VpnService declaration؛
- prominent disclosure where required؛
- encrypted device-to-tunnel traffic؛
- no traffic manipulation for ads؛
- reviewer test account/server.

## 11.3 Data Safety

باید دقیقاً با binary نهایی تطبیق داشته باشد، نه با intent سند.

نیاز به review SDK/permissions دارد.

## 11.4 Store Assets

- app icon؛
- feature graphic؛
- phone screenshots واقعی؛
- Persian/English listing در صورت عرضه؛
- short description؛
- full description؛
- privacy policy؛
- support contact؛
- demo/video در صورت نیاز declaration؛
- subscription terms.

## 11.5 Account deletion

اگر account creation وجود دارد، مسیر deletion باید مطابق policy ارائه شود:

- in-app entry؛
- public web path where required؛
- session revoke؛
- retention/legal exceptions policy؛
- deletion confirmation.

---

# 12. Release Stages

## Stage 1 — Developer APK

- debug build؛
- unit/lint؛
- developer devices؛
- not public.

## Stage 2 — Signed Candidate

- release tag؛
- release version/code؛
- signed AAB/APK؛
- checksum؛
- mapping؛
- SBOM؛
- artifact evidence.

## Stage 3 — Internal Testing

- Play Internal؛
- controlled testers؛
- real backend؛
- real test servers؛
- Play Billing tester؛
- crash monitoring.

## Stage 4 — Closed Beta

- broader device matrix؛
- auth/billing/VPN E2E؛
- refund/cancel/expire؛
- network switch/Doze؛
- privacy/compliance audit؛
- support workflow.

## Stage 5 — Production Go/No-Go

Required evidence:

- critical security findings = 0؛
- billing duplicate fulfillment = 0؛
- backup restore pass؛
- privacy/data safety reviewed؛
- VpnService declaration ready؛
- crash-free target؛
- ANR target؛
- connect performance target؛
- support/incident on-call ownership؛
- rollback tested.

## Stage 6 — Staged Rollout

هدف:

`5% → 20% → 50% → 100%`

با توقف در صورت regressions.

---

# 13. تست و Quality Gates

## 13.1 Android

- unit tests؛
- reducer/controller tests؛
- crypto tests؛
- VPN runtime tests؛
- instrumentation؛
- real-device smoke؛
- lint؛
- release lint؛
- dependency verification؛
- product guard؛
- no manual-config guard؛
- accessibility test؛
- screenshot/manual visual QA؛
- macrobenchmark where useful.

## 13.2 Backend

- unit؛
- integration؛
- PostgreSQL integration؛
- migration؛
- ownership؛
- auth؛
- replay؛
- idempotency؛
- billing lifecycle؛
- Telegram exchange؛
- RTDN؛
- admin scope؛
- dual control؛
- retention/consent.

## 13.3 Security

- SAST/CodeQL؛
- dependency review؛
- secret scan؛
- SBOM؛
- vulnerability scan؛
- artifact verification؛
- penetration test before production؛
- TLS/config review؛
- exported Android component review؛
- authorization matrix؛
- rate-limit/abuse tests.

---

# 14. Performance Targets

MVP targets:

| Metric | Target |
|---|---:|
| Crash-free users | ≥99.7% |
| ANR rate | <0.25% |
| P50 VPN connect | <1.8s |
| P95 VPN connect | <4.0s |
| Reconnect success | ≥95% |
| API P95 read | <350ms excluding providers |
| Idle connected battery | <1%/h target device |
| Duplicate purchase fulfillment | 0 |
| Secret/config leak | 0 findings |

این اعداد باید با telemetry آزمایشگاهی/production-safe اندازه‌گیری شوند.

---

# 15. وضعیت تقریبی Workstreams

> درصدها ابزار برنامه‌ریزی‌اند، نه معیار contractual. Done فقط با Acceptance Criteria و evidence معنی دارد.

| Workstream | وضعیت تقریبی | توضیح |
|---|---:|---|
| Architecture / Security foundations | 90%+ | هسته بسیار جلو است؛ production hardening مانده |
| Android functional shell | ~75% | UX نهایی/localization/polish کامل نیست |
| Xray/VpnService core | ~70% | resilience/leak/device matrix مانده |
| Device crypto/proof | ~90% | session integration و field validation مانده |
| Control API | ~80% | deployment/production adapters/ops مانده |
| Telegram auth/session | In progress | Phase 7 branch فعال |
| Bot subscription sync | early | integration/reconciliation مانده |
| Play Billing | mid/advanced | adapter آماده، E2E واقعی مانده |
| Direct wallet/payment | early | نیازمند vertical slice کامل |
| Admin Console | foundation | Phase 7 branch شروع شده |
| Enterprise APIs | advanced | Admin UX/production operations مانده |
| Release automation | advanced | execution واقعی و beta مانده |
| Public release readiness | ~55–60% overall | به E2E production closure وابسته |

---

# 16. ترتیب پیشنهادی ادامه توسعه

## Critical Path A — Product can authenticate

1. Phase 7 Auth/Session merge؛
2. Android Telegram login UI؛
3. Telegram account broker production implementation؛
4. device registration؛
5. token refresh/revoke؛
6. staging E2E.

## Critical Path B — Existing customers appear in app

1. legacy bot adapter؛
2. account mapping؛
3. entitlement sync؛
4. reconciliation؛
5. renewal/expiry/revoke tests.

## Critical Path C — VPN production reliability

1. resilience branch merge؛
2. real test servers؛
3. network switch؛
4. Doze/battery؛
5. DNS leak؛
6. kill switch behavior؛
7. Android matrix.

## Critical Path D — Commerce

1. Play E2E branch merge؛
2. Play Console products؛
3. service account؛
4. license tester؛
5. RTDN؛
6. lifecycle tests.

## Critical Path E — Operations

1. staging ops branch merge؛
2. staging deployment؛
3. secrets؛
4. DB backup/restore؛
5. monitoring؛
6. release candidate.

## Parallel Product Track

همزمان:

- final UI/localization؛
- Admin Console؛
- Free plan؛
- favorites/search/speed test؛
- notifications؛
- marketing/referral؛
- support polish.

---

# 17. Release Blockers — Must be zero before Public Production

موارد زیر Public Release blocker هستند:

- Telegram/Guest auth not E2E؛
- user session not revocable؛
- device registration incomplete؛
- bot subscription sync incomplete برای مدل کسب‌وکار فعلی؛
- production backend not deployed؛
- real DB backup/restore not proven؛
- no real VPN server E2E؛
- DNS leak unresolved؛
- reconnect unreliable؛
- Play Billing E2E incomplete برای Play flavor؛
- Privacy Policy/Data Safety mismatch؛
- VpnService declaration incomplete؛
- account deletion missing when required؛
- signed release not reproducible/attested؛
- critical/high unresolved security finding؛
- no support/incident path.

---

# 18. Non-blocking for First Public MVP but required roadmap

بسته به scope نسخه اول، این‌ها می‌توانند پس از MVP یا طی staged rollout تکمیل شوند اگر Product تصمیم بگیرد:

- advanced Speed Test؛
- referral؛
- complex campaign builder؛
- ads in Free plan؛
- advanced A/B testing؛
- advanced server map؛
- sophisticated recommendations؛
- organization/team accounts؛
- desktop/iOS.

---

# 19. Post-MVP Roadmap

## VPN

- WireGuard؛
- MultiHop؛
- rotating IP؛
- advanced anti-censorship transport selection؛
- per-app routing بدون broad package access در صورت امکان؛
- signed routing rules؛
- advanced obfuscation.

## Platforms

- iOS SwiftUI + Network Extension؛
- Windows؛
- macOS؛
- Linux؛
- browser/account portal where useful.

## Business

- team/organization accounts؛
- reseller/partner system؛
- gift codes؛
- advanced loyalty؛
- regional pricing؛
- invoice/business billing where applicable.

## Enterprise

- enhanced fraud engine؛
- anomaly detection؛
- SOC integrations؛
- advanced experimentation framework؛
- cost/capacity forecasting.

---

# 20. Smart Banking Integration — Future/Parallel Ganj Ecosystem

در Product vision امکان اتصال سیستم تأیید هوشمند بانکی نیز مطرح شده است.

در صورت پیاده‌سازی باید:

- از VPN core جدا باشد؛
- permission budget Play را نقض نکند؛
- SMS permission در Play VPN app بدون justification اضافه نشود؛
- ترجیحاً payment verification در backend/companion flow انجام شود؛
- receipt/bank-event data حداقلی و purpose-bound باشد؛
- financial event به entitlement transaction امن متصل شود؛
- idempotency و anti-fraud داشته باشد.

این قابلیت نباید Release VPN را بدون ضرورت تجاری block کند.

---

# 21. Repository Structure Target

```text
apps/
  android/                 Android client
web/
  admin/                   Admin console
services/
  control-api/             Main control plane
  telemetry-worker/        Future async worker/probes
packages/
  contracts/               Generated/shared contracts
  design-tokens/           Shared semantic tokens
vpn-core/
  xray-wrapper/            If extracted from Android core
ops/
  compose / nginx / backup / monitoring / deployment
api/
  openapi.yaml
database/
  baseline/reference schema
docs/
  architecture / security / release / this master plan
```

ساختار واقعی ممکن است در طول Refactor تغییر کند؛ dependency boundaries مهم‌تر از نام پوشه‌اند.

---

# 22. Definition of Done عمومی هر Feature

Feature فقط وقتی Done است که موارد مرتبط زیر انجام شده باشد:

- code complete؛
- no secret؛
- authorization checked؛
- input validation؛
- error model؛
- localization user-facing text؛
- accessibility impact؛
- telemetry/privacy impact؛
- migration if needed؛
- rollback/backward compatibility impact؛
- unit tests؛
- integration test where boundary exists؛
- CI green؛
- docs/OpenAPI update؛
- release impact assessed.

برای Featureهای امنیتی/مالی:

- replay/idempotency؛
- audit؛
- negative tests؛
- fail-closed behavior؛
- no client trust for authoritative state.

---

# 23. قواعد ادامه کار برای AI Agents

اگر چند Agent موازی روی پروژه کار می‌کنند:

1. هر Agent Branch جدا داشته باشد؛
2. overlap فایل‌ها حداقل شود؛
3. قرارداد مشترک اول مشخص شود؛
4. migration numbers conflict نکنند؛
5. API changes با OpenAPI هماهنگ شوند؛
6. security invariant شکسته نشود؛
7. هیچ Agent بدون بررسی `main` و active Phase branches تغییر موازی تکراری نسازد؛
8. merge order برای branchهای وابسته مستند شود؛
9. CI evidence قبل از merge؛
10. بعد از merge branch dependent rebase/compare شود.

### Agent streams پیشنهادی

- Auth/Identity؛
- Telegram/Bot Sync؛
- Android UI/Localization؛
- VPN Reliability؛
- Billing؛
- Admin Web؛
- Ops/SRE؛
- QA/Security/Release.

---

# 24. تصمیم‌های قطعی که نباید بدون Product/Security Review تغییر کنند

- no manual VPN config؛
- entitlement-only connection؛
- backend authoritative payment verification؛
- device-bound encrypted profile؛
- separate signing/encryption device keys؛
- no VPN credential in UI/log؛
- no browsing/DNS/traffic-content logging؛
- production test adapters forbidden؛
- Play and Direct payment policy separated؛
- signed Remote Config؛
- consent-governed analytics؛
- immutable audit for privileged changes؛
- production publication gated؛
- secrets outside Git.

---

# 25. Next Milestone Definition — Ganj VPN Alpha

برای اینکه پروژه را **Alpha واقعی** بنامیم، حداقل باید:

- Android login واقعی؛
- Telegram account/session؛
- existing/new service visible؛
- production-like staging backend؛
- real Xray server connection؛
- stable disconnect/reconnect؛
- Play test purchase؛
- entitlement grant/revoke؛
- Persian/RTL usable UI؛
- crash/bug reporting؛
- basic Admin access؛
- backup/restore evidence؛
- signed Internal Play build.

---

# 26. Next Milestone Definition — Closed Beta

Alpha +:

- broader device matrix؛
- network/Doze resilience؛
- full purchase lifecycle؛
- legacy bot sync؛
- server monitoring؛
- support workflow؛
- privacy policy/data safety binary review؛
- VpnService declaration materials؛
- accessibility pass؛
- performance/battery targets؛
- penetration test remediation؛
- release rollback drill.

---

# 27. Next Milestone Definition — Public MVP

Closed Beta + Go/No-Go evidence:

- no critical blockers؛
- production infrastructure؛
- stable billing/auth/VPN chain؛
- operational admin tools؛
- incident/support ownership؛
- store compliance؛
- staged rollout plan؛
- monitoring and rollback active.

---

# 28. خلاصه مدیریتی وضعیت فعلی

Ganj VPN از مرحله Prototype اولیه عبور کرده است. بخش مهمی از معماری، Security، Subscription Control API، Google Play Billing adapters، Xray/VpnService، device cryptography، replay protection، enterprise control plane و release automation ساخته و روی `main` تثبیت شده است.

اما محصول هنوز **Public Production Ready نیست**، چون زنجیره‌ی End-to-End زیر باید کاملاً بسته شود:

`Telegram/User Login → Revocable Session → Device Registration → Bot/Subscription Sync → Entitlement → Real Server/Profile → Stable VPN Runtime → Billing Lifecycle → Production Backend/Ops → Signed Play Beta → Compliance Go/No-Go`

Phase 7 دقیقاً برای بستن عمده این فاصله‌ها در Trackهای موازی شروع شده است.

### مهم‌ترین اولویت‌ها

1. Auth/Session؛
2. Telegram Bot subscription sync؛
3. VPN resilience + leak testing؛
4. Play Billing E2E؛
5. Staging/Production deployment؛
6. final UI/localization/accessibility؛
7. Admin Console؛
8. closed beta/release evidence.

---

# 29. Change Management این سند

بعد از هر Phase/PR بزرگ، این فایل باید به‌روزرسانی شود:

- Phase status: `PLANNED / IN PROGRESS / MERGED / VERIFIED / RELEASED`؛
- branch/PR reference؛
- delivered scope؛
- deferred scope؛
- new blockers؛
- changed security invariant؛
- new release gate.

هدف این است که هیچ توسعه‌دهنده یا AI برای فهمیدن وضعیت پروژه مجبور نباشد تاریخچه ده‌ها چت و PR را از ابتدا بازسازی کند.

---

## پایان سند

**اصل نهایی:** کیفیت Ganj VPN با تعداد Feature سنجیده نمی‌شود؛ معیار واقعی این است که یک کاربر بتواند بدون مشاهده یا واردکردن هیچ کانفیگ خام، به‌صورت امن وارد حساب شود، اشتراک معتبر خود را ببیند/بخرد، روی دستگاه مجاز به سرور سالم وصل شود، در شرایط واقعی موبایل اتصال پایدار داشته باشد، و تمام این زنجیره از نظر پرداخت، امنیت، حریم خصوصی، مانیتورینگ و عملیات قابل اتکا باشد.
