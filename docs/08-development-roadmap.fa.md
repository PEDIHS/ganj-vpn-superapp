# Development Roadmap

برآورد برای تیم 8–10 نفره با Sprint دو هفته‌ای است. تاریخ‌ها پس از دسترسی به Backend staging و Signing account نهایی می‌شوند.

## Phase 0 — Baseline (هفته 0–2)

خروجی این مخزن:

- current-state audit؛
- competitor analysis؛
- architecture/design/database/API/security docs؛
- OpenAPI و SQL baseline؛
- licensing decision و migration plan.

Exit gate: تأیید Product Scope، Application ID، payment channels و privacy position.

## مدل تحویل موازی

فازها صرفاً صف خطی نیستند. بعد از تثبیت قراردادهای فاز صفر، سه Track به‌صورت هم‌زمان اجرا می‌شوند و در نقاط ادغام مشترک به هم می‌رسند:

- Track A — Android Product: Design System، حساب، سرویس‌ها، Store و Subscription.
- Track B — Control Platform: API، داده، Telegram Sync، پرداخت، Admin و Enterprise Systems.
- Track C — VPN & Operations: Xray، Config Broker، Smart Connect، Observability، Security و Release.

وابستگی‌های امنیتی حذف نمی‌شوند: برای مثال UI خرید می‌تواند هم‌زمان با VPN Core ساخته شود، اما فعال‌سازی سرویس فقط پس از Server-side verification و Entitlement معتبر ممکن است.

## Phase 1 — Foundations (هفته 3–6، هم‌زمان با شروع Phase 3 و 4)

### Android

- Gradle convention plugins و modular skeleton؛
- Compose Design System، RTL، dark/light؛
- encrypted local session، networking و error model؛
- VPN engine interface و isolated service process؛
- navigation پنج‌تب، My Services و Store state machine؛
- ممنوعیت کامل manual/QR/clipboard/file config import؛
- unit/lint/detekt/CI.

### Backend

- Node.js modules، PostgreSQL، Redis و OpenAPI generation؛
- users/devices/sessions؛
- Telegram OIDC PKCE؛
- legacy read adapter و mapping؛
- admin RBAC/MFA baseline.

Exit gate: login guest/Telegram و bootstrap روی staging.

## Phase 2 — VPN MVP (هفته 5–12، هم‌زمان با Commerce و Observability)

- Xray wrapper pinned and licensed؛
- VLESS/VMess/Trojan/Shadowsocks؛
- Android VpnService، foreground notification، reconnect؛
- Home/Servers/Connect؛
- server catalog، health و Config Broker؛
- entitlement-only, device-bound encrypted profiles؛
- Smart Connect v1؛
- Kill Switch، DNS leak protection، local stats؛
- end-to-end connection tests روی Android 8–16.

Exit gate: P95 connect <4s روی test matrix، no cleartext leak، crash-free lab pass.

## Phase 3 — Commerce & Sync (هفته 4–18)

- My Services، Plan، Store، Wallet و Transactions؛
- Google Play Billing flavor و server verification؛
- Direct payment flavor؛
- entitlement sync با Bot؛
- multi-device/revoke؛
- idempotency/reconciliation؛
- purchase/renewal state machine، pending payment recovery و refund/chargeback revocation؛
- React admin: users, plans, servers, orders.

Exit gate: sandbox purchase → entitlement → connect → revoke با Audit کامل.

## Phase 4 — Enterprise, Marketing & Quality (هفته 4–22)

- FCM، notification preferences و consent؛
- banners/popup/announcement/campaign targeting؛
- Free plan، quota/speed policy و contextual ads؛
- favorites، search/filter/sort، speed test؛
- accessibility، performance profiles، battery tests؛
- privacy policy/data safety drafts.
- bug/crash reporting، support tickets و privacy-safe diagnostics؛
- remote config، feature flags، staged rollout و A/B testing؛
- analytics funnels، product dashboard و consent governance؛
- server/backend monitoring، alerting، audit، SOC و anti-abuse؛
- backup/restore drills و disaster recovery evidence.

Exit gate: internal alpha و policy review.

## Phase 5 — Release (هفته 23–26)

- closed beta، staged rollout، crash/ANR dashboard؛
- penetration test و remediation؛
- SBOM، provenance، signed AAB/APK؛
- Play listing، VpnService declaration، Data Safety؛
- support runbook و incident drills؛
- legacy cutover domain-by-domain.

Exit gate: Production readiness review و rollout 5% → 20% → 50% → 100%.

## Post-MVP

- WireGuard، MultiHop، rotating IP؛
- per-app routing بدون `QUERY_ALL_PACKAGES` broad access در صورت امکان؛
- iOS SwiftUI + Network Extension؛
- Desktop clients؛
- signed remote routing rules؛
- organization/team accounts؛
- advanced anti-censorship protocol selection؛
- A/B testing فقط برای UX/offer، نه security behavior.

## Workstreams and Owners

| Workstream | Primary | Reviewers |
|---|---|---|
| VPN engine | VPN Core Developer | Android Architect, Security |
| Android shell/features | Senior Android | UX, Motion, QA |
| Design system | iOS UX/HIG Expert | Android, Accessibility |
| API/data | Backend Architect | Security, Product |
| Telegram/legacy | Backend + Bot owner | Security, QA |
| Commerce | Backend + Android | Finance/Product/Security |
| CI/release | Mobile Architect | Security, Product |
| Admin/marketing | Web team | Product, Privacy |

## Release Metrics

| Metric | MVP target |
|---|---:|
| Crash-free users | ≥99.7% |
| ANR rate | <0.25% |
| P50 connect | <1.8s |
| P95 connect | <4.0s |
| reconnect success | ≥95% |
| API P95 read | <350ms excluding providers |
| battery during idle connected | <1%/h target device |
| purchase duplicate fulfillment | 0 |
| secret/config leak tests | 0 findings |

## Critical Dependencies

- Application ID نهایی و Play Console ownership؛
- release signing ownership و backup؛
- BotFather OIDC Client ID/Secret و allowed URLs؛
- Firebase App مطابق Application ID؛
- staging provider accounts و safe test configs؛
- Privacy/Terms owner و refund policy؛
- تصمیم نهایی Free quota/ads و pricing.
