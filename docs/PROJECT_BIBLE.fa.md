# Ganj VPN — Project Bible & Engineering Specification

> **وضعیت:** Canonical Engineering Context  
> **نوع سند:** Product Bible + Engineering Specification + Delivery Contract  
> **مخزن:** `PEDIHS/ganj-vpn-superapp`  
> **Application ID هدف:** `com.ganj.vpn`  
> **آخرین Snapshot فنی مبنا:** Phase 6C روی `be80335d90b04a9043edda585b79927c6b211d1b` + Phase 7 work-in-progress branches  
> **قاعده اعتبار:** کد و تست روی `main` از این سند معتبرتر است؛ این سند Context، Scope، Target State و Delivery Contract را نگه می‌دارد.

---

## 0. هدف این سند

این فایل مرجع اصلی برای هر انسان، AI Agent، Architect، Android Developer، Backend Developer، Security Engineer، DevOps Engineer، QA و Product Owner است که وارد پروژه Ganj VPN می‌شود.

هدف آن این است که بدون نیاز به خواندن تمام تاریخچه گفتگوها بتوان فهمید:

- محصول دقیقاً چیست و چه چیزی نیست؛
- چه تصمیم‌هایی غیرقابل مذاکره‌اند؛
- چه قابلیت‌هایی انجام شده‌اند؛
- چه قابلیت‌هایی در Branchهای در حال توسعه‌اند؛
- چه قابلیت‌هایی هنوز شروع نشده یا ناقص‌اند؛
- هر قابلیت با چه معماری و قرارداد امنیتی باید ساخته شود؛
- چه چیزی برای MVP، Beta و Production الزامی است؛
- ترتیب منطقی و بحرانی ادامه توسعه چیست؛
- Definition of Done هر Workstream چیست؛
- چه تست، Evidence، Migration، Runbook و Rollback برای Done شدن لازم است؛
- چه مواردی Post-MVP هستند و نباید قبل از بسته‌شدن Critical Path باعث انحراف شوند.

### 0.1 سلسله‌مراتب Source of Truth

در صورت تعارض اطلاعات، ترتیب اعتبار به‌شکل زیر است:

1. Source code، migrations و workflowهای روی `main`؛
2. تست‌های خودکار و CI evidence؛
3. `api/openapi.yaml` و قراردادهای ماشینی؛
4. اسناد Security/Architecture/Release تخصصی؛
5. این Project Bible؛
6. Branchهای در حال توسعه؛
7. Issue/PR description و یادداشت‌های تاریخی.

### 0.2 قانون Update این سند

هر PR بزرگ که یکی از موارد زیر را تغییر دهد باید Project Bible یا Appendix مرتبط را به‌روزرسانی کند:

- Product scope؛
- Auth/session model؛
- VPN profile schema یا crypto؛
- Billing/entitlement semantics؛
- Telegram linking؛
- Admin RBAC؛
- Privacy/Data Safety؛
- Deployment architecture؛
- Release gate؛
- Critical dependency؛
- Phase status یا production blocker.

---

# 1. هویت محصول

## 1.1 تعریف

**Ganj VPN** یک VPN Super App اشتراکی و مدیریت‌شده است که تجربه کاربر، فروش، اشتراک، اتصال VPN، مدیریت دستگاه، پشتیبانی، مارکتینگ و عملیات Enterprise را در یک محصول یکپارچه ارائه می‌دهد.

Ganj VPN یک «VPN client عمومی» یا «V2Ray config importer» نیست. کاربر باید فقط از سرویس‌ها و Entitlementهایی که Backend برای او صادر کرده استفاده کند.

هدف کیفی محصول این است که در سطح تجربه و اطمینان‌پذیری محصولات حرفه‌ای مانند NordVPN، Proton VPN، Surfshark و ExpressVPN قرار گیرد، اما Design، Brand، Backend، Commerce و Product Logic اختصاصی خودش را داشته باشد.

## 1.2 ستون‌های محصول

