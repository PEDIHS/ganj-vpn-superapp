package com.ganj.vpn.core.billing

private val identifierPattern = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{2,127}")
private val currencyPattern = Regex("[A-Z]{3}")

@JvmInline
value class BillingOperationId(val value: String) {
    init {
        require(identifierPattern.matches(value)) { "Invalid billing operation identifier" }
    }
}

@JvmInline
value class BillingEventId(val value: String) {
    init {
        require(identifierPattern.matches(value)) { "Invalid billing event identifier" }
    }
}

@JvmInline
value class CheckoutSessionId(val value: String) {
    init {
        require(identifierPattern.matches(value)) { "Invalid checkout session identifier" }
    }
}

@JvmInline
value class ProviderPurchaseReference(val value: String) {
    init {
        require(identifierPattern.matches(value)) { "Invalid provider purchase reference" }
    }
}

@JvmInline
value class ServerVerificationId(val value: String) {
    init {
        require(identifierPattern.matches(value)) { "Invalid verification identifier" }
    }
}

@JvmInline
value class EntitlementId(val value: String) {
    init {
        require(identifierPattern.matches(value)) { "Invalid entitlement identifier" }
    }
}

/**
 * A retry-stable key generated once for a logical billing operation.
 *
 * It is not a credential. The same value must be reused after timeout or process restart; creating
 * a new key for a retry can create a second order or a second entitlement.
 */
@JvmInline
value class BillingIdempotencyKey(val value: String) {
    init {
        require(value.length in 16..128) { "Idempotency key length must be between 16 and 128" }
        require(value.all { it.isLetterOrDigit() || it in "._:-" }) {
            "Idempotency key contains unsupported characters"
        }
    }
}

/**
 * Opaque reference to proof kept in encrypted, app-private storage.
 *
 * A Google Play purchase token, Telegram invoice payload, wallet proof or gateway receipt must be
 * placed in a secure proof vault by its adapter before this object is created. Raw proof is never a
 * property of a billing state, event, log or analytics record. The verifier resolves this handle,
 * sends the proof to the trusted backend, then deletes it according to its retention policy.
 */
class PurchaseProofHandle private constructor(
    val reference: String,
) {
    init {
        require(identifierPattern.matches(reference)) { "Invalid purchase proof handle" }
    }

    override fun equals(other: Any?): Boolean =
        other is PurchaseProofHandle && reference == other.reference

    override fun hashCode(): Int = reference.hashCode()

    override fun toString(): String = "PurchaseProofHandle([REDACTED])"

    companion object {
        fun fromSecureVault(reference: String): PurchaseProofHandle = PurchaseProofHandle(reference)
    }
}

enum class BillingProvider {
    GOOGLE_PLAY,
    WALLET,
    TELEGRAM,
    PAYMENT_GATEWAY,
}

enum class BillingOperationKind {
    CHECKOUT,
    RESTORE,
    RECONCILE,
}

enum class ProviderPurchaseState {
    PENDING,
    PURCHASED,
    CANCELLED,
    REFUNDED,
}

enum class BillingStatus {
    REQUESTED,
    AWAITING_USER,
    PENDING_PROVIDER,
    VERIFYING_BACKEND,
    PENDING_VERIFICATION_RETRY,
    VERIFIED_AWAITING_ENTITLEMENT,
    CANCELLATION_PENDING,
    COMPLETED,
    REFUND_VERIFYING,
    REFUND_PENDING_REVOCATION,
    REFUNDED,
    CANCELLED,
    FAILED,
}

enum class BillingFailureCode {
    PRODUCT_UNAVAILABLE,
    PROVIDER_UNAVAILABLE,
    PROVIDER_REJECTED,
    PAYMENT_DECLINED,
    NETWORK_UNAVAILABLE,
    BACKEND_UNAVAILABLE,
    VERIFICATION_REJECTED,
    VERIFICATION_MISMATCH,
    ENTITLEMENT_SYNC_FAILED,
    REFUND_REJECTED,
    RESTORE_FAILED,
    RECONCILIATION_FAILED,
    CONFLICT,
}

data class BillingFailure(
    val code: BillingFailureCode,
    val retryable: Boolean,
    val safeMessageKey: String,
) {
    init {
        require(identifierPattern.matches(safeMessageKey)) { "Message key must be a safe identifier" }
    }
}

data class BillingMoney(
    val amountMinor: Long,
    val currencyCode: String,
) {
    init {
        require(amountMinor >= 0) { "Amount cannot be negative" }
        require(currencyPattern.matches(currencyCode)) { "Currency must be an ISO 4217 code" }
    }
}

data class BillingOffer(
    val productId: String,
    val offerId: String?,
    val title: String,
    val price: BillingMoney,
    val provider: BillingProvider,
) {
    init {
        require(identifierPattern.matches(productId)) { "Invalid product identifier" }
        require(offerId == null || identifierPattern.matches(offerId)) { "Invalid offer identifier" }
        require(title.isNotBlank())
    }
}

sealed interface BillingOperationCommand {
    val operationId: BillingOperationId
    val eventId: BillingEventId
    val userId: String
    val provider: BillingProvider
    val idempotencyKey: BillingIdempotencyKey

