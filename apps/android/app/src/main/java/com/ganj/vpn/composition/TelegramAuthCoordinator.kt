package com.ganj.vpn.composition

import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.AuthSessionCredentials
import com.ganj.vpn.core.controlapi.TelegramAuthorization
import com.ganj.vpn.core.controlapi.TelegramAuthorizationCommand
import com.ganj.vpn.core.controlapi.TelegramBotApprovalCommand
import com.ganj.vpn.core.controlapi.TelegramBotApprovalExchangeCommand
import com.ganj.vpn.core.controlapi.TelegramBotApprovalRequest
import com.ganj.vpn.core.controlapi.TelegramBotApprovalState
import com.ganj.vpn.core.controlapi.TelegramBotApprovalStatus
import com.ganj.vpn.core.controlapi.TelegramExchangeCommand
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal enum class TelegramAuthFlowMode {
    BOT_APPROVAL,
    OIDC_FALLBACK,
}

internal data class TelegramAuthFlow(
    val state: String,
    val codeVerifier: String,
    val redirectUri: String,
    val expiresAt: String,
    val mode: TelegramAuthFlowMode = TelegramAuthFlowMode.OIDC_FALLBACK,
    val requestId: String? = null,
)

internal interface TelegramAuthFlowStore {
    fun restore(): TelegramAuthFlow?
    fun save(flow: TelegramAuthFlow): Result<Unit>
    fun clear(): Result<Unit>
}

/** Presentation-only marker. Protected API authorization must never trust this value. */
internal interface TelegramLinkStateStore {
    fun isLinked(): Boolean
    fun setLinked(linked: Boolean): Result<Unit>
}

internal interface TelegramAuthSessionGateway {
    fun beginTelegramBotApproval(command: TelegramBotApprovalCommand): ApiResult<TelegramBotApprovalRequest>
    fun telegramBotApprovalStatus(requestId: String): ApiResult<TelegramBotApprovalStatus>
    fun exchangeTelegramBotApproval(command: TelegramBotApprovalExchangeCommand): ApiResult<AuthSessionCredentials>
    fun beginTelegram(command: TelegramAuthorizationCommand): ApiResult<TelegramAuthorization>
    fun exchangeTelegram(command: TelegramExchangeCommand): ApiResult<AuthSessionCredentials>
    fun logout(): Boolean
}

internal sealed interface TelegramAuthResult {
    data class Launch(val authorizationUrl: String) : TelegramAuthResult
    data object Waiting : TelegramAuthResult
    data object Linked : TelegramAuthResult
    data object LoggedOut : TelegramAuthResult
    data class Failed(val code: String) : TelegramAuthResult
}

/**
 * Primary Telegram Bot Approval ceremony with OIDC/PKCE retained only as an explicit fallback.
 * The encrypted local flow stores only state/PKCE/request metadata. Telegram identity and session
 * credentials are resolved and issued server-side; the Bot never mints App access tokens.
 */