محصول نهایی بر این ستون‌ها بنا می‌شود:

### A. Android VPN Client

- Native Kotlin؛
- Jetpack Compose؛
- RTL/Persian first-class؛
- Dark/Light؛
- Home / Servers / Connect / Store / Account؛
- Device-bound identity؛
- Google Play Billing؛
- Free/Premium product states؛
- Diagnostics/Support؛
- Remote Config/Feature Flags؛
- privacy-safe analytics.

### B. VPN Runtime

- Android `VpnService`؛
- Xray Core پشت `VpnEngine` abstraction؛
- VLESS؛
- VMess؛
- Trojan؛
- Shadowsocks؛
- Smart Connect؛
- reconnect؛
- network switching؛
- DNS-in-tunnel؛
- Kill Switch policy؛
- device-bound short-lived profiles.

### C. Control Platform

- Identity؛
- Users/Devices/Sessions؛
- Catalog/Plans؛
- Orders؛
- Billing؛
- Services/Entitlements؛
- Servers؛
- Profile Broker؛
- Telegram linking؛
- Remote Config؛
- Feature Flags؛
- Analytics؛
- Bug Reports؛
- Diagnostics؛
- Support؛
- Audit؛
- Admin APIs؛
- Reconciliation.

### D. Telegram Ecosystem

- Telegram Login؛
- Bot Deep Link fallback؛
- linking با اکانت فعلی؛
- شناسایی سرویس‌های خریداری‌شده قبلی؛
- sync اشتراک؛
- sync تمدید/لغو/خرید؛
- legacy adapter؛
- migration تدریجی بدون شکستن ربات فعلی.

### E. Commerce

- Google Play Billing برای Play flavor؛
- Server-side verification؛
- RTDN؛
- restore/pending/refund/cancel/renewal؛
- Direct flavor؛
- Wallet؛
- Telegram checkout؛
- Gateway payment؛
- unified entitlement ledger.

### F. Enterprise & Operations

- Admin Console؛
- Bug Tracking؛
- Support Ticket؛
- Diagnostics؛
- Remote Config؛
- Feature Flag؛
- consent-governed Analytics؛
- immutable Audit؛
- Monitoring؛
- Alerting؛
- Backup/Restore؛
- Disaster Recovery؛
- Release automation؛
- SBOM/Provenance؛
- Security operations.

---

# 2. اصول غیرقابل مذاکره Product

## 2.1 ممنوعیت Manual Config

در هیچ flavor تولیدی کاربر نباید بتواند:

- URI از نوع VLESS/VMess/Trojan/Shadowsocks وارد کند؛
- QR کانفیگ اسکن کند؛
- Subscription URL بدهد؛
- Clipboard config import کند؛
- فایل config import کند؛
- Xray JSON خام وارد کند؛
- credential سرور را مشاهده کند؛
- config را export/share کند؛
- profile را از منبع arbitrary بارگذاری کند.

تنها زنجیره مجاز:

```text
Authenticated Account
    ↓
Owned Entitlement / Service
    ↓
Eligible Server selected by policy
    ↓
Device Proof + Entitlement Validation
    ↓
Short-lived Device-bound Encrypted Profile
    ↓
One-time Runtime Handoff
    ↓
VpnService / Xray
```

این invariant باید هم در Product UI، هم API، هم CI product guard و هم code review enforce شود.

## 2.2 Server-side Authority

موارد زیر هرگز نباید فقط از Client پذیرفته شوند:

- active subscription claim؛
- purchase success؛
- user identity؛
- device ownership؛
- server eligibility؛
- service expiry؛
- device slots؛
- refund state؛
- entitlement tier؛
- profile content.

## 2.3 Fail-Closed

اگر یکی از زنجیره‌های اعتماد Production ناقص باشد، سیستم باید fail-closed باشد نه اینکه mock/test/default ناامن را فعال کند.

نمونه‌ها:

- نبود Auth adapter واقعی → startup یا auth fail؛
- نبود Secret resolver → profile issuance fail؛
- نبود Play service account → verification fail؛
- invalid device proof → profile صادر نشود؛
- unknown subscription state → access grant نشود؛
- invalid runtime-config signature → config اعمال نشود.

## 2.4 Privacy by Design

هرگز نباید collect/log شود:

- browsing history؛
- destination URL/domain history؛
- DNS query history؛
- packet content؛
- VPN credential؛
- config URI؛
- raw access/refresh token؛
- raw purchase token در log/analytics؛
- precise location؛
- contacts/SMS/call logs؛
- installed app list برای marketing.

---

# 3. Product Channels و Flavor Strategy

## 3.1 Google Play Flavor

Play build باید:

- از Google Play Billing برای digital subscription استفاده کند؛
- purchase proof را به Backend بفرستد؛
- entitlement فقط پس از server verification فعال شود؛
- external payment surface را فقط در صورت مجاز بودن policy/region نمایش دهد؛
- `PENDING` را entitlement فعال تلقی نکند؛
- refund/cancel/expiry را reconcile کند؛
- Data Safety و VPN Service declaration واقعی و مطابق binary داشته باشد.

## 3.2 Direct APK Flavor

Direct build می‌تواند کانال‌های زیر را داشته باشد:

- Wallet؛
- Telegram purchase؛
- Gateway؛
- regional payment؛
- campaign-specific checkout؛
- referral credit.

با این حال entitlement backend باید unified باقی بماند و purchase channel فقط source of payment باشد، نه source of truth برای connection authorization.

## 3.3 Flavor Isolation

نباید با `if (play)`های پراکنده policy بسازیم. Feature availability باید از build flavor + policy + signed remote config کنترل شود و CI باید accidental exposure کانال Direct در Play build را متوقف کند.

---

# 4. User Journeys

## 4.1 Guest

```text
Install → Device Identity → Guest Session → Free/Store → Select Service → Connect
```

Guest session نیز device-bound است و نباید static anonymous credential داشته باشد.

## 4.2 Telegram-linked

```text
App → Telegram OIDC+PKCE / Bot Fallback → Account Mapping
→ Legacy Subscription Reconciliation → My Services → Connect
```

## 4.3 Premium

- مشاهده سرویس و انقضا؛
- Smart Connect یا انتخاب سرور؛
- تمدید/upgrade/downgrade؛
- restore purchase؛
- مدیریت دستگاه؛
- support/diagnostics.

## 4.4 Free

- server pool محدود؛
- quota/speed policy server-side؛
- anti-farming؛
- upgrade funnel؛
- optional compliant UI ads.

---

# 5. UX / UI Target State

UI فعلی `main` Functional UI است، نه Final Product Design.

Target:

- فارسی و انگلیسی resource-based؛
- RTL واقعی؛
- Light/Dark؛
- Ganj-specific Design System؛
- accessibility/TalkBack؛
- large font؛
- responsive؛
- polished motion؛
- states: loading/empty/error/offline/maintenance/forced update؛
- final Home/Servers/Connect/Store/Account.

Phase 7 UI branch `GanjTheme` را با semantic colors، Light/Dark، typography و RTL شروع کرده است؛ این فقط foundation است.

## 5.1 Connect State Machine

```text
Disconnected → Preparing → Connecting → Connected
                         ↘ Failed
Connected → Reconnecting → Connected/Failed
```

نمایش مجاز: server/country label، latency summary، duration، service status.  
نمایش ممنوع: raw profile/credential/Xray JSON.

---

# 6. Android Security & Identity

## 6.1 Keys

- P-256 signing key: non-exportable Android Keystore؛
- X25519 encryption key: private material AES-GCM wrapped by Keystore key؛
- separate/versioned identities.

## 6.2 GDP1 Proof

```text
GANJ-DEVICE-PROOF-V1
METHOD
PATH_AND_QUERY
BODY_SHA256
TIMESTAMP
NONCE
KEY_VERSION
```

