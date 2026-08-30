package com.ganj.vpn.core.controlapi

import java.util.UUID

class RefreshToken private constructor(private val secret: String) {
    internal fun rawValue(): String = secret

    override fun toString(): String = "RefreshToken([REDACTED])"

    companion object {
        fun from(value: String): RefreshToken {
            require(value.length == 43) { "Refresh token length is invalid" }
            require(value.matches(Regex("^[A-Za-z0-9_-]{43}$"))) { "Refresh token format is invalid" }
            return RefreshToken(value)
        }
    }
}

class AuthSessionCredentials(
    val userId: String,
    val deviceId: String,
    val accessToken: AccessToken,
    val accessTokenExpiresAt: String,
    val refreshToken: RefreshToken,
    val refreshTokenExpiresAt: String,
) {
    init {
        requireCanonicalUuid(userId, "userId")
        requireCanonicalUuid(deviceId, "deviceId")
        requireUtcTimestamp(accessTokenExpiresAt, "accessTokenExpiresAt")
        requireUtcTimestamp(refreshTokenExpiresAt, "refreshTokenExpiresAt")
    }

    override fun toString(): String =
        "AuthSessionCredentials(userId=$userId, deviceId=$deviceId, accessToken=[REDACTED], refreshToken=[REDACTED])"
}

interface SessionCredentialVault : AuthTokenProvider {
    fun currentUserId(): String?
    fun currentDeviceId(): String?
    fun currentRefreshToken(): RefreshToken?
    fun restore(): AuthSessionCredentials?
    fun save(session: AuthSessionCredentials): Result<Unit>
    fun clear(): Result<Unit>
}

data class GuestSessionCommand(
    val deviceId: String,
    val keyVersion: String,
    val signingPublicKeySpki: String,
    val encryptionPublicKeyRaw: String,
    val deviceProof: String,
) {
    init {
        requireCanonicalUuid(deviceId, "deviceId")
        require(keyVersion.matches(KEY_VERSION))
        require(signingPublicKeySpki.length in 80..512)
        require(encryptionPublicKeyRaw.matches(BASE64URL_32_BYTES))
        require(deviceProof.length in 80..1024)
        require(deviceProof.startsWith("gdp1."))
    }
}

data class RefreshSessionCommand(
    val refreshToken: RefreshToken,
    val deviceId: String,
    val deviceProof: String,
) {
    init {
        requireCanonicalUuid(deviceId, "deviceId")
        require(deviceProof.length in 80..1024)
        require(deviceProof.startsWith("gdp1."))
    }
}

/** OIDC fallback contract. Bot Approval must be preferred by product UI. */
data class TelegramAuthorizationCommand(
    val codeChallenge: String,
    val redirectUri: String,
) {
    init {
        require(codeChallenge.matches(BASE64URL_32_BYTES))
        requireSafeRedirectUri(redirectUri)
    }
}

data class TelegramAuthorization(
    val authorizationUrl: String,
    val state: String,
    val expiresAt: String,
) {
    init {
        require(authorizationUrl.startsWith("https://"))
        require(state.length in 32..512)
        requireUtcTimestamp(expiresAt, "expiresAt")
    }
}

data class TelegramExchangeCommand(
    val code: String,
    val state: String,
    val codeVerifier: String,
) {
    init {
        require(code.length in 8..2048)
        require(state.length in 32..512)
        requirePkceVerifier(codeVerifier)
    }
}

data class TelegramBotApprovalCommand(
    val codeChallenge: String,
    val redirectUri: String,
) {
    init {
        require(codeChallenge.matches(BASE64URL_32_BYTES))
        requireSafeRedirectUri(redirectUri)
    }
}

data class TelegramBotApprovalRequest(
    val requestId: String,
    val botUrl: String,
    val state: String,
    val expiresAt: String,
) {
    init {
        requireCanonicalUuid(requestId, "requestId")
        require(botUrl.startsWith("https://t.me/"))
        require(state.matches(BASE64URL_32_BYTES))
        requireUtcTimestamp(expiresAt, "expiresAt")
    }

    override fun toString(): String =
        "TelegramBotApprovalRequest(requestId=$requestId, botUrl=[REDACTED], state=[REDACTED], expiresAt=$expiresAt)"
}

enum class TelegramBotApprovalState {
    PENDING,
    APPROVED,
    DENIED,
    CONSUMED,
    EXPIRED,
}

data class TelegramBotApprovalStatus(
    val requestId: String,
    val state: TelegramBotApprovalState,
    val expiresAt: String,
) {
    init {
        requireCanonicalUuid(requestId, "requestId")
        requireUtcTimestamp(expiresAt, "expiresAt")
    }
}

data class TelegramBotApprovalExchangeCommand(
    val requestId: String,
    val state: String,
    val codeVerifier: String,
) {
    init {
        requireCanonicalUuid(requestId, "requestId")
        require(state.matches(BASE64URL_32_BYTES))
        requirePkceVerifier(codeVerifier)
    }

    override fun toString(): String =
        "TelegramBotApprovalExchangeCommand(requestId=$requestId, state=[REDACTED], codeVerifier=[REDACTED])"
}

interface AuthSessionApi {
    fun createGuest(command: GuestSessionCommand): ApiResult<AuthSessionCredentials>
    fun refresh(command: RefreshSessionCommand): ApiResult<AuthSessionCredentials>

    fun beginTelegramBotApproval(
        accessToken: AccessToken,
        command: TelegramBotApprovalCommand,
    ): ApiResult<TelegramBotApprovalRequest>

    fun telegramBotApprovalStatus(
        accessToken: AccessToken,
        requestId: String,
    ): ApiResult<TelegramBotApprovalStatus>

    fun exchangeTelegramBotApproval(
        accessToken: AccessToken,
        command: TelegramBotApprovalExchangeCommand,
    ): ApiResult<AuthSessionCredentials>

    /** Hardened OIDC/PKCE fallback; not the primary product login path. */
    fun beginTelegram(accessToken: AccessToken, command: TelegramAuthorizationCommand): ApiResult<TelegramAuthorization>
    fun exchangeTelegram(command: TelegramExchangeCommand): ApiResult<AuthSessionCredentials>
    fun logout(accessToken: AccessToken): ApiResult<Boolean>
}

object AuthSessionApiFactory {
    fun create(baseUrl: String): AuthSessionApi = DefaultAuthSessionApi(
        transport = UrlConnectionTransport(baseUrl),
    )
}

private val KEY_VERSION = Regex("^v[1-9][0-9]{0,8}$")
private val BASE64URL_32_BYTES = Regex("^[A-Za-z0-9_-]{43}$")
private val PKCE_VERIFIER = Regex("^[A-Za-z0-9._~-]{43,128}$")
private val UTC_TIMESTAMP = Regex(
    "^[0-9]{4}-(0[1-9]|1[0-2])-([0-2][0-9]|3[01])T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\\.[0-9]{1,9})?Z$",
)

private fun requireCanonicalUuid(value: String, field: String) {
    require(runCatching { value.length == 36 && UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)) {
        "$field must be a canonical UUID"
    }
}

private fun requireUtcTimestamp(value: String, field: String) {
    require(value.matches(UTC_TIMESTAMP)) { "$field must be an RFC 3339 UTC timestamp" }
}

private fun requirePkceVerifier(value: String) {
    require(value.matches(PKCE_VERIFIER)) { "codeVerifier format is invalid" }
}

private fun requireSafeRedirectUri(value: String) {
    require(value.length in 12..2048)
    require(value.startsWith("https://"))
    require(!value.contains('\n') && !value.contains('\r') && !value.contains('#'))
}
