# Ganj VPN — UI 100% Completion Progress Ledger

> **Baseline تاریخی هنگام ایجاد Ledger: 54٪ برآورد قدیمی — دیگر معیار پیشرفت نیست.**  
> **وضعیت سخت‌گیرانه فعلی پس از Batch 2026-08-30/Notifications-1: 196 / 491 = 39.9٪**  
> **هدف: 100٪ واقعی، نه صرفاً تکمیل ۵ تب اصلی.**  
> آخرین ممیزی مبنا: 2026-08-30 — branch: `ui/stitch-persian-liquid-v1`

این فایل **مرجع اجرایی اجباری تمام Agentها برای تکمیل UI/UX** است. هر Agent قبل از هر تغییر UI باید این فایل را کامل بخواند، وضعیت Runtime و Branch/PR را بررسی کند، سپس فقط آیتم‌هایی را که واقعاً مطابق Definition of Done تکمیل کرده است از `[ ]` به `[x]` تغییر دهد.

> **فرمول رسمی و تنها معیار درصد UI:** `checked executable items in Sections 1–26 / all executable items in Sections 1–26 × 100`.  
> درصد وزن‌دار یا دستی ممنوع است. Section 0 و Section 27 در درصد شمرده نمی‌شوند. Agent باید قبل و بعد از هر Batch اجرا کند: `node scripts/ui-progress.mjs --sections`.

---

## 0) قانون اجباری Agent — قبل از هر کار UI

- [x] مرجع اصلی UI جدید: Persian RTL + Emerald/Refined Gold + Liquid Glass.
- [x] پنج مقصد اصلی فعلی به Stitch متصل شده‌اند: خانه / سرورها / اتصال / فروشگاه / پروفایل.
- [ ] Agent قبل از شروع، آخرین `main`، Branch فعلی، PRهای باز و این Ledger را بررسی کند.
- [ ] Agent Scope انتخابی خود را از بین آیتم‌های `[ ]` مشخص کند و بدون دلیل Scope موازی/تکراری نسازد.
- [ ] Agent قبل از تغییر Component جدید، Componentهای موجود را بررسی کند و Duplicate Design System نسازد.
- [ ] Agent پس از هر Batch، همین فایل را در همان PR/commit به‌روز کند.
- [ ] Agent هر Parent item را فقط وقتی `[x]` کند که **تمام Child itemهای آن بخش** تکمیل شده باشند.
- [ ] هیچ Agentی حق ندارد صرف وجود Mock/Placeholder/Static UI را «Done» اعلام کند؛ Runtime wiring واقعی لازم است مگر صراحتاً External/System-owned باشد.
- [ ] هیچ Agentی حق ندارد برای بالا بردن درصد، Setting/Feature/Telemetry جعلی بسازد.
- [ ] هر آیتمی که به Backend/Runtime contract نیاز دارد باید Fail-closed و با داده واقعی پیاده شود.
- [ ] در پایان هر Batch، Agent باید Remaining Boundary همان بخش را صریح ثبت کند.

### Definition of Done برای هر UI Leaf Item

یک checkbox فقط وقتی `[x]` می‌شود که همه موارد زیر برقرار باشند:

- [ ] UI با Design System جدید یکدست باشد؛ raw one-off style/legacy blue/Material default بدون توجیه باقی نماند.
- [ ] RTL فارسی، Persian digits و BiDi برای مقادیر فنی درست باشد.
- [ ] Light/Dark/System و کنتراست مناسب بررسی شده باشد، مگر Scope رسمی branch عمداً Persian-only ولی همچنان Theme-aware باشد.
- [ ] اندازه‌های 360 / 390 / 412 / 430dp بدون overlap/cutoff بررسی شده باشد.
- [ ] Font scale بالا، متن طولانی، keyboard و gesture navigation باعث شکست layout نشوند.
- [ ] Touch targetها حداقل 48dp و semantics/TalkBack مناسب داشته باشند.
- [ ] Loading / Empty / Error / Disabled / Offline / Retry / Success stateهای لازم پوشش داده شوند.
- [ ] Reduce Motion و Reduce Transparency رعایت شود.
- [ ] Data/Action واقعی به state/reducer/controller/backend وصل باشد؛ Fake data ممنوع.
- [ ] Unit/UI test قابل‌اجرا برای logic مهم اضافه/به‌روز شود.
- [ ] اگر Build/Device available است screenshot/device evidence ثبت شود؛ اگر External blocker است صریحاً Pending بماند و `[x]` نشود.

> **قانون 100٪:** مقدار Overall UI فقط وقتی 100٪ است که هیچ آیتم اجرایی `[ ]` در این فایل باقی نمانده باشد و Final QA/Device gates نیز کامل شده باشند. System/Third-party surfaces مثل Android VPN permission dialog، Google Play checkout UI و خود Telegram Bot UI جایگزین نمی‌شوند؛ فقط pre/post/error surfaces اپ باید کامل شوند.

---

# 1) Foundation / Brand / Global Design System

## 1.1 Completed foundation
- [x] Persian-only RTL application shell.
- [x] Emerald + Refined Gold + neutral Liquid Glass palette.
- [x] Theme-aware Premium/Warning contrast.
- [x] Persian-tuned typography scale با letter-spacing صفر برای فارسی.
- [ ] Official Ganj logo در launcher/splash/header/connect/profile. (Alpha 0.3.1: truncated PNG replaced by existing shield-vector fallback; approved original artwork still required.)
- [x] Android window chrome و Android 12+ splash surface.
- [x] Persian numeral helpers.
- [x] Unicode BiDi isolation برای IP/protocol/latency/technical values.
- [x] Responsive policy برای 360/390/412/430dp.
- [x] Reduce Motion policy حفظ شده.
- [x] Reduce Transparency policy حفظ شده.

## 1.2 Pending foundation
- [ ] Vazirmatn Variable TTF به‌صورت asset واقعی وارد repo و Theme شود.
- [ ] Typography با Vazirmatn روی 360/390/412/430dp دوباره QA شود.
- [ ] Typography روی fontScale 1.0 / 1.3 / 1.5 / 2.0 QA شود.
- [ ] Component token audit: هیچ raw color/shape/padding تکراری خارج از Design System بدون دلیل باقی نماند.
- [ ] Legacy UI component audit: componentهای قدیمی که Runtime دیگر استفاده نمی‌کند حذف/Deprecate شوند تا Agentها دوباره مصرفشان نکنند.
- [ ] Unified icon policy برای تمام actionها، بدون emoji تزئینی و بدون icon style ناهمگون.
- [ ] Unified elevation/depth/blur policy روی تمام Sheet/Dialog/Card/CTAها.
- [ ] Unified spacing scale و section rhythm روی تمام صفحات فرعی.

---

# 2) Global Navigation / App Shell

