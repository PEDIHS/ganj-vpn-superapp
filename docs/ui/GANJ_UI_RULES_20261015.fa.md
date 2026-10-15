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
