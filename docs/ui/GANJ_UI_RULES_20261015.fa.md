# GANJ Android — UI Product Rules (2026-10-15)

## Scope
Apply to the existing Kotlin/Jetpack Compose app across connection, store, VIP upgrade,
profile, settings, subscriptions, server/config selection, and every sheet/dialog/state.
This document extends the canonical brand and liquid-glass standards.

## Hierarchy
- Neutral surfaces dominate. Emerald is a restrained active/secure status color;
  refined gold is reserved for Smart Connect, premium actions and selected side tabs.
- No wall-to-wall saturated green, permanent neon glow, heavy full-screen blur,
  generic AI-looking gradients, or decorative complexity.
- RTL bottom glass navigation: Store **right**, Connect **center**, Profile **left**.
  Home and Subscriptions are NOT primary tabs; subscription/server selection remains
  available from the Connect screen; Settings is available through Profile.
- The primary connect control stays prominent and above subscription/server selectors.
  Smart Connect stays above manual selection, with a real subscription-scoped
  latency result (never mocked). When no subscription is active, route to selection.
- Failed native tunnel status must show a red state and the available real error
  directly beneath the connection hero, with a functioning retry action.
- Premium Upgrade, Store, Profile and all secondary pages must share the same
  surface, type, spacing, motion and accessibility grammar.

## Engineering gates
- Never change profile decryption, entitlement, server identity, native VPN state,
  session timers, auth, billing or API response contracts for cosmetic work.
- Respect reduced motion/transparency, low RAM, touch targets >= 48dp and
  readable Persian on light and dark themes; no unbounded animated shaders.
- Keep screen state/restoration, input focus, overlays and back navigation working.
- Use actual backend data; do not invent usage, payments, speed, server or error data.
- Before release: unit + Compose instrumentation + debug/release lint + native VPN
  integration tests, reproducible signed artifact and in-place upgrade test.
  Preserve the deployed APK and rollback copy until those checks pass.
- UI progress only via docs/ui/UI_100_PERCENT_PROGRESS.fa.md calculator;
  do not claim 100% or production QA on emulator evidence alone.


## 2026-10-15 Store, VIP and Profile implementation
- Store tier filters only reflect the live backend catalog; VIP spotlight is absent
  when no VIP plan exists.
- VIP upgrade opens a real-plan sheet with prices, traffic, devices and benefits
  sourced from the current catalog and uses the same purchase confirmation path.
  Do not introduce invented VIP benefits or client-granted entitlements.
- Disable duplicate buy taps during Pending checkout and route back to the
  existing checkout and backend verification flows.
- Profile presents status/usage from real entitlements, followed by working
  Subscriptions and Settings shortcuts; Telegram, wallet, transactions, devices,
  selected subscription and support sections remain connected.
- A screenshot or source commit is not proof of functional QA; retain all open
  UI ledger gates until emulator/physical-device tests support completion.

## Bot-owned storefront (2026-10-15)
- Main Store destination renders the real bot-backed web catalog at
  https://bot.pedramhs.ir/site/api.php?action=products, not the empty
  Control API /v1/store/plans list.
- Select a location/panel first, then its real categories; product IDs, names,
  price in Toman, period and volume must originate unchanged from the bot.
- Catalog is a public retail view, so account-specific reseller rates and
  discount eligibility are not implied by its displayed base price.
- Product purchase opens the exact product and panel at the official
  Ganj website; website authentication, final pricing, purchase, wallet,
  activation and restrictions remain the bot's source of truth.
- Do not inject bot credentials into the APK. Never silently display cached
  or unrelated plans when the live catalog fails.
- Next-phase: a server-mediated personalized catalog with linked-account role,
  first-purchase rules and verified checkout in Android; do not claim that the
  public catalog alone accomplishes that.

## End-to-end bot finance (2026-10-15)
- The native Store product button routes to the exact linked product inside the
  embedded, trusted https://bot.pedramhs.ir/site/ origin.
- The Profile wallet and transaction shortcuts open the canonical bot web
  account, not a second financial ledger. No app-side payment approval.
- Existing site routes manage product quote, wallet purchase, card invoice
  creation, displayed recipient card, image/PDF receipt upload, Telegram admin
  message, smart/manual verification, and transaction history.
- The Android payment container enables a system file picker, keeps secure web
  session cookies, opens external gateways/Telegram through Android intents,
  denies cross-origin embedded navigation and WebView geolocation/permissions,
  disallows cleartext/mixed content, and observes the authenticated
  payment_status endpoint to display state changes in an in-app dialog.
