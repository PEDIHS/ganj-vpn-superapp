# Ganj VPN — Delivery Ledger

> مرجع تاریخی اجرای فازها، PRها، Commitها و وضعیت Trackها.

## وضعیت‌های مجاز

- `DONE`: Merge شده روی `main`.
- `IN PROGRESS`: روی Branch/PR است و هنوز جزو baseline پایدار نیست.
- `PLANNED`: فقط در Roadmap/Spec تعریف شده.
- `EXTERNAL GATE`: کد آماده است اما نیازمند حساب/Secret/Infra/Test واقعی خارج از Git است.

---

## Phase 0 — Product/Architecture Baseline — DONE

Baseline اولیه شامل Product scope، competitor study، architecture، design direction، database schema، OpenAPI، security threat model، Google Play compliance، enterprise platform، observability و roadmap شد.

### خروجی کلیدی

- Android native Kotlin/Compose decision؛
- Node.js modular monolith + PostgreSQL؛
- Telegram OIDC + PKCE؛
- no manual config invariant؛
- entitlement-only VPN؛
- Google Play vs Direct flavor separation؛
- privacy model؛
- release/security runbooks.

---

## Phase 1 — Android Foundation — DONE

PR #1 — `feat(android): establish Phase 1 foundation`

### Delivered

- package/application foundation؛
- Compose shell؛
- five-tab navigation؛
- `VpnEngine` boundary؛
- Smart Connect ranker baseline؛
- secure manifest؛
- Android CI؛
- debug APK artifact؛
- OpenAPI lint fixes.

### Security

- no cleartext؛
- no committed bot token/keystore/config؛
- VPN service non-exported؛
- no manual config import.

---

## Phase 1–4 Subscription/Enterprise Foundation — DONE

PR #2 — `feat: add subscription and enterprise platform foundations`

### Delivered

- subscription models؛
- Store/My Services state؛
- entitlement-only connection policy؛
- ownership/expiry/device-limit checks؛
- enterprise OpenAPI/database contracts؛
- reliability/support/privacy/monitoring/release/SOC/anti-abuse foundations؛
- repository guard؛
- SBOM/security workflows.

---

## Phase 2 — Control API & Billing Vertical Slice — DONE

PR #7 — `feat: add verified checkout and control API vertical slice`

### Backend

- catalog؛
- services؛
- server catalog؛
- order create/status؛
- server-side Play verification boundary؛
- entitlement creation/renewal؛
- encrypted connection-profile boundary؛
- production fail-closed adapters.

### Android

- HTTPS-only Control API client؛
- strict response validation؛
- profile envelope vault؛
- typed errors؛
- billing state machine؛
- no raw proof/profile in UI.

---

## Phase 3 — Production Commerce/Persistence/Observability Wiring — DONE

PR #8 — `Phase 3: production commerce, persistence and observability wiring`

### Delivered

- Google Play Billing 9.1.0؛
- purchase proof vault؛
- pending/restore/reconnect/ack states؛
- PostgreSQL persistence؛
- JWKS auth boundary؛
- Telegram OIDC PKCE exchange boundary؛
- Google Play subscriptions v2 adapter؛
- RTDN؛
- privacy-safe bug/analytics/logging controls؛
- Docker/PostgreSQL integration gate.

### Remaining boundary at that point

- production session issuer؛
- real provider secrets/accounts؛
- real Xray runtime integration.

---

## Phase 4 — Android Xray Runtime & Enterprise Control Plane — DONE

PR #9 — `Phase 4: subscription-only Android Xray runtime and enterprise control plane`

### VPN

- typed VLESS/VMess/Trojan/Shadowsocks profiles؛
- real Android `VpnService` lifecycle؛
- TUN؛
- Xray integration؛
- in-memory compiled config؛
- no import/export paths.

### Control Plane

- GVP1 profile envelope؛
- server eligibility؛
- signed Remote Config؛
- feature flags؛
- analytics consent؛
- bug reports؛
- tickets؛
- diagnostics؛
- immutable admin audit؛
- dual-control publication.

### Supply Chain