- [x] Bottom Navigation پنج‌تایی: خانه / سرورها / اتصال / فروشگاه / پروفایل.
- [x] Connect destination مرکزی elevated و motion-aware.
- [x] Responsive bottom navigation gutters.
- [x] Destination transition پایه.
- [ ] Back navigation policy برای تمام صفحات فرعی/Sheetها.
- [ ] Deep-link destination restore بدون پرش visual.
- [ ] State restoration بعد از process recreation برای صفحه فرعی باز.
- [ ] Keyboard-safe navigation برای فرم‌ها.
- [ ] Navigation transition consistency برای subpages و modal surfaces.
- [ ] Badge policy برای Notification/Wallet/Transaction pending state در صورت وجود contract.

---

# 3) Home

- [x] Home full Stitch visual port.
- [x] وضعیت سرویس/اشتراک و CTA اتصال/سرورها.
- [x] Quick actions با vector icon language.
- [ ] Wallet summary card در صورت Direct/Wallet capability.
- [ ] Pending payment/activation banner در صورت state واقعی.
- [ ] Expiring subscription banner با action تمدید.
- [ ] Important notification/banner placement policy.
- [ ] First-use Home empty state برای Guest بدون سرویس.
- [ ] Offline Home state.
- [ ] Home skeleton/loading fidelity.

---

# 4) Telegram Login / Account Link — P0

## 4.1 Entry
- [x] Login with Telegram entry surface با Liquid design.
- [x] توضیح روشن «Free بدون Login قابل استفاده است» در جای درست.
- [x] Guest vs Linked account visual state.
- [x] CTA اصلی «ورود با تلگرام».
- [x] Privacy/security copy کوتاه و غیرترسناک.

## 4.2 Bot Approval flow
- [x] Pre-Telegram confirmation/bottom sheet.
- [x] `Opening Telegram` state.
- [x] `Waiting for approval` state.
- [x] Return-to-app processing state.
- [x] Approval success state.
- [x] Approval denied state.
- [x] Request expired state.
- [x] Duplicate/replayed callback state.
- [x] Wrong-device state.
- [x] Wrong-state/invalid callback state.
- [x] Offline during approval state.
- [x] Cancelled flow state.
- [x] Retry action.
- [x] Safe fallback OIDC entry فقط در جایگاه fallback.

> **Batch Auth-3 note:** Bot Approval مسیر اصلی اپ باقی مانده است. لغو سمت App اکنون state/PKCE binding material را نابود می‌کند و fallback OIDC فقط بعد از failure واقعی capability/start نمایش داده می‌شود، نه کنار CTA اصلی. Canonical `api/openapi.yaml` نیز در نسخه 0.3.0 با Runtime/Android همگام شده و contract regression test برای جلوگیری از drift اضافه شده است.

## 4.3 Linked account / session
- [x] Linked Telegram account card با Design جدید.
- [x] نمایش identity-safe account info بدون provider token.
- [x] Session expired surface.
- [x] Re-login flow.
- [x] Logout action.
- [x] Logout confirmation dialog.
- [x] Logout success state.
- [x] Logout failure/retry state.
- [x] Post-login My Services refresh feedback.

> **Batch Auth-4 note:** `/v1/me` اکنون در Control API runtime و Android به‌صورت authenticated و fail-closed وصل است. UI فقط `display_name` و در صورت وجود `telegram_username` را نمایش می‌دهد؛ Telegram provider subject، provider credential، token و account UUID در UI نمایش داده نمی‌شوند. پاسخ معتبر سرور marker نمایشی Linked را reconcile می‌کند، ولی خطای شبکه marker/session را به‌صورت جعلی پاک نمی‌کند.

## 4.4 Auth QA
- [ ] Process recreation وسط login.
- [ ] Deep-link callback while app closed.
- [ ] Deep-link callback while app foreground.
- [ ] Double-tap Login idempotency visual feedback.
- [ ] Accessibility/TalkBack flow.
- [ ] 360–430dp QA.

---

# 5) Wallet — Direct Commerce

## 5.1 Wallet overview
- [x] Wallet screen.
- [x] Current balance card.
- [ ] Available balance vs pending balance در صورت contract.
- [ ] Add Balance CTA.
- [x] Recent transactions preview.
- [x] Balance refresh action/state.
- [x] Wallet Loading state.
- [x] Wallet Empty state.
- [x] Wallet Offline/Error state.
- [x] Wallet disabled/unavailable state.

## 5.2 Add Balance / Top-up
- [ ] Add Balance screen/sheet.
- [ ] Suggested amount chips.
- [ ] Custom amount field.
- [ ] Min/max amount validation.
- [ ] Persian numeric input formatting.
- [ ] Payment method selector فقط از providerهای واقعی.
- [ ] Payment summary before continue.
- [ ] Confirm top-up dialog/sheet.
- [ ] Redirecting/processing state.
- [ ] Pending state.
- [ ] Success state.
- [ ] Failed state.
- [ ] Cancelled state.
- [ ] Retry state.
- [ ] Receipt/reference display در صورت contract.
- [ ] Post-payment wallet refresh.
- [ ] Duplicate top-up protection feedback.

> **Batch Wallet-1 note:** Wallet دیگر placeholder نیست. migration واقعی `control_wallets` + immutable owner-scoped `control_wallet_ledger` اضافه شده، routeهای authenticated `/v1/wallet` و `/v1/wallet/transactions` در Runtime mount شده‌اند، Android client با token refresh و strict mapping وصل است و Liquid Wallet/Transactions مستقیماً همین داده را نمایش می‌دهند. Top-up write flow عمداً تا اتصال Provider واقعی غیرفعال مانده و هیچ balance یا تراکنش ساختگی در UI وجود ندارد.

---

# 6) Transactions

- [x] Transaction History screen.
- [x] Transaction row component.
- [x] Purchase transaction visual type.
- [x] Wallet top-up transaction visual type.
- [x] Refund transaction visual type.
- [x] Adjustment transaction visual type.
- [ ] Failed transaction visual type.
- [ ] Pending transaction visual type.
- [x] Filter by transaction type.
- [ ] Filter by status.
- [ ] Filter/date range UX در صورت contract.
- [x] Pagination/infinite loading.
- [x] Empty state.
- [ ] Loading skeleton.
- [x] Error/retry state.
- [x] Transaction Detail bottom sheet/page.
- [ ] Amount, date, status, reference/order id. (Amount/date/reference کامل است؛ status در immutable posted-ledger contract وجود ندارد.)
- [ ] Payment method/source.
- [x] Description/reason.
- [x] Copy reference action با feedback.
- [ ] Receipt/detail link در صورت موجود بودن.

> **Batch Transactions-2 note:** History اکنون type filter واقعی، Detail page، amount/balance-after/date/reference/description و copy-reference feedback دارد. Pending/Failed/status filter/payment method عمداً ساخته نشده‌اند چون `control_wallet_ledger` فقط posted immutable entries را مدل می‌کند و contract فعلی payment-operation status/source ندارد.

---

# 7) Settings

## 7.1 Settings shell
- [x] Settings main screen.
- [x] Section grouping و search در صورت نیاز. (Grouping انجام شده؛ search برای Scope فعلی لازم نیست.)
- [x] Consistent toggle/list row component.
- [ ] Reset/default state policy.

