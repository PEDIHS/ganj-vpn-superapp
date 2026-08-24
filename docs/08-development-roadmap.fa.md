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

## Phase 1 — Foundations (هفته 3–6)

### Android

- Gradle convention plugins و modular skeleton؛
- Compose Design System، RTL، dark/light؛
- encrypted local session، networking و error model؛
- VPN engine interface و isolated service process؛
- unit/lint/detekt/CI.

### Backend

- Laravel modules، PostgreSQL، Redis، OpenAPI generation؛
- users/devices/sessions؛
- Telegram OIDC PKCE؛
- legacy read adapter و mapping؛
- admin RBAC/MFA baseline.

Exit gate: login guest/Telegram و bootstrap روی staging.

## Phase 2 — VPN MVP (هفته 7–12)

- Xray wrapper pinned and licensed؛
- VLESS/VMess/Trojan/Shadowsocks؛
- Android VpnService، foreground notification، reconnect؛
- Home/Servers/Connect؛
- server catalog، health و Config Broker؛
- Smart Connect v1؛
- Kill Switch، DNS leak protection، local stats؛
- end-to-end connection tests روی Android 8–16.

Exit gate: P95 connect <4s روی test matrix، no cleartext leak، crash-free lab pass.

## Phase 3 — Commerce & Sync (هفته 13–18)

- My Services، Plan، Store، Wallet و Transactions؛
- Google Play Billing flavor و server verification؛
- Direct payment flavor؛
- entitlement sync با Bot؛
- multi-device/revoke؛
- idempotency/reconciliation؛
- React admin: users, plans, servers, orders.

Exit gate: sandbox purchase → entitlement → connect → revoke با Audit کامل.

## Phase 4 — Marketing & Quality (هفته 19–22)

- FCM، notification preferences و consent؛
- banners/popup/announcement/campaign targeting؛
- Free plan، quota/speed policy و contextual ads؛
- favorites، search/filter/sort، speed test؛
- accessibility، performance profiles، battery tests؛
- privacy policy/data safety drafts.

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

