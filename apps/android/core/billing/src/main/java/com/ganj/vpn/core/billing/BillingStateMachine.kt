package com.ganj.vpn.core.billing

internal enum class TransitionRejection {
    DUPLICATE_EVENT,
    TERMINAL_OPERATION,
    INVALID_STATE,
    WRONG_OPERATION,
    WRONG_PROVIDER,
    WRONG_PRODUCT,
    WRONG_PURCHASE,
    MISSING_SECURE_PROOF,
}

internal sealed interface BillingTransition {
    data class Applied(val state: BillingOperationSnapshot) : BillingTransition

    data class Rejected(
        val state: BillingOperationSnapshot,
        val reason: TransitionRejection,
    ) : BillingTransition
}

/**
 * Pure state machine for a single durable billing operation.
 *
 * Methods accepting backend receipts are internal so UI code cannot manufacture a success event.
 * A production coordinator in this module must call them only with results returned by the
 * authenticated [BillingBackendVerifier] and [EntitlementSyncGateway] boundaries.
 */
internal class BillingStateMachine {
    fun start(
        command: BillingOperationCommand,
        nowEpochMillis: Long,
    ): BillingOperationSnapshot {
        require(nowEpochMillis > 0)
        val kind = when (command) {
            is BillingOperationCommand.Checkout -> BillingOperationKind.CHECKOUT
            is BillingOperationCommand.Restore -> BillingOperationKind.RESTORE
            is BillingOperationCommand.Reconcile -> BillingOperationKind.RECONCILE
        }
        val productId = (command as? BillingOperationCommand.Checkout)?.productId
        val offerId = (command as? BillingOperationCommand.Checkout)?.offerId
        return BillingOperationSnapshot(
            operationId = command.operationId,
            kind = kind,
            status = BillingStatus.REQUESTED,
            userId = command.userId,
            provider = command.provider,
            idempotencyKey = command.idempotencyKey,
            productId = productId,
            offerId = offerId,
            processedEventIds = setOf(command.eventId),
            revision = 1,
            updatedAtEpochMillis = nowEpochMillis,
        )
    }

    fun checkoutPrepared(
        state: BillingOperationSnapshot,
        eventId: BillingEventId,
        action: ProviderClientAction,
        nowEpochMillis: Long,
    ): BillingTransition = transition(
        state = state,
        eventId = eventId,
        allowedStatuses = setOf(BillingStatus.REQUESTED),
        nowEpochMillis = nowEpochMillis,
    ) {
        if (state.kind != BillingOperationKind.CHECKOUT) {
            return@transition TransitionRejection.WRONG_OPERATION
        }
        if (!action.matches(state.provider, state.productId, state.offerId)) {
            return@transition TransitionRejection.WRONG_PROVIDER
        }
        state.copy(
            status = BillingStatus.AWAITING_USER,
            checkoutSessionId = action.checkoutSessionId,
            failure = null,
        )
    }

    fun providerPending(
        state: BillingOperationSnapshot,
        eventId: BillingEventId,
        checkoutSessionId: CheckoutSessionId?,
        nowEpochMillis: Long,
    ): BillingTransition = transition(
        state = state,
        eventId = eventId,
        allowedStatuses = setOf(
            BillingStatus.REQUESTED,
            BillingStatus.AWAITING_USER,
            BillingStatus.PENDING_PROVIDER,
        ),
        nowEpochMillis = nowEpochMillis,
    ) {
        if (
            state.checkoutSessionId != null &&
            checkoutSessionId != null &&
            state.checkoutSessionId != checkoutSessionId
        ) {
            return@transition TransitionRejection.WRONG_OPERATION
        }
        state.copy(
            status = BillingStatus.PENDING_PROVIDER,
            checkoutSessionId = state.checkoutSessionId ?: checkoutSessionId,
            failure = null,
        )
    }