## 7.2 Connection settings
- [ ] Auto Connect.
- [ ] Smart Connect default behavior.
- [ ] Kill Switch setting/status.
- [ ] Network change behavior Wi-Fi ↔ Cellular.
- [ ] Startup/reboot behavior در صورت supported.
- [ ] DNS options فقط اگر runtime contract واقعی expose شود.
- [ ] Protocol preference فقط اگر runtime contract واقعی expose شود.

## 7.3 Appearance
- [x] Theme: System / Light / Dark.
- [ ] Language/locale surface طبق Scope قطعی محصول.
- [x] Reduce Motion preference اگر app-owned.
- [x] Reduce Transparency preference اگر app-owned.

## 7.4 Privacy
- [ ] Analytics consent.
- [ ] Diagnostics consent/preferences.
- [ ] Privacy summary.
- [ ] Clear local non-sensitive UI cache در صورت supported.

## 7.5 About
- [x] App version/build.
- [ ] Update status.
- [ ] Official website/Telegram/support links.
- [ ] Open-source licenses entry.
- [ ] Privacy Policy entry.
- [ ] Terms entry.

---

# 8) Devices / Device Binding

- [x] My Devices screen.
- [x] Current device highlight.
- [x] Other registered devices list.
- [ ] Device name/type. (Backend فعلی model/name را persist نمی‌کند؛ UI فقط name واقعی در صورت وجود یا privacy-safe short identifier نشان می‌دهد.)
- [ ] Safe platform/OS metadata. (Android platform موجود است؛ OS/version metadata هنوز contract/persistence ندارد.)
- [x] Last activity در حد privacy policy.
- [x] Active/revoked status.
- [ ] Device limit summary.
- [ ] Device limit reached state.
- [x] Device Detail sheet/page.
- [x] Revoke device action.
- [x] Revoke confirmation dialog.
- [x] Revoke processing state.
- [x] Revoke success state.
- [x] Revoke failure/retry state.
- [x] Cannot revoke current/last device policy feedback در صورت applicable. (Current device fail-closed؛ last-device policy جداگانه در contract فعلی وجود ندارد.)
- [x] Device Binding explanation surface.

> **Batch Devices-1 note:** OpenAPI موجود به Runtime واقعی متصل شد. `GET /v1/me/devices` owner-scoped است؛ `DELETE /v1/me/devices/{device_id}` دستگاه فعلی را fail-closed رد می‌کند و revoke دستگاه دیگر را داخل PostgreSQL transaction انجام می‌دهد: device status، همه auth sessionهای همان device و service bindings همگام invalidate می‌شوند. Android `DeviceApi` با token refresh/strict mapping و Liquid Devices list/detail/revoke flow به Profile وصل شده است. مدل گوشی، OS version و app version چون در DB فعلی ذخیره نمی‌شوند جعل نشده‌اند.

---

# 9) Notifications

- [x] Notification Center screen.
- [x] Read/unread state.
- [ ] Badge count. (Unread count داخل Notification Center واقعی است؛ badge سراسری Profile/Nav هنوز اضافه نشده.)
- [x] Subscription expiry notification item.
- [x] Renewal/purchase success item. (Renewal success از همان verified purchase-success category استفاده می‌کند؛ state جداگانه جعل نشده.)
- [x] Payment failure item.
- [x] Maintenance item.
- [x] Forced security update item.
- [x] Support/ticket reply item در صورت contract.
- [x] Marketing/promo item فقط با opt-in.
- [x] Notification empty state.
- [x] Notification loading/error state.
- [x] Mark as read/all read actions.
- [x] Notification preferences screen.
- [x] Category toggles بر اساس permission/policy واقعی.
- [x] Android notification permission pre-prompt surface برای نسخه‌های لازم.
- [x] Permission denied/settings guidance state.

> **Batch Notifications-1 note:** Notifications دیگر placeholder نیست. migration واقعی inbox/preferences با marketing opt-in پیش‌فرض خاموش اضافه شده؛ owner-scoped list/read/read-all/preferences Runtime routes، unread count، cursor pagination و fail-closed stored-action validation پیاده شده‌اند. Android `NotificationApi` با strict mapping و token refresh وصل است. Liquid Notification Center و Preferences شامل تمام kindهای فعلی، read/unread، pagination، empty/loading/error، mark-all، category toggles، Android 13+ permission pre-prompt و Settings guidance است و از Settings قابل دسترس است. Push delivery token/FCM و badge سراسری هنوز جعل نشده‌اند و باز می‌مانند.

---

# 10) Profile / Account / Security

## 10.1 Already completed
- [x] Profile full Stitch visual port.
- [x] Account summary card.
- [x] Active service count.
- [x] Service cards with traffic/device/expiry/protocol metadata.
- [x] Enterprise status/bug report/diagnostics integration.

## 10.2 Pending
- [x] Telegram linked/unlinked status integrated into Profile.
- [x] Wallet entry integrated into Profile.
- [x] Transactions entry integrated into Profile.
- [x] Settings entry integrated into Profile.
- [x] Devices entry integrated into Profile.
- [ ] Notifications entry integrated into Profile. (Notification Center فعلاً از Settings قابل دسترس است؛ ورودی مستقیم Profile هنوز باز است.)
- [ ] Security/session status card.
- [x] Logout entry + confirmation.
- [ ] Logout all devices در صورت backend support.
- [ ] Delete account flow در صورت policy/legal requirement.
- [ ] Destructive action confirmation system.

---

# 11) Subscription Details / My Services

- [x] Basic service cards in Profile.
- [x] Traffic remaining display.
- [x] Device limit display.
- [x] Expiry display.
- [x] Allowed protocol metadata display.
- [x] Dedicated Subscription Detail page/sheet.
- [x] Plan/tier detail.
- [ ] Renewal date/status.
- [ ] Auto-renew status در صورت billing model.
- [ ] Renew action.
- [ ] Upgrade action در صورت supported.
- [ ] Downgrade action در صورت supported.
- [ ] Cancel action در صورت supported.
- [ ] Confirm plan change dialog.
- [ ] Confirm cancel dialog.
- [x] Expired service state.
- [x] Revoked service state.
- [ ] Refunded service state.
- [x] Pending activation state.
- [ ] Restore Purchase action/state.
- [ ] Purchase source display.
- [ ] Related transaction/payment history entry.

---

# 12) Servers / Server Details

## 12.1 Already completed
- [x] Servers full Stitch visual port.
- [x] Search.
- [x] Persian country matching.
- [x] Free/Premium filters.
- [x] Smart Connect basic action.
- [x] Selected server/service state.

## 12.2 Pending advanced UX
- [ ] Server Detail bottom sheet.
- [ ] Favorite/unfavorite action.
- [ ] Favorites filter.
- [ ] Country selector sheet.
- [ ] Protocol filter فقط با real contract.
- [ ] Sort by ping وقتی telemetry واقعی باشد.
- [ ] Sort by load وقتی backend safe metadata فراهم کند.
- [ ] Server unavailable detail state.
- [ ] Server maintenance state.
- [ ] Premium locked server state.
- [ ] Upgrade prompt/bottom sheet برای Premium locked server.
- [ ] Refresh ping action/state.
- [ ] Switching server while connected UX.
- [ ] Server switch confirmation فقط در صورت نیاز product UX.
- [ ] Server list pagination/grouping در صورت catalog بزرگ.

