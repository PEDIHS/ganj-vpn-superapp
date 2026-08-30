# Ganj VPN — UI 100% Completion Progress Ledger

> **وضعیت رسمی UI در شروع این Ledger: 54٪**  
> **هدف: 100٪ واقعی، نه صرفاً تکمیل ۵ تب اصلی.**  
> آخرین ممیزی مبنا: 2026-08-30 — branch: `ui/stitch-persian-liquid-v1`

این فایل **مرجع اجرایی اجباری تمام Agentها برای تکمیل UI/UX** است. هر Agent قبل از هر تغییر UI باید این فایل را کامل بخواند، وضعیت Runtime و Branch/PR را بررسی کند، سپس فقط آیتم‌هایی را که واقعاً مطابق Definition of Done تکمیل کرده است از `[ ]` به `[x]` تغییر دهد.

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
- [x] Official Ganj logo در launcher/splash/header/connect/profile.
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
- [ ] Login with Telegram entry surface با Liquid design.
- [ ] توضیح روشن «Free بدون Login قابل استفاده است» در جای درست.
- [ ] Guest vs Linked account visual state.
- [ ] CTA اصلی «ورود با تلگرام».
- [ ] Privacy/security copy کوتاه و غیرترسناک.

## 4.2 Bot Approval flow
- [ ] Pre-Telegram confirmation/bottom sheet.
- [ ] `Opening Telegram` state.
- [ ] `Waiting for approval` state.
- [ ] Return-to-app processing state.
- [ ] Approval success state.
- [ ] Approval denied state.
- [ ] Request expired state.
- [ ] Duplicate/replayed callback state.
- [ ] Wrong-device state.
- [ ] Wrong-state/invalid callback state.
- [ ] Offline during approval state.
- [ ] Cancelled flow state.
- [ ] Retry action.
- [ ] Safe fallback OIDC entry فقط در جایگاه fallback.

## 4.3 Linked account / session
- [ ] Linked Telegram account card با Design جدید.
- [ ] نمایش identity-safe account info بدون provider token.
- [ ] Session expired surface.
- [ ] Re-login flow.
- [ ] Logout action.
- [ ] Logout confirmation dialog.
- [ ] Logout success state.
- [ ] Logout failure/retry state.
- [ ] Post-login My Services refresh feedback.

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
- [ ] Wallet screen.
- [ ] Current balance card.
- [ ] Available balance vs pending balance در صورت contract.
- [ ] Add Balance CTA.
- [ ] Recent transactions preview.
- [ ] Balance refresh action/state.
- [ ] Wallet Loading state.
- [ ] Wallet Empty state.
- [ ] Wallet Offline/Error state.
- [ ] Wallet disabled/unavailable state.

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

---

# 6) Transactions

- [ ] Transaction History screen.
- [ ] Transaction row component.
- [ ] Purchase transaction visual type.
- [ ] Wallet top-up transaction visual type.
- [ ] Refund transaction visual type.
- [ ] Adjustment transaction visual type.
- [ ] Failed transaction visual type.
- [ ] Pending transaction visual type.
- [ ] Filter by transaction type.
- [ ] Filter by status.
- [ ] Filter/date range UX در صورت contract.
- [ ] Pagination/infinite loading.
- [ ] Empty state.
- [ ] Loading skeleton.
- [ ] Error/retry state.
- [ ] Transaction Detail bottom sheet/page.
- [ ] Amount, date, status, reference/order id.
- [ ] Payment method/source.
- [ ] Description/reason.
- [ ] Copy reference action با feedback.
- [ ] Receipt/detail link در صورت موجود بودن.

---

# 7) Settings

## 7.1 Settings shell
- [ ] Settings main screen.
- [ ] Section grouping و search در صورت نیاز.
- [ ] Consistent toggle/list row component.
- [ ] Reset/default state policy.

## 7.2 Connection settings
- [ ] Auto Connect.
- [ ] Smart Connect default behavior.
- [ ] Kill Switch setting/status.
- [ ] Network change behavior Wi-Fi ↔ Cellular.
- [ ] Startup/reboot behavior در صورت supported.
- [ ] DNS options فقط اگر runtime contract واقعی expose شود.
- [ ] Protocol preference فقط اگر contract واقعی expose شود.

## 7.3 Appearance
- [ ] Theme: System / Light / Dark.
- [ ] Language/locale surface طبق Scope قطعی محصول.
- [ ] Reduce Motion preference اگر app-owned.
- [ ] Reduce Transparency preference اگر app-owned.

## 7.4 Privacy
- [ ] Analytics consent.
- [ ] Diagnostics consent/preferences.
- [ ] Privacy summary.
- [ ] Clear local non-sensitive UI cache در صورت supported.

## 7.5 About
- [ ] App version/build.
- [ ] Update status.
- [ ] Official website/Telegram/support links.
- [ ] Open-source licenses entry.
- [ ] Privacy Policy entry.
- [ ] Terms entry.

---

# 8) Devices / Device Binding

- [ ] My Devices screen.
- [ ] Current device highlight.
- [ ] Other registered devices list.
- [ ] Device name/type.
- [ ] Safe platform/OS metadata.
- [ ] Last activity در حد privacy policy.
- [ ] Active/revoked status.
- [ ] Device limit summary.
- [ ] Device limit reached state.
- [ ] Device Detail sheet/page.
- [ ] Revoke device action.
- [ ] Revoke confirmation dialog.
- [ ] Revoke processing state.
- [ ] Revoke success state.
- [ ] Revoke failure/retry state.
- [ ] Cannot revoke current/last device policy feedback در صورت applicable.
- [ ] Device Binding explanation surface.