internal class TelegramAuthCoordinator(
    private val session: TelegramAuthSessionGateway,
    private val store: TelegramAuthFlowStore,
    private val linkState: TelegramLinkStateStore,
    private val redirectUri: String,
    private val random: SecureRandom = SecureRandom(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun isLinked(): Boolean = linkState.isLinked()

    /**
     * True while a Bot Approval ceremony is stored locally, including an already-expired request.
     * Keeping the expired flow visible lets the next resume turn it into an explicit expiry state
     * instead of silently dropping the user's login attempt.
     */
    fun hasPendingBotApproval(): Boolean = store.restore()?.let {
        it.mode == TelegramAuthFlowMode.BOT_APPROVAL && !it.requestId.isNullOrBlank()
    } == true

    /** Primary product login path. */
    fun begin(): TelegramAuthResult {
        if (!isAllowedRedirect(redirectUri)) {
            return TelegramAuthResult.Failed("auth.redirect_not_configured")
        }
        val pkce = newPkcePair() ?: return TelegramAuthResult.Failed("auth.pkce_unavailable")
        return when (
            val result = session.beginTelegramBotApproval(
                TelegramBotApprovalCommand(pkce.challenge, redirectUri),
            )
        ) {
            is ApiResult.Failure -> TelegramAuthResult.Failed(mapApprovalFailure(result.error, "auth.bot_approval_start_failed"))
            is ApiResult.Success -> {
                val flow = TelegramAuthFlow(
                    state = result.value.state,
                    codeVerifier = pkce.verifier,
                    redirectUri = redirectUri,
                    expiresAt = result.value.expiresAt,
                    mode = TelegramAuthFlowMode.BOT_APPROVAL,
                    requestId = result.value.requestId,
                )
                if (store.save(flow).isFailure) {
                    TelegramAuthResult.Failed("auth.flow_persistence_failed")
                } else {
                    TelegramAuthResult.Launch(result.value.botUrl)
                }
            }
        }
    }

    /** Called when the App regains focus after the user approves or denies inside Ganj Bot. */
    fun resumeBotApproval(): TelegramAuthResult {
        val flow = store.restore() ?: return TelegramAuthResult.Failed("auth.flow_missing_or_consumed")
        if (flow.mode != TelegramAuthFlowMode.BOT_APPROVAL || flow.requestId.isNullOrBlank()) {
            return TelegramAuthResult.Failed("auth.bot_approval_not_pending")
        }
        if (isExpired(flow.expiresAt)) return consumeFailure("auth.bot_approval_expired")

        return when (val status = session.telegramBotApprovalStatus(flow.requestId)) {
            is ApiResult.Failure -> TelegramAuthResult.Failed(
                mapApprovalFailure(status.error, "auth.bot_approval_status_failed"),
            )
            is ApiResult.Success -> when (status.value.state) {
                TelegramBotApprovalState.PENDING -> TelegramAuthResult.Waiting
                TelegramBotApprovalState.DENIED -> consumeFailure("auth.bot_approval_denied")
                TelegramBotApprovalState.EXPIRED -> consumeFailure("auth.bot_approval_expired")
                TelegramBotApprovalState.CONSUMED -> consumeFailure("auth.bot_approval_replayed")
                TelegramBotApprovalState.APPROVED -> exchangeApproved(flow)
            }
        }
    }

    /** Hardened OIDC/PKCE fallback. Never call this from the primary login CTA. */
    fun beginOidcFallback(): TelegramAuthResult {
        if (!isAllowedRedirect(redirectUri)) {
            return TelegramAuthResult.Failed("auth.redirect_not_configured")
        }
        val pkce = newPkcePair() ?: return TelegramAuthResult.Failed("auth.pkce_unavailable")
        return when (
            val result = session.beginTelegram(
                TelegramAuthorizationCommand(pkce.challenge, redirectUri),
            )
        ) {
            is ApiResult.Failure -> TelegramAuthResult.Failed("auth.telegram_start_failed")
            is ApiResult.Success -> {
                val flow = TelegramAuthFlow(
                    state = result.value.state,
                    codeVerifier = pkce.verifier,
                    redirectUri = redirectUri,
                    expiresAt = result.value.expiresAt,
                    mode = TelegramAuthFlowMode.OIDC_FALLBACK,
                )
                if (store.save(flow).isFailure) {
                    TelegramAuthResult.Failed("auth.flow_persistence_failed")
                } else {
                    TelegramAuthResult.Launch(result.value.authorizationUrl)
                }
            }
        }
    }

    /** Handles OIDC fallback App-Link callbacks; Bot Approval resumes by checking backend status. */
    fun complete(callbackUri: String): TelegramAuthResult {
        val flow = store.restore()
            ?: return TelegramAuthResult.Failed("auth.flow_missing_or_consumed")

        if (flow.mode == TelegramAuthFlowMode.BOT_APPROVAL) return resumeBotApproval()
        if (isExpired(flow.expiresAt)) return consumeFailure("auth.flow_expired")

        val callback = runCatching { URI(callbackUri) }.getOrNull()
            ?: return consumeFailure("auth.callback_invalid")
        val expected = runCatching { URI(flow.redirectUri) }.getOrNull()
            ?: return consumeFailure("auth.callback_invalid")

        if (
            callback.scheme != expected.scheme ||
            callback.host != expected.host ||
            effectivePort(callback) != effectivePort(expected) ||
            callback.path != expected.path ||
            callback.fragment != null
        ) return consumeFailure("auth.callback_redirect_mismatch")

        val query = parseQuery(callback.rawQuery ?: "")
            ?: return consumeFailure("auth.callback_invalid")
        val code = query["code"]?.singleOrNull()
        val state = query["state"]?.singleOrNull()
        if (code.isNullOrBlank() || state.isNullOrBlank() || state != flow.state) {
            return consumeFailure("auth.callback_state_mismatch")
        }

        if (store.clear().isFailure) {
            return TelegramAuthResult.Failed("auth.flow_clear_failed")
        }

        return when (
            session.exchangeTelegram(
                TelegramExchangeCommand(code = code, state = state, codeVerifier = flow.codeVerifier),
            )
        ) {
            is ApiResult.Success -> {
                linkState.setLinked(true)
                TelegramAuthResult.Linked
            }
            is ApiResult.Failure -> TelegramAuthResult.Failed("auth.telegram_exchange_failed")
        }
    }

    fun logout(): TelegramAuthResult {
        store.clear()
        val sessionCleared = session.logout()
        linkState.setLinked(false)
        return if (sessionCleared) TelegramAuthResult.LoggedOut
        else TelegramAuthResult.Failed("auth.logout_failed")
    }

    private fun exchangeApproved(flow: TelegramAuthFlow): TelegramAuthResult {
        val requestId = flow.requestId ?: return consumeFailure("auth.bot_approval_not_pending")
        return when (
            val result = session.exchangeTelegramBotApproval(
                TelegramBotApprovalExchangeCommand(
                    requestId = requestId,
                    state = flow.state,
                    codeVerifier = flow.codeVerifier,
                ),
            )
        ) {
            is ApiResult.Success -> {
                if (store.clear().isFailure) {
                    TelegramAuthResult.Failed("auth.flow_clear_failed")
                } else {
                    linkState.setLinked(true)
                    TelegramAuthResult.Linked
                }
            }
            is ApiResult.Failure -> TelegramAuthResult.Failed(
                mapApprovalFailure(result.error, "auth.bot_approval_exchange_failed"),
            )
        }
    }

    private data class PkcePair(val verifier: String, val challenge: String)

    private fun newPkcePair(): PkcePair? = runCatching {
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
        PkcePair(verifier, challenge)
    }.getOrNull()

    private fun mapApprovalFailure(error: ApiError, fallback: String): String = when (error) {
        is ApiError.Network -> "auth.bot_approval_offline"
        is ApiError.AuthenticationRequired -> "auth.session_expired"
        is ApiError.AuthenticationExpired -> when (error.code) {
            "bot_approval_binding_mismatch" -> "auth.bot_approval_binding_mismatch"
            else -> "auth.session_expired"
        }
        is ApiError.Forbidden -> when (error.code) {
            "bot_approval_denied" -> "auth.bot_approval_denied"
            else -> fallback
        }
        is ApiError.NotFound -> "auth.bot_approval_wrong_device"
        is ApiError.Conflict -> when (error.code) {
            "bot_approval_pending" -> "auth.bot_approval_pending"
            "bot_approval_expired" -> "auth.bot_approval_expired"
            "bot_approval_replayed" -> "auth.bot_approval_replayed"
            "bot_approval_binding_mismatch" -> "auth.bot_approval_binding_mismatch"
            else -> fallback
        }
        is ApiError.Server -> if (error.code == "bot_approval_unavailable") {
            "auth.bot_approval_unavailable"
        } else fallback
        else -> fallback
    }

    private fun consumeFailure(code: String): TelegramAuthResult.Failed {
        store.clear()
        return TelegramAuthResult.Failed(code)
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
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.query == null && uri.fragment == null && uri.host != "auth.invalid"
    }.getOrDefault(false)

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme == "https" -> 443
        else -> -1
    }

    private fun parseQuery(raw: String): Map<String, List<String>>? = runCatching {
        raw.split('&')
            .filter(String::isNotBlank)
            .map { part ->
                val pieces = part.split('=', limit = 2)
                val key = URLDecoder.decode(pieces[0], Charsets.UTF_8.name())
                val value = URLDecoder.decode(pieces.getOrElse(1) { "" }, Charsets.UTF_8.name())
                key to value
            }
            .groupBy({ it.first }, { it.second })
    }.getOrNull()
}