---

# 13) Connect / VPN Micro-Flows

## 13.1 Already completed
- [x] Stitch Connect main screen.
- [x] Brand header.
- [x] Protection banner.
- [x] Responsive central Connect orb.
- [x] Selected server card.
- [x] Smart Connect card.
- [x] Premium card.
- [x] Basic failure card + retry.
- [x] Connected/disconnected/connecting visual mapping.

## 13.2 Pending states/surfaces
- [x] VPN permission explanation pre-surface.
- [x] Permission denied guidance.
- [x] Permanent-denial platform review — `VpnService.prepare()` has no app-level permanent-denial state analogous to runtime permissions, so no fake Settings redirect is exposed.
- [ ] Connecting overlay/state polish.
- [ ] Reconnecting/recovery surface.
- [ ] Connection timeout state.
- [ ] Profile expired state.
- [ ] Device proof/crypto failure state.
- [ ] Server unavailable state.
- [ ] Network changed state.
- [ ] Kill Switch blocking state.
- [ ] Tunnel recovery state.
- [ ] Protocol unsupported state.
- [ ] Clean disconnect failure state.
- [ ] Detailed Connection Info sheet.
- [ ] Session duration در صورت real runtime telemetry.
- [ ] Current protocol display در صورت safe contract.
- [ ] Safe public connection metadata فقط در صورت privacy-approved contract.
- [ ] Server switch while connected transition.

---

# 14) Speed / Ping / Runtime Telemetry

- [ ] Real ping contract from VPN/Xray/runtime.
- [ ] Real download telemetry contract.
- [ ] Real upload telemetry contract.
- [ ] Connect metrics card با داده واقعی.
- [ ] Measuring state.
- [ ] Unavailable state.
- [ ] Stale value policy.
- [ ] Speed Test surface در صورت product scope.
- [ ] Start test action.
- [ ] Ping test progress.
- [ ] Download test progress.
- [ ] Upload test progress.
- [ ] Result state.
- [ ] Failed/retry state.
- [ ] Privacy/network-cost copy.
- [ ] Compare server result فقط در صورت real measurements.

> تا قبل از contract واقعی، `—` صحیح است و نباید با عدد fake جایگزین شود.

---

# 15) Store / Checkout / Purchase UX

## 15.1 Already completed
- [x] Store full Stitch visual port.
- [x] Plan cards.
- [x] Plan duration/traffic/device metadata.
- [x] Pending checkout visual state.
- [x] Verified payment visual state.
- [x] Active subscription visual state.
- [x] Failed checkout path به Error system.

## 15.2 Pending purchase surfaces
- [ ] Plan Detail sheet/page.
- [ ] Compare plans UX.
- [x] Confirm purchase sheet/dialog.
- [ ] Payment method selector برای flavorهای واقعی.
- [ ] Wallet vs Google Play/Gateway choice در Direct flavor فقط در صورت contract.
- [ ] Insufficient wallet balance state.
- [ ] Add Balance shortcut از checkout.
- [ ] Redirecting to provider state.
- [ ] Provider cancelled state.
- [ ] Billing unavailable state.
- [ ] Purchase already in progress state.
- [ ] Duplicate purchase prevention feedback.
- [ ] Verification timeout state.
- [ ] Activation pending state.
- [ ] Restore purchase state.
- [ ] Refund/revoke state.
- [ ] Purchase receipt/detail screen.
- [ ] Post-purchase CTA به Connect/My Services.

---

# 16) First Run / Onboarding

- [x] Welcome screen.
- [x] Brand/value proposition کوتاه.
- [x] Free access explanation.
- [x] Premium explanation بدون dark pattern.
- [x] Privacy summary.
- [x] Continue as Guest / Start Free path.
- [ ] Optional Login with Telegram path. (Bot Approval primary اکنون source-wired است؛ افزودن CTA اختیاری onboarding هنوز انجام نشده.)
- [x] VPN permission preparation step فقط قبل از نیاز واقعی.
- [x] First server selection guidance.
- [x] First connection guidance.
- [x] Skip policy.
- [x] Onboarding completed persistence.
- [x] Re-show onboarding/help entry در Settings.
- [ ] 360–430dp + accessibility QA.

---

# 17) Referral / Invite / Promotions

- [ ] Referral screen اگر feature در scope فعال است.
- [ ] Referral code/link.
- [ ] Copy/share actions.
- [ ] Reward summary.
- [ ] Reward history.
- [ ] Invite success/pending state.
- [ ] Invalid/expired referral state.
- [ ] Promo campaign banner component.
- [ ] Promo detail sheet/page.
- [ ] Redeem action/state در صورت contract.
- [ ] Expired promo state.
- [ ] Terms/restrictions disclosure.
- [ ] Marketing opt-in boundary.

---

# 18) Support / Tickets / Bug / Diagnostics

## 18.1 Already completed
- [x] Enterprise status Liquid UI.
- [x] Bug Report Liquid UI.
- [x] Bug category chips.
- [x] Bug consent row.
- [x] Diagnostic Liquid UI.
- [x] Diagnostic consent row.
- [x] Submitted/failed actions use common visual language.

## 18.2 Pending full support UX
- [ ] Support Center screen.
- [ ] Ticket list.
- [ ] New ticket.
- [ ] Category selector.
- [ ] Priority selector در صورت supported.
- [ ] Ticket conversation/thread.
- [ ] Attachment picker/upload در صورت policy-supported.
- [ ] Staff reply state.
- [ ] Waiting/Answered/Closed statuses.
- [ ] Ticket detail.
- [ ] Reopen ticket در صورت supported.
- [ ] Ticket reply notification/deep-link.
- [ ] Empty/loading/error states.

---

# 19) Privacy / Legal / About

- [ ] Privacy Policy screen/webview/deep-link policy.
- [ ] Terms of Service screen/webview/deep-link policy.
- [ ] Data Safety/privacy summary.
- [ ] Analytics consent management.
- [ ] Diagnostic consent explanation/history در صورت required.
- [ ] Open-source licenses.
- [ ] App version/build copy action.
- [ ] Official support/website/Telegram links.
- [ ] Security/contact entry.
- [ ] Delete account/data request path در صورت legal requirement.

---

# 20) Popup / Dialog / Bottom Sheet / Overlay Component System

## 20.1 Common components
- [x] Standard Liquid Dialog component.
- [x] Destructive Confirmation Dialog.
- [ ] Warning Dialog.
- [ ] Success Dialog.
- [ ] Error Dialog.
- [ ] Info Dialog.
- [ ] Standard Modal Bottom Sheet.
- [ ] Selection Bottom Sheet.
- [ ] Detail Bottom Sheet template.
- [ ] Full-screen blocking state template.
- [ ] Snackbar system.
- [ ] Inline banner system.
- [ ] Tooltip/help bubble policy.
- [ ] Context menu policy.
- [ ] Pull-to-refresh indicator.
- [ ] Skeleton/loading shimmer policy که Reduce Motion را رعایت کند.
- [ ] Inline field validation component.
- [x] Copy-to-clipboard feedback component. (Transaction reference feedback source-wired.)