- ES256؛
- P1363؛
- canonical JSON body؛
- bounded time window؛
- server-side one-time nonce؛
- no replay.

## 6.3 GVP1 Profile

- X25519؛
- HKDF-SHA256؛
- AES-256-GCM؛
- authenticated metadata؛
- short TTL؛
- one device؛
- one-time grant؛
- no plaintext persistence.

---

# 7. VPN Runtime

`VpnService` + Xray پشت boundary مستقل.

Requirements:

- foreground service؛
- real TUN؛
- real disconnect؛
- process restart recovery امن؛
- ConnectivityManager callback؛
- Wi-Fi/cellular switch؛
- reconnect backoff؛
- IPv4/IPv6 routes/DNS؛
- secure recovery store؛
- cleanup on revoke/logout/expiry؛
- Kill Switch/leak validation.

Phase 7 resilience branch recovery store، reconnect و network callbacks را توسعه داده است.

MVP protocols: VLESS, VMess, Trojan, Shadowsocks. WireGuard post-MVP.

---

# 8. Smart Connect

Backend first filters eligibility; client probes bounded candidates.

Reference score:

```text
0.33 latency + 0.18 packet_success + 0.22 capacity
+ 0.15 throughput_hint + 0.07 region_affinity + 0.05 stability
```

Hysteresis/cooldown برای جلوگیری از flapping.

No location permission; coarse region server-side.

---

# 9. Control API

Modular Monolith + PostgreSQL.

Domains: identity, users/devices/sessions, catalog, billing, entitlements, servers, profile broker, Telegram, enterprise, telemetry, support, audit, legacy.

Production invariants:

- ownership query؛
- idempotency؛
- transactional financial/entitlement writes؛
- unique replay/token constraints؛
- test adapter forbidden in production؛
- provider callbacks are signals؛
- raw server credentials never returned.

Existing major API surface شامل plans/services/servers/orders/play verify/RTDN/Telegram exchange/profile/runtime config/consent/analytics/bugs/diagnostics/tickets/admin config/flags/audit است.

---

# 10. Authentication & Sessions

Phase 7 auth branch در حال تکمیل vertical production auth است.

Target:

- Guest registration با device proof؛
- Telegram OIDC PKCE؛
- Bot fallback؛
- Ed25519-signed short-lived access JWT؛
- opaque refresh token؛
- hash-at-rest؛
- rotation؛
- family/reuse detection؛
- revoke؛
- JWKS؛
- relink conflict policy.

Current branch defaults include 15m access, 30d refresh, 90s device proof skew؛ configurable within bounded limits.

Telegram linking نباید دو حساب پولی را silent merge کند.

---

# 11. Legacy Telegram Sync

Android مستقیم به PHP/MariaDB legacy متصل نمی‌شود.

Adapter/Broker maps:

- Ganj user؛
- Telegram id؛
- legacy customer؛
- legacy service؛
- new service/entitlement؛
- order/payment.

Reconcile: expiry, status, tier, service, renewal, transaction.

Cutover domain-by-domain و rollback با تغییر read/write ownership.

---

# 12. Subscription / Multi-device

Connection authorization requires active entitlement + trusted device + slot + eligible server + valid one-time profile.

Need:

- active/grace/suspended/expired/revoked states؛
- atomic device slot؛
- revoke device؛
- concurrent connection policy؛
- upgrade/downgrade semantics؛
- channel-independent entitlement.

---

# 13. Google Play Billing

Client: Billing Library, ProductDetails, offers, pending, restore, reconnect, secure proof vault.

Backend authoritative: Android Publisher API.

Phase 7 classifier semantics:

- ACTIVE/GRACE/CANCELED-until-expiry → entitled if unexpired؛
- PENDING/PENDING_PURCHASE_CANCELED → no new grant, preserve existing؛
- PAUSED/ON_HOLD → disabled policy؛
- EXPIRED → expired.

Acknowledgement occurs after durable grant; retry-safe.

RTDN: verify push identity → dedupe → query Publisher API → reconcile.

