package com.ganj.vpn.composition

import com.ganj.vpn.core.controlapi.AccessToken
import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.AuthSessionCredentials
import com.ganj.vpn.core.controlapi.RefreshToken
import com.ganj.vpn.core.controlapi.ResponseMetadata
import com.ganj.vpn.core.controlapi.TelegramAuthorizationCommand
import com.ganj.vpn.core.controlapi.TelegramBotApprovalState
import com.ganj.vpn.core.controlapi.TelegramBotApprovalStatus
import com.ganj.vpn.core.controlapi.TelegramBotAuthorization
import com.ganj.vpn.core.controlapi.TelegramExchangeCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramBotAuthCoordinatorTest {
    private val metadata = ResponseMetadata(requestId = null, serverTime = null)
    private val now = 1_788_000_000_000L
    private val redirect = "https://auth.ganj.example/ganj/telegram/callback"
    private val state = "s".repeat(43)
    private val code = "c".repeat(43)
    private val expires = "2026-09-01T00:00:00Z"

    @Test
    fun `begin stores PKCE flow and returns Ganj Bot deep link`() {
        val gateway = FakeGateway()
        val store = MemoryFlowStore()
        val coordinator = coordinator(gateway, store)

        val result = coordinator.begin()

        assertTrue(result is TelegramBotAuthResult.Launch)
        assertEquals(
            "https://t.me/ganj_vpn_bot?start=APP-test-token",
            (result as TelegramBotAuthResult.Launch).approvalUrl,
        )
        val flow = requireNotNull(store.flow)
        assertEquals(state, flow.state)
        assertEquals(43, flow.codeVerifier.length)
        assertEquals(43, gateway.lastStart?.codeChallenge?.length)
        assertEquals(redirect, gateway.lastStart?.redirectUri)
    }

    @Test
    fun `pending approval preserves secret flow and does not exchange`() {
        val gateway = FakeGateway(statusState = TelegramBotApprovalState.PENDING)
        val store = MemoryFlowStore(validFlow())
        val coordinator = coordinator(gateway, store)

        val result = coordinator.resume()

        assertTrue(result is TelegramBotAuthResult.Pending)
        assertNotNull(store.flow)
        assertEquals(0, gateway.exchangeCount)
    }

    @Test
    fun `approved flow exchanges once clears secret flow and marks linked`() {
        val gateway = FakeGateway(statusState = TelegramBotApprovalState.APPROVED)
        val store = MemoryFlowStore(validFlow())
        val links = MemoryLinkStore()
        val coordinator = coordinator(gateway, store, links)

        val first = coordinator.resume()
        val second = coordinator.resume()

        assertTrue(first is TelegramBotAuthResult.Linked)
        assertEquals("auth.flow_missing_or_consumed", (second as TelegramBotAuthResult.Failed).code)
        assertEquals(1, gateway.exchangeCount)
        assertTrue(links.linked)
        assertNull(store.flow)
        assertEquals(state, gateway.lastExchange?.state)
        assertEquals(code, gateway.lastExchange?.code)
        assertEquals("v".repeat(43), gateway.lastExchange?.codeVerifier)
    }

    @Test
    fun `cancelled and expired approvals consume local flow without exchange`() {
        val cancelledStore = MemoryFlowStore(validFlow())
        val cancelledGateway = FakeGateway(statusState = TelegramBotApprovalState.CANCELLED)
        val cancelled = coordinator(cancelledGateway, cancelledStore).resume()
        assertTrue(cancelled is TelegramBotAuthResult.Cancelled)
        assertNull(cancelledStore.flow)
        assertEquals(0, cancelledGateway.exchangeCount)

        val expiredStore = MemoryFlowStore(validFlow())
        val expiredGateway = FakeGateway(statusState = TelegramBotApprovalState.EXPIRED)
        val expired = coordinator(expiredGateway, expiredStore).resume()
        assertEquals("auth.flow_expired", (expired as TelegramBotAuthResult.Failed).code)
        assertNull(expiredStore.flow)
        assertEquals(0, expiredGateway.exchangeCount)
    }

    @Test
    fun `locally expired flow fails before Bot status request`() {
        val gateway = FakeGateway()
        val store = MemoryFlowStore(validFlow(expiresAt = "2026-08-27T00:00:00Z"))
        val coordinator = coordinator(gateway, store)

        val result = coordinator.resume()

        assertEquals("auth.flow_expired", (result as TelegramBotAuthResult.Failed).code)
        assertEquals(0, gateway.statusCount)
        assertNull(store.flow)
    }

    @Test
    fun `status failure remains retryable locally while failed exchange is one shot`() {
        val statusGateway = FakeGateway(statusFails = true)
        val statusStore = MemoryFlowStore(validFlow())
        val statusResult = coordinator(statusGateway, statusStore).resume()
        assertEquals("auth.telegram_bot_status_failed", (statusResult as TelegramBotAuthResult.Failed).code)
        assertNotNull(statusStore.flow)

        val exchangeGateway = FakeGateway(
            statusState = TelegramBotApprovalState.APPROVED,
            exchangeSucceeds = false,
        )
        val exchangeStore = MemoryFlowStore(validFlow())
        val links = MemoryLinkStore()
        val exchangeResult = coordinator(exchangeGateway, exchangeStore, links).resume()
        assertEquals("auth.telegram_bot_exchange_failed", (exchangeResult as TelegramBotAuthResult.Failed).code)
        assertEquals(1, exchangeGateway.exchangeCount)
        assertNull(exchangeStore.flow)
        assertFalse(links.linked)
    }

    @Test
    fun `missing approval code fails closed and consumes local flow`() {
        val gateway = FakeGateway(statusState = TelegramBotApprovalState.APPROVED, approvedCode = null)
        val store = MemoryFlowStore(validFlow())
        val result = coordinator(gateway, store).resume()
        assertEquals("auth.telegram_bot_exchange_failed", (result as TelegramBotAuthResult.Failed).code)
        assertNull(store.flow)
        assertEquals(0, gateway.exchangeCount)
    }

    @Test
    fun `unsafe redirect fails before creating Bot request`() {
        val gateway = FakeGateway()
        val coordinator = TelegramBotAuthCoordinator(
            session = gateway,
            flowStore = MemoryFlowStore(),
            linkState = MemoryLinkStore(),
            redirectUri = "https://auth.invalid/telegram",
            nowMillis = { now },
        )
        val result = coordinator.begin()
        assertEquals("auth.redirect_not_configured", (result as TelegramBotAuthResult.Failed).code)
        assertNull(gateway.lastStart)
    }

    @Test
    fun `logout clears pending flow and presentation linked marker`() {
        val gateway = FakeGateway()
        val store = MemoryFlowStore(validFlow())
        val links = MemoryLinkStore(linked = true)
        val result = coordinator(gateway, store, links).logout()
        assertTrue(result is TelegramBotAuthResult.LoggedOut)
        assertNull(store.flow)
        assertFalse(links.linked)
        assertEquals(1, gateway.logoutCount)
    }

    @Test
    fun `failed logout still clears local authorization presentation`() {
        val gateway = FakeGateway(logoutSucceeds = false)
        val store = MemoryFlowStore(validFlow())
        val links = MemoryLinkStore(linked = true)
        val result = coordinator(gateway, store, links).logout()
        assertEquals("auth.logout_failed", (result as TelegramBotAuthResult.Failed).code)
        assertNull(store.flow)
        assertFalse(links.linked)
    }

    private fun coordinator(
        gateway: FakeGateway,
        store: MemoryFlowStore,
        links: MemoryLinkStore = MemoryLinkStore(),
    ) = TelegramBotAuthCoordinator(
        session = gateway,
        flowStore = store,
        linkState = links,
        redirectUri = redirect,
        nowMillis = { now },
    )

    private fun validFlow(expiresAt: String = expires) = TelegramBotAuthFlow(
        state = state,
        codeVerifier = "v".repeat(43),
        redirectUri = redirect,
        expiresAt = expiresAt,
    )

    private inner class FakeGateway(
        private val statusState: TelegramBotApprovalState = TelegramBotApprovalState.PENDING,
        private val statusFails: Boolean = false,
        private val exchangeSucceeds: Boolean = true,
        private val logoutSucceeds: Boolean = true,
        private val approvedCode: String? = code,
    ) : TelegramBotAuthSessionGateway {
        var lastStart: TelegramAuthorizationCommand? = null
        var lastExchange: TelegramExchangeCommand? = null
        var statusCount = 0
        var exchangeCount = 0
        var logoutCount = 0

        override fun begin(command: TelegramAuthorizationCommand): ApiResult<TelegramBotAuthorization> {
            lastStart = command
            return ApiResult.Success(
                TelegramBotAuthorization(
                    approvalUrl = "https://t.me/ganj_vpn_bot?start=APP-test-token",
                    state = state,
                    expiresAt = expires,
                ),
                metadata,
            )
        }

        override fun status(state: String): ApiResult<TelegramBotApprovalStatus> {
            statusCount += 1
            if (statusFails) return ApiResult.Failure(ApiError.Network())
            return ApiResult.Success(
                TelegramBotApprovalStatus(
                    state = statusState,
                    code = if (statusState == TelegramBotApprovalState.APPROVED) approvedCode ?: code else null,
                    expiresAt = expires,
                ),
                metadata,
            )
        }

        override fun exchange(command: TelegramExchangeCommand): ApiResult<AuthSessionCredentials> {
            exchangeCount += 1
            lastExchange = command
            if (!exchangeSucceeds) return ApiResult.Failure(ApiError.AuthenticationRequired())
            return ApiResult.Success(
                AuthSessionCredentials(
                    userId = "11111111-1111-4111-8111-111111111111",
                    deviceId = "22222222-2222-4222-8222-222222222222",
                    accessToken = AccessToken.from("access-token-value-1234567890"),
                    accessTokenExpiresAt = "2026-09-01T00:05:00Z",
                    refreshToken = RefreshToken.from("r".repeat(43)),
                    refreshTokenExpiresAt = "2026-10-01T00:00:00Z",
                ),
                metadata,
            )
        }

        override fun logout(): Boolean {
            logoutCount += 1
            return logoutSucceeds
        }
    }

    private class MemoryFlowStore(var flow: TelegramBotAuthFlow? = null) : TelegramBotAuthFlowStore {
        override fun restore(): TelegramBotAuthFlow? = flow
        override fun save(flow: TelegramBotAuthFlow): Result<Unit> = runCatching { this.flow = flow }
        override fun clear(): Result<Unit> = runCatching { flow = null }
    }

    private class MemoryLinkStore(var linked: Boolean = false) : TelegramLinkStateStore {
        override fun isLinked(): Boolean = linked
        override fun setLinked(linked: Boolean): Result<Unit> = runCatching { this.linked = linked }
    }
}