## 20.2 Required concrete sheets/dialogs
- [ ] Telegram Login pre-confirmation sheet. (Liquid confirmation dialog موجود است؛ sheet canonical جداگانه هنوز ساخته نشده.)
- [x] Logout confirmation dialog.
- [x] Revoke Device confirmation dialog.
- [ ] Server Detail sheet.
- [ ] Premium Server Upgrade sheet.
- [ ] Plan Detail sheet.
- [x] Purchase Confirmation sheet/dialog.
- [x] Transaction Detail sheet/page.
- [ ] Add Balance confirmation/result sheet.
- [ ] Subscription cancel/change confirmation.
- [ ] Session expired dialog/banner.
- [x] Notification permission pre-prompt.
- [x] VPN permission pre-surface.

> Android VPN permission dialog، Google Play checkout UI و Telegram Bot UI خودشان System/Third-party owned هستند و نباید fake clone شوند.

---

# 21) Loading / Empty / Error / Offline / Retry State System

- [x] Shared Loading card پایه.
- [x] Shared Empty card پایه.
- [x] Shared Auth Required card پایه.
- [x] Shared Error card پایه.
- [x] Forced Update full-screen gate.
- [x] Maintenance full-screen gate.
- [ ] Global Offline banner/state.
- [ ] Network restored feedback.
- [ ] Full-page loading template.
- [ ] Skeleton list template.
- [ ] Partial content failure pattern.
- [ ] Empty-with-action pattern.
- [ ] Retry-in-place pattern.
- [ ] Rate-limited state.
- [ ] Session-expired state.
- [x] Permission-required state (VPN pre-permission + denial guidance).
- [ ] Feature-disabled-by-policy state.
- [ ] Server-maintenance state.
- [ ] Billing-provider-unavailable state.

---

# 22) Forms / Inputs / Selection Components

- [x] Bug Report text field styling پایه.
- [x] Choice chip پایه در Enterprise UI.
- [x] Consent row پایه.
- [x] Servers search field styling پایه.
- [ ] Canonical TextField component برای کل App.
- [ ] Numeric currency/amount input.
- [ ] Search input with clear action.
- [ ] Password/secret field فقط اگر feature واقعی لازم شود.
- [ ] Dropdown/selector component.
- [x] Radio selection component.
- [x] Switch/toggle component.
- [ ] Date/range selector در صورت نیاز.
- [ ] Inline validation/error messaging.
- [ ] Disabled/read-only styling.
- [ ] Loading-in-button state.
- [ ] Keyboard IME actions.
- [ ] Paste/clear behavior.
- [ ] Focus/pressed/hover semantics where relevant.
- [ ] Error recovery preserving user input.

---

# 23) Motion / Haptics / Interaction Polish

- [x] Bottom-nav selected-state motion پایه.
- [x] Connect orb pulse با Reduce Motion respect.
- [ ] Global motion duration/easing tokens.
- [ ] Page transition consistency.
- [ ] Sheet enter/exit motion.
- [ ] Success/error micro-motion.
- [ ] Loading motion with Reduce Motion fallback.
- [ ] Haptic policy برای Connect/critical confirmations فقط در صورت native-safe implementation.
- [ ] Pressed state consistency برای CTA/chips/cards.
- [ ] No animation causes action duplication or state race.
- [ ] Jank/frame pacing review روی device.

---

# 24) Accessibility / RTL / Responsive Finalization

- [x] RTL app shell.
- [x] Persian digits helper.
- [x] BiDi isolation helper.
- [x] Reference width policy 360/390/412/430dp.
- [ ] TalkBack traversal برای هر صفحه اصلی.
- [ ] TalkBack traversal برای Sheet/Dialogها.
- [ ] Content descriptions برای icon-only actions.
- [ ] Selected/disabled semantics.
- [ ] 48dp minimum touch targets audit.
- [ ] Font scale تا 200% audit.
- [ ] Long Persian copy audit.
- [ ] LTR technical values داخل RTL audit.
- [ ] Keyboard-open layout audit.
- [ ] Gesture navigation inset audit.
- [ ] 3-button navigation inset audit.
- [ ] Display cutout audit.
- [ ] Landscape critical-flow audit.
- [ ] Color contrast audit Light/Dark.
- [ ] Reduce Motion audit.
- [ ] Reduce Transparency audit.

---

# 25) Physical Device / Screenshot / Visual Regression QA

- [ ] Build واقعی branch UI جدید تولید شود.
- [ ] حداقل یک device کلاس 360dp تست شود.
- [ ] حداقل یک device کلاس 412–430dp تست شود.
- [ ] Dark theme screenshots.
- [ ] Light theme screenshots.
- [ ] Keyboard-open screenshots برای forms.
- [ ] Login flow screenshots.
- [ ] Wallet/Transactions screenshots.
- [ ] Settings/Devices screenshots.
- [ ] Connect disconnected screenshot.
- [ ] Connect connecting screenshot.
- [ ] Connect connected screenshot.
- [ ] Store checkout states screenshots.
- [ ] Error/empty/loading screenshots.
- [ ] Dialog/Sheet screenshots.
- [ ] Visual regression baseline در صورت tooling available.
- [ ] Pixel/layout defects از QA به checklist برگردانده و رفع شوند.

---

# 26) Final Runtime Integration Audit

- [ ] هیچ صفحه‌ای با Legacy visual در Runtime reachable نباشد.
- [ ] هیچ Route/Deep-link به screen قدیمی نرود.
- [ ] هیچ Material default Dialog/Button/TextField ناهمگون در Runtime باقی نماند مگر System-owned.
- [ ] هیچ Mock server/ping/wallet/transaction/account data در Production UI نباشد.
- [ ] هیچ UI action بدون handler واقعی نمایش داده نشود.
- [ ] Feature-flagged actions وقتی backend capability خاموش است درست hide/disable شوند.
- [ ] Error codes به copy انسانی و privacy-safe map شوند.
- [ ] Sensitive value در UI/log/screenshot test leak نکند.
- [ ] Flavor differences Play/Direct در UI صحیح باشند.
- [ ] Purchase/Auth/VPN flows after process recreation state-safe باشند.

---

# 27) Final 100% Release Gate — همه باید تیک بخورند

