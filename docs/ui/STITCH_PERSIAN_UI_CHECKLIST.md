# Stitch Persian UI checklist

## Completed UI scope

- [x] Persian-only RTL application shell
- [x] Persian-only advertised locale
- [x] Persian default Android resources for Activity, VPN service and notifications
- [x] Emerald / refined-gold Liquid Glass theme
- [x] Theme-aware Premium / Warning contrast for light and dark surfaces
- [x] Persian-tuned typography scale and zero letter-spacing for RTL text
- [x] Floating five-tab navigation: خانه / سرورها / اتصال / فروشگاه / پروفایل
- [x] Elevated center Connect navigation with selected-state motion
- [x] Bottom-navigation responsive gutters sourced from the central responsive policy
- [x] Official supplied Ganj logo embedded as an Android resource
- [x] Official brand mark used in Connect hero, header, Profile and launcher surface
- [x] Branded Android window chrome and Android 12+ splash surface
- [x] Stitch-derived Connect screen wired to real state/actions
- [x] Home full visual port
- [x] Decorative Home emoji replaced by the shared vector icon language
- [x] Servers full visual port with real search, Persian country matching and Free/Premium filters
- [x] Smart/quick-connect visual action uses the shared Connect vector icon rather than emoji
- [x] Store full visual port wired to real catalog and dedicated Liquid checkout states
- [x] Store cards expose real plan duration, traffic limit and device limit from `PlanUiModel`
- [x] Premium/gold CTA contrast corrected
- [x] Profile full visual port preserving real service, Enterprise, bug-report and diagnostics actions
- [x] Profile service cards expose real remaining traffic, device limit, expiry and allowed-protocol metadata
- [x] Enterprise status, bug report and diagnostics moved to the same Liquid component language
- [x] Shared Loading / Empty / Auth / Error visual states
- [x] Full-screen forced-update and maintenance gates
- [x] Persian numeral helper
- [x] Unicode BiDi isolation for technical LTR values inside RTL UI
- [x] Responsive policy locked for 360 / 390 / 412 / 430dp reference phones
- [x] Unit tests added for Persian formatting and responsive breakpoints
- [x] Reduce-motion and reduced-transparency policies preserved
- [x] No fabricated ping/speed telemetry
- [x] No fake Settings / Notifications / Device-management actions added without a real presentation contract
- [x] Static diff review confirms the branch changes remain scoped to Android UI/resources/tests/docs
- [x] Static review fixed cross-file responsive-helper visibility issue

## Intentionally pending external inputs

- [ ] Embed Vazirmatn Variable TTF when the font binary can be transferred directly into the repository. Until then the app uses the Persian-tuned typography scale with the device sans fallback.
- [ ] Add live ping / download / upload only after a truthful telemetry contract exists in the VPN/Xray runtime.
- [ ] Final physical-device screenshot pass on at least one 360dp-class and one 412–430dp-class Android phone when a build is available.

## Validation note

GitHub Actions validation is intentionally paused while the repository Actions quota is exhausted. Do not spend Actions quota on this UI pass. Source UI work is reviewed through static repository inspection and diff review until build validation is available again.
