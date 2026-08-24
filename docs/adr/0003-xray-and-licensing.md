# ADR-0003: Xray Core without Copying v2rayNG UI/Application Code

- Status: Accepted pending final legal review
- Date: 2026-08-24

## Decision

Build a thin Ganj-owned Android binding around a pinned Xray-core source release. Do not copy v2rayNG UI/application code. Keep modified MPL files and notices traceable.

## Context

v2rayNG is GPL-3.0. The current Ganj APK is structurally almost identical to v2rayNG and therefore creates licensing and product differentiation risk. Xray-core itself is MPL-2.0.

## Consequences

- The existing APK is migration input, not the source baseline.
- Third-party notices and source obligations are part of every release.
- Core upgrades require protocol regression and SBOM review.
- Legal counsel must confirm distribution obligations before public release.