- [ ] Section 1 Foundation = 100%.
- [ ] Section 2 Navigation = 100%.
- [ ] Section 3 Home = 100%.
- [ ] Section 4 Telegram Login = 100%.
- [ ] Section 5 Wallet = 100% برای Direct scope.
- [ ] Section 6 Transactions = 100%.
- [ ] Section 7 Settings = 100%.
- [ ] Section 8 Devices = 100%.
- [ ] Section 9 Notifications = 100%.
- [ ] Section 10 Profile/Account/Security = 100%.
- [ ] Section 11 Subscription Details = 100%.
- [ ] Section 12 Servers = 100%.
- [ ] Section 13 Connect Micro-Flows = 100%.
- [ ] Section 14 Telemetry = 100% یا Scope رسمی حذف/Deferred شده و docs همگام شده باشد؛ Fake data ممنوع.
- [ ] Section 15 Store/Checkout = 100%.
- [ ] Section 16 Onboarding = 100%.
- [ ] Section 17 Referral/Promotions = 100% یا Scope رسمی محصول صریحاً Deferred شده باشد.
- [ ] Section 18 Support = 100% مطابق Scope فعال.
- [ ] Section 19 Privacy/Legal/About = 100%.
- [ ] Section 20 Popup/Dialog/Sheet = 100%.
- [ ] Section 21 State System = 100%.
- [ ] Section 22 Forms = 100%.
- [ ] Section 23 Motion/Haptics = 100%.
- [ ] Section 24 Accessibility/Responsive = 100%.
- [ ] Section 25 Physical Device QA = 100%.
- [ ] Section 26 Runtime Integration Audit = 100%.
- [ ] APK/AAB واقعی با UI نهایی build و نصب شده باشد.
- [ ] هیچ P0/P1 UI blocker باز نباشد.
- [ ] PR body و README/Delivery Ledger/Release Matrix با وضعیت نهایی sync شده باشند.
- [ ] **OVERALL UI COMPLETION = 100%** فقط بعد از تمام موارد بالا.

---

# Progress Snapshot

| Area | Current status (2026-08-30) |
|---|---:|
| Foundation / Brand / Main shell | High |
| Home | High |
| Servers basic | High |
| Connect main | High |
| Store basic | High |
| Profile basic | High |
| Enterprise Bug/Diagnostics | High |
| Telegram Login final UX | High — Bot Approval primary + cancel + guarded fallback + re-login + authoritative account identity + real service-refresh feedback wired; real Bot/device E2E pending |
| Wallet | Partial/High — real DB/runtime/Android/Liquid read flow complete; top-up write/provider flow pending |
| Transactions | High — real immutable ledger + pagination + type filter + detail + copy reference wired; payment-operation status/source remains unmodeled |
| Settings | Partial — real persisted Theme + accessibility + Notification Center entry wired |
| Devices | High — real owner-scoped runtime + Android + Liquid list/detail/revoke flow wired; richer device metadata/limits + physical QA pending |
| Notifications | High — real inbox/preferences runtime + Android + Liquid Center/settings/permission flow; global badge/push delivery/internal destination routing pending |
| Subscription detail flows | Partial/High — real read-only details + status surfaces wired |
| Advanced server UX | Partial |
| Connect micro-states | Partial — permission preflow + denial guidance complete |
| Purchase micro-flows | Partial — confirmation gate added |
| Onboarding | High — first-run/skip/persistence/replay complete; optional Bot Approval CTA + device QA pending |
| Referral/Promotions | Not finalized |
| Full Support/Tickets | Partial |
| Dialog/Bottom Sheet system | Partial — Liquid confirm + purchase/VPN/auth/device/notification dialogs and transaction detail added |
| Physical-device final QA | Pending |
| **Overall (strict checkbox formula)** | **196 / 491 = 39.9%** |

## Batch Log

### 2026-09-12 — Connection runtime checkpoint

- Fixed packaged native API mismatch, accumulated socket-protection callbacks, and duplicate-connect teardown.
- Disconnect now awaits the privileged service's actual result; binding timeout, binding death and cancellation cleanup are handled.
- UI receives live service state including reconnect/error/revocation/process-restoration; an active permission/launch handle is preserved so observation cannot cancel its own connection coroutine.
- Added native instrumentation and regression tests. Full TUN traffic/config probing and physical-device consent remain pending at this checkpoint; no new leaf checkbox is claimed.
- UI Ledger sections touched: 13, 25, 26. Remaining unchecked leaves unchanged; calculator: 196/492 = 39.8%.

### 2026-09-12 — Alpha startup crash repair

- Reproduced immediate first-launch crash on API 35: `ResourceResolutionException` in `StitchOnboardingScreen` loading `ganj_logo_official.png`.
- Source PNG is 12,619 bytes but its IDAT chunk declares 26,802 bytes; no IEND exists. Build/lint alone did not detect decode failure.
- Removed the broken raster from the APK; reused the existing shield-vector geometry with the existing Emerald token. This is a temporary Alpha fallback, not a replacement official brand design.
- Launcher/splash layer now references the vector directly, not through a bitmap-only inflater.
- Version is 0.3.1-alpha / code 4. Startup execution becomes a prerequisite of Android CI and rolling publication; PR builds no longer overwrite the release.
- UI Ledger sections touched: 1, 16, 25. Leaf items completed: none; official-artwork item reopened. Runtime regression results remain pending until CI completes.
- Runtime smoke passed on API 24 and 35 at `5b4267d`, but screenshot review caught transparent canvas gradients exposing the dark splash window underneath Light-theme text. Canvas tints now composite over the active theme background, with opacity/contrast unit regressions and Light/Dark runtime screenshots. Final rerun remains pending.
- Remaining boundary: approved original logo, physical-device startup/Login/VPN E2E, responsive/TalkBack and all other unchecked QA items remain open.
- Overall UI completion: 196/492 = 39.8% (calculator output; no final UI claim).

### 2026-08-30 — Notifications-1 / real inbox + preferences + Android permission UX

- Added PostgreSQL `control_notifications` and `control_notification_preferences` with owner indexes, unread index, existing-user backfill and automatic preferences for new users.
- Marketing notifications default to opt-out (`false`); account/service/security categories have explicit persisted toggles.
- Added authenticated owner-scoped list/read/read-all/preferences routes with cursor pagination, unread count and strict stored action/kind validation.
- Invalid action payload persisted in DB fails as server-data corruption rather than being mislabeled as client validation.
- Added backend tests for ownership, unread count, cross-user read protection, complete preference writes and invalid stored action failure.
- Added Android `NotificationApi`, strict kind/action/UUID/timestamp mapping, read/read-all/preferences calls and `PUT` transport support with unit tests.
- Bound Notification API to app composition and added Liquid Notification Center with read/unread states, all current product kinds, pagination, empty/loading/error and mark-all.
- Added Liquid Notification Preferences with persisted category toggles, marketing opt-in copy, Android 13+ `POST_NOTIFICATIONS` pre-prompt, permission result state and direct Android notification-settings guidance.
- Notification Center is reachable through Settings without inventing push delivery. Existing manifest permission is reused.
- **Remaining boundary:** direct Profile/Nav unread badge, FCM/push-token delivery, full in-app routing for Store/Subscription/Wallet/Support notification actions, canonical OpenAPI expansion, physical-device/TalkBack/responsive QA and CI build evidence remain open.

### 2026-08-30 — Devices-1 / trusted-device runtime + Liquid management

