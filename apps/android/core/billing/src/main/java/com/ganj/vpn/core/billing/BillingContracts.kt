package com.ganj.vpn.core.billing

/**
 * Provider-neutral boundary for billing SDKs and direct-payment launchers.
 *
 * Implementations must never return a raw purchase token. They first move provider proof into an
 * encrypted, app-private vault and return only [PurchaseProofHandle] in [ProviderPurchase].
 */
interface BillingGateway {
    val provider: BillingProvider

    suspend fun prepareCheckout(request: GatewayCheckoutRequest): GatewayCheckoutResult

    suspend fun queryOwnedPurchases(request: OwnedPurchasesRequest): OwnedPurchasesResult

    suspend fun requestCancellation(request: ProviderCancellationRequest): ProviderCancellationResult
}

interface GooglePlayBillingGateway : BillingGateway {
    override val provider: BillingProvider
        get() = BillingProvider.GOOGLE_PLAY
}

interface WalletBillingGateway : BillingGateway {
    override val provider: BillingProvider
        get() = BillingProvider.WALLET
}

interface TelegramBillingGateway : BillingGateway {
    override val provider: BillingProvider
        get() = BillingProvider.TELEGRAM
}

interface PaymentGatewayBillingGateway : BillingGateway {
    override val provider: BillingProvider
        get() = BillingProvider.PAYMENT_GATEWAY
}

interface BillingGatewayRegistry {
    fun gateway(provider: BillingProvider): BillingGateway?
}

data class GatewayCheckoutRequest(
    val operationId: BillingOperationId,
    val userId: String,
    val productId: String,
    val offerId: String?,
    val idempotencyKey: BillingIdempotencyKey,
) {
    init {
        require(userId.isNotBlank())
        require(productId.isNotBlank())
    }
}

sealed interface ProviderClientAction {
    val checkoutSessionId: CheckoutSessionId

    /** Product and offer IDs are catalog identifiers, never Play purchase tokens. */
    data class LaunchGooglePlay(
        override val checkoutSessionId: CheckoutSessionId,
        val productId: String,
        val offerId: String?,
        val obfuscatedAccountReference: String,
    ) : ProviderClientAction {
        init {
            require(productId.isNotBlank())
            require(obfuscatedAccountReference.length in 16..128)
        }
    }

    data class ApproveWalletDebit(
        override val checkoutSessionId: CheckoutSessionId,
        val approvalReference: String,
    ) : ProviderClientAction {
        init {
            require(approvalReference.isNotBlank())
        }
    }

    data class OpenTelegramInvoice(
        override val checkoutSessionId: CheckoutSessionId,
        val invoiceSessionReference: String,
    ) : ProviderClientAction {
        init {
            require(invoiceSessionReference.isNotBlank())
        }
    }

    data class OpenPaymentRedirect(
        override val checkoutSessionId: CheckoutSessionId,
        val redirectSessionReference: String,
    ) : ProviderClientAction {
        init {
            require(redirectSessionReference.isNotBlank())
        }
    }
}

sealed interface GatewayCheckoutResult {
    data class UserActionRequired(val action: ProviderClientAction) : GatewayCheckoutResult

    data class Pending(
        val checkoutSessionId: CheckoutSessionId,
        val providerStatusCode: String,
    ) : GatewayCheckoutResult

    data class Rejected(val failure: BillingFailure) : GatewayCheckoutResult
}

data class OwnedPurchasesRequest(
    val operationId: BillingOperationId,
    val userId: String,
    val idempotencyKey: BillingIdempotencyKey,
) {
    init {
        require(userId.isNotBlank())
    }
}

sealed interface OwnedPurchasesResult {
    data class Found(val purchases: List<ProviderPurchase>) : OwnedPurchasesResult {
        init {
            require(purchases.distinctBy { it.purchaseReference }.size == purchases.size) {
                "Provider purchase references must be unique"
            }
        }
    }

    data object None : OwnedPurchasesResult
    data class Failed(val failure: BillingFailure) : OwnedPurchasesResult
}

data class ProviderCancellationRequest(
    val operationId: BillingOperationId,
    val purchaseReference: ProviderPurchaseReference?,
    val idempotencyKey: BillingIdempotencyKey,
)

sealed interface ProviderCancellationResult {
    data object Confirmed : ProviderCancellationResult
    data object StillPending : ProviderCancellationResult
    data class Rejected(val failure: BillingFailure) : ProviderCancellationResult
}

/**
 * Backend-only verification boundary.
 *
 * Implementations resolve [PurchaseVerificationRequest.proofHandle] from secure storage, transmit
 * proof only to the authenticated backend, validate the authenticated response, and purge proof
 * after a terminal result. The client never treats an SDK callback as proof of payment.
 */
