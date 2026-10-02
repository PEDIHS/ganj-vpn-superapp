# Ganj VPN — Happ-inspired feature parity and real-connection gate

**Scope:** independent Ganj implementation; Happ Android repository publishes APKs and a README, not licensed Android application source. Do not copy proprietary APK code, UI assets, package names or branding.

## First release blocker: packets must actually traverse the VPN

1. An eligible account receives an entitlement-scoped, short-lived GVP1 sealed profile.
2. Android decrypts the device-bound profile and validates protocol, transport and TLS/REALITY.
3. VpnService establishes the TUN and passes its live FD to the gomobile SetTunFd bridge, not to an unrecognized JSON env field.
4. Register the dialer/socket protector; start the native Xray instance.
5. Check getXrayState and run an **actual tunneled HTTPS probe** before claiming internet access. A successful native start alone is insufficient.
6. Test DNS, IPv4, IPv6, UDP, process restart, connectivity changes and safe recovery on physical devices.
7. Fail closed on missing setter, unsupported profile, expired entitlement or failed socket protection.

The TUN FD bridge and core-state check are introduced on branch fix/android-real-tun-binding-happ-parity. Packet-level E2E still requires a native candidate built with the pinned source extension and a real device/server. Debug builds that resolve the old transitional AAR must not be distributed as VPN-enabled release candidates.

## Protocol and transport capability matrix

| Protocol | Current Ganj main | Target / evidence required |
|---|---|---|
| VLESS TLS/REALITY | Typed baseline | Real-device TCP/WS/gRPC and XHTTP compatibility scenarios |
| VMess | Typed baseline | Real-device TCP/WS/gRPC and any approved XHTTP scenario |
| Trojan | Typed baseline with TLS | Real-device TCP/WS/gRPC and any approved XHTTP scenario |
| Shadowsocks | Typed baseline, restricted methods | Verified cipher compatibility and UDP scenarios |
| SOCKS | Not supported in entitlement profile | Separate approved source/credential contract and native outbound |
| HTTP proxy | Not supported | Only if explicitly product-approved and supported by pinned core |
| WireGuard | Not supported by current profile schema | Separate engine/backend/provider integration and mobile app compatibility tests |
| Hysteria2 / TUIC | Not supported by current Xray profile | Separate audited implementation or feature gate; never label as supported without proof |

Neither Happ's README nor a generic Xray feature list proves that the pinned Ganj binary supports every combination.

## Managed server and configuration list

- List only servers and nodes entitled to the user and currently supported by the runtime.
- Group by country, service, protocol, subscription; expose name, region, usage, expiry and non-sensitive transport details.
- Never expose raw subscription URLs, passwords, UUIDs or private keys through catalog/API/logs.
- Show favorites, selected server, last check time, usable/unusable and a specific reason.
- Server IDs must remain stable across bot synchronization; unsupported nodes must not appear as connectable.

## Meaningful latency and smart connect

- ICMP against a gateway is not a proxy-connection test.
- Separate `reachability` (DNS/TCP/TLS), `tunnel_latency` (HTTP request through the actual outbound), `loss` and `backend_load`.
- A probe must have a bounded timeout, cancellation, network/measurement timestamp and fail state. Never fabricate a 0ms or green ping for missing data.
- Batch testing must use a bounded concurrency pool, no long-lived raw credentials and a trusted provider-side profile broker.
- Rank only supported, entitled and recently verified nodes; select manually or by measured latency/loss/stability and optional region preferences.
- Use hysteresis to prevent frequent flapping; changing servers must preserve traffic safety and be cancelable.

## Connection-screen feature requirements

- One prominent connect/disconnect control; visible connecting, verifying, connected, reconnecting, failed states.
- Selected node and country, server list shortcut, manual/automatic selection, last tested latency.
- Live upload/download and total transferred bytes from **actual** runtime counters, never estimated values.
- Current subscription usage and expiry from the authoritative provider.
- Connection duration, retry reason, clear error, support/diagnostics shortcut.
- Optional privacy-safe routing controls, split tunneling and DNS policy under Advanced settings.
- Accessibility: Persian RTL, English LTR, TalkBack, reduce motion and reduced transparency.

## Release acceptance gates

- No false CONNECTED without native core state and an optional verified end-to-end tunneled request.
- At least one successful real session for every advertised protocol/transport combination.
- Working gateway/proxy exit-IP, HTTPS/DNS and UDP smoke tests.
- No cleartext fallback or IPv6/DNS leak after failure, network handover or reconnect.
- Measured ping and Smart Connect results cross-checked against actual tunnel outcomes.
- APK/AAB built with pinned, auditable native extension; licensing, signing and SBOM evidence included.
- CI/test, physical-device runs and actual deployment evidence attached before merge/release.
