# libXray / Xray-core provenance notice

Ganj VPN does not vendor or redistribute a prebuilt third-party libXray AAR in this repository.
Production Android release CI rebuilds the binding from the exact upstream source recorded in
[`UPSTREAM.lock.json`](../../UPSTREAM.lock.json), verifies the Git tag and commit, verifies selected
source/license hashes, uses Go module checksum verification, and proves that the native libraries
embedded in the final AAB are byte-identical to that rebuilt AAR.

| Component | Upstream | Pin | License |
|---|---|---|---|
| libXray | `https://github.com/XTLS/libXray` | tag `v26.7.28`, commit `80263da83e96b2972455b0a94b13ee1a10e51391` | MIT |
| Xray-core | `https://github.com/XTLS/Xray-core` | Go pseudo-version ending at commit `5ca6f4b7d4dc` | MPL-2.0 |

The CI release evidence includes the unmodified upstream license files and checksums. Xray-core's
MPL-2.0 source-availability and notice obligations apply to distribution. Legal approval and a
stable public source-offer location are release gates; an SBOM alone does not satisfy those duties.

The transitional Maven coordinate used by the Debug runtime is not provenance for Production.
Production dependency resolution is exclusive to the ephemeral repository populated by the pinned
official-source build; resolution failure or binary mismatch stops the release.

## Ganj-owned Android TUN binding extension

The official libXray tag used by this release does not expose the Android TUN descriptor setter
through its gomobile class. Ganj adds the small source-owned extension
[`ganj_tun_fd.go`](ganj_tun_fd.go) to the verified upstream checkout immediately before
building the AAR, without changing the upstream files or importing another Go runtime.
The generated binding exports `setTunFd(int)` and `resetTunFd()` to the Android bridge.
Its source SHA-256 is included in the release provenance and its source is copied to the
release evidence. The Android app rejects the native runtime if this binding is absent.

The transitional Debug Maven AAR does not contain this extension and is not a working VPN
candidate for this change. Real connection verification requires a rebuilt, pinned native
artifact; the debug CI passing does not establish a working VPN connection.