---

# 9) Notifications

- [ ] Notification Center screen.
- [ ] Read/unread state.
- [ ] Badge count.
- [ ] Subscription expiry notification item.
- [ ] Renewal/purchase success item.
- [ ] Payment failure item.
- [ ] Maintenance item.
- [ ] Forced security update item.
- [ ] Support/ticket reply item در صورت contract.
- [ ] Marketing/promo item فقط با opt-in.
- [ ] Notification empty state.
- [ ] Notification loading/error state.
- [ ] Mark as read/all read actions.
- [ ] Notification preferences screen.
- [ ] Category toggles بر اساس permission/policy واقعی.
- [ ] Android notification permission pre-prompt surface برای نسخه‌های لازم.
- [ ] Permission denied/settings guidance state.

---

# 10) Profile / Account / Security

## 10.1 Already completed
- [x] Profile full Stitch visual port.
- [x] Account summary card.
- [x] Active service count.
- [x] Service cards with traffic/device/expiry/protocol metadata.
- [x] Enterprise status/bug report/diagnostics integration.

## 10.2 Pending
- [ ] Telegram linked/unlinked status integrated into Profile.
- [ ] Wallet entry integrated into Profile.
- [ ] Transactions entry integrated into Profile.
- [ ] Settings entry integrated into Profile.
- [ ] Devices entry integrated into Profile.
- [ ] Notifications entry integrated into Profile.
- [ ] Security/session status card.
- [ ] Logout entry + confirmation.
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
- [ ] Dedicated Subscription Detail page/sheet.
- [ ] Plan/tier detail.
- [ ] Renewal date/status.
- [ ] Auto-renew status در صورت billing model.
- [ ] Renew action.
- [ ] Upgrade action.
- [ ] Downgrade action در صورت supported.
- [ ] Cancel action در صورت supported.
- [ ] Confirm plan change dialog.
- [ ] Confirm cancel dialog.
- [ ] Expired service state.
- [ ] Revoked service state.
- [ ] Refunded service state.
- [ ] Pending activation state.
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
- [ ] VPN permission explanation pre-surface.
- [ ] Permission denied guidance.
- [ ] Permission permanently denied → Android Settings guidance.
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
- [ ] Confirm purchase sheet/dialog.
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

- [ ] Welcome screen.
- [ ] Brand/value proposition کوتاه.
- [ ] Free access explanation.
- [ ] Premium explanation بدون dark pattern.
- [ ] Privacy summary.
- [ ] Continue as Guest / Start Free path.
- [ ] Optional Login with Telegram path.
- [ ] VPN permission preparation step فقط قبل از نیاز واقعی.
- [ ] First server selection guidance.
- [ ] First connection guidance.
- [ ] Skip policy.
- [ ] Onboarding completed persistence.
- [ ] Re-show onboarding/help entry در صورت نیاز.
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
- [ ] Standard Liquid Dialog component.
- [ ] Destructive Confirmation Dialog.
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
- [ ] Copy-to-clipboard feedback component.

## 20.2 Required concrete sheets/dialogs
- [ ] Telegram Login pre-confirmation sheet.
- [ ] Logout confirmation dialog.
- [ ] Revoke Device confirmation dialog.
- [ ] Server Detail sheet.
- [ ] Premium Server Upgrade sheet.
- [ ] Plan Detail sheet.
- [ ] Purchase Confirmation sheet/dialog.
- [ ] Transaction Detail sheet.
- [ ] Add Balance confirmation/result sheet.
- [ ] Subscription cancel/change confirmation.
- [ ] Session expired dialog/banner.
- [ ] Notification permission pre-prompt.
- [ ] VPN permission pre-surface.

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
- [ ] Permission-required state.
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
- [ ] Radio selection component.
- [ ] Switch/toggle component.
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

| Area | Baseline status (2026-08-30) |
|---|---:|
| Foundation / Brand / Main shell | High |
| Home | High |
| Servers basic | High |
| Connect main | High |
| Store basic | High |
| Profile basic | High |
| Enterprise Bug/Diagnostics | High |
| Telegram Login final UX | Low / P0 pending |
| Wallet | Not implemented in new UI |
| Transactions | Not implemented in new UI |
| Settings | Not implemented in new UI |
| Devices | Not implemented in new UI |
| Notifications | Not implemented in new UI |
| Subscription detail flows | Partial |
| Advanced server UX | Partial |
| Connect micro-states | Partial |
| Purchase micro-flows | Partial |
| Onboarding | Not finalized |
| Referral/Promotions | Not finalized |
| Full Support/Tickets | Partial |
| Dialog/Bottom Sheet system | Partial |
| Physical-device final QA | Pending |
| **Overall** | **54%** |

## Mandatory update format after every Agent batch

Agent باید انتهای PR/commit summary این چهار خط را به‌روز کند:

```text
UI Ledger sections touched: <IDs>
Leaf items completed: <list>
Remaining unchecked items in touched sections: <count/list>
Overall UI completion: <N>% (100% forbidden until Final Gate is all checked)
```

این Ledger باید همراه کد تکامل پیدا کند؛ حذف checkbox برای پنهان‌کردن کار باقی‌مانده ممنوع است. اگر Scope رسمی تغییر کرد، ابتدا Scope canonical docs اصلاح شود و سپس آیتم با دلیل مشخص `Deferred by product scope` شود؛ هیچ Agentی حق ندارد مستقل از Product Scope آیتم را نادیده بگیرد.
