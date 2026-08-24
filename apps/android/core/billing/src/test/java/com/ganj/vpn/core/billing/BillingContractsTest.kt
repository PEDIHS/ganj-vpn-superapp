package com.ganj.vpn.core.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingContractsTest {
    @Test
    fun `proof handle redacts its reference`() {
        val rawReference = "vault-reference-that-must-not-log"
        val handle = PurchaseProofHandle.fromSecureVault(rawReference)

        assertEquals("PurchaseProofHandle([REDACTED])", handle.toString())
        assertFalse(handle.toString().contains(rawReference))
        assertEquals(handle, PurchaseProofHandle.fromSecureVault(rawReference))
        assertNotEquals(handle, PurchaseProofHandle.fromSecureVault("another-vault-reference"))
    }

    @Test
    fun `billing state and provider purchase expose no raw token field`() {
        val protectedTypes = listOf(
            BillingOperationSnapshot::class.java,
            ProviderPurchase::class.java,
            PurchaseVerificationRequest::class.java,
            RefundVerificationRequest::class.java,
        )

        val forbiddenFields = protectedTypes.flatMap { type ->
            type.declaredFields
                .filter { field -> field.name.contains("token", ignoreCase = true) }
                .map { field -> "${type.simpleName}.${field.name}" }
        }

        assertTrue("Raw token fields found: $forbiddenFields", forbiddenFields.isEmpty())
    }

    @Test
    fun `purchased observation requires secure proof handle`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProviderPurchase(
                provider = BillingProvider.GOOGLE_PLAY,
                purchaseReference = ProviderPurchaseReference("purchase-reference-001"),
                productId = "premium-monthly",
                state = ProviderPurchaseState.PURCHASED,
                proofHandle = null,
                observedAtEpochMillis = 1_000,
            )
        }
    }

    @Test
    fun `idempotency key rejects short or unsafe values`() {
        assertThrows(IllegalArgumentException::class.java) {
            BillingIdempotencyKey("short")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BillingIdempotencyKey("contains whitespace and is unsafe")
        }
        assertEquals(
            "checkout-idempotency-001",
            BillingIdempotencyKey("checkout-idempotency-001").value,
        )
    }

    @Test
    fun `provider marker interfaces bind all supported channels`() {
        assertEquals(BillingProvider.GOOGLE_PLAY, googlePlayGateway.provider)
        assertEquals(BillingProvider.WALLET, walletGateway.provider)
        assertEquals(BillingProvider.TELEGRAM, telegramGateway.provider)
        assertEquals(BillingProvider.PAYMENT_GATEWAY, paymentGateway.provider)
    }

    @Test
    fun `provider purchase references are unique in restore result`() {
        val purchase = pendingPurchase("purchase-reference-001")

        assertThrows(IllegalArgumentException::class.java) {
            OwnedPurchasesResult.Found(listOf(purchase, purchase.copy()))
        }
    }

    @Test
    fun `billing providers are closed known set`() {
        assertEquals(
            setOf(
                BillingProvider.GOOGLE_PLAY,
                BillingProvider.WALLET,
                BillingProvider.TELEGRAM,
                BillingProvider.PAYMENT_GATEWAY,
            ),
            BillingProvider.entries.toSet(),
        )
    }

    private fun pendingPurchase(reference: String) = ProviderPurchase(
        provider = BillingProvider.GOOGLE_PLAY,
        purchaseReference = ProviderPurchaseReference(reference),
        productId = "premium-monthly",
        state = ProviderPurchaseState.PENDING,
        proofHandle = null,
        observedAtEpochMillis = 1_000,
    )

    private abstract class StubBillingGateway : BillingGateway {
        override suspend fun prepareCheckout(request: GatewayCheckoutRequest): GatewayCheckoutResult =
            error("not used")

        override suspend fun queryOwnedPurchases(request: OwnedPurchasesRequest): OwnedPurchasesResult =
            error("not used")

        override suspend fun requestCancellation(
            request: ProviderCancellationRequest,
        ): ProviderCancellationResult = error("not used")
    }

    private val googlePlayGateway = object : StubBillingGateway(), GooglePlayBillingGateway {}
    private val walletGateway = object : StubBillingGateway(), WalletBillingGateway {}
    private val telegramGateway = object : StubBillingGateway(), TelegramBillingGateway {}
    private val paymentGateway = object : StubBillingGateway(), PaymentGatewayBillingGateway {}
}
