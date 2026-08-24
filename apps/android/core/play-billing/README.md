# Google Play Billing adapter

Android-only Google Play subscription adapter for `:core:billing`. The module uses
`com.android.billingclient:billing:9.1.0`, the current stable release verified against the official
Android Developers integration and release-note pages on 2026-08-24.

## Security and ownership rules

- The app creates one `GooglePlayBillingAdapter` per process through `Factory`.
- `BillingClient` receives `applicationContext`, enables pending one-time products and prepaid
  plans, and enables automatic service reconnection. Initial setup retries are bounded and use
  exponential backoff.
- A resumed `Activity` is passed only to `launchCheckout`; it is never stored. Optionally register
  `PlayBillingLifecycleBridge` from the application to reconnect/query purchases on foreground.
  Closing the bridge unregisters it and closes the adapter/BillingClient.
- Product details are queried fresh before every checkout launch. The public catalog contains a
  base-plan/offer reference, never the Play offer token.
- Purchase tokens are immediately encrypted with an Android Keystore AES-256-GCM key. Public
  events carry a random `PurchaseProofHandle`; purchase references are one-way SHA-256
  fingerprints. Neither raw value is logged or placed in a model `toString`.
- Unverified proof records are bounded to seven days and are rediscovered by `queryPurchasesAsync`
  if still owned. A successful/idempotent acknowledgement removes proof immediately; a transient
  failure retains it for retry.
- `PENDING` never becomes an entitlement. The host sends the opaque proof to its authenticated
  backend via `PlayPurchaseProofResolver`; `core:billing` verifies and syncs the server entitlement.
  Only a matching verification receipt plus active-entitlement receipt permits client fallback
  acknowledgement. Subscriptions are never consumed.

## Host integration

1. Include `:core:play-billing` and depend on it from the Android app.
2. Create the adapter once and feed `PlayPurchaseEvent.Observed` into the provider-neutral billing
   state machine. Persist only its opaque handle/reference.
3. Resolve proof only inside the authenticated backend-verification transport. Never put proof in
   analytics, crash reports, diagnostics, logs, saved state, intents or notifications.
4. Register `PlayBillingLifecycleBridge.start()` at application startup and close it from the owning
   application component/test teardown.
5. Call `prepareCheckout`, then call `launchCheckout` on the main thread with the currently resumed
   Activity. The checkout action is short-lived (15 minutes) and bound to the selected product,
   offer, session and obfuscated account reference.
6. Reconcile owned subscriptions at login/startup. Grant service only from the authenticated
   backend response. After the entitlement is active, call `acknowledgeVerifiedSubscription` if the
   backend has not already acknowledged through the Google Play Developer API.

This module intentionally contains no alternative/external payment setup, manual VPN configuration,
local entitlement activation, fake purchase success, static product-detail cache, or production
credential.
