# ADR-0001: Native Android with Kotlin and Compose

- Status: Accepted
- Date: 2026-08-24

## Decision

Android app is native Kotlin/Jetpack Compose. VPN engine and `VpnService` remain platform-specific modules behind interfaces.

## Rationale

Reliable foreground service lifecycle, TUN integration, battery control, app-link auth, Play Billing, performance profiles and low-level debugging are first-class requirements. Cross-platform UI would not remove the native VPN work and would add a bridge at the most sensitive boundary.

## Consequences

- iOS is a separate SwiftUI/Network Extension client sharing API and design tokens.
- Backend contract and product model stay platform-neutral.
- More initial platform work, lower runtime and release risk.