interface BillingBackendVerifier {
    suspend fun verifyPurchase(request: PurchaseVerificationRequest): PurchaseVerificationResult

    suspend fun verifyRefund(request: RefundVerificationRequest): RefundVerificationResult
}

data class PurchaseVerificationRequest(
    val operationId: BillingOperationId,
    val userId: String,
    val provider: BillingProvider,
    val productId: String,
    val purchaseReference: ProviderPurchaseReference,
    val proofHandle: PurchaseProofHandle,
    val idempotencyKey: BillingIdempotencyKey,
) {
    init {
        require(userId.isNotBlank())
        require(productId.isNotBlank())
    }
}

sealed interface PurchaseVerificationResult {
    data class Verified(
        val receipt: ServerPurchaseVerificationReceipt,
    ) : PurchaseVerificationResult

    data class Rejected(
        val failure: BillingFailure,
    ) : PurchaseVerificationResult

    data class RetryLater(
        val failure: BillingFailure,
        val retryAfterEpochMillis: Long,
    ) : PurchaseVerificationResult {
        init {
            require(failure.retryable)
            require(retryAfterEpochMillis > 0)
        }
    }
}

data class RefundVerificationRequest(
    val operationId: BillingOperationId,
    val userId: String,
    val provider: BillingProvider,
    val purchaseReference: ProviderPurchaseReference,
    val proofHandle: PurchaseProofHandle,
    val idempotencyKey: BillingIdempotencyKey,
) {
    init {
        require(userId.isNotBlank())
    }
}

sealed interface RefundVerificationResult {
    data class Verified(val receipt: ServerRefundVerificationReceipt) : RefundVerificationResult
    data class Rejected(val failure: BillingFailure) : RefundVerificationResult

    data class RetryLater(
        val failure: BillingFailure,
        val retryAfterEpochMillis: Long,
    ) : RefundVerificationResult {
        init {
            require(failure.retryable)
            require(retryAfterEpochMillis > 0)
        }
    }
}

/**
 * Refreshes server-authoritative services after a verified purchase.
 *
 * This boundary does not create an entitlement locally. A successful result is an authenticated
 * receipt for an entitlement already activated by the backend transaction.
 */
interface EntitlementSyncGateway {
    suspend fun syncAfterVerification(
        request: EntitlementSyncRequest,
    ): EntitlementSyncResult

    suspend fun syncRevocation(
        request: EntitlementRevocationRequest,
    ): EntitlementRevocationResult
}

data class EntitlementSyncRequest(
    val operationId: BillingOperationId,
    val userId: String,
    val productId: String,
    val verificationId: ServerVerificationId,
    val idempotencyKey: BillingIdempotencyKey,
)

sealed interface EntitlementSyncResult {
    data class Active(val receipt: EntitlementActivationReceipt) : EntitlementSyncResult
    data class Failed(val failure: BillingFailure) : EntitlementSyncResult
}

data class EntitlementRevocationRequest(
    val operationId: BillingOperationId,
    val userId: String,
    val entitlementId: EntitlementId,
    val refundVerificationId: ServerVerificationId,
    val idempotencyKey: BillingIdempotencyKey,
)

sealed interface EntitlementRevocationResult {
    data class Revoked(val receipt: EntitlementRevocationReceipt) : EntitlementRevocationResult
    data class Failed(val failure: BillingFailure) : EntitlementRevocationResult
}

interface BillingReconciliationGateway {
    suspend fun reconcile(request: ReconciliationRequest): ReconciliationResult
}

data class ReconciliationRequest(
    val operationId: BillingOperationId,
    val userId: String,
    val provider: BillingProvider,
    val idempotencyKey: BillingIdempotencyKey,
) {
    init {
        require(userId.isNotBlank())
    }
}

sealed interface ReconciliationResult {
    /** No client-side activation is implied; authoritative services must still be refreshed. */
    data class Completed(
        val observedPurchaseReferences: Set<ProviderPurchaseReference>,
        val serverReconciledAtEpochMillis: Long,
    ) : ReconciliationResult {
        init {
            require(serverReconciledAtEpochMillis > 0)
        }
    }

    data class VerificationRequired(val purchases: List<ProviderPurchase>) : ReconciliationResult
    data class Failed(val failure: BillingFailure) : ReconciliationResult
}

/** Durable compare-and-set store used to survive process death and duplicate provider callbacks. */
interface BillingOperationStore {
    suspend fun create(snapshot: BillingOperationSnapshot): Boolean
    suspend fun get(operationId: BillingOperationId): BillingOperationSnapshot?

    suspend fun compareAndSet(
        expectedRevision: Long,
        next: BillingOperationSnapshot,
    ): Boolean
}
