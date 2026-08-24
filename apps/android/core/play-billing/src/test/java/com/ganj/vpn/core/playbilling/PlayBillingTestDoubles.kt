package com.ganj.vpn.core.playbilling

import android.app.Activity
import com.ganj.vpn.core.billing.ProviderPurchaseReference
import com.ganj.vpn.core.billing.PurchaseProofHandle

internal class FakePlayBillingClient : PlayBillingClientFacade {
    override var isReady: Boolean = true
    var startConnectionCount = 0
    var endConnectionCount = 0
    var launchCount = 0
    var acknowledgeCount = 0
    var lastAcknowledgedTokenFingerprint: String? = null
    var includeSuspendedQueries = mutableListOf<Boolean>()
    var connectionListener: PlayBillingClientFacade.ConnectionListener? = null
    var connectionResult = PlayClientResult(PlayResponseCode.OK)
    var productResult = PlayClientResult(PlayResponseCode.OK)
    var purchaseResult = PlayClientResult(PlayResponseCode.OK)
    var launchResult = PlayClientResult(PlayResponseCode.OK)
    var acknowledgeResult = PlayClientResult(PlayResponseCode.OK)
    var products: List<PlayProductSnapshot> = emptyList()
    var purchases: List<PlayPurchaseSnapshot> = emptyList()
    var autoCompleteConnection = true

    override fun startConnection(listener: PlayBillingClientFacade.ConnectionListener) {
        startConnectionCount += 1
        connectionListener = listener
        if (autoCompleteConnection) {
            isReady = connectionResult.responseCode == PlayResponseCode.OK
            listener.onSetupFinished(connectionResult)
        }
    }

    override fun endConnection() {
        endConnectionCount += 1
        isReady = false
    }

    override fun querySubscriptionProducts(
        productIds: Set<String>,
        callback: (PlayClientResult, PlayProductQueryResult) -> Unit,
    ) {
        callback(
            productResult,
            PlayProductQueryResult(
                products.filter { it.productId in productIds },
                unfetchedProductCount = productIds.count { id -> products.none { it.productId == id } },
            ),
        )
    }

    override fun querySubscriptionPurchases(
        includeSuspended: Boolean,
        callback: (PlayClientResult, List<PlayPurchaseSnapshot>) -> Unit,
    ) {
        includeSuspendedQueries += includeSuspended
        callback(purchaseResult, purchases)
    }

    override fun launchSubscriptionFlow(
        activity: Activity,
        product: PlayProductSnapshot,
        offer: PlayOfferSnapshot,
        obfuscatedAccountId: String,
    ): PlayClientResult {
        launchCount += 1
        return launchResult
    }

    override fun acknowledgeSubscription(
        token: PlayPurchaseToken,
        callback: (PlayClientResult) -> Unit,
    ) {
        acknowledgeCount += 1
        lastAcknowledgedTokenFingerprint = token.fingerprint()
        callback(acknowledgeResult)
    }
}

internal class FakeRetryScheduler : RetryScheduler {
    data class Scheduled(val delayMillis: Long, val task: () -> Unit)

    val scheduled = mutableListOf<Scheduled>()

    override fun schedule(delayMillis: Long, task: () -> Unit) {
        scheduled += Scheduled(delayMillis, task)
    }

    fun runNext() {
        scheduled.removeAt(0).task()
    }
}

internal class FakeProofStore : PlayPurchaseProofStore {
    val tokensByHandle = mutableMapOf<PurchaseProofHandle, String>()
    val handlesByPurchase = mutableMapOf<ProviderPurchaseReference, PurchaseProofHandle>()
    val deleted = mutableListOf<PurchaseProofHandle>()

    override fun store(
        purchaseReference: ProviderPurchaseReference,
        token: PlayPurchaseToken,
    ): PurchaseProofHandle = handlesByPurchase.getOrPut(purchaseReference) {
        PurchaseProofHandle.fromSecureVault("gp.proof.${handlesByPurchase.size.toString().padStart(3, '0')}")
    }.also { handle -> tokensByHandle[handle] = token.use { it } }

    override fun <T> useProof(
        handle: PurchaseProofHandle,
        block: (SensitivePlayPurchaseProof) -> T,
    ): T {
        val value = tokensByHandle[handle] ?: throw PlayPurchaseProofUnavailableException()
        val proof = SensitivePlayPurchaseProof(value.toByteArray())
        return try {
            block(proof)
        } finally {
            proof.close()
        }
    }

    override fun delete(handle: PurchaseProofHandle) {
        deleted += handle
        tokensByHandle.remove(handle)
    }
}

internal fun subscriptionProduct(
    productId: String = "ganj.vip.monthly",
    offerRef: String = offerReference("monthly", null),
): PlayProductSnapshot {
    val offer = PlayOfferSnapshot(
        reference = offerRef,
        basePlanId = "monthly",
        offerId = null,
        tags = setOf("vip"),
        pricingPhases = listOf(
            PlayPricingPhaseSnapshot(
                formattedPrice = "\$4.99",
                priceAmountMicros = 4_990_000,
                currencyCode = "USD",
                billingPeriod = "P1M",
                billingCycleCount = 0,
                recurrenceMode = 1,
            ),
        ),
        launchToken = PlayOfferToken.fromBillingClient("offer-token-must-stay-private"),
    )
    return PlayProductSnapshot(
        productId = productId,
        title = "Ganj VIP Monthly",
        description = "Monthly VPN service",
        offers = listOf(offer),
        nativeProduct = Any(),
    )
}

internal fun purchase(
    token: String = "play-purchase-token-secret",
    productId: String = "ganj.vip.monthly",
    state: PlayPurchaseState = PlayPurchaseState.PURCHASED,
    acknowledged: Boolean = false,
): PlayPurchaseSnapshot = PlayPurchaseSnapshot(
    products = listOf(productId),
    token = PlayPurchaseToken.fromBillingClient(token),
    state = state,
    acknowledged = acknowledged,
    purchaseTimeEpochMillis = 1_700_000_000_000,
)
