package com.ganj.vpn.core.playbilling

import android.app.Activity

internal object PlayResponseCode {
    const val OK = 0
    const val USER_CANCELED = 1
    const val SERVICE_UNAVAILABLE = 2
    const val BILLING_UNAVAILABLE = 3
    const val ITEM_UNAVAILABLE = 4
    const val DEVELOPER_ERROR = 5
    const val ERROR = 6
    const val ITEM_ALREADY_OWNED = 7
    const val ITEM_NOT_OWNED = 8
    const val NETWORK_ERROR = 12
    const val SERVICE_DISCONNECTED = -1
}

internal data class PlayClientResult(val responseCode: Int)

internal enum class PlayPurchaseState {
    UNSPECIFIED,
    PENDING,
    PURCHASED,
}

/** Raw token wrapper with a redacted string representation and no public accessor. */
internal class PlayPurchaseToken private constructor(private val value: String) {
    init {
        require(value.isNotBlank())
    }

    inline fun <T> use(block: (String) -> T): T = block(value)

    fun fingerprint(): String = sha256Hex(value)

    override fun toString(): String = "PlayPurchaseToken([REDACTED])"

    companion object {
        fun fromBillingClient(value: String): PlayPurchaseToken = PlayPurchaseToken(value)
    }
}

internal class PlayOfferToken private constructor(private val value: String) {
    inline fun <T> use(block: (String) -> T): T = block(value)
    override fun toString(): String = "PlayOfferToken([REDACTED])"

    companion object {
        fun fromBillingClient(value: String): PlayOfferToken = PlayOfferToken(value)
    }
}

internal data class PlayPricingPhaseSnapshot(
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val currencyCode: String,
    val billingPeriod: String,
    val billingCycleCount: Int,
    val recurrenceMode: Int,
)

internal class PlayOfferSnapshot(
    val reference: String,
    val basePlanId: String,
    val offerId: String?,
    val tags: Set<String>,
    val pricingPhases: List<PlayPricingPhaseSnapshot>,
    val launchToken: PlayOfferToken,
) {
    override fun toString(): String =
        "PlayOfferSnapshot(reference=$reference, basePlanId=$basePlanId, offerId=$offerId, " +
            "tags=$tags, pricingPhases=$pricingPhases, launchToken=[REDACTED])"
}

internal class PlayProductSnapshot(
    val productId: String,
    val title: String,
    val description: String,
    val offers: List<PlayOfferSnapshot>,
    val nativeProduct: Any,
)

internal data class PlayProductQueryResult(
    val products: List<PlayProductSnapshot>,
    val unfetchedProductCount: Int,
)

internal class PlayPurchaseSnapshot(
    val products: List<String>,
    val token: PlayPurchaseToken,
    val state: PlayPurchaseState,
    val acknowledged: Boolean,
    val purchaseTimeEpochMillis: Long,
) {
    override fun toString(): String =
        "PlayPurchaseSnapshot(products=$products, token=[REDACTED], state=$state, " +
            "acknowledged=$acknowledged, purchaseTimeEpochMillis=$purchaseTimeEpochMillis)"
}

internal interface PlayBillingClientFacade {
    val isReady: Boolean

    fun startConnection(listener: ConnectionListener)
    fun endConnection()

    fun querySubscriptionProducts(
        productIds: Set<String>,
        callback: (PlayClientResult, PlayProductQueryResult) -> Unit,
    )

    fun querySubscriptionPurchases(
        includeSuspended: Boolean,
        callback: (PlayClientResult, List<PlayPurchaseSnapshot>) -> Unit,
    )

    fun launchSubscriptionFlow(
        activity: Activity,
        product: PlayProductSnapshot,
        offer: PlayOfferSnapshot,
        obfuscatedAccountId: String,
    ): PlayClientResult

    fun acknowledgeSubscription(
        token: PlayPurchaseToken,
        callback: (PlayClientResult) -> Unit,
    )

    interface ConnectionListener {
        fun onSetupFinished(result: PlayClientResult)
        fun onServiceDisconnected()
    }
}

internal fun interface PlayPurchaseSdkListener {
    fun onPurchasesUpdated(result: PlayClientResult, purchases: List<PlayPurchaseSnapshot>?)
}

internal fun sha256Hex(value: String): String =
    java.security.MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .toLowerHex()

internal fun ByteArray.toLowerHex(): String =
    joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