E2E matrix must cover purchase, pending, renewal, cancel, grace, hold, pause, refund, revoke, plan change, resubscribe, linked token, restore, duplicate callback, network failure.

---

# 14. Direct Commerce

Remaining major scope:

- Wallet؛
- double-entry ledger؛
- Telegram checkout؛
- gateway؛
- top-up؛
- transaction history؛
- refund/adjustment؛
- referral credit؛
- reconciliation.

Smart banking can be an isolated Direct payment adapter; it must not enter VPN data plane.

---

# 15. Enterprise

## Bug Tracking
New → Investigating → Assigned → Fixing → Testing → Released → Closed.

Context allowlist + attachment security + retention.

## Crash
Scrubbed stack, grouping, retention, no secrets.

## Analytics
Consent receipt, event/property allowlist, idempotent batches, aggregate funnels, no traffic surveillance.

## Remote Config
Ed25519 signed, ETag, TTL, immutable releases, dual-control for sensitive namespaces.

## Feature Flags
Immutable versions, deterministic cohorts, owner, expiry, kill switch, guardrail.

## Support/Diagnostics
Owned tickets, privacy-safe diagnostic tests only with explicit consent.

## Audit
Append-only admin/security/release/billing evidence.

---

# 16. Admin Console

Phase 7 foundation begins `web/admin` with RBAC/security baseline.

Security:

- `__Host-` secure HttpOnly session cookie؛
- CSRF digest + constant time؛
- strict Origin/Referer؛
- CSP/HSTS/XFO/no-store؛
- least privilege.

Target pages:

Dashboard, Users, Services, Devices/Sessions, Plans, Servers, Orders/Billing, Support, Bugs, Analytics, Campaigns, Remote Config, Feature Flags, Audit, Releases.

Target scopes should be action-based (`users.read`, `billing.adjust`, `remote_config.publish`, etc.).

---

# 17. Observability / SRE

Golden signals:

- API rate/error/latency/saturation؛
- VPN connect success/latency/reconnect/server health؛
- billing verification/ack/RTDN/reconciliation؛
- auth login/refresh/reuse/device proof؛
- crash/ANR.

Every alert requires severity, owner, threshold/window, runbook, dedupe, recovery condition.

Critical telemetry missing = unknown/failing, not healthy.

---

# 18. Data & Migration

PostgreSQL with ownership predicates, parameterized queries, transactions, constraints, row/advisory locks where needed.

Core entities: users, devices, sessions, plans, services, servers, orders, purchase digests, grants, webhook inbox, auth nonces, consents, enterprise config, telemetry, bugs, diagnostics, tickets, audit.

Migrations immutable/checksummed; expand→migrate→contract; production test seed forbidden.

---

# 19. Secrets

Never in Git: DB creds, bot/Telegram secrets, Play SA, server credentials, Reality keys, runtime signing private key, cohort secret, Android signing key, gateways.

Preferred path: Secret Manager/Vault/KMS → mounted/injected secret → adapter → minimum memory lifetime.

---

# 20. Deployment

Phase 7 staging branch adds hardened Compose with Postgres, migration job, API, Nginx, secret mounts, internal networks, read-only/cap-drop/no-new-privileges, health checks, backup/restore scripts.

Still required external production wiring: host/cloud, DNS/TLS, secret manager, WAF/rate limits, observability, off-host backups, deploy/rollback, HA strategy, DR evidence.

---

# 21. Security Threat Model

Primary threats: MITM, token theft/reuse, deep-link hijack, device proof replay, config extraction, API abuse, webhook spoof, wallet/payment race, admin takeover, provider compromise, supply chain, malicious release, telemetry privacy leak.

Required tests include proxy/MITM, PKCE/state replay, refresh reuse, GDP1 replay/body mutation, GVP1 tamper, rooted forensic/log scan, webhook spoof/replay, concurrent financial writes, admin CSRF/RBAC escalation, SBOM/dependency/secret scans.