- Activated the existing `/v1/me/devices` and `/v1/me/devices/{device_id}` contract with a production Postgres-backed route instead of mock data.
- Device listing is owner-scoped and marks only the authenticated `device_id` as current; model/name/app-version are not fabricated when persistence does not contain them.
- Revoke of the current device fails closed with `cannot_revoke_current_device`.
- Revoke of another owned device executes under the repository PostgreSQL transaction and atomically marks the device revoked, revokes its auth sessions and removes service-device bindings.
- Added backend route tests for owner scoping, current-device denial and atomic revoke effects.
- Added Android `DELETE` transport support and strict `DeviceApi` mapping with UUID/status/current/timestamp validation and explicit 204 handling.
- Added Device API tests including malformed data, current-device conflict and invalid-id no-network behavior.
- Bound Device API to the app composition lifecycle and added Liquid Devices list/detail/current/revoked/last-seen/revoke confirmation/processing/success/error flows plus a linked-account Profile entry.
- Added privacy-safe device labels: real backend name when available; otherwise current-device label or only an isolated short UUID suffix rather than fabricating hardware identity.
- **Remaining boundary:** persisted model/OS/app-version metadata, device-limit aggregation/reached state, physical-device/TalkBack/responsive QA and CI build evidence remain open.

### 2026-08-30 — Transactions-2 / type filter + detail + copy reference

- Added real ledger type filters over `WalletTransactionType` without introducing a fake status model.
- Added a Liquid Transaction Detail page with direction, amount, balance-after, timestamp, reference type/id and description from the real immutable ledger.
- Added copy-reference action with local feedback and unit coverage for transaction filtering.
- Pending/Failed/status/date-range/payment-source surfaces remain intentionally open because the current posted-ledger contract does not expose those concepts.

### 2026-08-30 — Wallet-1 / real balance + immutable ledger read flow

- Added PostgreSQL `control_wallets` and immutable owner-scoped `control_wallet_ledger` schema with backfill and automatic wallet creation for new users.
- Added authenticated `/v1/wallet` and cursor-paginated `/v1/wallet/transactions` runtime routes; malformed cursors and uninitialized wallets fail closed.
- Monetary DB values are checked for safe integer representation before JSON conversion; invalid currencies/unsafe amounts fail closed instead of silently losing precision.
- Added backend tests for owner-scoped balance, missing-wallet failure, pagination, privacy-minimized transaction mapping and malformed cursor rejection.
- Added Android `WalletApi` with authenticated token refresh, strict UUID/currency/timestamp/cursor mapping, redacted transport boundaries and tests for balance/ledger mapping plus fail-closed unknown transaction types.
- Bound Wallet API to the app composition lifecycle without mixing Wallet state into VPN/catalog presentation state.
- Added Liquid Wallet and Transactions screens, real balance, recent transactions, full history, pagination, empty/loading/error/retry states and Profile entries visible only for linked accounts.
- Transaction rows support posted top-up/purchase/refund/adjustment/reversal ledger types and display real amount, resulting balance, timestamp, optional reference and description.
- **Remaining boundary:** top-up/provider write flow, pending-balance contract, payment-source/status/date-range surfaces, canonical Wallet OpenAPI schema expansion, device QA and CI build evidence remain open. No fake balance/top-up/payment state is shown.

### 2026-08-30 — Auth-4 / authoritative privacy-safe account identity

- Added authenticated `/v1/me` runtime routing backed by the same `control_users` account row updated by AuthSession/Bot Approval linking.
- Production response is privacy-minimized: provider subject and provider credentials are never returned; current account UUID is transport-only and is not rendered in Compose UI.
- Added Android `CurrentAccount` contract plus bearer-authenticated `/me` client mapping with redacted `toString()` behavior.
- Wired account identity through `AndroidAuthSessionManager` → `GanjCompositionOwner` → `MainActivity` → `GanjVpnApp` → `StitchTelegramAccountCard`.
- Linked UI now shows only real `display_name` and, if the backend actually provides it, normalized `@telegram_username`; no identifier is fabricated when username is unavailable.
- Added Loading / Offline / AuthRequired / Inactive / NotFound / Retry identity states without clearing a valid session on transient network failure.
- Fixed account lookup I/O so `/me` is executed only on `Dispatchers.IO`, not the Android main thread.
- Authenticated `/me` success now reconciles the presentation-only linked marker persistently; network/server failures do not mutate it.
- Added backend privacy/fail-closed tests, Android API mapping/redaction tests, UI identity normalization tests and coordinator reconciliation regression coverage.
- **Remaining boundary:** Auth process recreation, App-closed/foreground deep-link callback QA, double-tap visual idempotency, TalkBack, 360–430dp physical-device QA and real staging Bot approval E2E remain open. GitHub Actions build evidence also remains external until jobs actually receive a runner.

### 2026-08-30 — Auth-3 / relogin + retryable logout + service-refresh feedback

- Canonical `api/openapi.yaml` updated to 0.3.0 with the primary Bot Approval start/status/exchange routes and HMAC-protected internal Bot decision callback; OIDC is explicitly fallback-only.
- Added a no-dependency Control API contract regression test so Bot Approval paths, operation IDs, internal HMAC surface and fallback wording cannot silently drift from the canonical OpenAPI contract.
- Added App-side Bot Approval cancellation; clearing encrypted local state/PKCE binding material makes a later Bot approval non-exchangeable from that cancelled App flow.
- Added guarded OIDC fallback entry that appears only after real Bot Approval capability/start failure and is never shown beside the normal primary CTA.
- Added a real Re-login flow for stale/expired linked sessions, including pending-approval resume/cancel behavior for an already-linked account.
- Fixed a re-login lifecycle defect where `onResume()` previously ignored pending Bot Approval while the old linked marker was still true.
- Changed logout semantics so retryable remote failures preserve the local credential and linked presentation marker; the user can perform a real server revoke retry instead of receiving a fake success or losing the revocation credential.
- Added post-login `/services` refresh feedback driven only by real `ContentState`: syncing, truthful service count, empty, auth-required and retryable/non-retryable failure.
- Added explicit retry for failed service synchronization and regression tests for relogin marker preservation, cancellation, logout failure and service-sync feedback mapping.
- Fixed the real Compose compile mismatch where `GanjVpnApp` passed service-sync feedback to a `StitchTelegramAccountCard` signature that had not yet accepted it.
- **Remaining boundary:** authoritative identity-safe account info moved to Auth-4. Real Ganj Bot/staging migration, process-death/deep-link/device/accessibility QA and GitHub Actions build evidence remain open.

### 2026-08-30 — Auth-2 / Telegram Bot Approval primary

