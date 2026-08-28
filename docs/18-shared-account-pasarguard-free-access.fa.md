# Ganj VPN — Shared Account, PasarGuard Live Profiles & Login-Free Free Tier

> **Status:** Canonical / Mandatory  
> **Applies to:** Android, Control API, Telegram Bot integration, Admin Console, PasarGuard integration, Commerce, Free Tier  
> **Purpose:** تثبیت معماری مشترک Ganj VPN و ربات Ganj و جلوگیری از ساخت چند منبع حقیقت، کپی کانفیگ، Login اجباری برای Free یا اتصال مستقیم Client به دیتابیس/پنل‌های مدیریتی.

---

## 1. تصمیم نهایی محصول

Ganj VPN و Telegram Bot دو محصول جدا با حساب و دیتای مستقل نیستند؛ هر دو Surface یک اکوسیستم مشترک هستند.

کاربر باید بتواند:

- اپ را نصب کند و بدون Login از Free Tier استفاده کند؛
- وقتی قصد خرید، فعال‌سازی، تمدید یا بازیابی سرویس قبلی را دارد با Telegram وارد/Link شود؛
- سرویس‌هایی که قبلاً از ربات Ganj خریده است داخل App ببیند؛
- بدون واردکردن کانفیگ، QR، Subscription URL یا فایل به سرویس خودش وصل شود؛
- وضعیت واقعی سرویس، حجم، انقضا و Node/Configهای قابل استفاده را از داده زنده Upstream ببیند؛
- در Bot و App وضعیت تجاری سازگار داشته باشد و خرید تکراری به‌خاطر جدا بودن دو سیستم رخ ندهد.

---

## 2. Login UX Lock

### 2.1 Primary: Telegram Bot Approval

مسیر اصلی Login/Account Link باید ساده‌ترین UX ممکن را داشته باشد:

```text
Ganj VPN
  -> «ورود با تلگرام»
  -> Telegram / Ganj Bot deep-link
  -> «تأیید ورود به Ganj VPN»
  -> One-time approval
  -> بازگشت/Resume App
  -> Session issued
  -> My Services refresh
```

کاربر در این Flow:

- شماره تلفن وارد نمی‌کند؛
- OTP داخل Ganj VPN وارد نمی‌کند؛
- Password تلگرام در اختیار Ganj قرار نمی‌دهد؛
- Telegram session/MTProto credential به Ganj نمی‌دهد.

### 2.2 Security properties that MUST remain

سادگی Bot Approval نباید hardening فعلی را حذف کند. Flow باید همچنان:

- device-bound باشد؛
- state تصادفی one-time داشته باشد؛
- PKCE S256 یا معادل cryptographic binding داشته باشد؛
- expiry کوتاه داشته باشد؛
- replay-safe باشد؛
- approval را دقیقاً به device/request شروع‌کننده bind کند؛
- session نهایی را فقط Backend صادر کند؛
- Telegram Bot هرگز مستقیماً Access Token اپ را تولید نکند؛
- duplicate callback/approval را idempotent و fail-closed مدیریت کند.

### 2.3 OIDC is fallback, not the default UX

Telegram OIDC می‌تواند به‌عنوان Fallback/alternative provider نگه داشته شود، اما UI اصلی باید Bot Approval باشد؛ مگر اینکه در Production evidence نشان دهد Bot Approval در یک Platform/Region قابل استفاده نیست.

### 2.4 Phone-number / Telegram-code login is prohibited for MVP

Flow زیر برای Ganj VPN MVP استفاده نشود:

```text
phone number -> Telegram login code -> enter code in Ganj VPN
```

این مسیر وارد مدیریت Telegram user session/MTProto می‌شود و سطح ریسک، نگهداری و UX نامطلوب‌تری نسبت به Bot Approval دارد.

---

## 3. Free Tier: No Visible Login

### 3.1 UX rule

کاربر Free نباید برای اولین اتصال مجبور به Telegram Login، شماره تلفن یا ساخت Account دستی شود.

App باید در پشت‌صحنه یک **Guest Device Identity** امن بسازد و Guest Session کوتاه/قابل refresh دریافت کند. از دید کاربر این Login محسوب نمی‌شود و هیچ Form نمایش داده نمی‌شود.

### 3.2 Why not fully anonymous profile issuance

Backend نباید raw connection profile را به caller کاملاً anonymous بدهد. Guest Device Identity برای موارد زیر الزامی است:

- abuse/rate limiting؛
- quota policy؛
- device-scoped free access؛
- one-time profile grants؛
- replay protection؛
- revoke/block در سوءاستفاده؛
- telemetry حداقلی و consent-aware؛
- جلوگیری از تبدیل Free endpoint به public config dump.

### 3.3 Login gate

Telegram Login فقط وقتی اجباری شود که کاربر یکی از عملیات زیر را بخواهد:

- خرید سرویس؛
- فعال‌سازی/Claim سرویس خریداری‌شده از Bot؛
- مشاهده My Services پولی متعلق به Telegram account؛
- تمدید/Upgrade/Downgrade؛
- Wallet/Direct commerce؛
- عملیات مالکیتی حساس؛
- بازیابی account روی device جدید در صورت نیاز policy.

Free browsing/connect نباید به این Gate وابسته باشد.

---

## 4. Source-of-Truth Matrix

هیچ Agent نباید یک Database واحد را به‌صورت مبهم «منبع همه چیز» فرض کند. Truth بر اساس Domain مشخص است.

| Domain | Authoritative Source | Ganj VPN behavior |
|---|---|---|
| Telegram identity | Telegram Bot verified user ID / approved provider subject | map به Ganj account |
| Bot-originated order | Ganj Bot commercial DB/ledger | reconcile به Control API read model |
| Bot-originated service ownership | Ganj Bot invoice/service records | reconcile، idempotent، no duplicate purchase |
| Google Play purchase | Google Play + verified Control API order | mirror/reconcile into shared Ganj account views |
| Direct/App purchase | verified payment/order backend | Bot و App هر دو همان ownership را ببینند |
| Live service status | PasarGuard | fetch/revalidate هنگام نمایش/اتصال |
| Live usage/expiry | PasarGuard | normalize for UI; stale cache فقط fallback محدود |
| Subscription node/config inventory | PasarGuard subscription/user APIs | backend parses and exposes safe metadata only |
| Actual connection secret/profile | PasarGuard-derived live data + Ganj profile issuer | sealed short-lived device-bound profile only |
| Free server catalog | Ganj Control Plane | Admin Console و Bot admin هر دو همین registry را تغییر دهند |
| Free access policy | Ganj Control Plane | quota/rate/session/provider policy centrally enforced |

---

## 5. Shared Ganj Account Model

### 5.1 Android must never read the Telegram Bot database directly

Android Client نباید مستقیم به MySQL/MariaDB ربات، فایل‌های Bot یا PasarGuard Admin API وصل شود.

مسیر درست:

```text
Android
  -> Ganj Control API
      -> Shared account/read model
      -> Ganj Bot reconciliation adapter
      -> PasarGuard adapter
      -> Play/payment adapters
```

این Boundary برای امنیت، migration، observability، versioning و جلوگیری از لو رفتن secret الزامی است.

### 5.2 Existing Bot customers

بعد از Bot Approval:

1. Telegram identity به Ganj user متصل می‌شود؛
2. Legacy/Bot reconciliation اجرا یا refresh می‌شود؛
3. order/service records مرتبط پیدا می‌شوند؛
4. همان سرویس‌ها در My Services ظاهر می‌شوند؛
5. هیچ config خامی از Bot DB به Android منتقل نمی‌شود؛
6. وضعیت زنده سرویس از PasarGuard revalidate می‌شود؛
7. اتصال از PasarGuard-derived short-lived profile انجام می‌شود.

### 5.3 Purchases created inside the App

خرید App نباید یک اکوسیستم دوم بسازد. Order نهایی باید در Shared Ganj account model ثبت شود و Bot نیز بتواند همان سرویس/وضعیت را نمایش دهد.

هدف نهایی:

> یک کاربر، یک account graph، یک ownership view؛ صرف‌نظر از اینکه خرید از Bot، Web، Play یا Direct انجام شده است.

---

## 6. Bot Data vs PasarGuard Data

### 6.1 Bot is commercial/ownership authority for legacy Bot purchases

برای سرویس‌هایی که از ربات ساخته شده‌اند، داده‌هایی مانند موارد زیر از Bot reconciliation می‌آیند:

- Telegram user/customer identity؛
- invoice/order identifier؛
- product/plan mapping؛
- service username/external service key؛
- panel/source key؛
- purchase channel؛
- purchase/renewal lifecycle event؛
- ownership relationship.

### 6.2 PasarGuard is runtime service authority

برای PasarGuard-backed service، موارد زیر نباید از snapshot قدیمی Bot به‌عنوان truth استفاده شوند:

