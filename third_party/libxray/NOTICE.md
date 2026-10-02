# libXray / Xray-core provenance notice

Ganj VPN does not vendor or redistribute a prebuilt third-party libXray AAR in this repository.
Production Android release CI rebuilds the binding from the exact source recorded in
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
