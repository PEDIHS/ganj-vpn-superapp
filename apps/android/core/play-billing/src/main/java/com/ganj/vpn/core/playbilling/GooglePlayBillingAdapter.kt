package com.ganj.vpn.core.playbilling

import android.app.Activity
import android.content.Context
import android.os.Looper
import com.ganj.vpn.core.billing.BillingFailure
import com.ganj.vpn.core.billing.BillingFailureCode
import com.ganj.vpn.core.billing.CheckoutSessionId
import com.ganj.vpn.core.billing.GatewayCheckoutRequest
import com.ganj.vpn.core.billing.GatewayCheckoutResult
import com.ganj.vpn.core.billing.GooglePlayBillingGateway
import com.ganj.vpn.core.billing.OwnedPurchasesRequest
import com.ganj.vpn.core.billing.OwnedPurchasesResult
import com.ganj.vpn.core.billing.ProviderCancellationRequest
import com.ganj.vpn.core.billing.ProviderCancellationResult
import com.ganj.vpn.core.billing.ProviderClientAction
import com.ganj.vpn.core.billing.ProviderPurchase
import com.ganj.vpn.core.billing.ProviderPurchaseReference
import com.ganj.vpn.core.billing.ProviderPurchaseState
import java.io.Closeable
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google Play subscription adapter for the provider-neutral `core:billing` contracts.
 *
 * It has no manual product/config import and no alternative-payment code. Purchase callbacks are
 * observations only: they are encrypted into an opaque proof handle and must pass the backend
 * verifier and entitlement sync in `core:billing` before [acknowledgeVerifiedSubscription].
 */
