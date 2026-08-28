# Ganj VPN — AI / Developer Engineering Handoff Protocol

> این سند برای انتقال پروژه به AI Agent یا Developer جدید است تا بدون بازسازی Context قبلی بتواند امن و دقیق ادامه دهد.

---

# 1. قبل از هر تغییر

اجباری:

1. `README.md` را بخوان؛
2. `00-CURRENT_PRODUCT_SCOPE.fa.md` را بخوان؛
3. اگر Task به Android UI/UX/Brand مربوط است، **حتماً** `15-android-ui-ux-brand-system.fa.md` را بخوان؛
4. `PROJECT_BIBLE.fa.md` را بخوان؛
5. `DELIVERY_LEDGER.fa.md` را بخوان؛
6. `IMPLEMENTATION_BACKLOG.fa.md` را بخوان؛
7. `RELEASE_READINESS_MATRIX.fa.md` را بخوان؛
8. `main` latest commit را بررسی کن؛
9. Branchهای مرتبط با task را جست‌وجو کن؛
10. PRهای باز و merged مشابه را بررسی کن؛
11. فایل‌های implementation واقعی را بخوان؛
12. تست‌های همان module را قبل از طراحی تغییر بخوان.

هیچ Feature را فقط از روی مستندات دوباره نساز؛ GitHub code source of truth است. برای Requirement فعلی Scope Lock و برای Visual Brand سند 15 مرجع قطعی‌اند.

---

# 2. Product Invariants که نباید شکسته شوند

## Current Platform Scope

- Client اجرایی فعلی Android-only است؛
- iOS/Desktop فقط Post-MVP؛
- Apple HIG inspiration به معنی iOS clone یا ایجاد iOS workstream فعلی نیست.

## Visual Brand

- Primary Brand/Action = Emerald Green؛
- Premium/Signature Accent = Refined Gold؛
- غالب screen composition = Neutral Dark / Warm Light؛
- Blue به‌عنوان Brand Primary ممنوع؛
- `#0A84FF`, `#007AFF`, Material default blue و `GanjBlue` نباید Design Primary نهایی باشند؛
- Gold نباید تمام App را پر کند؛
- Liquid Glass محدود و functional است؛
- Dynamic Color برای Brand Core در MVP پیش‌فرض خاموش؛
- UI branchهای قدیمی با Blue قبل از merge باید migration شوند.

## VPN Config

ممنوع:

- manual URI؛
- QR import؛
- clipboard config؛
- file import؛
- subscription URL؛
- raw Xray JSON input؛
- credential export.

Connection فقط از Entitlement + trusted device + eligible server + encrypted short-lived profile.

## Billing

- client purchase success = entitlement نیست؛
- Backend verification اجباری؛
- pending no grant؛
- RTDN signal only؛
- idempotency اجباری؛
- token raw logging ممنوع.

## Auth

- test header/adapter در production ممنوع؛
- device binding؛
- refresh rotation/reuse handling؛
- server-side ownership.

## Privacy

هرگز log/analytics نشود:

- destination؛
- DNS query؛
- packet content؛
- VPN secret؛
- access/refresh token؛
- raw purchase token؛
- private key؛
- precise location.

---

# 3. Branch Strategy

برای هر Track بزرگ Branch مستقل:

```text
phase-N/<workstream>-vX
fix/<scope>
docs/<scope>
```

قبل از کار روی Branch جدید بررسی کن Branch مشابه موجود نباشد.

از `main` پایدار base بگیر مگر task صریحاً ادامه Branch در حال توسعه باشد.

برای UI اگر `phase-7/android-ui-accessibility-v2` یا successor آن وجود دارد، ابتدا compare کن؛ Feature را از صفر duplicate نکن.

---

# 4. PR Contract

هر PR حرفه‌ای باید حداقل این بخش‌ها را داشته باشد:

## Outcome

چه gap واقعی بسته شد؟

## Scope

چه فایل/دامنه‌هایی تغییر کردند؟

## Security

- auth؛
- secrets؛
- ownership؛
- crypto؛
- privacy؛
- abuse/replay impact.

## UI/UX — اگر relevant

- Screen/Componentهای تغییرکرده؛
- Design tokenهای استفاده‌شده؛
- Emerald/Gold/Neutral brand compliance؛
- Dark/Light/System؛
- fa/en؛
- RTL/LTR؛
- font scale؛
- TalkBack؛
- screenshot/device evidence؛
- motion/performance impact.

## Data/Migration

- schema changes؛
- indexes؛
- idempotency؛
- backwards compatibility.

## Verification

- tests run؛
- CI؛
- integration؛
- manual/device/provider evidence اگر relevant.

## Remaining Boundary

صریحاً چه چیزی هنوز production-ready نیست؟

هرگز Remaining Boundary را حذف نکن فقط برای اینکه PR کامل‌تر به‌نظر برسد.

---

# 5. Code Change Rules

## Backend

- parameterized SQL؛
- ownership in query؛
- financial changes transactional؛
- idempotency keys durable؛
- provider response schema validated؛
- timeouts bounded؛
- redirects controlled؛
- secrets by references/injection؛
- test behavior isolated.

## Android

- secret/profile outside Compose state؛
- no intent extra for raw profile؛
- no SavedState secrets؛
- Keystore for persistent sensitive tokens/keys؛
- lifecycle cancellation safe؛
- VPN connected state tied to runtime؛
- localization via resources؛
- accessibility semantics؛
- Material 3 behavior without default blue branding؛
- `design/tokens.json` + `15-android-ui-ux-brand-system.fa.md` for final visual choices؛
- user-facing hardcoded English ممنوع در production screens.

