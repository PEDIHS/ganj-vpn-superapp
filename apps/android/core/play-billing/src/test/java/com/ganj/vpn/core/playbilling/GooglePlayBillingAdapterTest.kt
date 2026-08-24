package com.ganj.vpn.core.playbilling

import com.ganj.vpn.core.billing.BillingIdempotencyKey
import com.ganj.vpn.core.billing.BillingOperationId
import com.ganj.vpn.core.billing.BillingProvider
import com.ganj.vpn.core.billing.CheckoutSessionId
import com.ganj.vpn.core.billing.EntitlementActivationReceipt
import com.ganj.vpn.core.billing.EntitlementActivationState
import com.ganj.vpn.core.billing.EntitlementId
import com.ganj.vpn.core.billing.GatewayCheckoutRequest
import com.ganj.vpn.core.billing.GatewayCheckoutResult
import com.ganj.vpn.core.billing.OwnedPurchasesRequest
import com.ganj.vpn.core.billing.OwnedPurchasesResult
import com.ganj.vpn.core.billing.ProviderCancellationRequest
import com.ganj.vpn.core.billing.ProviderCancellationResult
import com.ganj.vpn.core.billing.ProviderClientAction
import com.ganj.vpn.core.billing.ProviderPurchaseReference
import com.ganj.vpn.core.billing.ProviderPurchaseState
import com.ganj.vpn.core.billing.ServerPurchaseVerificationReceipt
import com.ganj.vpn.core.billing.ServerVerificationId
import java.security.SecureRandom
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePlayBillingAdapterTest {
    @Test
    fun `prepare checkout returns opaque account and stable catalog offer but no sdk token`() = runTest {
        val client = FakePlayBillingClient().apply { products = listOf(subscriptionProduct()) }
        val events = mutableListOf<PlayPurchaseEvent>()
        val adapter = adapter(client, FakeProofStore(), events)

        val result = adapter.prepareCheckout(checkoutRequest())

        assertTrue(result is GatewayCheckoutResult.UserActionRequired)
        val action = (result as GatewayCheckoutResult.UserActionRequired).action as
            ProviderClientAction.LaunchGooglePlay
        assertEquals("ganj.vip.monthly", action.productId)
        assertEquals(offerReference("monthly", null), action.offerId)
        assertEquals(64, action.obfuscatedAccountReference.length)
        assertTrue(action.checkoutSessionId.value.matches(Regex("gp\\.checkout\\.[0-9a-f]{32}")))
        assertFalse(action.obfuscatedAccountReference.contains("telegram-user-42"))
        assertFalse(action.toString().contains("offer-token-must-stay-private"))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `catalog has localized pricing and never exposes launch offer token`() = runTest {
        val client = FakePlayBillingClient().apply { products = listOf(subscriptionProduct()) }
        val adapter = adapter(client)

        val result = adapter.querySubscriptionCatalog(setOf("ganj.vip.monthly"))

        assertTrue(result is PlayCatalogResult.Success)
        val catalog = result as PlayCatalogResult.Success
        val offer = catalog.products.single().offers.single()
        assertEquals("\$4.99", offer.pricingPhases.single().formattedPrice)
        assertTrue(offer.reference.matches(Regex("gp\\.offer\\.[0-9a-f]{32}")))
        assertFalse(catalog.toString().contains("offer-token-must-stay-private"))
    }

    @Test
    fun `launch binding re-queries fresh details and rejects tampered checkout action`() = runTest {
        val client = FakePlayBillingClient().apply { products = listOf(subscriptionProduct()) }
        val adapter = adapter(client)
        val prepared = adapter.prepareCheckout(checkoutRequest()) as
            GatewayCheckoutResult.UserActionRequired
        val action = prepared.action as ProviderClientAction.LaunchGooglePlay
        var launches = 0

        val tampered = adapter.launchPreparedCheckout(
            action.copy(productId = "ganj.vip.yearly"),
        ) { _, _, _ ->
            launches += 1
            PlayClientResult(PlayResponseCode.OK)
        }
        val valid = adapter.launchPreparedCheckout(action) { product, offer, accountId ->
            launches += 1
            assertEquals("ganj.vip.monthly", product.productId)
            assertEquals(offerReference("monthly", null), offer.reference)
            assertEquals(action.obfuscatedAccountReference, accountId)
            PlayClientResult(PlayResponseCode.OK)
        }

        assertTrue(tampered is PlayBillingLaunchResult.Rejected)
        assertEquals(PlayBillingLaunchResult.Launched, valid)
        assertEquals(1, launches)
    }

    @Test
    fun `purchased callback emits encrypted handle and never raw token`() {
        val client = FakePlayBillingClient()
        val proofStore = FakeProofStore()
        val events = mutableListOf<PlayPurchaseEvent>()
        val adapter = adapter(client, proofStore, events)

        adapter.onPurchasesUpdated(PlayClientResult(PlayResponseCode.OK), listOf(purchase()))

        val observed = (events.single() as PlayPurchaseEvent.Observed).purchase
        assertEquals(ProviderPurchaseState.PURCHASED, observed.state)
        assertEquals(BillingProvider.GOOGLE_PLAY, observed.provider)
        assertTrue(observed.purchaseReference.value.startsWith("gp."))
        assertFalse(observed.purchaseReference.value.contains("play-purchase-token-secret"))
        assertFalse(observed.proofHandle.toString().contains("play-purchase-token-secret"))
        assertEquals(1, proofStore.tokensByHandle.size)
    }

    @Test
    fun `pending purchase is observed but never converted to completed entitlement`() {
        val events = mutableListOf<PlayPurchaseEvent>()
        val adapter = adapter(FakePlayBillingClient(), FakeProofStore(), events)

        adapter.onPurchasesUpdated(
            PlayClientResult(PlayResponseCode.OK),
            listOf(purchase(state = PlayPurchaseState.PENDING)),
        )

        val observed = (events.single() as PlayPurchaseEvent.Observed).purchase
        assertEquals(ProviderPurchaseState.PENDING, observed.state)
        assertTrue(observed.proofHandle != null)
    }

    @Test
    fun `user cancellation is a flow event and not a fake cancelled purchase`() {
        val events = mutableListOf<PlayPurchaseEvent>()
        val adapter = adapter(FakePlayBillingClient(), events = events)

        adapter.onPurchasesUpdated(PlayClientResult(PlayResponseCode.USER_CANCELED), null)

        assertEquals(listOf(PlayPurchaseEvent.UserCancelledFlow), events)
    }

    @Test
    fun `restore queries subscriptions including suspended and returns server-verifiable proof`() = runTest {
        val client = FakePlayBillingClient().apply { purchases = listOf(purchase()) }
        val adapter = adapter(client)

        val result = adapter.queryOwnedPurchases(ownedRequest())

        assertTrue(result is OwnedPurchasesResult.Found)
        assertEquals(listOf(true), client.includeSuspendedQueries)
        assertTrue((result as OwnedPurchasesResult.Found).purchases.single().proofHandle != null)
    }

    @Test
    fun `multi-product subscription payload is rejected rather than ambiguously restored`() = runTest {
        val invalid = PlayPurchaseSnapshot(
            products = listOf("ganj.vip.monthly", "ganj.vip.yearly"),
            token = PlayPurchaseToken.fromBillingClient("private-token"),
            state = PlayPurchaseState.PURCHASED,
            acknowledged = false,
            purchaseTimeEpochMillis = 1_700_000_000_000,
        )
        val client = FakePlayBillingClient().apply { purchases = listOf(invalid) }
        val adapter = adapter(client)

        val result = adapter.queryOwnedPurchases(ownedRequest())

        assertTrue(result is OwnedPurchasesResult.Failed)
    }

    @Test
    fun `client never falsely confirms Play subscription cancellation`() = runTest {
        val adapter = adapter(FakePlayBillingClient())

        val result = adapter.requestCancellation(
            ProviderCancellationRequest(
                operationId = BillingOperationId("operation.cancel.001"),
                purchaseReference = null,
                idempotencyKey = BillingIdempotencyKey("idempotency-cancel-001"),
            ),
        )

        assertTrue(result is ProviderCancellationResult.Rejected)
    }

    @Test
    fun `verified active server entitlement gates subscription acknowledgement and proof purge`() = runTest {
        val rawToken = "play-purchase-token-secret"
        val purchase = purchase(token = rawToken)
        val client = FakePlayBillingClient().apply { purchases = listOf(purchase) }
        val proofStore = FakeProofStore()
        val reference = ProviderPurchaseReference("gp.${sha256Hex(rawToken).take(48)}")
        val handle = proofStore.store(reference, purchase.token)
        val adapter = adapter(client, proofStore)

        val result = adapter.acknowledgeVerifiedSubscription(
            verifiedPurchase(reference, handle),
        )

        assertEquals(PlayAcknowledgementResult.Acknowledged, result)
        assertEquals(1, client.acknowledgeCount)
        assertEquals(sha256Hex(rawToken), client.lastAcknowledgedTokenFingerprint)
        assertEquals(listOf(handle), proofStore.deleted)
    }

    @Test
    fun `pending subscription is never acknowledged or consumed`() = runTest {
        val rawToken = "pending-play-token"
        val pending = purchase(token = rawToken, state = PlayPurchaseState.PENDING)
        val client = FakePlayBillingClient().apply { purchases = listOf(pending) }
        val proofStore = FakeProofStore()
        val reference = ProviderPurchaseReference("gp.${sha256Hex(rawToken).take(48)}")
        val handle = proofStore.store(reference, pending.token)
        val adapter = adapter(client, proofStore)

        val result = adapter.acknowledgeVerifiedSubscription(verifiedPurchase(reference, handle))

        assertEquals(PlayAcknowledgementResult.PurchaseStillPending, result)
        assertEquals(0, client.acknowledgeCount)
        assertTrue(proofStore.deleted.isEmpty())
    }

    @Test
    fun `already acknowledged renewal is idempotent and clears local proof`() = runTest {
        val rawToken = "already-acknowledged-token"
        val owned = purchase(token = rawToken, acknowledged = true)
        val client = FakePlayBillingClient().apply { purchases = listOf(owned) }
        val proofStore = FakeProofStore()
        val reference = ProviderPurchaseReference("gp.${sha256Hex(rawToken).take(48)}")
        val handle = proofStore.store(reference, owned.token)
        val adapter = adapter(client, proofStore)

        val result = adapter.acknowledgeVerifiedSubscription(verifiedPurchase(reference, handle))

        assertEquals(PlayAcknowledgementResult.AlreadyAcknowledged, result)
        assertEquals(0, client.acknowledgeCount)
        assertEquals(listOf(handle), proofStore.deleted)
    }

    @Test
    fun `failed acknowledgement retains encrypted proof for retry`() = runTest {
        val rawToken = "retry-acknowledgement-token"
        val owned = purchase(token = rawToken)
        val client = FakePlayBillingClient().apply {
            purchases = listOf(owned)
            acknowledgeResult = PlayClientResult(PlayResponseCode.NETWORK_ERROR)
        }
        val proofStore = FakeProofStore()
        val reference = ProviderPurchaseReference("gp.${sha256Hex(rawToken).take(48)}")
        val handle = proofStore.store(reference, owned.token)
        val adapter = adapter(client, proofStore)

        val result = adapter.acknowledgeVerifiedSubscription(verifiedPurchase(reference, handle))

        assertTrue(result is PlayAcknowledgementResult.Failed)
        assertTrue(proofStore.deleted.isEmpty())
        assertTrue(proofStore.tokensByHandle.containsKey(handle))
    }

    @Test
    fun `proof handle for a different token cannot acknowledge owned subscription`() = runTest {
        val ownedToken = "owned-subscription-token"
        val owned = purchase(token = ownedToken)
        val client = FakePlayBillingClient().apply { purchases = listOf(owned) }
        val proofStore = FakeProofStore()
        val reference = ProviderPurchaseReference("gp.${sha256Hex(ownedToken).take(48)}")
        val wrongToken = PlayPurchaseToken.fromBillingClient("different-proof-token")
        val wrongHandle = proofStore.store(
            ProviderPurchaseReference("gp.${wrongToken.fingerprint().take(48)}"),
            wrongToken,
        )
        val adapter = adapter(client, proofStore)

        val result = adapter.acknowledgeVerifiedSubscription(
            verifiedPurchase(reference, wrongHandle),
        )

        assertTrue(result is PlayAcknowledgementResult.Failed)
        assertEquals(0, client.acknowledgeCount)
        assertTrue(proofStore.deleted.isEmpty())
    }

    @Test
    fun `token-bearing sdk snapshots redact secrets from strings`() {
        val raw = "never-log-this-token"
        val token = PlayPurchaseToken.fromBillingClient(raw)
        val snapshot = purchase(token = raw)

        assertFalse(token.toString().contains(raw))
        assertFalse(snapshot.toString().contains(raw))
        assertNotEquals(raw, token.fingerprint())
        assertEquals(64, token.fingerprint().length)
        assertTrue(token.fingerprint().matches(Regex("[0-9a-f]{64}")))
    }

    private fun adapter(
        client: FakePlayBillingClient,
        proofStore: FakeProofStore = FakeProofStore(),
        events: MutableList<PlayPurchaseEvent> = mutableListOf(),
    ): GooglePlayBillingAdapter = GooglePlayBillingAdapter(
        client = client,
        connection = PlayBillingConnectionManager(client, FakeRetryScheduler()),
        proofStore = proofStore,
        proofResolver = proofStore,
        observer = PlayPurchaseObserver(events::add),
        nowEpochMillis = { 1_700_000_000_000 },
        random = SecureRandom(),
    )

    private fun checkoutRequest(): GatewayCheckoutRequest = GatewayCheckoutRequest(
        operationId = BillingOperationId("operation.checkout.001"),
        userId = "telegram-user-42",
        productId = "ganj.vip.monthly",
        offerId = offerReference("monthly", null),
        idempotencyKey = BillingIdempotencyKey("idempotency-checkout-001"),
    )

    private fun ownedRequest(): OwnedPurchasesRequest = OwnedPurchasesRequest(
        operationId = BillingOperationId("operation.restore.001"),
        userId = "telegram-user-42",
        idempotencyKey = BillingIdempotencyKey("idempotency-restore-001"),
    )

    private fun verifiedPurchase(
        purchaseReference: ProviderPurchaseReference,
        proofHandle: com.ganj.vpn.core.billing.PurchaseProofHandle,
    ): VerifiedPlaySubscription {
        val verificationId = ServerVerificationId("verification.play.001")
        val verification = ServerPurchaseVerificationReceipt(
            verificationId = verificationId,
            operationId = BillingOperationId("operation.checkout.001"),
            userId = "telegram-user-42",
            productId = "ganj.vip.monthly",
            provider = BillingProvider.GOOGLE_PLAY,
            purchaseReference = purchaseReference,
            verifiedAtEpochMillis = 1_700_000_000_100,
        )
        val entitlement = EntitlementActivationReceipt(
            verificationId = verificationId,
            entitlementId = EntitlementId("entitlement.vip.001"),
            serviceId = "service.vip.001",
            userId = verification.userId,
            productId = verification.productId,
            state = EntitlementActivationState.ACTIVE,
            effectiveAtEpochMillis = 1_700_000_000_200,
            expiresAtEpochMillis = 1_702_592_000_200,
        )
        return VerifiedPlaySubscription(
            verification = verification,
            entitlement = entitlement,
            purchaseReference = purchaseReference,
            proofHandle = proofHandle,
        )
    }
}
