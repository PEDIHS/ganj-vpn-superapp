package com.ganj.vpn.presentation

import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.CatalogProduct
import com.ganj.vpn.core.controlapi.Money
import com.ganj.vpn.core.controlapi.ResponseMetadata
import com.ganj.vpn.core.controlapi.ServiceStatus
import com.ganj.vpn.core.controlapi.SubscriptionTier
import com.ganj.vpn.core.controlapi.UserService
import com.ganj.vpn.core.controlapi.VpnProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjPresentationMapperTest {
    private val mapper = GanjPresentationMapper()

    @Test
    fun `catalog maps only presentation-safe product fields`() {
        val result = mapper.catalog(ApiResult.Success(listOf(product()), metadata()))

        assertTrue(result is ContentState.Ready)
        val plan = (result as ContentState.Ready).items.single()
        assertEquals(PLAN_ID, plan.id)
        assertEquals(UiTier.VIP, plan.tier)
        assertEquals(499_00, plan.amountMinor)
        assertEquals("EUR", plan.currency)
        assertEquals(listOf("Smart connect", "Priority routing"), plan.benefits)
    }

    @Test
    fun `services map entitlement and status without connection material`() {
        val result = mapper.services(ApiResult.Success(listOf(service()), metadata()))

        val item = (result as ContentState.Ready).items.single()
        assertEquals(SERVICE_ID, item.entitlementId)
        assertTrue(item.isActive)
        assertEquals(setOf("VLESS", "TROJAN"), item.allowedProtocols)
        assertEquals(80_000L, item.remainingBytes)

        val forbidden = listOf("ciphertext", "nonce", "credential", "host", "port", "uri", "qr", "clipboard")
        val fieldNames = (ServiceUiModel::class.java.declaredFields + GanjUiState::class.java.declaredFields)
            .map { it.name.lowercase() }
        assertFalse(fieldNames.any { field -> forbidden.any(field::contains) })
    }

    @Test
    fun `empty results become explicit empty states`() {
        assertEquals(ContentState.Empty, mapper.catalog(ApiResult.Success(emptyList(), metadata())))
        assertEquals(ContentState.Empty, mapper.services(ApiResult.Success(emptyList(), metadata())))
    }

    @Test
    fun `401 becomes auth required`() {
        val result = mapper.catalog(ApiResult.Failure(ApiError.AuthenticationRequired("request-401")))

        assertEquals(ContentState.AuthRequired, result)
        assertEquals(
            UiFailureKind.AUTHENTICATION,
            mapper.apiFailure(ApiError.AuthenticationExpired("request-401", "expired")).kind,
        )
    }

    @Test
    fun `403 409 429 and 5xx retain safe error taxonomy`() {
        val forbidden = mapper.apiFailure(ApiError.Forbidden("request-403", "denied"))
        val conflict = mapper.apiFailure(ApiError.Conflict("request-409", "duplicate"))
        val limited = mapper.apiFailure(ApiError.RateLimited("request-429", 30))
        val server = mapper.apiFailure(ApiError.Server("request-500", 503, "unavailable", true))

        assertEquals(UiFailureKind.ENTITLEMENT, forbidden.kind)
        assertFalse(forbidden.retryable)
        assertEquals(UiFailureKind.CONFLICT, conflict.kind)
        assertTrue(conflict.retryable)
        assertEquals(UiFailureKind.RATE_LIMIT, limited.kind)
        assertTrue(limited.retryable)
        assertEquals(UiFailureKind.SERVER, server.kind)
        assertTrue(server.retryable)
        assertEquals("request-500", server.requestId)
    }

    private fun product() = CatalogProduct(
        id = PLAN_ID,
        code = "vip-annual",
        name = "VIP Annual",
        tier = SubscriptionTier.VIP,
        durationDays = 365,
        trafficLimitBytes = null,
        deviceLimit = 5,
        features = listOf("Smart connect", "Priority routing"),
        price = Money(499_00, "EUR"),
    )

    private fun service() = UserService(
        id = SERVICE_ID,
        name = "Germany VIP",
        status = ServiceStatus.ACTIVE,
        tier = SubscriptionTier.VIP,
        countryCode = "DE",
        trafficLimitBytes = 100_000,
        trafficUsedBytes = 20_000,
        expiresAt = "2027-01-01T00:00:00Z",
        deviceLimit = 5,
        allowedProtocols = setOf(VpnProtocol.VLESS, VpnProtocol.TROJAN),
    )

    private fun metadata() = ResponseMetadata("request-id", "2026-08-24T00:00:00Z")

    private companion object {
        const val PLAN_ID = "10000000-0000-4000-8000-000000000001"
        const val SERVICE_ID = "20000000-0000-4000-8000-000000000001"
    }
}
