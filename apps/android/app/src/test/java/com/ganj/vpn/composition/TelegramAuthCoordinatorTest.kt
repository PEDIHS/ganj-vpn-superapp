package com.ganj.vpn.composition

import com.ganj.vpn.core.controlapi.AccessToken
import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.AuthSessionCredentials
import com.ganj.vpn.core.controlapi.NetworkFailure
import com.ganj.vpn.core.controlapi.RefreshToken
import com.ganj.vpn.core.controlapi.ResponseMetadata
import com.ganj.vpn.core.controlapi.TelegramAuthorization
import com.ganj.vpn.core.controlapi.TelegramAuthorizationCommand
import com.ganj.vpn.core.controlapi.TelegramBotApprovalCommand
import com.ganj.vpn.core.controlapi.TelegramBotApprovalExchangeCommand
import com.ganj.vpn.core.controlapi.TelegramBotApprovalRequest
import com.ganj.vpn.core.controlapi.TelegramBotApprovalState
import com.ganj.vpn.core.controlapi.TelegramBotApprovalStatus
import com.ganj.vpn.core.controlapi.TelegramExchangeCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramAuthCoordinatorTest {
    private val metadata = ResponseMetadata(requestId = null, serverTime = null)
    private val now = 1_788_000_000_000L
    private val redirect = "https://auth.ganj.example/ganj/telegram/callback"
    private val oidcState = "state-abcdefghijklmnopqrstuvwxyz-123456"
    private val botState = "s".repeat(43)
    private val requestId = "33333333-3333-4333-8333-333333333333"

    @Test
    fun `begin uses Bot Approval and persists device-bound request`() {
        val gateway = FakeGateway()
        val flowStore = MemoryFlowStore()
        val coordinator = coordinator(gateway, flowStore)

        val result = coordinator.begin()

        assertTrue(result is TelegramAuthResult.Launch)
        assertEquals("https://t.me/GanjTestBot?start=ga_test", (result as TelegramAuthResult.Launch).authorizationUrl)
        val flow = requireNotNull(flowStore.flow)
        assertEquals(TelegramAuthFlowMode.BOT_APPROVAL, flow.mode)
        assertEquals(requestId, flow.requestId)
        assertEquals(botState, flow.state)
        assertEquals(43, flow.codeVerifier.length)
        assertEquals(43, gateway.lastBotStart?.codeChallenge?.length)
        assertEquals(redirect, gateway.lastBotStart?.redirectUri)
    }

    @Test
    fun `linked account can start relogin without dropping current linked marker`() {
        val gateway = FakeGateway()
        val flowStore = MemoryFlowStore()
        val linkStore = MemoryLinkStore(linked = true)
        val coordinator = coordinator(gateway, flowStore, linkStore)

        val result = coordinator.begin()

        assertTrue(result is TelegramAuthResult.Launch)
        assertTrue(linkStore.linked)
        assertTrue(coordinator.hasPendingBotApproval())
    }

    @Test
    fun `pending Bot Approval stays resumable`() {
        val gateway = FakeGateway(botStatus = TelegramBotApprovalState.PENDING)
        val flowStore = MemoryFlowStore(validBotFlow())
        val coordinator = coordinator(gateway, flowStore)

        val result = coordinator.resumeBotApproval()

        assertTrue(result is TelegramAuthResult.Waiting)
        assertNotNull(flowStore.flow)
        assertEquals(1, gateway.statusCount)
        assertEquals(0, gateway.botExchangeCount)
    }

    @Test
    fun `app-side cancellation destroys binding material and cannot resume`() {
        val gateway = FakeGateway(botStatus = TelegramBotApprovalState.APPROVED)
        val flowStore = MemoryFlowStore(validBotFlow())
        val coordinator = coordinator(gateway, flowStore)

        val cancelled = coordinator.cancelPendingBotApproval()
        val resumed = coordinator.resumeBotApproval()

        assertTrue(cancelled is TelegramAuthResult.Cancelled)
        assertTrue(flowStore.flow == null)
        assertFalse(coordinator.hasPendingBotApproval())
        assertEquals("auth.flow_missing_or_consumed", (resumed as TelegramAuthResult.Failed).code)
        assertEquals(0, gateway.statusCount)
        assertEquals(0, gateway.botExchangeCount)
    }

    @Test
    fun `linked relogin cancellation keeps existing linked marker`() {
        val gateway = FakeGateway()
        val flowStore = MemoryFlowStore(validBotFlow())
        val linkStore = MemoryLinkStore(linked = true)
        val coordinator = coordinator(gateway, flowStore, linkStore)

        val cancelled = coordinator.cancelPendingBotApproval()

        assertTrue(cancelled is TelegramAuthResult.Cancelled)
        assertTrue(linkStore.linked)
        assertFalse(coordinator.hasPendingBotApproval())
    }

    @Test
    fun `authenticated account response can reconcile stale presentation marker`() {
        val gateway = FakeGateway()
        val flowStore = MemoryFlowStore()
        val linkStore = MemoryLinkStore(linked = true)
        val coordinator = coordinator(gateway, flowStore, linkStore)

        assertTrue(coordinator.reconcileLinkedState(false).isSuccess)

        assertFalse(coordinator.isLinked())
        assertFalse(linkStore.linked)
    }

    @Test
    fun `approved Bot Approval exchanges once marks linked and clears flow`() {
        val gateway = FakeGateway(botStatus = TelegramBotApprovalState.APPROVED)
        val flowStore = MemoryFlowStore(validBotFlow())
        val linkStore = MemoryLinkStore()
        val coordinator = coordinator(gateway, flowStore, linkStore)

        val result = coordinator.resumeBotApproval()

        assertTrue(result is TelegramAuthResult.Linked)
        assertEquals(1, gateway.botExchangeCount)
        assertTrue(linkStore.linked)
        assertTrue(flowStore.flow == null)
    }

    @Test
    fun `denied Bot Approval is consumed locally without exchange`() {
        val gateway = FakeGateway(botStatus = TelegramBotApprovalState.DENIED)
        val flowStore = MemoryFlowStore(validBotFlow())
        val coordinator = coordinator(gateway, flowStore)

        val result = coordinator.resumeBotApproval()

        assertEquals("auth.bot_approval_denied", (result as TelegramAuthResult.Failed).code)
        assertTrue(flowStore.flow == null)
        assertEquals(0, gateway.botExchangeCount)
    }

    @Test
    fun `expired stored Bot Approval remains detectable until resume surfaces expiry`() {
        val gateway = FakeGateway()
        val flowStore = MemoryFlowStore(validBotFlow(expiresAt = "2026-08-27T00:00:00Z"))
        val coordinator = coordinator(gateway, flowStore)

        assertTrue(coordinator.hasPendingBotApproval())
        val result = coordinator.resumeBotApproval()

        assertEquals("auth.bot_approval_expired", (result as TelegramAuthResult.Failed).code)
        assertEquals(0, gateway.statusCount)
        assertTrue(flowStore.flow == null)
        assertFalse(coordinator.hasPendingBotApproval())
    }

    @Test
    fun `owner mismatch is mapped to wrong-device state`() {
        val gateway = FakeGateway(
            statusFailure = ApiError.NotFound(null, "bot_approval_not_found"),
        )
        val coordinator = coordinator(gateway, MemoryFlowStore(validBotFlow()))

        val result = coordinator.resumeBotApproval()

        assertEquals("auth.bot_approval_wrong_device", (result as TelegramAuthResult.Failed).code)
    }

    @Test
    fun `PKCE or state binding mismatch is not misreported as session expiry`() {
        val gateway = FakeGateway(
            botStatus = TelegramBotApprovalState.APPROVED,
            botExchangeFailure = ApiError.AuthenticationExpired(null, "bot_approval_binding_mismatch"),
        )
        val flowStore = MemoryFlowStore(validBotFlow())
        val coordinator = coordinator(gateway, flowStore)

        val result = coordinator.resumeBotApproval()

        assertEquals("auth.bot_approval_binding_mismatch", (result as TelegramAuthResult.Failed).code)
        assertEquals(1, gateway.botExchangeCount)
        assertNotNull(flowStore.flow)
    }

    @Test
    fun `replayed exchange is surfaced and flow remains available for explicit restart handling`() {
        val gateway = FakeGateway(
            botStatus = TelegramBotApprovalState.APPROVED,
            botExchangeFailure = ApiError.Conflict(null, "bot_approval_replayed"),
        )
        val flowStore = MemoryFlowStore(validBotFlow())
        val coordinator = coordinator(gateway, flowStore)

        val result = coordinator.resumeBotApproval()

        assertEquals("auth.bot_approval_replayed", (result as TelegramAuthResult.Failed).code)
        assertEquals(1, gateway.botExchangeCount)
        assertNotNull(flowStore.flow)
    }

    @Test
    fun `offline status keeps pending flow for retry`() {
        val gateway = FakeGateway(
            statusFailure = ApiError.Network(kind = NetworkFailure.OFFLINE_OR_DNS),
        )
        val flowStore = MemoryFlowStore(validBotFlow())
        val coordinator = coordinator(gateway, flowStore)

        val result = coordinator.resumeBotApproval()

        assertEquals("auth.bot_approval_offline", (result as TelegramAuthResult.Failed).code)
        assertNotNull(flowStore.flow)
    }

    @Test
    fun `OIDC fallback remains one-shot and separate from primary flow`() {
        val gateway = FakeGateway()
        val flowStore = MemoryFlowStore()
        val linkStore = MemoryLinkStore()
        val coordinator = coordinator(gateway, flowStore, linkStore)

        val launch = coordinator.beginOidcFallback()
        assertTrue(launch is TelegramAuthResult.Launch)
        assertEquals(TelegramAuthFlowMode.OIDC_FALLBACK, flowStore.flow?.mode)

        val first = coordinator.complete("$redirect?code=abcdefgh&state=$oidcState")
        val second = coordinator.complete("$redirect?code=abcdefgh&state=$oidcState")

        assertTrue(first is TelegramAuthResult.Linked)
        assertEquals("auth.flow_missing_or_consumed", (second as TelegramAuthResult.Failed).code)
        assertEquals(1, gateway.oidcExchangeCount)
        assertTrue(linkStore.linked)
    }

    @Test
    fun `OIDC fallback mismatched state is consumed and rejected`() {
        val gateway = FakeGateway()
        val flowStore = MemoryFlowStore(validOidcFlow())
        val coordinator = coordinator(gateway, flowStore)

        val result = coordinator.complete("$redirect?code=abcdefgh&state=wrong-state")

        assertEquals("auth.callback_state_mismatch", (result as TelegramAuthResult.Failed).code)
        assertTrue(flowStore.flow == null)
        assertEquals(0, gateway.oidcExchangeCount)
    }

    @Test
    fun `logout clears linked marker and any pending approval`() {
        val gateway = FakeGateway()
        val linkStore = MemoryLinkStore(linked = true)
        val flowStore = MemoryFlowStore(validBotFlow())
        val coordinator = coordinator(gateway, flowStore, linkStore)

        val result = coordinator.logout()

        assertTrue(result is TelegramAuthResult.LoggedOut)
        assertFalse(linkStore.linked)
        assertTrue(flowStore.flow == null)
        assertEquals(1, gateway.logoutCount)
    }

    @Test
    fun `logout failure preserves linked marker so user can retry`() {
        val gateway = FakeGateway(logoutSucceeds = false)
        val linkStore = MemoryLinkStore(linked = true)
        val flowStore = MemoryFlowStore(validBotFlow())
        val coordinator = coordinator(gateway, flowStore, linkStore)

        val result = coordinator.logout()

        assertEquals("auth.logout_failed", (result as TelegramAuthResult.Failed).code)
        assertTrue(linkStore.linked)
        assertTrue(flowStore.flow == null)
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

    private fun validBotFlow(
        expiresAt: String = "2026-09-01T00:00:00Z",
    ) = TelegramAuthFlow(
        state = botState,
        codeVerifier = "v".repeat(43),
        redirectUri = redirect,
        expiresAt = expiresAt,
        mode = TelegramAuthFlowMode.BOT_APPROVAL,
        requestId = requestId,
    )

    private fun validOidcFlow(
        expiresAt: String = "2026-09-01T00:00:00Z",
    ) = TelegramAuthFlow(
        state = oidcState,
        codeVerifier = "v".repeat(43),
        redirectUri = redirect,
        expiresAt = expiresAt,
        mode = TelegramAuthFlowMode.OIDC_FALLBACK,
    )

    private inner class FakeGateway(
        private val botStatus: TelegramBotApprovalState = TelegramBotApprovalState.PENDING,
        private val statusFailure: ApiError? = null,
        private val botExchangeFailure: ApiError? = null,
        private val logoutSucceeds: Boolean = true,
    ) : TelegramAuthSessionGateway {
        var lastBotStart: TelegramBotApprovalCommand? = null
        var lastOidcStart: TelegramAuthorizationCommand? = null
        var statusCount = 0
        var botExchangeCount = 0
        var oidcExchangeCount = 0
        var logoutCount = 0

        override fun beginTelegramBotApproval(
            command: TelegramBotApprovalCommand,
        ): ApiResult<TelegramBotApprovalRequest> {
            lastBotStart = command
            return ApiResult.Success(
                TelegramBotApprovalRequest(
                    requestId = requestId,
                    botUrl = "https://t.me/GanjTestBot?start=ga_test",
                    state = botState,
                    expiresAt = "2026-09-01T00:00:00Z",
                ),
                metadata,
            )
        }

        override fun telegramBotApprovalStatus(
            requestId: String,
        ): ApiResult<TelegramBotApprovalStatus> {
            statusCount += 1
            statusFailure?.let { return ApiResult.Failure(it) }
            return ApiResult.Success(
                TelegramBotApprovalStatus(
                    requestId = requestId,
                    state = botStatus,
                    expiresAt = "2026-09-01T00:00:00Z",
                ),
                metadata,
            )
        }

        override fun exchangeTelegramBotApproval(
            command: TelegramBotApprovalExchangeCommand,
        ): ApiResult<AuthSessionCredentials> {
            botExchangeCount += 1
            botExchangeFailure?.let { return ApiResult.Failure(it) }
            return session()
        }

        override fun beginTelegram(
            command: TelegramAuthorizationCommand,
        ): ApiResult<TelegramAuthorization> {
            lastOidcStart = command
            return ApiResult.Success(
                TelegramAuthorization(
                    authorizationUrl = "https://telegram.example/authorize",
                    state = oidcState,
                    expiresAt = "2026-09-01T00:00:00Z",
                ),
                metadata,
            )
        }

        override fun exchangeTelegram(
            command: TelegramExchangeCommand,
        ): ApiResult<AuthSessionCredentials> {
            oidcExchangeCount += 1
            return session()
        }

        override fun logout(): Boolean {
            logoutCount += 1
            return logoutSucceeds
        }

        private fun session(): ApiResult<AuthSessionCredentials> = ApiResult.Success(
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
