# Connection completion checkpoints

Source of truth for deployed APK: server `/opt/ganj-vpn/apk/ganj-vpn-alpha.apk`.
Baseline verified: Alpha 0.3.4, `ce1d46a`, SHA-256 `b078403343b2057f9c88a1041b6a5d83ecd10ab1ac7a7fa8c1e6fbdacf50a546`.

## Checkpoint 1: packaged native runtime

- Alpha resolved transitional Maven libXray 26.6.27, but the runtime adapter requires protected `setDNS`, introduced in the locked official 26.7.28 API. Missing method makes `installSocketProtector` fail closed.
- Alpha CI now builds/reuses the repository-pinned official AAR, verifies source/provenance checksums, and resolves it exclusively through the existing Gradle init script.
- Added an Android instrumentation check against the actual packaged native classes, in addition to startup screenshots. Unit-test stand-ins cannot validate native API presence.
- Candidate version: 0.3.5-alpha / 8. Build and native instrumentation remain pending until CI completes.

## Next checkpoints

- Fix accumulated native socket-protector callbacks across reconnects; upstream registration appends callbacks.
- Await actual VPN service disconnect completion and reconcile UI with live service state.
- Verify Android VPN consent and a real TUN-to-VLESS traffic path in an isolated emulator fixture.
- Measure config latency through each authorized provisioned outbound, publish only numeric/status results to UI, cancel stale work and avoid exposing profiles.
- Deploy only a verified final ARM64 APK; physical user-device Telegram/service/VPN E2E remains a separate gate.
