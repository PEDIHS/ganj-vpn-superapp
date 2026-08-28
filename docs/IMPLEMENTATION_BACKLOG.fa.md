# Ganj VPN — Implementation Backlog & Acceptance Criteria

> این فایل Backlog اجرایی پروژه است. اولویت‌ها بر اساس Critical Path تا نسخه قابل فروش/انتشار تنظیم شده‌اند.

## Priority Definitions

- `P0`: Release blocker؛ بدون آن Beta/Production قابل قبول نیست.
- `P1`: برای محصول حرفه‌ای و عملیات پایدار بسیار مهم؛ در Beta یا بلافاصله بعد از P0.
- `P2`: Enhancement؛ نباید Critical Path را متوقف کند.
- `POST`: Post-MVP.

---

# EPIC P0-01 — Production Auth & Session

## Goal

کاربر بتواند Guest یا Telegram-linked session معتبر و device-bound بگیرد و کل Control API از همان session استفاده کند.

## Deliverables

- Auth session schema/migration؛
- guest registration؛
- device key registration؛
- GDP1 proof verification؛
- access JWT؛
- refresh rotation؛
- reuse detection؛
- logout/revoke؛
- JWKS؛
- Android secure session store؛
- token refresh interceptor/coordinator؛
- auth-required UI states.

## Acceptance Criteria

- Guest cannot register without valid device proof؛
- access token issuer/audience/device validated؛
- expired access refreshes with exactly one concurrent refresh operation؛
- refresh token reuse revokes token family؛
- revoked device/session cannot request connection profile؛
- tokens never appear in logs/UI/SavedState؛
- production refuses test auth adapter؛
- PostgreSQL integration green؛
- Android auth tests green.

## Security Tests

- forged JWT؛
- wrong audience؛
- expired/nbf؛
- stolen refresh replay؛
- GDP1 replay/body change؛
- key-version mismatch؛
- concurrent refresh race.

---

# EPIC P0-02 — Telegram Login & Account Linking

## Goal

کاربر بتواند به Telegram identity واقعی وصل شود و حساب/اشتراک ربات فعلی را داخل اپ ببیند.

## Deliverables

- Telegram OIDC start/exchange؛
- PKCE S256؛
- state one-time persistence؛
- verified app-link callback؛
- Bot Deep-Link fallback؛
- account broker؛
- account conflict/relink policy؛
- audit؛
- Android login UI؛
- cancellation/retry UX.

## Acceptance Criteria

- state replay rejected؛
- wrong verifier rejected؛
- invalid provider issuer/signature rejected؛
- no provider secret/client token logged؛
- no auth token in deep link؛
- linked identity resolves stable Ganj user؛
- two paying accounts are never silent-merged؛
- logout does not unintentionally destroy remote subscription؛
- re-link is audited.

---

# EPIC P0-03 — Legacy Bot Subscription Sync

## Goal

مشتری قدیمی بعد از Link، سرویس‌های موجود خود را بدون manual config مشاهده و استفاده کند.

## Deliverables

- legacy customer mapping؛
- service mapping؛
- read adapter؛
- entitlement projection؛
- expiry/status reconciliation؛
- plan mapping؛
- renewal sync؛
- conflict queue؛
- admin reconciliation view؛
- periodic reconciliation worker/job.

## Acceptance Criteria

- active legacy service appears in My Services؛
- expired service does not connect؛
- sync is idempotent؛
- duplicate service creation impossible؛
- legacy outage does not corrupt current entitlement؛
- mismatch is observable/audited؛
- cutover can be rolled back by switching read owner.

---

# EPIC P0-04 — Staging Production-like Deployment

## Goal

Control API روی محیط staging واقعی با topology نزدیک Production اجرا شود.

## Deliverables

- domain؛
- TLS؛
- PostgreSQL؛
- migrations؛
- immutable container images؛
- secret injection؛
- Nginx/edge؛
- health/readiness؛
- log collection؛
- metrics؛
- backup؛
- restore verification؛
- deploy/rollback scripts.