    fun purchaseObserved(
        state: BillingOperationSnapshot,
        eventId: BillingEventId,
        purchase: ProviderPurchase,
        nowEpochMillis: Long,
    ): BillingTransition = transition(
        state = state,
        eventId = eventId,
        allowedStatuses = setOf(
            BillingStatus.REQUESTED,
            BillingStatus.AWAITING_USER,
            BillingStatus.PENDING_PROVIDER,
            BillingStatus.PENDING_VERIFICATION_RETRY,
        ),
        nowEpochMillis = nowEpochMillis,
    ) {
        if (purchase.provider != state.provider) {
            return@transition TransitionRejection.WRONG_PROVIDER
        }
        if (state.productId != null && purchase.productId != state.productId) {
            return@transition TransitionRejection.WRONG_PRODUCT
        }

        when (purchase.state) {
            ProviderPurchaseState.PENDING -> state.copy(
                status = BillingStatus.PENDING_PROVIDER,
                productId = state.productId ?: purchase.productId,
                purchase = purchase,
                failure = null,
            )

            ProviderPurchaseState.PURCHASED -> {
                if (purchase.proofHandle == null) {
                    TransitionRejection.MISSING_SECURE_PROOF
                } else {
                    state.copy(
                        status = BillingStatus.VERIFYING_BACKEND,
                        productId = state.productId ?: purchase.productId,
                        purchase = purchase,
                        failure = null,
                    )
                }
            }

            ProviderPurchaseState.CANCELLED -> state.copy(
                status = BillingStatus.CANCELLED,
                productId = state.productId ?: purchase.productId,
                purchase = purchase,
                failure = null,
            )

            ProviderPurchaseState.REFUNDED -> TransitionRejection.INVALID_STATE
        }
    }

    fun verificationResult(
        state: BillingOperationSnapshot,
        eventId: BillingEventId,
        result: PurchaseVerificationResult,
        nowEpochMillis: Long,
    ): BillingTransition = transition(
        state = state,
        eventId = eventId,
        allowedStatuses = setOf(BillingStatus.VERIFYING_BACKEND),
        nowEpochMillis = nowEpochMillis,
    ) {
        when (result) {
            is PurchaseVerificationResult.Verified -> {
                if (!result.receipt.matches(state)) {
                    state.failed(BillingFailureCode.VERIFICATION_MISMATCH)
                } else {
                    state.copy(
                        status = BillingStatus.VERIFIED_AWAITING_ENTITLEMENT,
                        verificationReceipt = result.receipt,
                        failure = null,
                    )
                }
            }

            is PurchaseVerificationResult.Rejected -> state.copy(
                status = BillingStatus.FAILED,
                failure = result.failure.copy(retryable = false),
            )

            is PurchaseVerificationResult.RetryLater -> state.copy(
                status = BillingStatus.PENDING_VERIFICATION_RETRY,
                failure = result.failure,
            )
        }
    }

    fun retryVerification(
        state: BillingOperationSnapshot,
        eventId: BillingEventId,
        nowEpochMillis: Long,
    ): BillingTransition = transition(
        state = state,
        eventId = eventId,
        allowedStatuses = setOf(BillingStatus.PENDING_VERIFICATION_RETRY),
        nowEpochMillis = nowEpochMillis,
    ) {
        if (state.purchase?.proofHandle == null) {
            TransitionRejection.MISSING_SECURE_PROOF
        } else {
            state.copy(status = BillingStatus.VERIFYING_BACKEND, failure = null)
        }
    }

    fun entitlementSyncResult(
        state: BillingOperationSnapshot,
        eventId: BillingEventId,
        result: EntitlementSyncResult,
        nowEpochMillis: Long,
    ): BillingTransition = transition(
        state = state,
        eventId = eventId,
        allowedStatuses = setOf(BillingStatus.VERIFIED_AWAITING_ENTITLEMENT),
        nowEpochMillis = nowEpochMillis,
    ) {
        when (result) {
            is EntitlementSyncResult.Active -> {
                if (!result.receipt.matchesVerifiedPurchase(state)) {
                    state.failed(BillingFailureCode.VERIFICATION_MISMATCH)
                } else {
                    state.copy(
                        status = BillingStatus.COMPLETED,
                        entitlementReceipt = result.receipt,
                        failure = null,
                    )
                }
            }

            is EntitlementSyncResult.Failed -> state.copy(
                status = BillingStatus.VERIFIED_AWAITING_ENTITLEMENT,
                failure = result.failure,
            )
        }
    }

    fun cancellationRequested(
        state: BillingOperationSnapshot,
        eventId: BillingEventId,
        nowEpochMillis: Long,
    ): BillingTransition = transition(
        state = state,
        eventId = eventId,
        allowedStatuses = setOf(
            BillingStatus.REQUESTED,
            BillingStatus.AWAITING_USER,
            BillingStatus.PENDING_PROVIDER,
        ),
        nowEpochMillis = nowEpochMillis,
    ) {
        if (state.kind != BillingOperationKind.CHECKOUT) {
            TransitionRejection.WRONG_OPERATION
        } else {
            state.copy(status = BillingStatus.CANCELLATION_PENDING, failure = null)
        }
    }