- current status؛
- live traffic usage؛
- current expire time؛
- proxy settings؛
- current subscription URL/path؛
- currently available configs/nodes؛
- credentials؛
- server-side changes/revocation.

این موارد باید از PasarGuard در Backend خوانده و validate شوند.

### 6.3 Known PasarGuard compatibility contract

Integration فعلی Ganj Bot، PasarGuard را به‌عنوان Marzban-compatible API با semantics مخصوص PasarGuard می‌شناسد. Contract مورد انتظار فعلی شامل الگوهای زیر است:

```text
POST /api/admin/token
GET  /api/user/{username}
POST /api/user
PUT/PATCH user operations according to supported upstream contract
GET  /api/nodes (where needed)
```

PasarGuard-specific behavior باید در Adapter مستقل encapsulate شود؛ هیچ caller دیگری نباید فرض کند همه Marzban-compatible providerها دقیقاً semantics یکسان دارند.

---

## 7. Subscription Config / Node Display

عبارت «نمایش کانفیگ‌های Subscription» در Ganj VPN به معنی نمایش Raw Secret نیست.

App می‌تواند Nodeهای Subscription کاربر را به شکل امن نمایش دهد:

- کشور؛
- شهر؛
- نام Node؛
- protocol؛
- transport؛
- ping/latency؛
- availability؛
- favorite؛
- مناسب بودن برای service tier؛
- optional capability labels.

اما موارد زیر نباید در UI/clipboard/export قرار گیرند:

- `vless://...`
- `vmess://...`
- `trojan://...`
- `ss://...`
- raw Xray JSON؛
- Subscription URL؛
- UUID/password/private credential؛
- PasarGuard admin token/password.

برای Connect، Backend node انتخاب‌شده را به Profile کوتاه‌عمر، one-time و device-bound تبدیل می‌کند.

---

## 8. PasarGuard Live Profile Pipeline

Target pipeline:

```text
Linked Ganj service
  -> upstream binding from Bot reconciliation
     (panel/source key + external service username/id)
  -> PasarGuard adapter
  -> GET live service
  -> validate ownership/status/expiry/quota
  -> fetch/parse current subscription node inventory
  -> choose requested eligible node
  -> normalize to Ganj profile schema
  -> seal to exact Android device (GVP1)
  -> short TTL / one-time grant
  -> Xray/VpnService
```

### Hard rule

`subscription_url` فقط Backend-side upstream locator است. Android نباید آن را دریافت یا fetch کند.

### Caching

- Commercial ownership may be reconciled/cached durably.
- Node metadata may have short TTL cache.
- Credentials/profile material must not be persisted as long-lived plaintext cache.
- At connect time, stale service state must be revalidated according to policy.

---

## 9. Free Server Management

### 9.1 Admin Console is the primary management surface

Admin Console باید امکان مدیریت Free Serverها را داشته باشد:

- Add/Edit/Disable؛
- country/city/name؛
- priority/sort؛
- protocol capabilities؛
- upstream provider؛
- upstream panel/reference؛
- health status؛
- maintenance؛
- max load/concurrency policy؛
- per-device/session quota؛
- rollout percentage؛
- minimum app version؛
- optional region/ISP targeting؛
- emergency disable.

### 9.2 Telegram Bot admin is an alternate control surface

در صورت نیاز ادمین باید بتواند Free Server را از Bot هم مدیریت کند، اما Bot نباید Registry جدا داشته باشد.

هر دو Surface باید به همان Control Plane بنویسند:

```text
Admin Web ----\
               -> Ganj Free Server Registry -> Android
Admin Bot ----/
```

### 9.3 Secrets

Admin UI فقط secret reference / connector status را نشان دهد، نه raw password/token/config.

---

## 10. Free Connection Architecture

Professional default برای Free، shared static public credential نیست.

Preferred model:

1. App silent Guest identity می‌سازد؛
2. Free server انتخاب می‌شود؛
3. Backend policy/quota را بررسی می‌کند؛
4. Backend از upstream free pool/adapter یک device-scoped credential/profile می‌گیرد یا ایجاد می‌کند؛
5. profile کوتاه‌عمر و encrypted به device صادر می‌شود؛
6. abuse/revocation policy server-side باقی می‌ماند.

اگر Upstream قابلیت per-device ephemeral identity نداشته باشد، هر fallback به shared credential باید Security Review جدا داشته باشد و برای Production پیش‌فرض محسوب نشود.

---

## 11. Admin Console Required Modules

Admin Console هنوز کامل نشده و باید حداقل این بخش‌ها را داشته باشد:

