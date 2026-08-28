# Ganj VPN — Release Readiness Matrix

> این ماتریس برای جلوگیری از ادعای زودهنگام «آماده انتشار» است. وضعیت هر مورد باید با Evidence واقعی تغییر کند.

## Legend

- ✅ Ready/Merged
- 🟡 In Progress / Partial
- 🔴 Blocker / Not Ready
- ⚪ Planned / Optional

| Domain | Status | Current Evidence | Required Before Public Production |
|---|---|---|---|
| Product architecture | ✅ | Architecture/OpenAPI/roadmap موجود | Maintain docs with major changes |
| Android foundation | ✅ | Native Compose app + CI | Final UX/accessibility pass |
| Manual config prohibition | ✅ | Product guards + no import routes | Keep CI guard permanently |
| Xray/VpnService base runtime | ✅ | Phase 4/5 merged | Real server/device E2E |
| Device identity crypto | ✅ | Phase 6A | Auth registration integration |
| Backend device proof | ✅ | Phase 6B + replay DB | Production auth/session linkage |
| Control API product slice | ✅ | plans/services/orders/profile | Deploy staging/production |
| PostgreSQL repository | ✅ | production adapter/migrations | Real DB integration/deploy evidence |
| Enterprise APIs | ✅ | config/flags/analytics/bugs/tickets/audit | Operational Admin UI + deployment |
| Auth/session backend | 🟡 | Phase 7 branch | Merge + Android + Telegram E2E |
| Android session management | 🔴 | incomplete production flow | Secure guest/login/refresh/revoke |
| Telegram OIDC login | 🟡 | boundary + Phase 7 work | Real provider credentials/E2E |
| Bot deep-link fallback | 🟡 | designed/partial | Implement + replay/conflict tests |
| Legacy subscription sync | 🔴 | architecture defined | Adapter + reconciliation + E2E |
| Google Play client billing | ✅/🟡 | Billing adapter merged | Real Play sandbox scenarios |
| Publisher server verification | ✅/🟡 | adapter exists | Real credentials/product state E2E |
| RTDN | ✅/🟡 | verification adapter exists | Real Pub/Sub signal E2E |
| Refund/cancel/restore | 🟡 | models/tests partial | Provider E2E evidence |
| Multi-device | 🟡 | policy/data foundations | Atomic slots/revoke real flow |
| Real VPN servers | 🔴 | code expects secret refs | Real catalog/secrets/endpoints |
| Secret resolver | 🟡 | adapter boundary/Phase 7 ops | Real Vault/KMS/secret service |
| VPN process restart recovery | 🟡 | Phase 7 resilience branch | Merge/device test |
| Wi-Fi/cellular reconnect | 🟡 | code branch | Real device metrics |
| DNS leak protection | 🔴/🟡 | architecture/TUN DNS | formal leak test evidence |
| IPv6 | 🟡 | routes in resilience code | real dual-stack validation |
| Kill Switch | 🔴/🟡 | policy target | implementation + leak tests |
| Smart Connect | 🟡 | ranker/architecture | real health/probe integration |
| Servers search/favorites | 🔴/🟡 | target defined | final UI/data |
| Speed Test | 🔴 | planned | controlled implementation if MVP scope |
| Persian localization | 🔴/🟡 | Phase 7 RTL/theme | actual string resources + QA |
| English localization | 🔴/🟡 | current hardcoded English | resources + locale QA |
| Final visual design | 🟡 | functional UI | brand polish/motion/screens |
| Light/Dark | 🟡 | Phase 7 theme | merge + complete all screens |
| Accessibility | 🟡 | policy/theme branch | TalkBack/font/contrast device QA |
| Free plan | 🔴/🟡 | policy/roadmap | quota/speed/server/abuse wiring |
| Ads | ⚪/🔴 | planned | only if included; compliance audit |
| FCM notifications | 🔴 | planned | production integration/preferences |
| Marketing campaigns | 🔴 | contracts/plan | engine/Admin/consent |
| Referral | 🔴 | planned | fraud-safe reward flow |
| Direct Wallet | 🔴 | architecture | ledger/payment/reconciliation |
| Smart banking | ⚪/🔴 | future integration | isolated payment adapter |
| Admin security foundation | 🟡 | Phase 7 branch | auth/MFA/session persistence |
| Admin operational pages | 🔴 | not on main | minimum beta console |
| Monitoring alerts policy | ✅/🟡 | alert catalog exists | deployed collectors/routing |
| Staging topology | 🟡 | Phase 7 ops branch | real host/domain/secrets |
| Production topology | 🔴/🟡 | compose design | external infra/HA/security |
| Backup scripts | 🟡 | Phase 7 ops | off-host backup + restore proof |
| DR | 🔴/🟡 | runbooks/design | exercise evidence |
| Android CI | ✅ | green builds in previous phases | keep all gates green |
| Control API CI | ✅ | test suites | include merged Phase 7 tests |
| SBOM/provenance | ✅ | workflow foundations | signed candidate evidence |
| Signed AAB pipeline | ✅ | Phase 6C | execute with real signing secrets |
| Signed release candidate | 🔴 | no release execution at snapshot | tag/build/sign/evidence |
| Play Internal upload | 🔴 | pipeline only | actual upload/testers |
| Closed Beta | 🔴 | runbook only | real testers/metrics |
| Penetration test | 🔴 | planned | external/internal assessment + fixes |
| Privacy Policy | 🔴/🟡 | draft requirements | public final policy |
| Data Safety | 🔴 | draft only | submit exact final binary behavior |
| VPN Service declaration | 🔴 | requirements known | Play Console declaration |
| Account deletion | 🔴/🟡 | product target | working app/backend flow |
| Store listing | 🔴 | planned | screenshots/video/text/support |
| Production rollout | 🔴 | intentionally gated | Go/No-Go then staged percentages |

---

# Hard Go/No-Go Gates

Public Production = **NO-GO** if any of these are unresolved:

1. real Auth/Session unavailable؛
2. Telegram/account mapping required by product but unavailable؛
3. existing paid users cannot recover services؛
4. VPN real-device tunnel/leak tests fail؛
5. billing can duplicate fulfillment؛
6. refund/expiry can leave invalid access؛
7. Critical/High security finding open؛
8. no monitoring/rollback؛
9. no backup restore evidence؛
10. Privacy/Data Safety mismatch؛
11. signed artifact/provenance unavailable؛
12. crash/ANR unacceptable in beta.

---

# Beta Entry Gate

Closed Beta may begin when:

- Auth/session E2E works؛
- Telegram or approved account path works؛
- at least production-like entitlement path works؛
- real servers connect reliably؛
- purchase test path works؛
- support channel available؛
- crash reporting/monitoring active؛
- no known severe leak/security blocker؛
- signed test artifact exists؛
- rollback available.

---

# Evidence Required Per Release

- source commit SHA؛
- versionName/versionCode؛
- CI run IDs؛
- unit/integration/lint results؛
- dependency/SBOM evidence؛
- artifact SHA-256؛
- signing identity/version؛
- mapping file؛
- staging smoke؛
- VPN device matrix summary؛
- billing scenario summary؛
- known issues/accepted risks؛
- rollout plan؛
- rollback target؛
- approvals.

---

# Status Update Rule

هیچ ردیفی فقط بر اساس وجود فایل یا طراحی به ✅ تبدیل نمی‌شود. برای `Ready` شدن باید implementation روی baseline مورد استفاده Release باشد و Evidence مرتبط وجود داشته باشد.