- Added a dedicated one-time Bot Approval backend state machine and PostgreSQL migration bound to initiating user, device, state and S256 PKCE challenge.
- Approval token is opaque and only its SHA-256 digest is persisted; Bot callbacks use HMAC-SHA256 + timestamp skew validation + idempotent event IDs.
- Added authenticated start/status/exchange routes plus a fail-closed internal Bot decision route; the Bot never mints App access/refresh tokens.
- Fresh-ported the companion Ganj Bot 0.1.5.4 bridge/installer with deterministic backup/hook patching, private 0600 HMAC secret outside web-root, approve/deny buttons and existing ownership/PasarGuard projection behavior preserved.
- Android `begin()` now uses Bot Approval as the primary login path; hardened OIDC/PKCE is isolated behind `beginOidcFallback()`.
- Added encrypted backward-compatible Bot flow persistence, app-resume status checking, one-time session exchange and immediate account/service refresh trigger.
- Added Liquid pre-Telegram confirmation plus Opening/Waiting/Processing/Approved/Denied/Expired/Replay/Wrong-device/Binding-mismatch/Offline/Retry surfaces.
- Added backend negative tests and Android coordinator tests for pending/approve/deny/expiry/wrong-device/binding mismatch/replay/offline and OIDC fallback separation.
- Static review fixed two real UX/security mapping defects: PKCE/state binding mismatch is no longer mislabeled as session expiry, and an expired stored approval remains detectable until an explicit expiry result is surfaced and the flow is cleared.
- **Remaining boundary:** cancellation/fallback/re-login/sync feedback moved to Auth-3; real Ganj Bot installation/staging secrets, process-death/deep-link/device/accessibility QA and GitHub Actions build evidence are still pending. Issue #39 remains open until the real fresh-install → Bot approval → linked services E2E is proven.

### 2026-08-30 — Auth-1 / Telegram fallback + Liquid account surface

- Fresh-ported hardened PKCE/session/App-Link primitives from PR #34 without merging the stale branch.
- Added encrypted transient Telegram auth flow vault and one-shot replay/expiry/state/redirect protections.
- Added Runtime-wired Guest/Linked/Busy/Error account state into the Stitch Profile destination.
- Added Liquid Telegram account card and real logout action.
- Added reusable Liquid confirmation dialog and concrete logout confirmation.
- Added coordinator unit tests for PKCE start, state mismatch/replay, expiry, redirect mismatch, one-shot success, failed exchange and logout.
- **Remaining boundary:** superseded by Auth-2/Auth-3/Auth-4 for the primary flow; OIDC/PKCE remains fallback, while real Bot/device E2E and CI evidence remain external gates.

### 2026-08-30 — Settings-1 / persisted appearance + accessibility

- Added a real Settings subflow under Account with system Back handling.
- Added persistent `System / Light / Dark` theme preference using app-local SharedPreferences.
- Added persistent Reduce Motion and Reduce Transparency preferences.
- User reductions are additive: they never override stricter system accessibility/power/low-RAM behavior.
- Reduce Transparency disables ambient background effects and reuses the existing opaque Liquid fallback.
- Added a Liquid radio selector, accessible switch/toggle rows with `Role.Switch`, and minimum touch targets.
- Added real app version/build display from `BuildConfig`.
- Added pure policy unit tests for theme resolution and system accessibility precedence.
- **Remaining boundary:** connection settings, privacy settings, legal/support links and device QA are still open. Wallet/Transactions read flow moved to Wallet-1; top-up/provider writes remain open and no fake balance/ledger is shown.

### 2026-08-30 — Subscription-1 / real service details

- Added a dedicated Liquid Subscription Details surface driven only by real `ServiceUiModel` data.
- Added tier/status, remaining/used/limit traffic, device limit, expiry, country, entitlement id and allowed-protocol display.
- Added explicit Pending, Disabled, Expired and Revoked state copy instead of treating every service as active.
- Added a real Connect action for active services and a real Store navigation action; no unsupported renewal/cancel/payment actions were fabricated.
- Integrated the selected-service details entry into the Account/Profile flow with system Back handling.
- **Remaining boundary:** renewal/auto-renew, purchase source/history, refund, cancel/change-plan and restore-purchase require real commerce/presentation contracts before they can be marked complete.

### 2026-08-30 — Connect-Checkout-1 / permission and purchase gates

- Added an app-owned Liquid explanation before Android's real `VpnService` consent screen.
- Added permission-denied guidance while preserving the system-owned VPN consent UI.
- Reviewed Android's VPN permission contract: there is no app-level permanent-denial state analogous to runtime permissions, so no fake Settings redirect is exposed.
- Added a real Liquid purchase confirmation before invoking the existing checkout controller/provider flow.
- Purchase confirmation does not fabricate price conversion; Provider remains authoritative for final payable amount/terms.
- **Remaining boundary:** reconnect/timeout/network-change/tunnel-recovery detail states and richer billing-provider states still require their real runtime contracts/state mappings.

### 2026-08-30 — Onboarding-1 / first-run guest flow

- Added a three-step Liquid first-run experience using the official Ganj logo and existing responsive policy.
- Added clear Free-without-login guidance, Premium positioning and privacy summary without fabricated limits or fake telemetry.
- Added first server-selection and first-connection guidance tied to the real VPN permission model.
- Added `Start Free` and explicit Skip behavior; both persist completion in app-local preferences.
- Added a Settings action to replay onboarding at any time.
- Forced Update/Maintenance remain higher-priority gates than onboarding.
- **Remaining boundary:** optional Telegram login CTA is still not exposed in onboarding; physical-device/accessibility QA is also pending.

## Mandatory update format after every Agent batch

### 2026-09-12 — Connection/proxy latency checkpoint

- 2026-09-13: obtain OS VPN consent before requesting the expiring profile; emulator test launches a foreground Activity before the system consent dialog.
- Connection card now resolves the actual selected/connected server metadata instead of labeling the subscription name as a server; the measured latency is reused on that card.

- Added server-authorized native Xray HTTP latency, streamed per-config results, explicit no-response state, and foreground-only 15-second refresh for the connected config.
- Smart Connect measures eligible configs and chooses the fastest successful result; no synthetic latency or connection success is shown.
- Cancelled stale service/server requests; switching active configs retains the TUN, and underlying-network callbacks exclude the VPN itself.
- Added isolated emulator checks for OS consent denial/approval, real VLESS traffic through Android TUN, latency beside an active tunnel, disconnect and reconnect. CI results and physical-device QA are still pending for this batch.
- UI Ledger sections touched: 5, 6, 20; leaf items completed: none pending validation; overall: 196/492 = 39.8%.

Agent باید انتهای PR/commit summary این چهار خط را به‌روز کند:

```text
UI Ledger sections touched: 9, 20
Leaf items completed: Notification Center/read-unread/kinds/empty-loading-error/read-actions/preferences/category toggles/Android permission pre-prompt/settings guidance
Remaining unchecked items in touched sections: global unread badge; push delivery; full internal action routing; remaining common overlay system; physical-device QA
Overall UI completion: 196/491 = 39.9% (calculator formula; 100% forbidden until Final Gate is all checked)
```

این Ledger باید همراه کد تکامل پیدا کند؛ حذف checkbox برای پنهان‌کردن کار باقی‌مانده ممنوع است. اگر Scope رسمی تغییر کرد، ابتدا Scope canonical docs اصلاح شود و سپس آیتم با دلیل مشخص `Deferred by product scope` شود؛ هیچ Agentی حق ندارد مستقل از Product Scope آیتم را نادیده بگیرد.
