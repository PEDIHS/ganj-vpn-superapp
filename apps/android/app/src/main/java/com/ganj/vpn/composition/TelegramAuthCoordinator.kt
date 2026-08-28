package com.ganj.vpn.composition

import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.TelegramAuthorizationCommand
import com.ganj.vpn.core.controlapi.TelegramExchangeCommand
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal data class TelegramAuthFlow(
    val state: String,
    val codeVerifier: String,
    val redirectUri: String,
    val expiresAt: String,
)

internal interface TelegramAuthFlowStore {
    fun restore(): TelegramAuthFlow?
    fun save(flow: TelegramAuthFlow): Result<Unit>
    fun clear(): Result<Unit>
}

internal sealed interface TelegramAuthResult {
    data class Launch(val authorizationUrl: String) : TelegramAuthResult
    data object Linked : TelegramAuthResult
    data object LoggedOut : TelegramAuthResult
    data class Failed(val code: String) : TelegramAuthResult
}

/**
 * Owns the Android Telegram OIDC/PKCE ceremony. Provider credentials never enter Compose state.
 * The callback flow is cleared before exchange so duplicate intents and process recreation cannot
 * replay an authorization code against the app.
 */
internal class TelegramAuthCoordinator(
    private val session: AndroidAuthSessionManager,
    private val store: TelegramAuthFlowStore,
    private val redirectUri: String,
    private val random: SecureRandom = SecureRandom(),
) {
    fun begin(): TelegramAuthResult {
        if (!isAllowedRedirect(redirectUri)) return TelegramAuthResult.Failed("auth.redirect_not_configured")
        val verifierBytes = ByteArray(32)
        random.nextBytes(verifierBytes)
        val verifier = try {
            Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes)
        } finally {
            verifierBytes.fill(0)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = try {
            Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        } finally {
            digest.fill(0)
        }
        return when (val result = session.beginTelegram(TelegramAuthorizationCommand(challenge, redirectUri))) {
            is ApiResult.Failure -> TelegramAuthResult.Failed("auth.telegram_start_failed")
            is ApiResult.Success -> {
                val flow = TelegramAuthFlow(
                    state = result.value.state,
                    codeVerifier = verifier,
                    redirectUri = redirectUri,
                    expiresAt = result.value.expiresAt,
                )
                if (store.save(flow).isFailure) {
                    TelegramAuthResult.Failed("auth.flow_persistence_failed")
                } else {
                    TelegramAuthResult.Launch(result.value.authorizationUrl)
                }
            }
        }
    }

    fun complete(callbackUri: String): TelegramAuthResult {
        val flow = store.restore() ?: return TelegramAuthResult.Failed("auth.flow_missing_or_consumed")
        val callback = runCatching { URI(callbackUri) }.getOrNull()
            ?: return consumeFailure("auth.callback_invalid")
        val expected = runCatching { URI(flow.redirectUri) }.getOrNull()
            ?: return consumeFailure("auth.callback_invalid")
        if (callback.scheme != expected.scheme || callback.host != expected.host || callback.path != expected.path) {
            return consumeFailure("auth.callback_redirect_mismatch")
        }
        val query = parseQuery(callback.rawQuery ?: "")
        val code = query["code"]?.singleOrNull()
        val state = query["state"]?.singleOrNull()
        if (code.isNullOrBlank() || state.isNullOrBlank() || state != flow.state) {
            return consumeFailure("auth.callback_state_mismatch")
        }
        // One-shot locally before any network call. A failed exchange starts a fresh ceremony.
        if (store.clear().isFailure) return TelegramAuthResult.Failed("auth.flow_clear_failed")
        return when (
            session.exchangeTelegram(
                TelegramExchangeCommand(code = code, state = state, codeVerifier = flow.codeVerifier),
            )
        ) {
            is ApiResult.Success -> TelegramAuthResult.Linked
            is ApiResult.Failure -> TelegramAuthResult.Failed("auth.telegram_exchange_failed")
        }
    }

    fun logout(): TelegramAuthResult {
        store.clear()
        return if (session.logout()) TelegramAuthResult.LoggedOut
        else TelegramAuthResult.Failed("auth.logout_remote_failed")
    }

    private fun consumeFailure(code: String): TelegramAuthResult.Failed {
        store.clear()
        return TelegramAuthResult.Failed(code)
    }

    private fun isAllowedRedirect(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.query == null && uri.fragment == null && uri.host != "auth.invalid"
    }.getOrDefault(false)

    private fun parseQuery(raw: String): Map<String, List<String>> = raw.split('&')
        .filter(String::isNotBlank)
        .mapNotNull { part ->
            val pieces = part.split('=', limit = 2)
            val key = java.net.URLDecoder.decode(pieces[0], Charsets.UTF_8.name())
            val value = java.net.URLDecoder.decode(pieces.getOrElse(1) { "" }, Charsets.UTF_8.name())
            key to value
        }
        .groupBy({ it.first }, { it.second })
}