## Acceptance Criteria

- no plaintext secret in repo/image؛
- test adapter unavailable in production mode؛
- migration succeeds from clean DB؛
- repeated deploy does not destroy state؛
- rollback documented/tested؛
- health checks useful؛
- database backup restores into isolated environment؛
- rate limits configured؛
- TLS scanner acceptable.

---

# EPIC P0-05 — Server Secret Resolver & Real Server Catalog

## Goal

Backend بتواند از secret reference امن، credential واقعی سرور را resolve کرده و فقط profile encrypted صادر کند.

## Deliverables

- KMS/Vault/secret service؛
- service identity؛
- secret reference mapping؛
- server CRUD/admin؛
- health state؛
- capacity؛
- protocol schema validation؛
- rotation؛
- incident disable.

## Acceptance Criteria

- DB stores `secret_ref`, not raw credential؛
- Android/API JSON never exposes raw secret؛
- invalid Reality/TLS/UUID config rejected؛
- `allow_insecure=true` rejected in production؛
- secret resolver outage fails closed؛
- secret access audited；
- rotation does not require app update.

---

# EPIC P0-06 — Real VPN End-to-End

## Goal

کاربر واقعی روی دستگاه واقعی از entitlement تا encrypted profile تا Xray tunnel متصل شود.

## Minimum Protocol Evidence

- VLESS؛
- Trojan؛
- then VMess/Shadowsocks parity.

## Scenarios

- Wi-Fi؛
- cellular؛
- Wi-Fi→cellular؛
- cellular→Wi-Fi؛
- network loss/restore؛
- app background؛
- screen lock؛
- process restart؛
- Doze؛
- IPv4؛
- IPv6/dual stack.

## Acceptance Criteria

- no manual config used؛
- profile cannot replay؛
- profile cannot be used by wrong device؛
- Connected UI matches real tunnel state؛
- disconnect closes tunnel؛
- DNS/IP leak tests meet policy؛
- reconnect ≥ target؛
- crash-free lab pass؛
- no secret appears in logcat/crash report.

---

# EPIC P0-07 — VPN Resilience & Kill Switch

## Deliverables

- secure recovery store؛
- process restart recovery؛
- reconnect backoff؛
- network epoch/race handling؛
- Always-on compatibility؛
- Android lockdown behavior documentation؛
- kill-switch UX/policy؛
- prepare timeout؛
- stale profile cleanup.

## Acceptance Criteria

- no bypass window outside declared policy؛
- no reconnect loop storm؛
- stale/expired recovery fails closed؛
- process kill does not leak plaintext profile؛
- logout/revoke clears recovery data؛
- battery acceptable.

---

# EPIC P0-08 — Google Play Billing E2E

## Deliverables

- Play Console app/product؛
- base plans/offers؛
- service account؛
- Android Publisher API؛
- RTDN؛
- license tester؛
- purchase/restore؛
- acknowledgement؛
- cancel/refund/revoke؛
- plan change؛
- reconciliation.

## Acceptance Criteria

- pending never grants؛
- valid purchase grants exactly once؛
- duplicate verify is idempotent؛
- cancellation access lasts only per authoritative expiry؛
- refund/revoke removes access per policy؛
- restore reconstructs entitlement؛
- RTDN alone cannot grant؛
- failed acknowledgement retries safely؛
- purchase token raw value not persisted beyond required secure handling.

---

# EPIC P0-09 — Subscription Lifecycle & Multi-device

## Deliverables

- device slots؛
- concurrent policy؛
- revoke؛
- expiry；
- grace؛
- suspended؛
- upgrade/downgrade؛
- plan limit changes؛
- renewal sync.

## Acceptance Criteria

- slot reservation atomic؛
- over-limit request rejected؛
- revoked device loses profile access؛
- plan change does not duplicate entitlement؛
- expiry enforced server-side؛
- UI state reconciles after restart/offline recovery.

---

# EPIC P0-10 — Final Localization & Product UI

## Deliverables