- pinned libXray source؛
- source/native embedding verification؛
- SBOM؛
- provenance؛
- signed AAB workflow foundation؛
- production alert catalog.

---

## Phase 5 — Secure Android Profile-to-Tunnel Orchestration — DONE

PR #10 — `Phase 5: secure Android profile-to-tunnel orchestration`

### Delivered

- opaque one-time connection action؛
- profile binding validation؛
- Android VPN permission؛
- foreground service binding؛
- Xray runtime start؛
- UI `Connected` only after runtime success؛
- real disconnect؛
- replay/auth/zeroization/tunnel-failure tests.

---

## Phase 6A — Android Device-bound Identity & GVP1 Crypto — DONE

PR #11 — `Phase 6A: Android device-bound identity and GVP1 crypto`

### Delivered

- non-exportable P-256 signing key؛
- X25519 encryption identity؛
- AES-GCM wrapping؛
- distinct/versioned key roles؛
- GDP1 canonical proof primitives؛
- P1363 signatures؛
- X25519+HKDF+AES-GCM GVP1 decrypt؛
- tamper/interoperability tests.

### Explicit remaining boundary

At merge time production session registration/login remained fail-closed until matching backend auth/session slice.

---

## Phase 6B — Replay-resistant Backend Device Proof — DONE

PR #12 — `Phase 6B: replay-resistant device proof verification`

### Delivered

- GDP1 parse/verify؛
- ES256/P-256/P1363؛
- method/path/body/timestamp/nonce/key-version binding؛
- 90-second window؛
- PostgreSQL nonce digest replay protection؛
- replay/body mutation tests؛
- migration 004.

---

## Phase 6C — Approval-gated Play Test Publication — DONE AS PIPELINE

PR #13 — `Phase 6C: approval-gated Google Play test publication`

### Delivered

- optional Play publish job؛
- Internal/Alpha tracks only؛
- protected environments/reviewer؛
- fail closed without service account؛
- signed artifact re-download/revalidation؛
- SHA-256/release evidence؛
- Artifact Guard؛
- mapping upload؛
- pinned external upload action؛
- publication evidence artifact؛
- Persian closed-beta runbook.

### Not equivalent to production publication

- no production track automation؛
- no actual signed release/tag execution at snapshot time؛
- no actual Play upload at snapshot time؛
- no closed-beta evidence yet.

---

# Phase 7 — Production Vertical Completion — IN PROGRESS

Baseline branch when Phase 7 began: `main` at Phase 6C.

## Track A — Auth/Session v2

Branch: `phase-7/auth-session-vertical-v2`

At inspected snapshot: **7 commits ahead of Phase 6C baseline**.

### Files/areas changed

- migration `005_auth_sessions.sql`؛
- `auth-session.js`؛
- test-only auth session adapter؛
- application/runtime wiring؛
- focused auth-session tests.

### Implemented in branch

- guest registration contract؛
- device identity decode/validation؛
- GDP1 proof verification in auth flow؛
- Ed25519 access JWT issuance؛
- JWKS؛
- opaque refresh token؛
- hashed refresh storage boundary؛
- token family tracking؛
- rotation؛
- refresh reuse detection؛
- session revoke؛
- PKCE/Telegram auth state؛
- safe relink behavior/tests؛
- production session adapter requirement.

### Not Done until

- OpenAPI alignment؛
- PostgreSQL migration integration green؛
- Android session client wired؛
- real Telegram OIDC/broker E2E؛
- logout/revoke UI؛
- session cleanup policy؛
- security review؛
- merge CI green.

---

## Track B — Play Billing E2E v2

Branch: `phase-7/play-billing-e2e-v2`

At inspected snapshot: **3 commits ahead**.

### Implemented in branch

- explicit SubscriptionPurchaseV2 lifecycle classifier؛
- ACTIVE/GRACE/CANCELED-until-expiry entitlement semantics؛
- PENDING/PENDING_PURCHASE_CANCELED preserve semantics؛
- PAUSED/ON_HOLD disable semantics؛
- EXPIRED semantics؛
- package/product/expiry validation improvements؛
- tests.

