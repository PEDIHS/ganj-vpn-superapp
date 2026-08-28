package com.ganj.vpn.composition

import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.AuthSessionCredentials
import com.ganj.vpn.core.controlapi.TelegramAuthorizationCommand
import com.ganj.vpn.core.controlapi.TelegramBotApprovalState
import com.ganj.vpn.core.controlapi.TelegramBotApprovalStatus
import com.ganj.vpn.core.controlapi.TelegramBotAuthorization
import com.ganj.vpn.core.controlapi.TelegramExchangeCommand
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal data class TelegramBotAuthFlow(
    val state: String,
    val codeVerifier: String,
    val redirectUri: String,
    val expiresAt: String,
)

internal interface TelegramBotAuthFlowStore {
    fun restore(): TelegramBotAuthFlow?
    fun save(flow: TelegramBotAuthFlow): Result<Unit>
    fun clear(): Result<Unit>
}

/** Local UX marker only. Server session and ownership remain authoritative. */
internal interface TelegramLinkStateStore {
    fun isLinked(): Boolean
    fun setLinked(linked: Boolean): Result<Unit>
}

internal interface TelegramBotAuthSessionGateway {
    fun begin(command: TelegramAuthorizationCommand): ApiResult<TelegramBotAuthorization>
    fun status(state: String): ApiResult<TelegramBotApprovalStatus>
    fun exchange(command: TelegramExchangeCommand): ApiResult<AuthSessionCredentials>
    fun logout(): Boolean
}

internal sealed interface TelegramBotAuthResult {
    data class Launch(val approvalUrl: String) : TelegramBotAuthResult
    data object Pending : TelegramBotAuthResult
    data object Linked : TelegramBotAuthResult
    data object LoggedOut : TelegramBotAuthResult
    data object Cancelled : TelegramBotAuthResult
    data class Failed(val code: String) : TelegramBotAuthResult
}

/**
 * Primary Telegram account-link ceremony for Android.
 *
 * Flow: existing silent Guest Session -> PKCE start -> Ganj Bot deep link -> explicit approval ->
 * status -> one-time backend exchange -> durable linked session. No Telegram provider token, phone
 * number, OTP, MTProto credential, code verifier or exchange code enters Compose state or logs.
 */
internal class TelegramBotAuthCoordinator(
    private val session: TelegramBotAuthSessionGateway,
    private val flowStore: TelegramBotAuthFlowStore,
    private val linkState: TelegramLinkStateStore,
    private val redirectUri: String,
    private val random: SecureRandom = SecureRandom(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun isLinked(): Boolean = linkState.isLinked()

    fun hasPendingFlow(): Boolean = flowStore.restore()?.let { !isExpired(it.expiresAt) } == true

    fun begin(): TelegramBotAuthResult {
        if (!isAllowedRedirect(redirectUri)) {
            return TelegramBotAuthResult.Failed("auth.redirect_not_configured")
        }
        val verifierBytes = ByteArray(32)
        random.nextBytes(verifierBytes)
        val verifier = try {
            verifierBytes.toBase64UrlWithoutPadding()
        } finally {
            verifierBytes.fill(0)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = try {
            digest.toBase64UrlWithoutPadding()
        } finally {
            digest.fill(0)
        }
        return when (
            val result = session.begin(TelegramAuthorizationCommand(challenge, redirectUri))
        ) {
            is ApiResult.Failure -> TelegramBotAuthResult.Failed("auth.telegram_bot_start_failed")
            is ApiResult.Success -> {
                val flow = TelegramBotAuthFlow(
                    state = result.value.state,
                    codeVerifier = verifier,
                    redirectUri = redirectUri,
                    expiresAt = result.value.expiresAt,
                )
                if (flowStore.save(flow).isFailure) {
                    TelegramBotAuthResult.Failed("auth.flow_persistence_failed")
                } else {
                    TelegramBotAuthResult.Launch(result.value.approvalUrl)
                }
            }
        }
    }

    fun resume(): TelegramBotAuthResult {
        val flow = flowStore.restore() ?: return TelegramBotAuthResult.Failed("auth.flow_missing_or_consumed")
        if (isExpired(flow.expiresAt)) return consumeFailure("auth.flow_expired")
        return when (val status = session.status(flow.state)) {
            is ApiResult.Failure -> TelegramBotAuthResult.Failed("auth.telegram_bot_status_failed")
            is ApiResult.Success -> when (status.value.state) {
                TelegramBotApprovalState.PENDING -> TelegramBotAuthResult.Pending
                TelegramBotApprovalState.CANCELLED -> consumeCancelled()
                TelegramBotApprovalState.EXPIRED -> consumeFailure("auth.flow_expired")
                TelegramBotApprovalState.CONSUMED -> consumeFailure("auth.flow_missing_or_consumed")
                TelegramBotApprovalState.APPROVED -> exchangeApproved(flow, status.value)
            }
        }
    }

    fun logout(): TelegramBotAuthResult {
        flowStore.clear()
        val remoteCleared = session.logout()
        linkState.setLinked(false)
        return if (remoteCleared) TelegramBotAuthResult.LoggedOut
        else TelegramBotAuthResult.Failed("auth.logout_failed")
    }

    private fun exchangeApproved(
        flow: TelegramBotAuthFlow,
        status: TelegramBotApprovalStatus,
    ): TelegramBotAuthResult {
        val code = status.code ?: return consumeFailure("auth.telegram_bot_exchange_failed")
        // Consume local secret-bearing flow before the network exchange. A failed exchange starts a
        // fresh ceremony and therefore cannot be replayed through process recreation or duplicate UI.
        if (flowStore.clear().isFailure) {
            return TelegramBotAuthResult.Failed("auth.flow_clear_failed")
        }
        return when (
            session.exchange(
                TelegramExchangeCommand(
                    code = code,
                    state = flow.state,
                    codeVerifier = flow.codeVerifier,
                ),
            )
        ) {
            is ApiResult.Failure -> TelegramBotAuthResult.Failed("auth.telegram_bot_exchange_failed")
            is ApiResult.Success -> {
                linkState.setLinked(true)
                TelegramBotAuthResult.Linked
            }
        }
    }

    private fun consumeCancelled(): TelegramBotAuthResult {
        flowStore.clear()
        return TelegramBotAuthResult.Cancelled
    }

    private fun consumeFailure(code: String): TelegramBotAuthResult.Failed {
        flowStore.clear()
        return TelegramBotAuthResult.Failed(code)
    }

    private fun isExpired(timestamp: String): Boolean {
        val expiry = parseUtcMillis(timestamp) ?: return true
        return expiry <= nowMillis()
    }

    private fun parseUtcMillis(value: String): Long? = runCatching {
        val seconds = value.removeSuffix("Z").substringBefore('.')
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(seconds)?.time
    }.getOrNull()

    private fun isAllowedRedirect(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.query == null &&
            uri.fragment == null &&
            uri.host != "auth.invalid"
    }.getOrDefault(false)
}