    fun cancellationResult(
        state: BillingOperationSnapshot,
        eventId: BillingEventId,
        result: ProviderCancellationResult,
        nowEpochMillis: Long,
    ): BillingTransition = transition(
        state = state,
        eventId = eventId,
        allowedStatuses = setOf(BillingStatus.CANCELLATION_PENDING),
        nowEpochMillis = nowEpochMillis,
    ) {
        when (result) {
            ProviderCancellationResult.Confirmed -> state.copy(
                status = BillingStatus.CANCELLED,
                failure = null,
            )
            ProviderCancellationResult.StillPending -> state.copy(
                status = BillingStatus.PENDING_PROVIDER,
                failure = null,
            )
            is ProviderCancellationResult.Rejected -> state.copy(
                status = BillingStatus.PENDING_PROVIDER,
                failure = result.failure,
            )
        }
    }

    fun refundObserved(
        state: BillingOperationSnapshot,
        eventId: BillingEventId,
        purchase: ProviderPurchase,
        nowEpochMillis: Long,
    ): BillingTransition = transition(
        state = state,
        eventId = eventId,
        allowedStatuses = setOf(BillingStatus.COMPLETED),
        nowEpochMillis = nowEpochMillis,
    ) {
        if (purchase.provider != state.provider) {
            return@transition TransitionRejection.WRONG_PROVIDER
        }
        if (
            purchase.state != ProviderPurchaseState.REFUNDED ||
            purchase.purchaseReference != state.purchase?.purchaseReference
        ) {
            return@transition TransitionRejection.WRONG_PURCHASE
        }
        if (purchase.proofHandle == null) {
            return@transition TransitionRejection.MISSING_SECURE_PROOF
        }
        state.copy(
            status = BillingStatus.REFUND_VERIFYING,
            purchase = purchase,
            failure = null,
        )
    }

    fun refundVerificationResult(
        state: BillingOperationSnapshot,
        eventId: BillingEventId,
        result: RefundVerificationResult,
        nowEpochMillis: Long,
    ): BillingTransition = transition(
        state = state,
        eventId = eventId,
        allowedStatuses = setOf(BillingStatus.REFUND_VERIFYING),
        nowEpochMillis = nowEpochMillis,
    ) {
        when (result) {
            is RefundVerificationResult.Verified -> {
                if (!result.receipt.matchesRefund(state)) {
                    state.copy(
                        status = BillingStatus.COMPLETED,
                        failure = failure(BillingFailureCode.VERIFICATION_MISMATCH, retryable = false),
                    )
                } else {
                    state.copy(
                        status = BillingStatus.REFUND_PENDING_REVOCATION,
                        refundVerificationReceipt = result.receipt,
                        failure = null,
                    )
                }
            }

            is RefundVerificationResult.Rejected -> state.copy(
                status = BillingStatus.COMPLETED,
                failure = result.failure.copy(retryable = false),
            )

            is RefundVerificationResult.RetryLater -> state.copy(
                status = BillingStatus.REFUND_VERIFYING,
                failure = result.failure,
            )
        }
    }

    fun entitlementRevocationResult(
        state: BillingOperationSnapshot,
        eventId: BillingEventId,
        result: EntitlementRevocationResult,
        nowEpochMillis: Long,
    ): BillingTransition = transition(
        state = state,
        eventId = eventId,
        allowedStatuses = setOf(BillingStatus.REFUND_PENDING_REVOCATION),
        nowEpochMillis = nowEpochMillis,
    ) {
        when (result) {
            is EntitlementRevocationResult.Revoked -> {
                val entitlement = state.entitlementReceipt
                val refund = state.refundVerificationReceipt
                if (
                    entitlement == null ||
                    refund == null ||
                    result.receipt.entitlementId != entitlement.entitlementId ||
                    result.receipt.verificationId != refund.verificationId
                ) {
                    state.copy(
                        failure = failure(BillingFailureCode.VERIFICATION_MISMATCH, retryable = false),
                    )
                } else {
                    state.copy(status = BillingStatus.REFUNDED, failure = null)
                }
            }

            is EntitlementRevocationResult.Failed -> state.copy(
                status = BillingStatus.REFUND_PENDING_REVOCATION,
                failure = result.failure,
            )
        }
    }

