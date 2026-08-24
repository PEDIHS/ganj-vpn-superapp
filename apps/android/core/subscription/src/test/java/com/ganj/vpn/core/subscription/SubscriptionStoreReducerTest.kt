package com.ganj.vpn.core.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionStoreReducerTest {
    private val reducer = SubscriptionStoreReducer()

    @Test
    fun `checkout must reference a catalog product`() {
        val original = state()

        val rejected = reducer.reduce(
            original,
            SubscriptionStoreAction.BeginCheckout("missing"),
        )
        val accepted = reducer.reduce(
            original,
            SubscriptionStoreAction.BeginCheckout("vip-monthly"),
        )

        assertEquals(PurchaseStatus.FAILED, rejected.purchaseStatus)
        assertEquals(PurchaseFailure.PRODUCT_UNAVAILABLE, rejected.purchaseFailure)
        assertEquals(PurchaseStatus.CHECKOUT, accepted.purchaseStatus)
        assertEquals("vip-monthly", accepted.selectedProductId)
    }

    @Test
    fun `successful purchase activates and selects returned service`() {
        val purchased = service(id = "new-service")
        val result = reducer.reduce(
            state().copy(purchaseStatus = PurchaseStatus.VERIFYING),
            SubscriptionStoreAction.CompletePurchase(purchased),
        )

        assertEquals(PurchaseStatus.SUCCEEDED, result.purchaseStatus)
        assertEquals("new-service", result.selectedServiceId)
        assertEquals(purchased, result.selectedService)
    }

    @Test
    fun `service cannot be injected without matching verified checkout`() {
        val wrongProductService = service(id = "injected").copy(productId = "free-monthly")
        val wrongOwnerService = service(id = "stolen").copy(ownerUserId = "attacker")

        val withoutCheckout = reducer.reduce(
            state(),
            SubscriptionStoreAction.CompletePurchase(service(id = "injected")),
        )
        val wrongProduct = reducer.reduce(
            state().copy(purchaseStatus = PurchaseStatus.VERIFYING),
            SubscriptionStoreAction.CompletePurchase(wrongProductService),
        )
        val wrongOwner = reducer.reduce(
            state().copy(purchaseStatus = PurchaseStatus.VERIFYING),
            SubscriptionStoreAction.CompletePurchase(wrongOwnerService),
        )

        assertEquals(PurchaseStatus.FAILED, withoutCheckout.purchaseStatus)
        assertEquals(PurchaseFailure.VERIFICATION_REJECTED, withoutCheckout.purchaseFailure)
        assertEquals(PurchaseStatus.FAILED, wrongProduct.purchaseStatus)
        assertEquals(PurchaseStatus.FAILED, wrongOwner.purchaseStatus)
        assertTrue(withoutCheckout.services.none { it.id == "injected" })
        assertTrue(wrongProduct.services.none { it.id == "injected" })
        assertTrue(wrongOwner.services.none { it.id == "stolen" })
    }

    @Test
    fun `connection is generated only from selected service entitlement`() {
        val requested = reducer.reduce(
            state(),
            SubscriptionStoreAction.RequestConnection("de-fra-01", NOW),
        )
        val established = reducer.reduce(
            requested,
            SubscriptionStoreAction.ConnectionEstablished,
        )

        assertTrue(requested.connection is ConnectionUiState.Requested)
        assertTrue(established.connection is ConnectionUiState.Connected)
        val command = (established.connection as ConnectionUiState.Connected).command
        assertEquals("service-vip", command.serviceId)
        assertEquals("de-fra-01", command.serverId)
    }

    @Test
    fun `invalid service selection cannot replace current selection`() {
        val original = state()
        val result = reducer.reduce(
            original,
            SubscriptionStoreAction.SelectService("unknown"),
        )

        assertSame(original, result)
    }

    @Test
    fun `server outside subscription remains blocked in reducer`() {
        val result = reducer.reduce(
            state(),
            SubscriptionStoreAction.RequestConnection("vip-only", NOW),
        )

        assertEquals(
            ConnectionDenial.SERVER_NOT_INCLUDED,
            (result.connection as ConnectionUiState.Blocked).reason,
        )
    }

    private fun state() = SubscriptionStoreState(
        currentUserId = USER_ID,
        products = listOf(product()),
        services = listOf(service()),
        selectedProductId = "vip-monthly",
        selectedServiceId = "service-vip",
    )

    private fun product() = SubscriptionProduct(
        id = "vip-monthly",
        title = "VIP Monthly",
        tier = SubscriptionTier.VIP,
        billingPeriod = BillingPeriod.MONTHLY,
        price = Money(999, "USD"),
        dataLimitBytes = null,
        maxDevices = 2,
        benefits = listOf("All premium locations"),
    )

    private fun service(id: String = "service-vip") = UserService(
        id = id,
        ownerUserId = USER_ID,
        productId = "vip-monthly",
        displayName = "Germany VIP",
        tier = SubscriptionTier.VIP,
        status = UserServiceStatus.ACTIVE,
        validUntilEpochMillis = NOW + 86_400_000,
        remainingBytes = null,
        maxDevices = 2,
        activeDevices = 1,
        allowedServerIds = setOf("de-fra-01"),
    )

    private companion object {
        const val USER_ID = "telegram:10001"
        const val NOW = 1_800_000_000_000L
    }
}