- `fa` resources؛
- `en` resources؛
- RTL؛
- locale-safe formatting؛
- final Design System؛
- icons؛
- motion؛
- Home؛
- Servers؛
- Connect؛
- Store؛
- Account؛
- error/empty/offline؛
- accessibility.

## Acceptance Criteria

- no user-facing hardcoded English in production screens؛
- RTL screenshots pass؛
- large font no critical clipping؛
- touch targets accessible؛
- TalkBack labels meaningful؛
- contrast acceptable؛
- dark/light complete؛
- loading/failed states actionable؛
- VPN/payment state never visually lies.

---

# EPIC P0-11 — Minimum Admin Operations Console

## Minimum Pages for Beta

- auth/MFA؛
- dashboard؛
- users؛
- services؛
- devices/sessions؛
- plans؛
- servers؛
- orders؛
- support؛
- bugs؛
- audit؛
- remote config/flags.

## Acceptance Criteria

- RBAC enforced server-side؛
- CSRF/origin controls؛
- privileged actions audited؛
- no secret rendering؛
- session expiry/re-auth؛
- destructive actions confirmation/permissions؛
- no shared super-admin credential.

---

# EPIC P0-12 — Release Compliance

## Deliverables

- Privacy Policy؛
- Data Safety؛
- account deletion flow؛
- VpnService declaration؛
- subscription terms؛
- support contact؛
- screenshots/video؛
- reviewer account/server؛
- current target API؛
- permission review.

## Acceptance Criteria

- forms match actual binary/SDK behavior؛
- external payment exposure compliant؛
- no undeclared data collection؛
- account deletion operational؛
- Play pre-launch blockers addressed.

---

# EPIC P0-13 — Security / Penetration / Supply Chain

## Required

- SAST؛
- CodeQL؛
- dependency review؛
- SBOM؛
- provenance؛
- secret scan؛
- mobile/API penetration test؛
- admin auth/RBAC test؛
- token/profile replay testing؛
- remediation.

## Exit Gate

No unresolved Critical/High security finding without documented accepted-risk approval, owner and expiry.

---

# EPIC P0-14 — Beta & Production Release

## Sequence

- signed candidate؛
- Internal؛
- Closed Beta؛
- metrics review؛
- remediation؛
- production 5%؛
- 20%؛
- 50%؛
- 100%.

## Required Metrics

- crash-free؛
- ANR؛
- connect P50/P95؛
- reconnect؛
- billing duplicate=0؛
- auth failure anomaly؛
- server failure rate؛
- support incidents؛
- battery.

---

# P1 Backlog

## Free Plan

- free catalog؛
- quota؛
- speed band؛
- abuse controls؛
- upgrade funnel؛
- optional ads.

## Direct Wallet/Payments

- ledger؛
- top-up؛
- gateway؛
- Telegram checkout؛
- smart banking adapter؛
- refunds/history.

## FCM & Notifications

- transactional؛
- maintenance؛
- security؛
- marketing preferences.

## Marketing

- banner؛
- popup؛
- campaign targeting؛
- lifecycle campaigns.

## Referral

- code؛
- attribution؛
- fraud prevention؛
- ledger reward.

## Server UX

- favorites؛
- search؛
- sort/filter؛
- capacity/latency display.

## Speed Test

- controlled endpoints؛
- privacy-safe measurement؛
- no arbitrary destination behavior.

---

# P2 / Post-MVP

- WireGuard؛
- MultiHop؛
- rotating IP؛
- advanced anti-censorship؛
- per-app routing؛
- iOS؛
- desktop؛
- team/org accounts؛
- advanced experimentation.

---

# Backlog Governance

هر Epic هنگام شروع باید:

- owner؛
- branch؛
- PR؛
- acceptance tests؛
- dependencies؛
- migration impact؛
- privacy/security impact؛
- rollout strategy

داشته باشد.

هر Epic هنگام پایان باید از `PLANNED/IN PROGRESS` به `DONE` فقط پس از merge + evidence تغییر کند.