### Shared Account
- users؛
- Telegram link state؛
- devices؛
- services؛
- orders؛
- source of purchase؛
- reconciliation status.

### Bot Sync
- source health؛
- cursor/checkpoint؛
- last successful sync؛
- conflicts؛
- unmapped plans؛
- ownership collisions؛
- retry/replay status.

### PasarGuard
- connector list؛
- health؛
- last API success/error؛
- panel/source mapping؛
- service lookup diagnostics؛
- subscription inventory diagnostics؛
- secret reference status only.

### Free Tier
- free server catalog؛
- upstream mapping؛
- quota policy؛
- concurrency/load policy؛
- enabled regions؛
- maintenance/emergency disable؛
- abuse metrics.

### Product/Commerce
- plans؛
- prices؛
- Play/direct mapping؛
- feature flags؛
- order/payment reconciliation.

---

## 12. API Boundaries Required

Control API should evolve toward explicit ports/adapters:

```text
CommercialAccountSource
LegacyBotSource
PasarGuardServiceSource
SubscriptionNodeSource
ConnectionProfileIssuer
FreeServerRegistry
FreeAccessPolicy
```

Do not put Bot SQL, PasarGuard HTTP and Android response mapping in one module.

---

## 13. Security Boundaries

Mandatory:

- no Android -> Bot DB direct connection؛
- no Android -> PasarGuard Admin API direct connection؛
- no panel credentials in app؛
- no raw configs in logs/audit؛
- no subscription URL exposure to Client؛
- no admin token in reconciliation event؛
- HTTPS only upstream connectors؛
- SSRF-safe allowlisted panel endpoints؛
- bounded response sizes/timeouts؛
- strict JSON/content parsing؛
- no automatic ownership guess on conflicts؛
- one-time profile grant؛
- device proof for profile issuance؛
- secret zeroization where practical؛
- audit admin changes without storing raw secret.

---

## 14. Failure Behavior

### Bot unavailable

Existing cached ownership may be shown with explicit stale state, but destructive/financial assumptions must not be made.

### PasarGuard unavailable

Do not fabricate a connection profile. UI may show service as temporarily unavailable and retry safely.

### Bot says owned, PasarGuard says service missing

Create reconciliation conflict/diagnostic state. Do not silently create a duplicate paid service.

### PasarGuard says expired/disabled

Connect must fail closed even if Bot snapshot is stale-active.

### User has paid service but Telegram mapping conflicts

Require reviewed merge/recovery. Do not transfer financial/service ownership silently.

---

## 15. Agent Implementation Order

Unless a narrower issue explicitly overrides it, agents working on this area should execute in this order:

1. Bot Approval primary Login + OIDC fallback؛
2. Bot commercial/ownership source adapter؛
3. durable Telegram account mapping؛
4. service upstream binding (`source/panel + external service id/username`)؛
5. PasarGuard live-service adapter؛
6. subscription node inventory normalization؛
7. sealed live connection profile issuance؛
8. silent Guest Free path؛
9. Free Server Registry + policy؛
10. Admin Console Free/PasarGuard/Bot Sync modules؛
11. Bot-admin write path to the same registry؛
12. real E2E evidence.

---

## 16. Definition of Done

این Requirement فقط وقتی Complete است که حداقل Evidence زیر وجود داشته باشد:

### Free user

```text
fresh install
-> no Telegram login
-> free server list
-> connect
-> disconnect/reconnect
```

و هیچ raw config در Client وجود نداشته باشد.

### Existing Bot customer

```text
fresh install
-> Bot Approval
-> existing Bot service appears once
-> live status/usage from PasarGuard
-> nodes shown as safe metadata
-> selected node connects through sealed profile
```

### Cross-surface consistency

```text
Bot renewal/change
-> reconciliation
-> App reflects same ownership/lifecycle

App verified purchase
-> shared ownership record
-> Bot can reflect the same service/account state
```

### Admin

```text
add/disable free server in Admin Console
-> same registry visible to Android
-> optional Bot admin action changes the same registry
-> no app release required
```

---

## 17. Conflict Resolution Rule

اگر سند قدیمی با این سند تعارض داشت، برای موضوعات زیر این سند مقدم است:

- Login UX؛
- Bot Approval vs OIDC priority؛
- shared Bot/App account data؛
- PasarGuard as live runtime service source؛
- subscription config/node handling؛
- Free without visible Login؛
- Free Server Registry/Admin Console requirements.

Security invariants قوی‌تر در اسناد امنیتی همچنان معتبرند و نباید توسط این سند تضعیف شوند.