---

# 22. Delivery History

## Phase 0 — DONE
Product/architecture/API/DB/security/enterprise/release baseline.

## Phase 1 — DONE
Native Android foundation, Compose shell, VpnEngine, Smart Connect baseline, CI/APK.

## Subscription/Enterprise Foundation — DONE
Models, Store/My Services, entitlement-only boundaries, enterprise schemas/guards.

## Phase 2 — DONE
Control API, catalog/services/orders, Play verification boundary, encrypted profile, billing state machine.

## Phase 3 — DONE
PostgreSQL, JWKS auth boundary, Telegram OIDC boundary, Play Publisher/RTDN, Billing adapter, observability privacy.

## Phase 4 — DONE
Real VpnService/Xray, typed protocols, enterprise control plane, signed release foundations/SBOM/provenance/alerts.

## Phase 5 — DONE
One-time profile→tunnel orchestration, VPN permission, foreground service, real disconnect, runtime-confirmed Connected state.

## Phase 6A — DONE
P-256/X25519 device identity, Keystore protection, GDP1, GVP1.

## Phase 6B — DONE
Backend GDP1 ES256 verification + PostgreSQL replay protection.

## Phase 6C — PIPELINE DONE
Approval-gated Play Internal/Alpha publication workflow. Actual signed/publication execution remains external release work.

---

# 23. Phase 7 — IN PROGRESS

### Auth/session `phase-7/auth-session-vertical-v2`
Guest/device auth, Ed25519 JWT, refresh rotation/reuse, revoke, JWKS, PKCE/Telegram session work. Needs Android/provider E2E, CI/contracts/migration review.

### Billing `phase-7/play-billing-e2e-v2`
Lifecycle classification/testing. Needs real Play license/RTDN/refund/plan-change/restore evidence.

### VPN resilience `phase-7/android-vpn-resilience-v2`
Encrypted recovery, reconnect backoff, network callback/switching/process restart. Needs real-device Doze/leak/IPv6/lockdown/battery validation.

### UI/accessibility `phase-7/android-ui-accessibility-v2`
Theme, Light/Dark, RTL, typography/accessibility baseline. Final localization/brand/motion/screens remain substantial.

### Admin `phase-7/admin-console-foundation-v2`
Security/RBAC/config foundation. Operational pages/auth/MFA/API integration remain.

### Staging Ops `phase-7/staging-ops-v2`
Hardened production topology/scripts/adapters. Real infra/secrets/TLS/monitoring/off-host backup/E2E remain.

---

# 24. Critical Path to Sellable MVP

```text
Auth/Session
→ Telegram Linking + Legacy Sync
→ Staging Control API
→ Real Secret Resolver + VPN Servers
→ Real VPN E2E/Resilience/Leaks
→ Billing E2E + Subscription lifecycle
→ Final Localization/UI/Accessibility
→ Minimum Admin Operations
→ Internal/Closed Beta
→ PenTest/Compliance/Go-No-Go
→ Production Rollout
```

Referral/advanced marketing/Speed Test/WireGuard must not derail this P0 chain.

---

# 25. Remaining Priority Backlog

P0:

- auth/session merge + Android integration؛
- Telegram real linking/sync؛
- production staging؛
- real server secret resolver؛
- real VPN server E2E؛
- DNS/leak/reconnect/device matrix؛
- Play E2E؛
- subscription/multi-device lifecycle؛
- final Persian/English UI؛
- minimum Admin؛
- Privacy/Data Safety/VPN declaration؛
- signed beta؛
- penetration test؛
- monitoring/backup/rollback evidence.

P1:

- Free plan final policy؛
- direct wallet/payment؛
- FCM/preferences؛
- marketing/campaigns؛
- favorites/search/filter؛
- Speed Test؛
- referral؛
- richer Admin/analytics.

P2/Post-MVP:

- WireGuard؛
- MultiHop؛
- rotating IP؛
- advanced anti-censorship؛
- per-app routing؛
- iOS؛
- desktop؛
- team accounts.

