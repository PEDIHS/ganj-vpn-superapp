# Stitch Persian UI checklist

## Completed

- [x] Persian-only RTL application shell
- [x] Persian-only advertised locale
- [x] Persian default Android resources for Activity, VPN service and notifications
- [x] Emerald / refined-gold Liquid Glass theme
- [x] Floating five-tab navigation: خانه / سرورها / اتصال / فروشگاه / پروفایل
- [x] Elevated center Connect navigation
- [x] Official supplied Ganj logo embedded as an Android resource
- [x] Stitch-derived Connect screen wired to real state/actions
- [x] Home full visual port
- [x] Servers full visual port with real search and Free/Premium filters
- [x] Store full visual port wired to real catalog/checkout
- [x] Profile full visual port preserving Enterprise, bug report and diagnostics
- [x] Shared Loading / Empty / Auth / Error visual states
- [x] Full-screen forced-update and maintenance gates
- [x] Persian numeral helper
- [x] Unicode BiDi isolation for technical LTR values inside RTL UI
- [x] Responsive policy locked for 360 / 390 / 412 / 430dp reference phones
- [x] Unit tests added for Persian formatting and responsive breakpoints
- [x] No fabricated ping/speed telemetry
- [x] Static review fixed cross-file responsive-helper visibility issue

## Pending external validation / inputs

- [ ] GitHub Actions runner must execute Android CI successfully before merge
- [ ] Embed Vazirmatn Variable TTF once the binary is available in-repository or uploaded directly
- [ ] Add live ping / download / upload only after a truthful telemetry contract exists in the VPN/Xray runtime
- [ ] Final device screenshot pass on at least one 360dp-class and one 412–430dp-class Android phone

## Merge rule

Do not merge this UI branch while Android CI is terminating before runner assignment. Current failed runs have `runner_id=0` and no executed steps, so they do not provide compile/lint evidence.
