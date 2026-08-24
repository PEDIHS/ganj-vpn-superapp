package com.ganj.vpn.core.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingStateMachineTest {
    private val machine = BillingStateMachine()

    @Test
    fun `provider purchase remains unentitled until backend verification`() {
        val started = startCheckout()
        val observed = applied(
            machine.purchaseObserved(started, event("purchase-seen"), purchased(), 2_000),
        )

        assertEquals(BillingStatus.VERIFYING_BACKEND, observed.status)
        assertNull(observed.verificationReceipt)
        assertNull(observed.entitlementReceipt)
        assertFalse(observed.canActivateEntitlement)
    }

    @Test
    fun `verified purchase completes only after matching server entitlement receipt`() {
        val observed = applied(
            machine.purchaseObserved(startCheckout(), event("purchase-seen"), purchased(), 2_000),
        )
        val verified = applied(
            machine.verificationResult(
                observed,
                event("verified-event"),
                PurchaseVerificationResult.Verified(verificationReceipt()),
                3_000,
            ),
        )

        assertEquals(BillingStatus.VERIFIED_AWAITING_ENTITLEMENT, verified.status)
        assertTrue(verified.canActivateEntitlement)
        assertNull(verified.entitlementReceipt)

        val completed = applied(
            machine.entitlementSyncResult(
                verified,
                event("entitlement-event"),
                EntitlementSyncResult.Active(entitlementReceipt()),
                4_000,
            ),
        )

        assertEquals(BillingStatus.COMPLETED, completed.status)
        assertEquals(entitlement("entitlement-001"), completed.entitlementReceipt?.entitlementId)
        assertTrue(completed.isTerminal)
    }

    @Test
    fun `entitlement confirmation before backend verification is rejected`() {
        val state = applied(
            machine.purchaseObserved(startCheckout(), event("purchase-seen"), purchased(), 2_000),
        )

        val transition = machine.entitlementSyncResult(
            state,
            event("premature-entitlement"),
            EntitlementSyncResult.Active(entitlementReceipt()),
            3_000,
        )

        assertRejected(transition, TransitionRejection.INVALID_STATE)
        assertNull((transition as BillingTransition.Rejected).state.entitlementReceipt)
    }

    @Test
    fun `mismatched backend receipt fails closed`() {
        val observed = applied(
            machine.purchaseObserved(startCheckout(), event("purchase-seen"), purchased(), 2_000),
        )
        val mismatched = verificationReceipt().copy(userId = "another-user")

        val failed = applied(
            machine.verificationResult(
                observed,
                event("verified-event"),
                PurchaseVerificationResult.Verified(mismatched),
                3_000,
            ),
        )

        assertEquals(BillingStatus.FAILED, failed.status)
        assertEquals(BillingFailureCode.VERIFICATION_MISMATCH, failed.failure?.code)
        assertNull(failed.entitlementReceipt)
    }

    @Test
    fun `verification retry preserves proof purchase and idempotency key`() {
        val observed = applied(
            machine.purchaseObserved(startCheckout(), event("purchase-seen"), purchased(), 2_000),
        )
        val retry = applied(
            machine.verificationResult(
                observed,
                event("verify-timeout"),
                PurchaseVerificationResult.RetryLater(
                    failure = retryableFailure(BillingFailureCode.BACKEND_UNAVAILABLE),
                    retryAfterEpochMillis = 4_000,
                ),
                3_000,
            ),
        )
        val retried = applied(machine.retryVerification(retry, event("verify-retry"), 4_000))

        assertEquals(BillingStatus.VERIFYING_BACKEND, retried.status)
        assertEquals(observed.idempotencyKey, retried.idempotencyKey)
        assertEquals(observed.purchase, retried.purchase)
    }

    @Test
    fun `duplicate callback is idempotently rejected without mutating revision`() {
        val started = startCheckout()
        val first = applied(
            machine.purchaseObserved(started, event("purchase-seen"), purchased(), 2_000),
        )

        val duplicate = machine.purchaseObserved(
            first,
            event("purchase-seen"),
            purchased(),
            3_000,
        )

        assertRejected(duplicate, TransitionRejection.DUPLICATE_EVENT)
        assertSame(first, (duplicate as BillingTransition.Rejected).state)
        assertEquals(first.revision, duplicate.state.revision)
    }

    @Test
    fun `cancel request is not terminal until provider confirms`() {
        val prepared = applied(
            machine.checkoutPrepared(
                startCheckout(),
                event("checkout-prepared"),
                ProviderClientAction.LaunchGooglePlay(
                    checkoutSessionId = session("checkout-session-001"),
                    productId = PRODUCT_ID,
                    offerId = null,
                    obfuscatedAccountReference = "account-ref-00000001",
                ),
                2_000,
            ),
        )
        val requested = applied(
            machine.cancellationRequested(prepared, event("cancel-request"), 3_000),
        )

        assertEquals(BillingStatus.CANCELLATION_PENDING, requested.status)
        assertFalse(requested.isTerminal)

        val pending = applied(
            machine.cancellationResult(
                requested,
                event("cancel-still-pending"),
                ProviderCancellationResult.StillPending,
                4_000,
            ),
        )
        assertEquals(BillingStatus.PENDING_PROVIDER, pending.status)

        val requestedAgain = applied(
            machine.cancellationRequested(pending, event("cancel-request-again"), 5_000),
        )
        val cancelled = applied(
            machine.cancellationResult(
                requestedAgain,
                event("cancel-confirmed"),
                ProviderCancellationResult.Confirmed,
                6_000,
            ),
        )
        assertEquals(BillingStatus.CANCELLED, cancelled.status)
        assertTrue(cancelled.isTerminal)
    }

    @Test
    fun `verified refund revokes entitlement before becoming refunded`() {
        val completed = completedCheckout()
        val refundPurchase = purchased(state = ProviderPurchaseState.REFUNDED)
        val refundVerifying = applied(
            machine.refundObserved(completed, event("refund-observed"), refundPurchase, 5_000),
        )
        assertEquals(BillingStatus.REFUND_VERIFYING, refundVerifying.status)

        val refundReceipt = ServerRefundVerificationReceipt(
            verificationId = verification("refund-verification-001"),
            operationId = operation("operation-checkout-001"),
            purchaseReference = purchaseReference("provider-purchase-001"),
            verifiedAtEpochMillis = 6_000,
        )
        val awaitingRevocation = applied(
            machine.refundVerificationResult(
                refundVerifying,
                event("refund-verified"),
                RefundVerificationResult.Verified(refundReceipt),
                6_000,
            ),
        )
        assertEquals(BillingStatus.REFUND_PENDING_REVOCATION, awaitingRevocation.status)
        assertFalse(awaitingRevocation.isTerminal)

        val refunded = applied(
            machine.entitlementRevocationResult(
                awaitingRevocation,
                event("entitlement-revoked"),
                EntitlementRevocationResult.Revoked(
                    EntitlementRevocationReceipt(
                        verificationId = refundReceipt.verificationId,
                        entitlementId = entitlement("entitlement-001"),
                        revokedAtEpochMillis = 7_000,
                    ),
                ),
                7_000,
            ),
        )
        assertEquals(BillingStatus.REFUNDED, refunded.status)
        assertTrue(refunded.isTerminal)
    }

    @Test
    fun `refund mismatch cannot revoke active entitlement`() {
        val completed = completedCheckout()
        val refundVerifying = applied(
            machine.refundObserved(
                completed,
                event("refund-observed"),
                purchased(state = ProviderPurchaseState.REFUNDED),
                5_000,
            ),
        )
        val mismatched = ServerRefundVerificationReceipt(
            verificationId = verification("refund-verification-001"),
            operationId = operation("wrong-operation-001"),
            purchaseReference = purchaseReference("provider-purchase-001"),
            verifiedAtEpochMillis = 6_000,
        )

        val transition = applied(
            machine.refundVerificationResult(
                refundVerifying,
                event("refund-verified"),
                RefundVerificationResult.Verified(mismatched),
                6_000,
            ),
        )

        assertEquals(BillingStatus.COMPLETED, transition.status)
        assertEquals(BillingFailureCode.VERIFICATION_MISMATCH, transition.failure?.code)
        assertEquals(EntitlementActivationState.ACTIVE, transition.entitlementReceipt?.state)
    }

    @Test
    fun `restore purchase follows the same backend verification gate`() {
        val restore = machine.start(
            BillingOperationCommand.Restore(
                operationId = operation("operation-restore-001"),
                eventId = event("restore-started"),
                userId = USER_ID,
                provider = BillingProvider.GOOGLE_PLAY,
                idempotencyKey = idempotency("restore-idempotency-0001"),
            ),
            1_000,
        )

        val observed = applied(
            machine.purchaseObserved(restore, event("restored-purchase"), purchased(), 2_000),
        )

        assertEquals(BillingOperationKind.RESTORE, observed.kind)
        assertEquals(PRODUCT_ID, observed.productId)
        assertEquals(BillingStatus.VERIFYING_BACKEND, observed.status)
        assertNull(observed.entitlementReceipt)
    }

    @Test
    fun `empty reconciliation completes without manufacturing entitlement`() {
        val reconcile = machine.start(
            BillingOperationCommand.Reconcile(
                operationId = operation("operation-reconcile-001"),
                eventId = event("reconcile-started"),
                userId = USER_ID,
                provider = BillingProvider.GOOGLE_PLAY,
                idempotencyKey = idempotency("reconcile-idempotency-01"),
            ),
            1_000,
        )

        val completed = applied(
            machine.completeEmptyRecovery(reconcile, event("reconcile-empty"), 2_000),
        )

        assertEquals(BillingStatus.COMPLETED, completed.status)
        assertNull(completed.entitlementReceipt)
        assertNull(completed.verificationReceipt)
    }

    @Test
    fun `restore cannot be cancelled as a checkout`() {
        val restore = machine.start(
            BillingOperationCommand.Restore(
                operationId = operation("operation-restore-001"),
                eventId = event("restore-started"),
                userId = USER_ID,
                provider = BillingProvider.GOOGLE_PLAY,
                idempotencyKey = idempotency("restore-idempotency-0001"),
            ),
            1_000,
        )

        val transition = machine.cancellationRequested(
            restore,
            event("invalid-restore-cancel"),
            2_000,
        )

        assertRejected(transition, TransitionRejection.WRONG_OPERATION)
    }

    @Test
    fun `wrong provider callback is rejected`() {
        val wrongPurchase = purchased().copy(provider = BillingProvider.TELEGRAM)
        val transition = machine.purchaseObserved(
            startCheckout(),
            event("wrong-provider"),
            wrongPurchase,
            2_000,
        )

        assertRejected(transition, TransitionRejection.WRONG_PROVIDER)
    }

    @Test
    fun `failed entitlement sync keeps verified purchase recoverable`() {
        val observed = applied(
            machine.purchaseObserved(startCheckout(), event("purchase-seen"), purchased(), 2_000),
        )
        val verified = applied(
            machine.verificationResult(
                observed,
                event("verified-event"),
                PurchaseVerificationResult.Verified(verificationReceipt()),
                3_000,
            ),
        )
        val failedSync = applied(
            machine.entitlementSyncResult(
                verified,
                event("sync-failed"),
                EntitlementSyncResult.Failed(
                    retryableFailure(BillingFailureCode.ENTITLEMENT_SYNC_FAILED),
                ),
                4_000,
            ),
        )

        assertEquals(BillingStatus.VERIFIED_AWAITING_ENTITLEMENT, failedSync.status)
        assertTrue(failedSync.canActivateEntitlement)
        assertEquals(BillingFailureCode.ENTITLEMENT_SYNC_FAILED, failedSync.failure?.code)
    }

    private fun startCheckout(): BillingOperationSnapshot = machine.start(
        BillingOperationCommand.Checkout(
            operationId = operation("operation-checkout-001"),
            eventId = event("checkout-started"),
            userId = USER_ID,
            provider = BillingProvider.GOOGLE_PLAY,
            idempotencyKey = idempotency("checkout-idempotency-001"),
            productId = PRODUCT_ID,
        ),
        1_000,
    )

    private fun completedCheckout(): BillingOperationSnapshot {
        val observed = applied(
            machine.purchaseObserved(startCheckout(), event("purchase-seen"), purchased(), 2_000),
        )
        val verified = applied(
            machine.verificationResult(
                observed,
                event("verified-event"),
                PurchaseVerificationResult.Verified(verificationReceipt()),
                3_000,
            ),
        )
        return applied(
            machine.entitlementSyncResult(
                verified,
                event("entitlement-event"),
                EntitlementSyncResult.Active(entitlementReceipt()),
                4_000,
            ),
        )
    }

    private fun purchased(
        state: ProviderPurchaseState = ProviderPurchaseState.PURCHASED,
    ): ProviderPurchase = ProviderPurchase(
        provider = BillingProvider.GOOGLE_PLAY,
        purchaseReference = purchaseReference("provider-purchase-001"),
        productId = PRODUCT_ID,
        state = state,
        proofHandle = PurchaseProofHandle.fromSecureVault("vault-proof-handle-001"),
        observedAtEpochMillis = 2_000,
    )

    private fun verificationReceipt(): ServerPurchaseVerificationReceipt =
        ServerPurchaseVerificationReceipt(
            verificationId = verification("server-verification-001"),
            operationId = operation("operation-checkout-001"),
            userId = USER_ID,
            productId = PRODUCT_ID,
            provider = BillingProvider.GOOGLE_PLAY,
            purchaseReference = purchaseReference("provider-purchase-001"),
            verifiedAtEpochMillis = 3_000,
        )

    private fun entitlementReceipt(): EntitlementActivationReceipt =
        EntitlementActivationReceipt(
            verificationId = verification("server-verification-001"),
            entitlementId = entitlement("entitlement-001"),
            serviceId = "service-germany-vip-001",
            userId = USER_ID,
            productId = PRODUCT_ID,
            state = EntitlementActivationState.ACTIVE,
            effectiveAtEpochMillis = 3_500,
            expiresAtEpochMillis = 9_000_000,
        )

    private fun applied(transition: BillingTransition): BillingOperationSnapshot =
        (transition as BillingTransition.Applied).state

    private fun assertRejected(
        transition: BillingTransition,
        reason: TransitionRejection,
    ) {
        assertTrue(transition is BillingTransition.Rejected)
        assertEquals(reason, (transition as BillingTransition.Rejected).reason)
    }

    private fun retryableFailure(code: BillingFailureCode): BillingFailure = BillingFailure(
        code = code,
        retryable = true,
        safeMessageKey = "billing.${code.name.lowercase()}",
    )

    private companion object {
        const val USER_ID = "user-10001"
        const val PRODUCT_ID = "premium-monthly"

        fun operation(value: String) = BillingOperationId(value)
        fun event(value: String) = BillingEventId(value)
        fun session(value: String) = CheckoutSessionId(value)
        fun idempotency(value: String) = BillingIdempotencyKey(value)
        fun purchaseReference(value: String) = ProviderPurchaseReference(value)
        fun verification(value: String) = ServerVerificationId(value)
        fun entitlement(value: String) = EntitlementId(value)
    }
}
