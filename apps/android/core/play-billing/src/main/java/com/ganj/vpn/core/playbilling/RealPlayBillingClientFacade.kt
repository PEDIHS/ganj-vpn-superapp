package com.ganj.vpn.core.playbilling

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * The only class that touches BillingClient. It enables Google Play billing only: none of the
 * alternative/external billing APIs are enabled or called.
 */
internal class RealPlayBillingClientFacade(
    context: Context,
    private val purchaseListener: PlayPurchaseSdkListener,
) : PlayBillingClientFacade, PurchasesUpdatedListener {
    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts() // Mandatory SDK acknowledgement even for SUBS-only apps.
                .enablePrepaidPlans()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    override val isReady: Boolean
        get() = client.isReady

    override fun startConnection(listener: PlayBillingClientFacade.ConnectionListener) {
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    listener.onSetupFinished(billingResult.toClientResult())
                }

                override fun onBillingServiceDisconnected() {
                    listener.onServiceDisconnected()
                }
            },
        )
    }

    override fun endConnection() = client.endConnection()

    override fun querySubscriptionProducts(
        productIds: Set<String>,
        callback: (PlayClientResult, PlayProductQueryResult) -> Unit,
    ) {
        val products = productIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        client.queryProductDetailsAsync(params) { result, queryResult ->
            callback(
                result.toClientResult(),
                PlayProductQueryResult(
                    products = queryResult.productDetailsList.map { it.toSnapshot() },
                    unfetchedProductCount = queryResult.unfetchedProductList.size,
                ),
            )
        }
    }

    override fun querySubscriptionPurchases(
        includeSuspended: Boolean,
        callback: (PlayClientResult, List<PlayPurchaseSnapshot>) -> Unit,
    ) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .includeSuspendedSubscriptions(includeSuspended)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            callback(result.toClientResult(), purchases.map { it.toSnapshot() })
        }
    }

    override fun launchSubscriptionFlow(
        activity: Activity,
        product: PlayProductSnapshot,
        offer: PlayOfferSnapshot,
        obfuscatedAccountId: String,
    ): PlayClientResult {
        val nativeProduct = product.nativeProduct as? ProductDetails
            ?: return PlayClientResult(PlayResponseCode.DEVELOPER_ERROR)
        val productParams = offer.launchToken.use { offerToken ->
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(nativeProduct)
                .setOfferToken(offerToken)
                .build()
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .setObfuscatedAccountId(obfuscatedAccountId)
            .build()
        return client.launchBillingFlow(activity, flowParams).toClientResult()
    }

    override fun acknowledgeSubscription(
        token: PlayPurchaseToken,
        callback: (PlayClientResult) -> Unit,
    ) {
        val params = token.use { value ->
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(value)
                .build()
        }
        client.acknowledgePurchase(params) { result -> callback(result.toClientResult()) }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        purchaseListener.onPurchasesUpdated(
            result.toClientResult(),
            purchases?.map { it.toSnapshot() },
        )
    }

    private fun ProductDetails.toSnapshot(): PlayProductSnapshot = PlayProductSnapshot(
        productId = productId,
        title = title,
        description = description,
        offers = subscriptionOfferDetails.orEmpty().map { it.toSnapshot() },
        nativeProduct = this,
    )

    private fun ProductDetails.SubscriptionOfferDetails.toSnapshot(): PlayOfferSnapshot =
        PlayOfferSnapshot(
            reference = offerReference(basePlanId, offerId),
            basePlanId = basePlanId,
            offerId = offerId,
            tags = offerTags.toSet(),
            pricingPhases = pricingPhases.pricingPhaseList.map { phase ->
                PlayPricingPhaseSnapshot(
                    formattedPrice = phase.formattedPrice,
                    priceAmountMicros = phase.priceAmountMicros,
                    currencyCode = phase.priceCurrencyCode,
                    billingPeriod = phase.billingPeriod,
                    billingCycleCount = phase.billingCycleCount,
                    recurrenceMode = phase.recurrenceMode,
                )
            },
            launchToken = PlayOfferToken.fromBillingClient(offerToken),
        )

    private fun Purchase.toSnapshot(): PlayPurchaseSnapshot = PlayPurchaseSnapshot(
        products = products.toList(),
        token = PlayPurchaseToken.fromBillingClient(purchaseToken),
        state = when (purchaseState) {
            Purchase.PurchaseState.PENDING -> PlayPurchaseState.PENDING
            Purchase.PurchaseState.PURCHASED -> PlayPurchaseState.PURCHASED
            else -> PlayPurchaseState.UNSPECIFIED
        },
        acknowledged = isAcknowledged,
        purchaseTimeEpochMillis = purchaseTime,
    )

    private fun BillingResult.toClientResult(): PlayClientResult = PlayClientResult(responseCode)
}

internal fun offerReference(basePlanId: String, offerId: String?): String =
    "gp.offer.${sha256Hex("$basePlanId\u0000${offerId.orEmpty()}").take(32)}"