- Server handles real-time authority. In-app status checks are foreground
  polling and do not constitute background push or Android OS notifications.
- First-time WebView users may need the website's existing Telegram account
  linking. Never export Android auth secrets to the WebView or fabricate SSO.
- DO NOT publish until Android CI/emulator and payment receipt end-to-end
  checks are verified with safe non-monetary test data.

## Verified app-to-bot single sign-on contract
- The Android linked account bearer remains in native memory and travels over
  HTTPS POST only to /site/api.php?action=android_sso with same-session CSRF.
- PHP asks the local Control API to validate the short-lived, device-bound JWT,
  reads ONLY the Telegram subject already associated with that server-side
  authenticated account, checks an active retail user row, rotates the web
  session, and returns new HttpOnly session cookies.
- No client-provided username/Telegram ID, no secret in JavaScript or URL,
  no app-issued admin permissions, no wallet mutations, and no representative
  pricing support. App logout also expires WebView site cookies.
- CI tests exercise subject ownership and deny unlinked/invalid IDs. Production
  end-to-end positive test requires an authorized linked Telegram test account.

## 2026-10-15 visual unification pass
- Active product surfaces must use the shared GANJ controls for primary/secondary actions,
  selection pills, text fields, compact loading, back actions and inline states. Avoid raw
  Material buttons/chips/dialogs in active screens unless they are an implementation detail
  behind a GANJ wrapper.
- Store, bot-owned finance, wallet, devices, profile, subscription details, Telegram account,
  support, notifications and settings must share the same 20–26dp surface radius family,
  restrained emerald/gold accents, neutral canvas, press-scale feedback and status pills.
- Motion is short and functional: roughly 120ms press feedback and 240–280ms state/page
  transitions. Infinite motion is limited to bounded loading indicators and must stop when
  reduced-motion or lifecycle policies require it.
- Error/payment states use the same liquid message hierarchy and never render legacy Material
  alert dialogs over otherwise custom GANJ surfaces.
- Web checkout remains visually framed by native GANJ chrome, while the website body remains
  canonical and unmodified; no CSS injection or app-side financial state is introduced.

## 2026-10-15 — Approved preview fidelity contract (supersedes cosmetic approximations)
- Visual reference: `ganj-profile-complete-review.html` reviewed in the product conversation.
  Reference data is NEVER production data; catalog, finance and account authority remain real.
- Canvas `#111718`, content `#1A2324`, emerald accent `#39E5A8`, refined gold `#E0BE79`.
- The primary connection face is a **circle**, 212dp within a 250dp orbital allocation at
  standard text scale. At large fonts the circle grows within the actual available width.
  A rounded square / tunnel-core replacement is prohibited without new product approval.
- Power icon, state label and actual session duration live INSIDE the same circular hit area.
  Disconnected: «برای اتصال کلیک کنید»; connected: «متصل هستید» + monotonic native timer;
  unknown start time stays unavailable. No duplicate timer card beneath the control.
- Failure is a red face with a finite 440ms shake and broken red orbital arcs. Native error
  text sits immediately below it. Never generate fake success or allow duplicate busy taps.
- Smart Connect is a compact 55dp-minimum gold ribbon above manual subscription/server
  selection, with one bounded 1200ms sheen. Its existing real probe/selection handler remains.
- Bottom navigation is physically Store/right, Connect/center, Profile/left in RTL. Draw a
  concave continuous wave around a raised 62dp center lens; a flat capsule is not equivalent.
  One interruptible spring lens travels between side tabs. Keep >=48dp semantic Tab hit areas.
- Main content cards use 12–18dp radii, thin borders and 12–20dp padding. Do not substitute
  oversized 24–28dp cards or pill-shaped actions throughout the app for the reference.
- Store uses compact selectable product cards, actual available period/location filters,
  one selected-order summary and one purchase CTA. VIP is a dedicated gold/emerald view
  only when real catalog VIP products exist. Never invent devices/benefits/prices to match a mock.
- Profile opens on real identity, selected-subscription usage, finance and grouped menu rows;
  full services, account management and diagnostic forms open as secondary content rather than
  occupying the whole overview. Bot financial pages keep their current trusted SSO/receipt route.
- Default fresh-install appearance is Dark for this reference. Explicit saved Light/System
  preferences remain supported and every secondary page inherits the same component geometry.
- Screenshots from the actual Android renderer (Connect/off/on/error, wave navigation, Store,
  VIP, Profile and large text) must be inspected before publication. A passing build alone is
  not evidence that the approved visual has been faithfully implemented.