## Android UI Brand Migration

- `GanjBlue` باید از Final Theme حذف شود؛
- `GanjEmerald`, Gold و neutral semantic tokens استفاده شوند؛
- Gold فقط premium micro-accent؛
- Neutral surfaces غالب؛
- Dynamic Color نباید Brand را override کند؛
- Glass layer باید fallback داشته باشد؛
- 48dp touch target minimum؛
- Light/Dark هر دو مستقل design شوند.

## Admin

- server-side RBAC؛
- CSRF؛
- secure cookies؛
- re-auth for destructive operations؛
- audit privileged changes؛
- no secret reveal.

---

# 6. Migration Rules

هر DB change:

- migration file جدید؛
- existing migration edit ممنوع پس از merge؛
- forward compatible؛
- indexes/constraints explicit؛
- integration test؛
- test seed separated؛
- destructive changes with staged plan.

برای enum/status changes، readerهای قدیمی/جدید و rollout order را در نظر بگیر.

---

# 7. Testing Requirements by Workstream

## Auth

- happy path؛
- forged token؛
- expired؛
- wrong issuer/aud؛
- refresh reuse؛
- concurrency؛
- device replay؛
- relink conflict.

## Billing

- valid purchase؛
- pending؛
- cancel؛
- expiry؛
- refund؛
- duplicate؛
- acknowledgement failure؛
- RTDN spoof؛
- plan replacement.

## VPN

- profile binding؛
- expiry؛
- replay؛
- connect؛
- disconnect؛
- network loss؛
- reconnect؛
- process death؛
- leak؛
- IPv6؛
- no log secret.

## Android UI

- Dark/Light/System؛
- fa-IR RTL؛
- en LTR؛
- font scale 100/130/160/200%؛
- TalkBack؛
- contrast؛
- touch target؛
- Reduce Motion؛
- Glass opaque fallback؛
- small/standard/large phone؛
- all VPN states؛
- all core purchase states؛
- screenshot review confirming no dominant blue brand surface.

## Admin

- auth؛
- CSRF؛
- same-origin؛
- scope deny؛
- privilege escalation؛
- audit؛
- session expiry.

---

# 8. Production Claims Rule

از عبارت‌های `production-ready`, `ready for release`, `fully complete` فقط وقتی استفاده کن که external evidence موجود باشد.

وجود این موارد به‌تنهایی کافی نیست:

- class/adapter نوشته شده؛
- mock test pass؛
- workflow تعریف شده؛
- Docker compose وجود دارد؛
- API endpoint وجود دارد؛
- UI screenshot زیبا است.

برای Production claim معمولاً لازم است:

- real account/provider؛
- real secret؛
- staging deployment؛
- E2E؛
- monitoring؛
- rollback؛
- security/compliance evidence؛
- در UI: device/accessibility/localization evidence.

---

# 9. Current Recommended Next Order

اگر task عمومی «ادامه پروژه» بود، ترتیب پیش‌فرض:

1. بررسی latest `main` و Phase 7 branches؛
2. نزدیک‌ترین P0 Track به merge را کامل کن؛
3. Auth/Session first اگر هنوز merge نشده؛
4. Telegram linking/sync؛
5. staging deployment؛
6. real VPN E2E/resilience؛
7. Play Billing E2E؛
8. UI localization/finalization طبق سند 15؛
9. Admin minimum؛
10. beta/release gates.

به‌صورت موازی می‌توان UI/Admin/Ops را پیش برد، ولی dependencyهای امنیتی bypass نشوند.

---

# 10. Handoff State Template

در پایان هر session/task مهم، این اطلاعات باید در PR/docs قابل بازیابی باشد:

```text
Current baseline:
Branch:
Latest commit:
Goal:
Completed:
Files changed:
Tests run:
CI status:
Security notes:
UI/Brand notes:
Migration notes:
External dependencies:
Known blockers:
Exact next actions:
Do not change:
```

این template برای جلوگیری از گم‌شدن Context بین AI Agentها الزامی است.

---

# 11. No-Go Behaviors for AI Agents

- ساخت mock تولیدی برای نمایش progress؛
- hardcode secret؛
- حذف fail-closed check برای عبور test؛
- merge بدون بررسی CI؛
- تغییر migration قدیمی برای راحتی؛
- افزودن manual config؛
- claim کردن تستی که اجرا نشده؛
- claim کردن Play upload که انجام نشده؛
- تبدیل server-side permission به client-only check؛
- log کردن payload محرمانه برای debug؛
- حذف تست امنیتی برای سبز شدن build؛
- overwrite کردن branch کاری دیگر بدون compare؛
- برگرداندن Blue به‌عنوان Brand Primary؛
- طلایی/سبز کردن کل UI برای «لوکس» نشان دادن؛
- کپی مستقیم iOS/Nord/Proton؛
- فعال‌کردن Dynamic Color به‌گونه‌ای که Brand Ganj override شود.

---

# 12. Definition of a Good AI Contribution

Contribution خوب:

- یک gap واقعی را می‌بندد؛
- scope مشخص دارد؛
- existing architecture را دنبال می‌کند؛
- امنیت را کاهش نمی‌دهد؛
- tests اضافه/تقویت می‌کند؛
- docs را sync می‌کند؛
- در UI، Brand Lock و Accessibility را رعایت می‌کند؛
- Remaining Boundary را صادقانه ثبت می‌کند؛
- کار Agent بعدی را آسان‌تر می‌کند.

هدف سرعت خام نیست؛ هدف **parallel delivery بدون ایجاد technical/security/design debt پنهان** است.
