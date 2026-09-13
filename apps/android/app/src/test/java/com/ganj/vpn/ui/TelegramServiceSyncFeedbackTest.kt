package com.ganj.vpn.ui

import com.ganj.vpn.presentation.ContentState
import com.ganj.vpn.presentation.ServiceUiModel
import com.ganj.vpn.presentation.ServiceUiStatus
import com.ganj.vpn.presentation.UiFailure
import com.ganj.vpn.presentation.UiFailureKind
import com.ganj.vpn.presentation.UiTier
import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramServiceSyncFeedbackTest {
    @Test
    fun `loading empty and auth states map to explicit account sync feedback`() {
        assertEquals(
            TelegramServiceSyncFeedback.Syncing,
            telegramServiceSyncFeedback(ContentState.Loading),
        )
        assertEquals(
            TelegramServiceSyncFeedback.Empty,
            telegramServiceSyncFeedback(ContentState.Empty),
        )
        assertEquals(
            TelegramServiceSyncFeedback.AuthRequired,
            telegramServiceSyncFeedback(ContentState.AuthRequired),
        )
    }

    @Test
    fun `ready state reports truthful service count`() {
        val services = ContentState.Ready(
            listOf(
                ServiceUiModel(
                    entitlementId = "11111111-1111-4111-8111-111111111111",
                    displayName = "Premium",
                    status = ServiceUiStatus.ACTIVE,
                    tier = UiTier.PREMIUM,
                    countryCode = null,
                    trafficLimitBytes = null,
                    trafficUsedBytes = 0,
                    expiresAt = null,
                    deviceLimit = 2,
                    allowedProtocols = setOf("vless"),
                ),
            ),
        )

        assertEquals(
            TelegramServiceSyncFeedback.Success(serviceCount = 1),
            telegramServiceSyncFeedback(services),
        )
    }

    @Test
    fun `error state preserves retryability instead of claiming success`() {
        val retryable = ContentState.Error(
            UiFailure(
                kind = UiFailureKind.NETWORK,
                messageKey = "network.offline",
                retryable = true,
            ),
        )
        val terminal = ContentState.Error(
            UiFailure(
                kind = UiFailureKind.SERVER,
                messageKey = "services.unavailable",
                retryable = false,
            ),
        )

        assertEquals(
            TelegramServiceSyncFeedback.Failed(retryable = true),
            telegramServiceSyncFeedback(retryable),
        )
        assertEquals(
            TelegramServiceSyncFeedback.Failed(retryable = false),
            telegramServiceSyncFeedback(terminal),
        )
    }
}