class GooglePlayBillingAdapter internal constructor(
    private val client: PlayBillingClientFacade,
    private val connection: PlayBillingConnectionManager,
    private val proofStore: PlayPurchaseProofStore,
    val proofResolver: PlayPurchaseProofResolver,
    private val observer: PlayPurchaseObserver,
    private val nowEpochMillis: () -> Long,
    private val random: SecureRandom,
) : GooglePlayBillingGateway, Closeable {
    private val preparedCheckouts = ConcurrentHashMap<CheckoutSessionId, PreparedCheckout>()

    override suspend fun prepareCheckout(request: GatewayCheckoutRequest): GatewayCheckoutResult {
        val ready = connection.awaitReady()
        if (ready.responseCode != PlayResponseCode.OK) {
            return GatewayCheckoutResult.Rejected(ready.toFailure("billing.play.checkout_unavailable"))
        }
        val productResult = queryProducts(setOf(request.productId))
        if (productResult.first.responseCode != PlayResponseCode.OK) {
            return GatewayCheckoutResult.Rejected(
                productResult.first.toFailure("billing.play.product_query_failed"),
            )
        }
        val product = productResult.second.products.singleOrNull { it.productId == request.productId }
            ?: return GatewayCheckoutResult.Rejected(productUnavailable())
        val offer = selectOffer(product, request.offerId)
            ?: return GatewayCheckoutResult.Rejected(productUnavailable())
        val sessionId = CheckoutSessionId("gp.checkout.${randomHex(16)}")
        val accountReference = sha256Hex("${request.userId}:${request.idempotencyKey.value}")
        val createdAt = nowEpochMillis()
        purgeExpiredCheckouts(createdAt)
        val prepared = PreparedCheckout(
            productId = product.productId,
            offerReference = offer.reference,
            obfuscatedAccountId = accountReference,
            createdAtEpochMillis = createdAt,
        )
        preparedCheckouts[sessionId] = prepared
        return GatewayCheckoutResult.UserActionRequired(
            ProviderClientAction.LaunchGooglePlay(
                checkoutSessionId = sessionId,
                productId = product.productId,
                offerId = offer.reference,
                obfuscatedAccountReference = accountReference,
            ),
        )
    }

    /**
     * Launches Play UI from a currently resumed Activity. The Activity is used for this call only;
     * neither the adapter nor BillingClient stores it. ProductDetails are queried again immediately
     * before launch, because Play explicitly warns against long-lived ProductDetails caching.
     */
    suspend fun launchCheckout(
        activity: Activity,
        action: ProviderClientAction.LaunchGooglePlay,
    ): PlayBillingLaunchResult {
        if (Looper.myLooper() != Looper.getMainLooper() || activity.isFinishing || activity.isDestroyed) {
            return PlayBillingLaunchResult.Rejected(
                nonRetryableFailure("billing.play.resumed_activity_required"),
            )
        }
        return launchPreparedCheckout(action) { product, offer, accountId ->
            client.launchSubscriptionFlow(
                activity = activity,
                product = product,
                offer = offer,
                obfuscatedAccountId = accountId,
            )
        }
    }

    internal suspend fun launchPreparedCheckout(
        action: ProviderClientAction.LaunchGooglePlay,
        launcher: (PlayProductSnapshot, PlayOfferSnapshot, String) -> PlayClientResult,
    ): PlayBillingLaunchResult {
        val prepared = preparedCheckouts[action.checkoutSessionId]
            ?: return PlayBillingLaunchResult.Rejected(
                nonRetryableFailure("billing.play.checkout_session_expired"),
            )
        val preparedAge = nowEpochMillis() - prepared.createdAtEpochMillis
        if (preparedAge !in 0..PREPARED_CHECKOUT_TTL_MILLIS) {
            preparedCheckouts.remove(action.checkoutSessionId)
            return PlayBillingLaunchResult.Rejected(
                nonRetryableFailure("billing.play.checkout_session_expired"),
            )
        }
        if (
            prepared.productId != action.productId ||
            prepared.offerReference != action.offerId ||
            prepared.obfuscatedAccountId != action.obfuscatedAccountReference
        ) {
            return PlayBillingLaunchResult.Rejected(
                nonRetryableFailure("billing.play.checkout_session_mismatch"),
            )
        }
        val ready = connection.awaitReady()
        if (ready.responseCode != PlayResponseCode.OK) {
            return PlayBillingLaunchResult.Rejected(ready.toFailure("billing.play.launch_unavailable"))
        }
        val query = queryProducts(setOf(prepared.productId))
        if (query.first.responseCode != PlayResponseCode.OK) {
            return PlayBillingLaunchResult.Rejected(
                query.first.toFailure("billing.play.product_query_failed"),
            )
        }
        val product = query.second.products.singleOrNull { it.productId == prepared.productId }
            ?: return PlayBillingLaunchResult.Rejected(productUnavailable())
        val offer = product.offers.singleOrNull { it.reference == prepared.offerReference }
            ?: return PlayBillingLaunchResult.Rejected(productUnavailable())

        val result = launcher(product, offer, prepared.obfuscatedAccountId)
        if (result.responseCode != PlayResponseCode.SERVICE_DISCONNECTED) {
            preparedCheckouts.remove(action.checkoutSessionId)
        }
        return when (result.responseCode) {
            PlayResponseCode.OK -> PlayBillingLaunchResult.Launched
            PlayResponseCode.ITEM_ALREADY_OWNED -> PlayBillingLaunchResult.AlreadyOwned(
                result.toFailure("billing.play.item_already_owned"),
            )
            else -> PlayBillingLaunchResult.Rejected(result.toFailure("billing.play.launch_rejected"))
        }
    }

    suspend fun querySubscriptionCatalog(productIds: Set<String>): PlayCatalogResult {
        if (productIds.isEmpty() || productIds.any(String::isBlank)) {
            return PlayCatalogResult.Failed(nonRetryableFailure("billing.play.invalid_catalog_request"))
        }
        val ready = connection.awaitReady()
        if (ready.responseCode != PlayResponseCode.OK) {
            return PlayCatalogResult.Failed(ready.toFailure("billing.play.catalog_unavailable"))
        }
        val result = queryProducts(productIds)
        if (result.first.responseCode != PlayResponseCode.OK) {
            return PlayCatalogResult.Failed(result.first.toFailure("billing.play.product_query_failed"))
        }
        return PlayCatalogResult.Success(
            products = result.second.products.map { it.toPublicProduct() },
            unfetchedProductCount = result.second.unfetchedProductCount,
        )
    }

    override suspend fun queryOwnedPurchases(request: OwnedPurchasesRequest): OwnedPurchasesResult {
        val ready = connection.awaitReady()
        if (ready.responseCode != PlayResponseCode.OK) {
            return OwnedPurchasesResult.Failed(ready.toFailure("billing.play.restore_unavailable", restore = true))
        }
        val result = queryPurchases(includeSuspended = true)
        if (result.first.responseCode != PlayResponseCode.OK) {
            return OwnedPurchasesResult.Failed(
                result.first.toFailure("billing.play.restore_query_failed", restore = true),
            )
        }
        val mapped = result.second.map { purchase ->
            purchase.toProviderPurchaseOrNull()
                ?: return OwnedPurchasesResult.Failed(
                    BillingFailure(
                        BillingFailureCode.RESTORE_FAILED,
                        retryable = false,
                        safeMessageKey = "billing.play.invalid_purchase_payload",
                    ),
                )
        }
        return if (mapped.isEmpty()) OwnedPurchasesResult.None else OwnedPurchasesResult.Found(mapped)
    }

    /** Google Play cancellation is user-managed; this adapter never reports a false confirmation. */
    override suspend fun requestCancellation(
        request: ProviderCancellationRequest,
    ): ProviderCancellationResult = ProviderCancellationResult.Rejected(
        BillingFailure(
            code = BillingFailureCode.PROVIDER_REJECTED,
            retryable = false,
            safeMessageKey = "billing.play.manage_subscription_required",
        ),
    )

    /**
     * Safe foreground hook for Application.ActivityLifecycleCallbacks. No Activity is retained.
     * It reconnects if needed and queries purchases to catch pending/out-of-app completions.
     */
    fun onAppResumed() {
        connection.ensureReady { ready ->
            if (ready.responseCode != PlayResponseCode.OK) return@ensureReady
            client.querySubscriptionPurchases(includeSuspended = true) { result, purchases ->
                if (result.responseCode == PlayResponseCode.OK) dispatchPurchases(purchases)
            }
        }
    }

    /**
     * Subscriptions are acknowledged, never consumed, and only after matching backend verification
     * and active-entitlement receipts. Renewals already acknowledged by Play are treated as success.
     */
    suspend fun acknowledgeVerifiedSubscription(
        verified: VerifiedPlaySubscription,
    ): PlayAcknowledgementResult {
        val ready = connection.awaitReady()
        if (ready.responseCode != PlayResponseCode.OK) {
            return PlayAcknowledgementResult.Failed(
                ready.toFailure("billing.play.acknowledge_unavailable"),
            )
        }
        val result = queryPurchases(includeSuspended = true)
        if (result.first.responseCode != PlayResponseCode.OK) {
            return PlayAcknowledgementResult.Failed(
                result.first.toFailure("billing.play.acknowledge_query_failed"),
            )
        }
        val purchase = result.second.singleOrNull {
            it.token.toPurchaseReference() == verified.purchaseReference
        } ?: return PlayAcknowledgementResult.PurchaseNotOwned
        if (purchase.state != PlayPurchaseState.PURCHASED) {
            return PlayAcknowledgementResult.PurchaseStillPending
        }
        if (purchase.acknowledged) {
            proofStore.delete(verified.proofHandle)
            return PlayAcknowledgementResult.AlreadyAcknowledged
        }
        val proofMatchesPurchase = try {
            proofStore.useProof(verified.proofHandle) { proof ->
                "gp.${proof.fingerprint().take(48)}" == verified.purchaseReference.value
            }
        } catch (_: PlayPurchaseProofUnavailableException) {
            false
        }
        if (!proofMatchesPurchase) {
            return PlayAcknowledgementResult.Failed(
                nonRetryableFailure("billing.play.proof_purchase_mismatch"),
            )
        }
        val acknowledgement = acknowledge(purchase.token)
        return if (acknowledgement.responseCode == PlayResponseCode.OK) {
            proofStore.delete(verified.proofHandle)
            PlayAcknowledgementResult.Acknowledged
        } else {
            PlayAcknowledgementResult.Failed(
                acknowledgement.toFailure("billing.play.acknowledge_failed"),
            )
        }
    }

    internal fun onPurchasesUpdated(
        result: PlayClientResult,
        purchases: List<PlayPurchaseSnapshot>?,
    ) {
        when {
            result.responseCode == PlayResponseCode.USER_CANCELED ->
                observer.onPurchaseEvent(PlayPurchaseEvent.UserCancelledFlow)
            result.responseCode != PlayResponseCode.OK ->
                observer.onPurchaseEvent(
                    PlayPurchaseEvent.Failed(result.toFailure("billing.play.purchase_update_failed")),
                )
            purchases == null ->
                observer.onPurchaseEvent(
                    PlayPurchaseEvent.Failed(nonRetryableFailure("billing.play.empty_purchase_update")),
                )
            else -> dispatchPurchases(purchases)
        }
    }

    override fun close() {
        preparedCheckouts.clear()
        connection.close()
    }

    private fun dispatchPurchases(purchases: List<PlayPurchaseSnapshot>) {
        purchases.forEach { purchase ->
            val mapped = purchase.toProviderPurchaseOrNull()
            observer.onPurchaseEvent(
                if (mapped == null) {
                    PlayPurchaseEvent.Failed(nonRetryableFailure("billing.play.invalid_purchase_payload"))
                } else {
                    PlayPurchaseEvent.Observed(mapped)
                },
            )
        }
    }

    private fun PlayPurchaseSnapshot.toProviderPurchaseOrNull(): ProviderPurchase? {
        val productId = products.singleOrNull()?.takeIf(String::isNotBlank) ?: return null
        val providerState = when (state) {
            PlayPurchaseState.PENDING -> ProviderPurchaseState.PENDING
            PlayPurchaseState.PURCHASED -> ProviderPurchaseState.PURCHASED
            PlayPurchaseState.UNSPECIFIED -> return null
        }
        val reference = token.toPurchaseReference()
        val proofHandle = try {
            proofStore.store(reference, token)
        } catch (_: PlayPurchaseProofUnavailableException) {
            return null
        }
        return ProviderPurchase(
            provider = provider,
            purchaseReference = reference,
            productId = productId,
            state = providerState,
            proofHandle = proofHandle,
            observedAtEpochMillis = purchaseTimeEpochMillis.takeIf { it > 0 } ?: nowEpochMillis(),
        )
    }

    private suspend fun queryProducts(
        productIds: Set<String>,
    ): Pair<PlayClientResult, PlayProductQueryResult> = suspendCancellableCoroutine { continuation ->
        client.querySubscriptionProducts(productIds) { result, products ->
            if (continuation.isActive) continuation.resume(result to products)
        }
    }

    private suspend fun queryPurchases(
        includeSuspended: Boolean,
    ): Pair<PlayClientResult, List<PlayPurchaseSnapshot>> = suspendCancellableCoroutine { continuation ->
        client.querySubscriptionPurchases(includeSuspended) { result, purchases ->
            if (continuation.isActive) continuation.resume(result to purchases)
        }
    }

    private suspend fun acknowledge(token: PlayPurchaseToken): PlayClientResult =
        suspendCancellableCoroutine { continuation ->
            client.acknowledgeSubscription(token) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        }

    private fun selectOffer(product: PlayProductSnapshot, requested: String?): PlayOfferSnapshot? =
        if (requested != null) {
            product.offers.singleOrNull { it.reference == requested }
        } else {
            product.offers.singleOrNull()
        }

    private fun PlayProductSnapshot.toPublicProduct(): PlaySubscriptionProduct =
        PlaySubscriptionProduct(
            productId = productId,
            title = title,
            description = description,
            offers = offers.map { offer ->
                PlaySubscriptionOffer(
                    productId = productId,
                    reference = offer.reference,
                    basePlanId = offer.basePlanId,
                    offerId = offer.offerId,
                    tags = offer.tags,
                    pricingPhases = offer.pricingPhases.map { phase ->
                        PlayPricingPhase(
                            formattedPrice = phase.formattedPrice,
                            priceAmountMicros = phase.priceAmountMicros,
                            currencyCode = phase.currencyCode,
                            billingPeriodIso8601 = phase.billingPeriod,
                            billingCycleCount = phase.billingCycleCount,
                            recurrenceMode = phase.recurrenceMode,
                        )
                    },
                )
            },
        )

    private fun PlayPurchaseToken.toPurchaseReference(): ProviderPurchaseReference =
        ProviderPurchaseReference("gp.${fingerprint().take(48)}")

    private fun randomHex(byteCount: Int): String = ByteArray(byteCount)
        .also(random::nextBytes)
        .toLowerHex()

    private fun purgeExpiredCheckouts(now: Long) {
        preparedCheckouts.entries.forEach { entry ->
            val age = now - entry.value.createdAtEpochMillis
            if (age !in 0..PREPARED_CHECKOUT_TTL_MILLIS) {
                preparedCheckouts.remove(entry.key, entry.value)
            }
        }
        if (preparedCheckouts.size >= MAX_PREPARED_CHECKOUTS) {
            preparedCheckouts.entries.minByOrNull { it.value.createdAtEpochMillis }?.let { oldest ->
                preparedCheckouts.remove(oldest.key, oldest.value)
            }
        }
    }

    class Factory(
        context: Context,
        private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    ) {
        private val applicationContext = context.applicationContext

        fun create(observer: PlayPurchaseObserver): GooglePlayBillingAdapter {
            val relay = PurchaseListenerRelay()
            val client = RealPlayBillingClientFacade(applicationContext, relay)
            val scheduler = MainThreadRetryScheduler()
            val connection = PlayBillingConnectionManager(client, scheduler)
            val vault = AndroidKeystorePurchaseProofVault(applicationContext)
            val proofStore = AndroidPlayPurchaseProofStore(vault)
            val adapter = GooglePlayBillingAdapter(
                client = client,
                connection = connection,
                proofStore = proofStore,
                proofResolver = vault,
                observer = observer,
                nowEpochMillis = nowEpochMillis,
                random = SecureRandom(),
            )
            relay.delegate = adapter::onPurchasesUpdated
            return adapter
        }
    }

    private data class PreparedCheckout(
        val productId: String,
        val offerReference: String,
        val obfuscatedAccountId: String,
        val createdAtEpochMillis: Long,
    )

    private companion object {
        const val PREPARED_CHECKOUT_TTL_MILLIS = 15 * 60 * 1_000L
        const val MAX_PREPARED_CHECKOUTS = 64
    }
}

