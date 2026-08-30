package com.ganj.vpn.composition

import com.ganj.vpn.core.controlapi.AccessToken
import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.AuthSessionCredentials
import com.ganj.vpn.core.controlapi.RefreshToken
import com.ganj.vpn.core.controlapi.ResponseMetadata
import com.ganj.vpn.core.controlapi.TelegramAuthorization
import com.ganj.vpn.core.controlapi.TelegramAuthorizationCommand
import com.ganj.vpn.core.controlapi.TelegramExchangeCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramAuthCoordinatorTest {
    private val metadata = ResponseMetadata(requestId = null, serverTime = null)
    private val now = 1_788_000_000_000L
    private val redirect = "https://auth.ganj.example/ganj/telegram/callback"
    private val state = "state-abcdefghijklmnopqrstuvwxyz-123456"

    @Test
    fun `begin stores PKCE flow and returns authorization url`() {
        val gateway = FakeGateway()
        val flowStore = MemoryFlowStore()
        val coordinator = coordinator(gateway, flowStore)

        val result = coordinator.begin()

        assertTrue(result is TelegramAuthResult.Launch)
        assertEquals("https://telegram.example/authorize", (result as TelegramAuthResult.Launch).authorizationUrl)
        val flow = requireNotNull(flowStore.flow)
        assertEquals(state, flow.state)
        assertEquals(43, flow.codeVerifier.length)
        assertEquals(43, gateway.lastStart?.codeChallenge?.length)
        assertEquals(redirect, gateway.lastStart?.redirectUri)
    }

    @Test
    fun `mismatched state is consumed and cannot be replayed`() {
        val gateway = FakeGateway()
        val flowStore = MemoryFlowStore(validFlow())
        val coordinator = coordinator(gateway, flowStore)

        val first = coordinator.complete("$redirect?code=abcdefgh&state=wrong-state-abcdefghijklmnopqrstuvwxyz")
        val second = coordinator.complete("$redirect?code=abcdefgh&state=$state")

        assertEquals("auth.callback_state_mismatch", (first as TelegramAuthResult.Failed).code)
        assertEquals("auth.flow_missing_or_consumed", (second as TelegramAuthResult.Failed).code)
        assertEquals(0, gateway.exchangeCount)
        assertTrue(flowStore.flow == null)
    }

    @Test
    fun `expired authorization flow fails before exchange`() {
        val gateway = FakeGateway()
        val flowStore = MemoryFlowStore(validFlow(expiresAt = "2026-08-27T00:00:00Z"))
        val coordinator = coordinator(gateway, flowStore)

        val result = coordinator.complete("$redirect?code=abcdefgh&state=$state")

        assertEquals("auth.flow_expired", (result as TelegramAuthResult.Failed).code)
        assertEquals(0, gateway.exchangeCount)
        assertTrue(flowStore.flow == null)
    }

    @Test
    fun `redirect origin or path mismatch is rejected`() {
        val gateway = FakeGateway()
        val flowStore = MemoryFlowStore(validFlow())
        val coordinator = coordinator(gateway, flowStore)

        val result = coordinator.complete(
            "https://evil.example/ganj/telegram/callback?code=abcdefgh&state=$state",
        )

        assertEquals("auth.callback_redirect_mismatch", (result as TelegramAuthResult.Failed).code)
        assertEquals(0, gateway.exchangeCount)
        assertTrue(flowStore.flow == null)
    }

    @Test
    fun `successful callback is one shot and marks presentation linked`() {
        val gateway = FakeGateway()
        val flowStore = MemoryFlowStore(validFlow())
        val linkStore = MemoryLinkStore()
        val coordinator = coordinator(gateway, flowStore, linkStore)

        val first = coordinator.complete("$redirect?code=abcdefgh&state=$state")
        val second = coordinator.complete("$redirect?code=abcdefgh&state=$state")

        assertTrue(first is TelegramAuthResult.Linked)
        assertEquals("auth.flow_missing_or_consumed", (second as TelegramAuthResult.Failed).code)
        assertEquals(1, gateway.exchangeCount)
        assertTrue(linkStore.linked)
    }

    @Test
    fun `failed exchange remains consumed and does not mark linked`() {
        val gateway = FakeGateway(exchangeSucceeds = false)
        val flowStore = MemoryFlowStore(validFlow())
        val linkStore = MemoryLinkStore()
        val coordinator = coordinator(gateway, flowStore, linkStore)

        val result = coordinator.complete("$redirect?code=abcdefgh&state=$state")

        assertEquals("auth.telegram_exchange_failed", (result as TelegramAuthResult.Failed).code)
        assertEquals(1, gateway.exchangeCount)
        assertFalse(linkStore.linked)
        assertTrue(flowStore.flow == null)
    }

    @Test
    fun `logout clears linked marker`() {
        val gateway = FakeGateway()
        val linkStore = MemoryLinkStore(linked = true)
        val coordinator = coordinator(gateway, MemoryFlowStore(), linkStore)

        val result = coordinator.logout()

        assertTrue(result is TelegramAuthResult.LoggedOut)
        assertFalse(linkStore.linked)
        assertEquals(1, gateway.logoutCount)
    }

    private fun coordinator(
        gateway: FakeGateway,
        flowStore: MemoryFlowStore,
        linkStore: MemoryLinkStore = MemoryLinkStore(),
    ) = TelegramAuthCoordinator(
        session = gateway,
        store = flowStore,
        linkState = linkStore,
        redirectUri = redirect,
        nowMillis = { now },
    )

    private fun validFlow(
        expiresAt: String = "2026-09-01T00:00:00Z",
    ) = TelegramAuthFlow(
        state = state,
        codeVerifier = "v".repeat(43),
        redirectUri = redirect,
        expiresAt = expiresAt,
    )

    private inner class FakeGateway(
        private val exchangeSucceeds: Boolean = true,
    ) : TelegramAuthSessionGateway {
        var lastStart: TelegramAuthorizationCommand? = null
        var exchangeCount = 0
        var logoutCount = 0

        override fun beginTelegram(
            command: TelegramAuthorizationCommand,
        ): ApiResult<TelegramAuthorization> {
            lastStart = command
            return ApiResult.Success(
                TelegramAuthorization(
                    authorizationUrl = "https://telegram.example/authorize",
                    state = state,
                    expiresAt = "2026-09-01T00:00:00Z",
                ),
                metadata,
            )
        }

        override fun exchangeTelegram(
            command: TelegramExchangeCommand,
        ): ApiResult<AuthSessionCredentials> {
            exchangeCount += 1
            if (!exchangeSucceeds) {
                return ApiResult.Failure(ApiError.AuthenticationRequired())
            }
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
            return true
        }
    }

    private class MemoryFlowStore(
        var flow: TelegramAuthFlow? = null,
    ) : TelegramAuthFlowStore {
        override fun restore(): TelegramAuthFlow? = flow

        override fun save(flow: TelegramAuthFlow): Result<Unit> = runCatching {
            this.flow = flow
        }

        override fun clear(): Result<Unit> = runCatching {
            flow = null
        }
    }

    private class MemoryLinkStore(
        var linked: Boolean = false,
    ) : TelegramLinkStateStore {
        override fun isLinked(): Boolean = linked

        override fun setLinked(linked: Boolean): Result<Unit> = runCatching {
            this.linked = linked
        }
    }
}
