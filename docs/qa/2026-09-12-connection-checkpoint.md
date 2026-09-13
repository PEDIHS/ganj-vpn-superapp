# Connection completion checkpoints

Source of truth for deployed APK: server `/opt/ganj-vpn/apk/ganj-vpn-alpha.apk`.
Baseline verified: Alpha 0.3.4, `ce1d46a`, SHA-256 `b078403343b2057f9c88a1041b6a5d83ecd10ab1ac7a7fa8c1e6fbdacf50a546`.

## Checkpoint 1: packaged native runtime

- Alpha resolved transitional Maven libXray 26.6.27, but the runtime adapter requires protected `setDNS`, introduced in the locked official 26.7.28 API. Missing method makes `installSocketProtector` fail closed.
- Alpha CI now builds/reuses the repository-pinned official AAR, verifies source/provenance checksums, and resolves it exclusively through the existing Gradle init script.
- Added an Android instrumentation check against the actual packaged native classes, in addition to startup screenshots. Unit-test stand-ins cannot validate native API presence.
- Candidate version: 0.3.5-alpha / 8. Build and native instrumentation remain pending until CI completes.

## Checkpoint 2: connection lifecycle and real config latency

- Fixed accumulated native socket-protector callbacks across reconnects; upstream registration appends callbacks.
- Disconnect awaits the actual binder operation; UI reconciles with the live foreground service.
- Network callbacks exclude VPN networks. Config switching uses core reconnect over the existing TUN.
- OS consent is requested before fetching an expiring connection lease. Denial remains retryable.
- Per-config latency uses pinned native `pingBatch` with only the authenticated proxy outbound. No TCP-only or synthetic result is shown. Temporary native input is private, owner-only and removed in `finally`; profile credentials are destroyed.
- Config-list probes update individually with bounded concurrency; active-config latency refreshes every 15 seconds only while the connection screen is resumed. Smart Connect chooses the fastest successful measurement.
- Added fail-closed/cancellation probe tests and emulator consent/TUN/VLESS/probe/disconnect/reconnect checks. Packaged socket/DNS contract and cold startup have passed; complete TUN test is still pending.
- API 24 has now passed real OS denial/approval and native TUN startup. The native latency request reached the loopback VLESS fixture; updated that fixture to support libXray's actual HEAD measurement request as well as the GET traffic marker.

## Remaining release gate

- API 35 root cause captured on `91055c3`: upstream TUN config omitted `name`, causing `GetAvailableTunName()` to call `net.Interfaces()` and fail with `netlinkrib: permission denied` on modern Android. Compiler now provides an explicit logical name; Android's already-established TUN FD remains the real interface. This requires no extra device privilege or runtime permission.
- Network-state permission is declared by the Control API library; static-analysis fallback now passes.

- Commit `b2a6f5a32ce61855463c843d3ae7abb222247aab`, Android CI run `34752657598`: API 24 passed both instrumentation tests, including real OS consent, native profile latency, TUN traffic, config switching, two disconnect/connect cycles. API 35 rejected native startup and remains under investigation. This is not yet a release approval.

- Deploy only a verified final ARM64 APK; physical user-device Telegram/service/VPN E2E remains a separate gate.
