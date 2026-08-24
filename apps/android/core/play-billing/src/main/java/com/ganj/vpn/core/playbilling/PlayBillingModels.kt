package com.ganj.vpn.core.playbilling

import com.ganj.vpn.core.billing.BillingFailure
import com.ganj.vpn.core.billing.EntitlementActivationReceipt
import com.ganj.vpn.core.billing.EntitlementActivationState
import com.ganj.vpn.core.billing.ProviderPurchase
import com.ganj.vpn.core.billing.ProviderPurchaseReference
import com.ganj.vpn.core.billing.PurchaseProofHandle
import com.ganj.vpn.core.billing.ServerPurchaseVerificationReceipt

/** A catalog representation that deliberately excludes Play offer and purchase tokens. */
data class PlaySubscriptionProduct(
    val productId: String,
    val title: String,
    val description: String,
    val offers: List<PlaySubscriptionOffer>,
) {
    init {
        require(productId.isNotBlank())
        require(title.isNotBlank())
        require(offers.all { it.productId == productId })
    }
}

/**
 * Stable catalog reference understood only by this module.
 *
 * [reference] contains a base-plan/offer identifier, never the short-lived Play offer token.
 */
data class PlaySubscriptionOffer(
    val productId: String,
    val reference: String,
    val basePlanId: String,
    val offerId: String?,
    val tags: Set<String>,
    val pricingPhases: List<PlayPricingPhase>,
) {
    init {
        require(productId.isNotBlank())
        require(reference.isNotBlank())
        require(basePlanId.isNotBlank())
        require(pricingPhases.isNotEmpty())
    }
}

data class PlayPricingPhase(
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val currencyCode: String,
    val billingPeriodIso8601: String,
    val billingCycleCount: Int,
    val recurrenceMode: Int,
) {
    init {
        require(formattedPrice.isNotBlank())
        require(priceAmountMicros >= 0)
        require(currencyCode.length == 3)
        require(billingPeriodIso8601.isNotBlank())
        require(billingCycleCount >= 0)
    }
}

sealed interface PlayCatalogResult {
    data class Success(
        val products: List<PlaySubscriptionProduct>,
        val unfetchedProductCount: Int,
    ) : PlayCatalogResult {
        init {
            require(unfetchedProductCount >= 0)
        }
    }

    data class Failed(val failure: BillingFailure) : PlayCatalogResult
}

sealed interface PlayBillingLaunchResult {
    /** The Play UI was accepted for launch; final state arrives through the purchase observer. */
    data object Launched : PlayBillingLaunchResult

    data class AlreadyOwned(val failure: BillingFailure) : PlayBillingLaunchResult
    data class Rejected(val failure: BillingFailure) : PlayBillingLaunchResult
}

sealed interface PlayPurchaseEvent {
    data class Observed(val purchase: ProviderPurchase) : PlayPurchaseEvent

    /** User dismissal is an observation only; it never activates or revokes an entitlement. */
    data object UserCancelledFlow : PlayPurchaseEvent

    data class Failed(val failure: BillingFailure) : PlayPurchaseEvent
}

fun interface PlayPurchaseObserver {
    fun onPurchaseEvent(event: PlayPurchaseEvent)
}

/**
 * Receipt pair required before a subscription can be acknowledged on-device.
 *
 * Google Play verification must have completed on the backend and that same verification must
 * already have produced an active server entitlement. This module never creates an entitlement.
 */
data class VerifiedPlaySubscription(
    val verification: ServerPurchaseVerificationReceipt,
    val entitlement: EntitlementActivationReceipt,
    val purchaseReference: ProviderPurchaseReference,
    val proofHandle: PurchaseProofHandle,
) {
    init {
        require(verification.provider == com.ganj.vpn.core.billing.BillingProvider.GOOGLE_PLAY)
        require(verification.purchaseReference == purchaseReference)
        require(entitlement.verificationId == verification.verificationId)
        require(entitlement.userId == verification.userId)
        require(entitlement.productId == verification.productId)
        require(entitlement.state == EntitlementActivationState.ACTIVE)
    }
}

sealed interface PlayAcknowledgementResult {
    data object Acknowledged : PlayAcknowledgementResult
    data object AlreadyAcknowledged : PlayAcknowledgementResult
    data object PurchaseNotOwned : PlayAcknowledgementResult
    data object PurchaseStillPending : PlayAcknowledgementResult
    data class Failed(val failure: BillingFailure) : PlayAcknowledgementResult
}