    fun completeEmptyRecovery(
        state: BillingOperationSnapshot,
        eventId: BillingEventId,
        nowEpochMillis: Long,
    ): BillingTransition = transition(
        state = state,
        eventId = eventId,
        allowedStatuses = setOf(BillingStatus.REQUESTED, BillingStatus.PENDING_PROVIDER),
        nowEpochMillis = nowEpochMillis,
    ) {
        if (state.kind == BillingOperationKind.CHECKOUT) {
            TransitionRejection.WRONG_OPERATION
        } else {
            state.copy(status = BillingStatus.COMPLETED, failure = null)
        }
    }

    fun fail(
        state: BillingOperationSnapshot,
        eventId: BillingEventId,
        billingFailure: BillingFailure,
        nowEpochMillis: Long,
    ): BillingTransition = transition(
        state = state,
        eventId = eventId,
        allowedStatuses = BillingStatus.entries.filterNot {
            it in setOf(
                BillingStatus.COMPLETED,
                BillingStatus.REFUNDED,
                BillingStatus.CANCELLED,
                BillingStatus.FAILED,
            )
        }.toSet(),
        nowEpochMillis = nowEpochMillis,
    ) {
        state.copy(status = BillingStatus.FAILED, failure = billingFailure)
    }

    private fun transition(
        state: BillingOperationSnapshot,
        eventId: BillingEventId,
        allowedStatuses: Set<BillingStatus>,
        nowEpochMillis: Long,
        change: () -> Any,
    ): BillingTransition {
        require(nowEpochMillis > 0)
        if (eventId in state.processedEventIds) {
            return BillingTransition.Rejected(state, TransitionRejection.DUPLICATE_EVENT)
        }
        if (state.isTerminal && state.status !in allowedStatuses) {
            return BillingTransition.Rejected(state, TransitionRejection.TERMINAL_OPERATION)
        }
        if (state.status !in allowedStatuses) {
            return BillingTransition.Rejected(state, TransitionRejection.INVALID_STATE)
        }

        return when (val changed = change()) {
            is TransitionRejection -> BillingTransition.Rejected(state, changed)
            is BillingOperationSnapshot -> BillingTransition.Applied(
                changed.copy(
                    processedEventIds = remember(state.processedEventIds, eventId),
                    revision = state.revision + 1,
                    updatedAtEpochMillis = nowEpochMillis,
                ),
            )
            else -> error("Unexpected billing transition result: ${changed::class.java.name}")
        }
    }

    private fun remember(
        processed: Set<BillingEventId>,
        eventId: BillingEventId,
    ): Set<BillingEventId> = (processed + eventId).toList().takeLast(256).toSet()

    private fun BillingOperationSnapshot.failed(code: BillingFailureCode): BillingOperationSnapshot = copy(
        status = BillingStatus.FAILED,
        failure = failure(code, retryable = false),
    )

    private fun failure(
        code: BillingFailureCode,
        retryable: Boolean,
    ): BillingFailure = BillingFailure(
        code = code,
        retryable = retryable,
        safeMessageKey = "billing.${code.name.lowercase()}",
    )

    private fun ProviderClientAction.matches(
        provider: BillingProvider,
        expectedProductId: String?,
        expectedOfferId: String?,
    ): Boolean = when (this) {
        is ProviderClientAction.LaunchGooglePlay ->
            provider == BillingProvider.GOOGLE_PLAY &&
                productId == expectedProductId &&
                offerId == expectedOfferId
        is ProviderClientAction.ApproveWalletDebit -> provider == BillingProvider.WALLET
        is ProviderClientAction.OpenTelegramInvoice -> provider == BillingProvider.TELEGRAM
        is ProviderClientAction.OpenPaymentRedirect -> provider == BillingProvider.PAYMENT_GATEWAY
    }

    private fun ServerPurchaseVerificationReceipt.matches(
        state: BillingOperationSnapshot,
    ): Boolean =
        operationId == state.operationId &&
            userId == state.userId &&
            productId == state.productId &&
            provider == state.provider &&
            purchaseReference == state.purchase?.purchaseReference

    private fun EntitlementActivationReceipt.matchesVerifiedPurchase(
        snapshot: BillingOperationSnapshot,
    ): Boolean =
        state == EntitlementActivationState.ACTIVE &&
            verificationId == snapshot.verificationReceipt?.verificationId &&
            userId == snapshot.userId &&
            productId == snapshot.productId

    private fun ServerRefundVerificationReceipt.matchesRefund(
        state: BillingOperationSnapshot,
    ): Boolean =
        operationId == state.operationId &&
            purchaseReference == state.purchase?.purchaseReference
}