---

# 26. Testing Matrix

Backend: unit/integration/Postgres/contracts/auth/permission/idempotency/concurrency/provider/webhook/crypto.

Android: unit/lint/instrumentation/VPN smoke/process death/network switch/billing/accessibility/benchmark/battery/locale/device matrix.

Security: SAST/dependency/SBOM/secret/CodeQL/API abuse/replay/tamper/CSRF/RBAC/penetration.

Release: signed artifacts/checksum/provenance/mapping/pre-launch/closed-beta/rollback.

Device SDK minimum matrix: Android 8, 10, 12, 14, 15, 16; Pixel, Samsung, Xiaomi/POCO, low-memory + flagship; Wi-Fi/cellular/unstable networks.

---

# 27. Reliability Targets

| Metric | MVP target |
|---|---:|
| Crash-free users | ≥99.7% |
| ANR | <0.25% |
| P50 connect | <1.8s |
| P95 connect | <4.0s |
| reconnect success | ≥95% |
| API P95 read | <350ms excluding providers |
| idle connected battery | <1%/h target device |
| duplicate fulfillment | 0 |
| secret/config leak | 0 |
| manual config production path | 0 |

Only real Beta evidence can validate these targets.

---

# 28. Google Play & Release Gates

Before submission: current target API, VPN declaration, Privacy Policy, Data Safety, account deletion, subscription disclosures, listing/screenshots/demo, reviewer/test server, Play server verification, signed AAB, pre-launch clean enough, crash/ANR gates, permission/export review.

Rollout:

```text
CI → Staging → Signed Candidate → Internal → Closed Beta → 5% → 20% → 50% → 100%
```

Stop rollout on auth regression, entitlement duplication, DNS/traffic leak, crash/ANR spike, payment duplication, severe battery issue, security incident or server failure spike.

---

# 29. Definition of Done

Feature Done = code + tests + error states + auth/ownership + privacy + telemetry policy + migration if needed + rollback impact + docs + CI green + no unresolved Critical/High + acceptance pass.

Production Done adds real provider/secret/staging E2E/monitoring/alert/runbook/backup impact/release evidence/owner.

---

# 30. Current Release Blockers

1. Phase 7 Auth/Session completion/integration؛
2. Telegram real login/link/sync؛
3. legacy service reconciliation؛
4. staging deployment؛
5. real secret resolver؛
6. real server E2E؛
7. resilience/leak/device evidence؛
8. Play E2E؛
9. final localization/UI/accessibility؛
10. minimum Admin؛
11. compliance docs/declarations؛
12. signed Internal/Closed Beta؛
13. pen test/remediation؛
14. monitoring/backup/rollback evidence.

---

# 31. AI / Developer Handoff Rules

Before coding: inspect `main` + relevant branches, avoid duplicate feature, preserve product/security invariants.

Always:

- no manual config؛
- no production mocks؛
- no secrets؛
- fail closed؛
- DB changes via migration؛
- server-side ownership؛
- UI no secrets؛
- provider callbacks not authoritative؛
- privacy forbidden fields not logged؛
- work on branch/PR؛
- PR must state Outcome, Security, Verification, Remaining Boundaries؛
- check CI before merge؛
- update docs/status for major phases؛
- never claim production readiness without external evidence.

---

# 32. Maintenance Contract

Every large merge must classify each capability as exactly one of:

- **Merged/Implemented** — on `main`, tests/CI evidence;
- **In Progress** — branch/PR only؛
- **Planned** — target without implementation evidence.

Project completion criterion:

> A real user can install Ganj VPN, obtain a device-bound identity/session, link Telegram or use an allowed account mode, see an already-owned or newly purchased entitlement, obtain a server-authorized encrypted profile without manual config, connect reliably on real devices/networks, survive network transitions, have purchase/renewal/refund states reconciled correctly, and be supported/operated through monitored, secure, recoverable production systems with release rollback and privacy/compliance evidence.