### External/E2E remaining

- Play Console products؛
- service account؛
- license testers؛
- real purchase؛
- RTDN push؛
- cancel/refund/revoke؛
- plan replacement؛
- restore؛
- linked purchase؛
- acknowledgement retry evidence.

---

## Track C — Android VPN Resilience v2

Branch: `phase-7/android-vpn-resilience-v2`

At inspected snapshot: **8 commits ahead**.

### Implemented in branch

- secure profile recovery store؛
- recovery codec/tests؛
- reconnect backoff؛
- process restart restore؛
- `ConnectivityManager.NetworkCallback`؛
- underlying network switching؛
- network loss/capability change handling؛
- reconnect foreground notification states؛
- Xray reconnect path؛
- cleanup/revoke improvements.

### Remaining evidence

- Wi-Fi→cellular واقعی؛
- cellular→Wi-Fi؛
- airplane mode؛
- Doze؛
- process kill؛
- reboot/always-on policy؛
- IPv6/dual-stack؛
- captive portal؛
- leak tests؛
- battery؛
- P95 reconnect metrics.

---

## Track D — Android UI/Accessibility v2

Branch: `phase-7/android-ui-accessibility-v2`

At inspected snapshot: **1 commit ahead**.

### Implemented in branch

- `GanjTheme`؛
- semantic palette؛
- Light/Dark؛
- forced RTL composition baseline؛
- typography line-height؛
- accessibility policy/tests؛
- initial app UI refactor.

### Remaining major work

- actual localized string resources؛
- Persian copy؛
- English copy؛
- locale switching/device language policy؛
- final visual identity؛
- icon system؛
- motion system؛
- responsive layouts؛
- TalkBack semantics؛
- large font layout QA؛
- final Home/Servers/Connect/Store/Account؛
- production screenshots.

---

## Track E — Admin Console Foundation v2

Branch: `phase-7/admin-console-foundation-v2`

At inspected snapshot: **1 commit ahead**.

### Implemented in branch

- `web/admin` package foundation؛
- config boundary؛
- RBAC baseline؛
- `__Host-ganj_admin_session` secure cookie؛
- token digest utilities؛
- constant-time CSRF comparison؛
- strict same-origin validation؛
- CSP/HSTS/XFO/security headers.

### Remaining

- admin authentication/MFA؛
- persistent sessions؛
- route guard؛
- API client؛
- dashboard؛
- users/services/devices؛
- plans؛
- servers؛
- billing؛
- tickets/bugs؛
- analytics؛
- remote config/flags؛
- audit/release؛
- tests/deploy.

---

## Track F — Staging/Production Ops v2

Branch: `phase-7/staging-ops-v2`

At inspected snapshot: **1 commit ahead**.

### Implemented in branch

- hardened production Compose؛
- Postgres service؛
- migration job؛
- Control API production adapter env؛
- Nginx reverse proxy؛
- internal data/control networks؛
- dropped Linux capabilities؛
- read-only containers؛
- no-new-privileges؛
- bounded logs؛
- secret-mounted files؛
- health checks؛
- backup profile؛
- Postgres backup/restore scripts؛
- env validation؛
- smoke test؛
- server-secret adapter boundary؛
- Telegram account broker adapter boundary.

### Remaining

- real infrastructure؛
- immutable image digests؛
- DNS/TLS؛
- secret manager؛
- WAF/rate limits؛
- monitoring/log aggregation؛
- off-host backup storage؛
- automated deploy/rollback؛
- DR exercise؛
- staging E2E.

---

# Next Merge Discipline

هر Track فاز 7 قبل از Merge باید:

1. از latest `main` rebase/merge شود؛
2. conflicts بین Auth/Staging/Application wiring حل شود؛
3. all CI green؛
4. migrations ordered/checksummed؛
5. OpenAPI/docs updated؛
6. no test adapter leaks into production؛
7. security boundaries reviewed؛
8. Remaining Production Boundary در PR صریح ثبت شود.

بعد از هر Merge، این Ledger باید با SHA/PR جدید به‌روزرسانی شود.
