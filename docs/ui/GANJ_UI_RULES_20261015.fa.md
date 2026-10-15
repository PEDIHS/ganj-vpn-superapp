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
