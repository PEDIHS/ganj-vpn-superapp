# Ganj VPN — Happ / v2rayNG / V2Box feature parity and connection recovery

Status: Engineering implementation plan. **Not** a declaration that any unsupported protocol or UI feature is shipped.

## 0. Current release blocker: service listing != working VPN

In `main`, `GanjCompositionOwner.Factory` creates the composition with
`connectionContext = ConnectionProfileContextProvider { null }`.
Consequently the normal `GanjController.prepareConnection` path fails closed
at `connection.context_unavailable`. PR #38 already contains the first
`AndroidConnectionProfileContextProvider` and explicit server-selection path.
Finish its review, integration and real-device proof instead of adding another
hard-coded fallback or bypassing the ownership/device-proof checks.

The client must complete all of the following *in order*:
1. Valid guest/linked user session and registered device identity.
2. Entitlement-owned server inventory; explicit server selection or eligible Smart Connect candidate.
3. Fresh device proof for the exact service ID, server ID, body and API path.
4. Server-side entitlement, PasarGuard live state and device-limit verification.
5. A device-bound GVP1 encrypted, short-lived connection profile.
6. Strict payload validation and supported-protocol compilation.
7. Android `VpnService` permission and established TUN with a valid FD.
8. Official `libXray` socket protection and protected DNS registration.
9. Native core startup **and** `getXrayState.running == true`.
10. A real traffic-path probe via the selected proxy/tunnel; only then claim Internet reachability.

**TUN contract correction:** the pinned official libXray v26.7.28 explicitly
REMOVED `SetTunFd`. The supported mechanism is the root
`env["xray.tun.fd"]` in the JSON passed to `runXrayFromJson`.
Do not fork libXray or inject a non-existent `setTunFd` API.
Do not treat native startup, raw TCP socket reachability or locally generated
TUN ICMP replies as successful proxy egress.

## 1. Implementation and licensing policy

- Happ Android's public repository ships distribution metadata and binaries,
  not an available full implementation/license grant. Independently implement
  UX behavior; do not copy proprietary source, assets or trademarks.
- 2dust/v2rayNG is GPL-3.0. Borrow behavior, protocols and architecture as
  references. Copy code only under an explicitly approved GPL-3.0-compliant
  distribution model (source obligations included).
- An unrelated open-source repository named `v2box` is not the official
  commercial V2Box app. Its source cannot be passed off as official code.
- Reuse auditable, independently licensed official XTLS/libXray/Xray-core,
  maintaining the pinned build, SBOM and MPL notices.
- Android retains the managed-account/entitlement-only security invariant
  unless the product owner explicitly approves a separate manual-config
  product mode and corresponding security/privacy change.

## 2. Protocol and transport support (advertise only executable combinations)

| Family | Transport/capabilities | Current main | Target implementation |
| --- | --- | --- | --- |
| VLESS | TCP/TLS, REALITY, Vision, WS, gRPC | Typed subset | Live E2E per combination |
| VLESS | XHTTP, compatible newer Xray transports | Missing profile schema | Version-gated backend and Android schema |
| VMess | TCP, WS, gRPC, TLS | Typed subset | Real configuration/handshake tests |
| Trojan | TLS with supported transport | Typed subset | Real handshake tests |
| Shadowsocks | Approved classic and 2022 ciphers | Typed subset | Real UDP and TCP tests |
| SOCKS5 / HTTP | Authorized managed upstreams | Not in profile contract | New credential/transport schema and policy |
| WireGuard | UDP, route/DNS/device policy | Not in runtime profile | Evaluate isolated, licensed engine |
| Hysteria2 / TUIC | QUIC, congestion/UDP | Not in runtime profile | Evaluate isolated sing-box adapter |
| SSH / DNSTT / other V2Box features | Separate runtimes | Not implemented | Independently evaluated after core release |

Xray and sing-box native Go runtimes must **not** be loaded as separately
compiled Go libraries in one OS process. If a second Go-based engine is required,
use a separate, explicitly managed OS process with its own lifecycle, socket
protection, interface ownership and security review. Only one selected VPN
engine owns the TUN at a time. Do not promise all protocols until proven on
the actual Android build and backend catalog.

## 3. Client screens and controls

**Connect:** primary connect/disconnect, chosen account/service, server/country,
protocol label, state and failure reason, upload/download bytes and current
speed from measured runtime data, connection duration, reconnect/failover,
persistent VPN notification and last successful network probe.

**Servers/configurations:** safe node metadata rather than raw credentials:
provider, location, entitlement, protocol/transport/TLS, last test time,
latency, availability, favorite, grouped country/service/subscription view,
search/sort/filter and per-server test result. Raw config stays within the
device-bound encrypted broker/runtime boundary.

**Smart Connect:** test only eligible nodes. Measure real proxy-path HTTP
latency with bounded parallelism; keep loss, jitter, last verified success,
load/capacity and network-specific historical results. Apply hysteresis and
cooldown to avoid server flapping. If no verified candidate is reachable, show
an honest failure instead of a fake ping or unverified "best" label.

**Diagnostics:** distinguish (a) list/catalog success; (b) guest/login
failure; (c) entitlement/ownership; (d) unavailable PasarGuard binding;
(e) unsupported protocol/transport; (f) GVP1 decrypt/device proof;
(g) tunnel FD/VpnService permission; (h) native core startup;
(i) DNS/bootstrap; (j) proxy egress/UDP. Report stable safe error codes
and request ID only; never log credentials, endpoint secrets, tokens,
raw user configurations or traffic destinations.

## 4. Ping/health architecture

- `libXray` has `pingBatch`, but its documented lifecycle excludes
  running the same managed Xray instance simultaneously. Do not interrupt
  the active user's tunnel merely to ping every server.
- Disconnected: use an isolated probe engine or bounded server-mediated
  health, with short-lived valid provisioned profiles as needed.
- Connected: use measured proxy-path probe through the active tunnel;
  passive connection-quality samples can supplement but do not replace it.
- Label TCP port availability distinctly from an actual egress result.
- The server catalog should not disclose private provider credentials.

## 5. Delivery gates

P0: Merge/finish PR #38 server-selection and real device-proof flow; paid
PasarGuard bindings; GVP1; verify exact working paid/free profiles; native
readiness plus tunnel egress and DNS/IPv6 leak tests; actionable safe errors.

P1: Protocol/transport matrix, bounded batch ping, Smart Connect, favorites,
service/country grouping, speed/traffic telemetry and Android foreground UX.

P2: Advanced split-tunnel/domain rules, custom DNS, app-based routing,
subscription refresh behavior, optional alternate engines and extended
protocols. Check Play policy and license obligations for each release flavor.

Release requires CI, instrumented Android VPN tests and real operator/network
samples. Neither a green mock-based unit test nor a listed server is sufficient
evidence that a user can browse through the VPN.