private class PurchaseListenerRelay : PlayPurchaseSdkListener {
    var delegate: ((PlayClientResult, List<PlayPurchaseSnapshot>?) -> Unit)? = null

    override fun onPurchasesUpdated(
        result: PlayClientResult,
        purchases: List<PlayPurchaseSnapshot>?,
    ) {
        delegate?.invoke(result, purchases)
    }
}

private fun PlayClientResult.toFailure(
    safeMessageKey: String,
    restore: Boolean = false,
): BillingFailure {
    val retryable = responseCode in setOf(
        PlayResponseCode.SERVICE_DISCONNECTED,
        PlayResponseCode.SERVICE_UNAVAILABLE,
        PlayResponseCode.NETWORK_ERROR,
        PlayResponseCode.ERROR,
    )
    val code = when {
        restore -> BillingFailureCode.RESTORE_FAILED
        responseCode == PlayResponseCode.ITEM_UNAVAILABLE -> BillingFailureCode.PRODUCT_UNAVAILABLE
        responseCode == PlayResponseCode.BILLING_UNAVAILABLE -> BillingFailureCode.PROVIDER_UNAVAILABLE
        responseCode == PlayResponseCode.NETWORK_ERROR -> BillingFailureCode.NETWORK_UNAVAILABLE
        responseCode in setOf(PlayResponseCode.SERVICE_DISCONNECTED, PlayResponseCode.SERVICE_UNAVAILABLE) ->
            BillingFailureCode.PROVIDER_UNAVAILABLE
        else -> BillingFailureCode.PROVIDER_REJECTED
    }
    return BillingFailure(code, retryable, safeMessageKey)
}

private fun productUnavailable(): BillingFailure = BillingFailure(
    code = BillingFailureCode.PRODUCT_UNAVAILABLE,
    retryable = false,
    safeMessageKey = "billing.play.product_unavailable",
)

private fun nonRetryableFailure(safeMessageKey: String): BillingFailure = BillingFailure(
    code = BillingFailureCode.PROVIDER_REJECTED,
    retryable = false,
    safeMessageKey = safeMessageKey,
)