    data class Checkout(
        override val operationId: BillingOperationId,
        override val eventId: BillingEventId,
        override val userId: String,
        override val provider: BillingProvider,
        override val idempotencyKey: BillingIdempotencyKey,
        val productId: String,
        val offerId: String? = null,
    ) : BillingOperationCommand {
        init {
            require(userId.isNotBlank())
            require(identifierPattern.matches(productId)) { "Invalid product identifier" }
            require(offerId == null || identifierPattern.matches(offerId)) { "Invalid offer identifier" }
        }
    }

    data class Restore(
        override val operationId: BillingOperationId,
        override val eventId: BillingEventId,
        override val userId: String,
        override val provider: BillingProvider,
        override val idempotencyKey: BillingIdempotencyKey,
    ) : BillingOperationCommand {
        init {
            require(userId.isNotBlank())
        }
    }

    data class Reconcile(
        override val operationId: BillingOperationId,
        override val eventId: BillingEventId,
        override val userId: String,
        override val provider: BillingProvider,
        override val idempotencyKey: BillingIdempotencyKey,
    ) : BillingOperationCommand {
        init {
            require(userId.isNotBlank())
        }
    }
}

data class ProviderPurchase(
    val provider: BillingProvider,
    val purchaseReference: ProviderPurchaseReference,
    val productId: String,
    val state: ProviderPurchaseState,
    val proofHandle: PurchaseProofHandle?,
    val observedAtEpochMillis: Long,
) {
    init {
        require(identifierPattern.matches(productId)) { "Invalid product identifier" }
        require(observedAtEpochMillis > 0)
        require(state != ProviderPurchaseState.PURCHASED || proofHandle != null) {
            "Purchased observations require an opaque proof handle"
        }
    }
}

data class ServerPurchaseVerificationReceipt(
    val verificationId: ServerVerificationId,
    val operationId: BillingOperationId,
    val userId: String,
    val productId: String,
    val provider: BillingProvider,
    val purchaseReference: ProviderPurchaseReference,
    val verifiedAtEpochMillis: Long,
) {
    init {
        require(userId.isNotBlank())
        require(identifierPattern.matches(productId)) { "Invalid product identifier" }
        require(verifiedAtEpochMillis > 0)
    }
}

enum class EntitlementActivationState {
    ACTIVE,
    REVOKED,
}

data class EntitlementActivationReceipt(
    val verificationId: ServerVerificationId,
    val entitlementId: EntitlementId,
    val serviceId: String,
    val userId: String,
    val productId: String,
    val state: EntitlementActivationState,
    val effectiveAtEpochMillis: Long,
    val expiresAtEpochMillis: Long?,
) {
    init {
        require(identifierPattern.matches(serviceId)) { "Invalid service identifier" }
        require(userId.isNotBlank())
        require(identifierPattern.matches(productId)) { "Invalid product identifier" }
        require(effectiveAtEpochMillis > 0)
        require(expiresAtEpochMillis == null || expiresAtEpochMillis > effectiveAtEpochMillis)
    }
}

data class ServerRefundVerificationReceipt(
    val verificationId: ServerVerificationId,
    val operationId: BillingOperationId,
    val purchaseReference: ProviderPurchaseReference,
    val verifiedAtEpochMillis: Long,
) {
    init {
        require(verifiedAtEpochMillis > 0)
    }
}

data class EntitlementRevocationReceipt(
    val verificationId: ServerVerificationId,
    val entitlementId: EntitlementId,
    val revokedAtEpochMillis: Long,
) {
    init {
        require(revokedAtEpochMillis > 0)
    }
}

data class BillingOperationSnapshot(
    val operationId: BillingOperationId,
    val kind: BillingOperationKind,
    val status: BillingStatus,
    val userId: String,
    val provider: BillingProvider,
    val idempotencyKey: BillingIdempotencyKey,
    val productId: String?,
    val offerId: String? = null,
    val checkoutSessionId: CheckoutSessionId? = null,
    val purchase: ProviderPurchase? = null,
    val verificationReceipt: ServerPurchaseVerificationReceipt? = null,
    val entitlementReceipt: EntitlementActivationReceipt? = null,
    val refundVerificationReceipt: ServerRefundVerificationReceipt? = null,
    val failure: BillingFailure? = null,
    val processedEventIds: Set<BillingEventId> = emptySet(),
    val revision: Long = 0,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(userId.isNotBlank())
        require(productId == null || identifierPattern.matches(productId))
        require(offerId == null || identifierPattern.matches(offerId))
        require(revision >= 0)
        require(updatedAtEpochMillis > 0)
        require(processedEventIds.size <= 256) { "Processed event window is bounded" }
    }

    val canActivateEntitlement: Boolean
        get() = status == BillingStatus.VERIFIED_AWAITING_ENTITLEMENT && verificationReceipt != null

    val isTerminal: Boolean
        get() = status in setOf(
            BillingStatus.COMPLETED,
            BillingStatus.REFUNDED,
            BillingStatus.CANCELLED,
            BillingStatus.FAILED,
        )
}
