# Ganj VPN — UI/UX Design Handoff

This branch is a design handoff snapshot for AI/UI tools.

## Product goal
Ganj VPN is a hybrid free + premium VPN super app connected to the existing Telegram commerce ecosystem.

## Primary mobile flows
- Onboarding
- Authentication / account linking
- Home / VPN connect state
- Free & premium server list
- Smart Connect
- Favorites
- Country/location filtering
- Subscription purchase, renewal, upgrade/downgrade
- Speed test
- Profile & devices
- Notifications
- Settings
- Support, diagnostics, ticketing, bug report

## Design direction
- Modern iOS-inspired visual language while remaining practical on Android
- Strong hierarchy, generous spacing, high-quality typography
- Bottom tab navigation
- Central VPN connect interaction
- Clear free vs premium differentiation without aggressive paywall patterns
- Light and dark mode
- Responsive layouts for compact and large Android phones
- Reusable design system, components, tokens, states and variants
- Consistent empty/loading/error/offline/success states
- Accessibility-aware contrast and touch targets
- Avoid generic AI dashboard aesthetics and excessive glass/gradient effects

## Important engineering constraint
Do not redesign product behavior blindly. Existing code, domain models, APIs and flows in this repository are the source of truth. Design should improve presentation and UX while preserving functional contracts unless a UX change is explicitly documented.

## Deliverables expected from designer/AI
1. Information architecture
2. UX flow map
3. Design tokens
4. Reusable component library
5. Complete mobile screen set
6. Light + dark themes
7. Interactive states and transitions
8. Developer-ready specifications
9. Mapping from proposed screens/components to existing implementation areas

## Notes
Generated build outputs, caches, local secrets and machine-specific files should not be considered part of the design source of truth.
