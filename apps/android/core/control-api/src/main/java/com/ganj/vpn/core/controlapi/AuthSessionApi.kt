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

data class TelegramAuthorizationCommand(
    val codeChallenge: String,
    val redirectUri: String,
) {
    init {
        require(codeChallenge.matches(BASE64URL_32_BYTES))
        require(redirectUri.length in 12..2048)
        require(!redirectUri.contains('\n') && !redirectUri.contains('\r'))
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
        require(codeVerifier.length in 43..128)
        require(codeVerifier.matches(Regex("^[A-Za-z0-9._~-]+$")))
    }
}

data class TelegramBotAuthorization(
    val approvalUrl: String,
    val state: String,
    val expiresAt: String,
) {
    init {
        require(approvalUrl.startsWith("https://t.me/"))
        require(state.matches(BASE64URL_32_BYTES))
        requireUtcTimestamp(expiresAt, "expiresAt")
    }
}

enum class TelegramBotApprovalState {
    PENDING,
    APPROVED,
    CANCELLED,
    EXPIRED,
    CONSUMED,
}

data class TelegramBotApprovalStatus(
    val state: TelegramBotApprovalState,
    val code: String?,
    val expiresAt: String,
) {
    init {
        requireUtcTimestamp(expiresAt, "expiresAt")
        if (state == TelegramBotApprovalState.APPROVED) {
            require(code != null && code.matches(BASE64URL_32_BYTES))
        } else {
            require(code == null)
        }
    }
}

interface AuthSessionApi {
    fun createGuest(command: GuestSessionCommand): ApiResult<AuthSessionCredentials>
    fun refresh(command: RefreshSessionCommand): ApiResult<AuthSessionCredentials>

    /** OIDC fallback. Bot Approval is the primary Telegram UX. */
    fun beginTelegram(accessToken: AccessToken, command: TelegramAuthorizationCommand): ApiResult<TelegramAuthorization>
    fun exchangeTelegram(command: TelegramExchangeCommand): ApiResult<AuthSessionCredentials>

    fun beginTelegramBot(accessToken: AccessToken, command: TelegramAuthorizationCommand): ApiResult<TelegramBotAuthorization>
    fun telegramBotStatus(accessToken: AccessToken, state: String): ApiResult<TelegramBotApprovalStatus>
    fun exchangeTelegramBot(
        accessToken: AccessToken,
        command: TelegramExchangeCommand,
    ): ApiResult<AuthSessionCredentials>

    fun logout(accessToken: AccessToken): ApiResult<Boolean>
}

object AuthSessionApiFactory {
    fun create(baseUrl: String): AuthSessionApi = DefaultAuthSessionApi(
        transport = UrlConnectionTransport(baseUrl),
    )
}

private val KEY_VERSION = Regex("^v[1-9][0-9]{0,8}$")
private val BASE64URL_32_BYTES = Regex("^[A-Za-z0-9_-]{43}$")
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
