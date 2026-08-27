# ADR-0005: Production Android uses a pinned official-source libXray build

- Status: Accepted
- Date: 2026-08-24

## Context

The transitional `core:xray-runtime` dependency resolves an exact third-party Maven artifact for
development convenience. An exact Maven version is not enough to prove that its source, toolchain
and native binaries correspond to the official XTLS repository. Shipping it in Production would
leave a supply-chain and licensing evidence gap.

The official `XTLS/libXray` Android build script also resolves `gomobile@latest` and downloads
latest Geo assets during its preparation phase. Those moving inputs cannot be accepted silently in
a reproducible production gate.

## Decision

Production release CI must:

1. clone only `https://github.com/XTLS/libXray.git` at Tag `v26.7.28`;
2. verify dereferenced HEAD equals `80263da83e96b2972455b0a94b13ee1a10e51391`;
3. verify source and license hashes from [`UPSTREAM.lock.json`](../../UPSTREAM.lock.json)؛
4. use Go `1.26.3`, the `gomobile` module pseudo-version already pinned by that upstream commit,
   Android API 21 and NDK `29.0.14206865`؛
5. run `go mod download`, `go mod verify`, upstream Go tests and the official Android gomobile bind
   command with 16 KiB linker alignment؛
6. avoid the nondeterministic `@latest`/Geo-download preparation path; Geo databases are runtime
   service assets and are not embedded in this binding؛
7. expose the rebuilt AAR only through an ephemeral, exclusive Gradle repository that overrides
   the transitional coordinate for the Production build؛
8. compare every ABI native-library checksum from the official AAR with the final AAB؛
9. attach AAR/source/license/module/toolchain hashes to release evidence.

No prebuilt AAR is committed. Missing toolchain, upstream mismatch, network checksum failure,
unverified license, unexpected ABI or binary mismatch fails closed before AAB signing.

## Consequences

- Debug development can continue temporarily with the exact transitional coordinate, but no
  artifact produced through that path is a Production release candidate.
- Release duration increases because native source is rebuilt and tested.
- Updating libXray requires a reviewed lock-file change, license review, protocol regression suite,
  16 KiB page-alignment check and refreshed release evidence.
- The official source pin proves provenance, not product safety; Security and VPN reliability tests
  remain mandatory.
- MPL-2.0 obligations of the pinned Xray-core source are tracked in
  [`third_party/libxray/NOTICE.md`](../../third_party/libxray/NOTICE.md).

## Rejected alternatives

- Trusting the third-party Maven artifact because its version is exact: rejected; source-to-binary
  correspondence is unknown.
- Downloading an upstream GitHub Release AAR without source rebuild: rejected; it weakens toolchain
  and binary evidence.
- Falling back to a stub/fake engine when native build fails: rejected; it could create a signed but
  nonfunctional or deceptive VPN release.
