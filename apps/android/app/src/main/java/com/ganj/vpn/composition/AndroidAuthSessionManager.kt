package com.ganj.vpn.composition

import com.ganj.vpn.core.controlapi.AccessToken
import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.AuthSessionApi
import com.ganj.vpn.core.controlapi.AuthSessionCredentials
import com.ganj.vpn.core.controlapi.AuthSessionProofContract
import com.ganj.vpn.core.controlapi.AuthenticationEventSink
import com.ganj.vpn.core.controlapi.CurrentAccount
import com.ganj.vpn.core.controlapi.GuestSessionCommand
import com.ganj.vpn.core.controlapi.RefreshSessionCommand
import com.ganj.vpn.core.controlapi.RefreshingAuthTokenProvider
import com.ganj.vpn.core.controlapi.SessionCredentialVault
import com.ganj.vpn.core.controlapi.TelegramAuthorization
import com.ganj.vpn.core.controlapi.TelegramAuthorizationCommand
import com.ganj.vpn.core.controlapi.TelegramBotApprovalCommand
import com.ganj.vpn.core.controlapi.TelegramBotApprovalExchangeCommand
import com.ganj.vpn.core.controlapi.TelegramBotApprovalRequest
import com.ganj.vpn.core.controlapi.TelegramBotApprovalStatus
import com.ganj.vpn.core.controlapi.TelegramExchangeCommand
import com.ganj.vpn.core.deviceidentity.DeviceIdentity
import com.ganj.vpn.core.deviceidentity.DeviceProofRequest
import com.ganj.vpn.presentation.CurrentUserIdProvider
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal class AndroidAuthSessionManager(
    private val api: AuthSessionApi,
    private val vault: SessionCredentialVault,
    private val identity: DeviceIdentity,
    private val sessionRevocationSink: SessionRevocationSink = SessionRevocationSink.NoOp,
    private val random: SecureRandom = SecureRandom(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : RefreshingAuthTokenProvider,
    AuthenticationEventSink,
    CurrentUserIdProvider,
    TelegramAuthSessionGateway {
    private val lock = Any()

    @Volatile
    private var forceRefresh = false

    override fun currentAccessToken(): AccessToken? = synchronized(lock) {
        val current = vault.restore() ?: return@synchronized createGuestLocked()?.accessToken
        if (!forceRefresh && !expiresWithin(current.accessTokenExpiresAt, REFRESH_EARLY_MILLIS)) {
            return@synchronized current.accessToken
        }
        val refreshed = refreshLocked(current)
        refreshed?.accessToken ?: current.takeIf { !isExpired(it.accessTokenExpiresAt) }?.accessToken
    }

    override fun refreshAccessToken(): AccessToken? = synchronized(lock) {
        val current = vault.restore() ?: return@synchronized createGuestLocked()?.accessToken
        refreshLocked(current)?.accessToken
    }

    override fun currentUserId(): String? = synchronized(lock) {
        (vault.restore() ?: createGuestLocked())?.userId
    }

    fun currentDeviceId(): String? = synchronized(lock) {
        (vault.restore() ?: createGuestLocked())?.deviceId
    }

    fun currentAccount(): ApiResult<CurrentAccount> = synchronized(lock) {
        val token = currentAccessToken()
            ?: return@synchronized ApiResult.Failure(ApiError.AuthenticationExpired(null, "auth_required"))
        when (val first = api.currentAccount(token)) {
            is ApiResult.Success -> first
            is ApiResult.Failure -> if (first.error is ApiError.AuthenticationExpired) {
                val current = vault.restore()
                val refreshed = current?.let(::refreshLocked)
                if (refreshed != null) api.currentAccount(refreshed.accessToken) else first
            } else {
                first
            }
        }
    }

    override fun beginTelegramBotApproval(
        command: TelegramBotApprovalCommand,
    ): ApiResult<TelegramBotApprovalRequest> = synchronized(lock) {
        val token = currentAccessToken()
            ?: return@synchronized ApiResult.Failure(ApiError.AuthenticationExpired(null, "auth_required"))
        api.beginTelegramBotApproval(token, command)
    }

    override fun telegramBotApprovalStatus(
        requestId: String,
    ): ApiResult<TelegramBotApprovalStatus> = synchronized(lock) {
        val token = currentAccessToken()
            ?: return@synchronized ApiResult.Failure(ApiError.AuthenticationExpired(null, "auth_required"))
        api.telegramBotApprovalStatus(token, requestId)
    }

    override fun exchangeTelegramBotApproval(
        command: TelegramBotApprovalExchangeCommand,
    ): ApiResult<AuthSessionCredentials> = synchronized(lock) {
        val token = currentAccessToken()
            ?: return@synchronized ApiResult.Failure(ApiError.AuthenticationExpired(null, "auth_required"))
        when (val result = api.exchangeTelegramBotApproval(token, command)) {
            is ApiResult.Success -> if (persistLocked(result.value) != null) {
                result
            } else {
                ApiResult.Failure(ApiError.Protocol(null, "session_persistence_failed"))
            }
            is ApiResult.Failure -> result
        }
    }

    override fun beginTelegram(
        command: TelegramAuthorizationCommand,
    ): ApiResult<TelegramAuthorization> = synchronized(lock) {
        val token = currentAccessToken()
            ?: return@synchronized ApiResult.Failure(
                ApiError.AuthenticationExpired(null, "auth_required"),
            )
        api.beginTelegram(token, command)
    }

    override fun exchangeTelegram(
        command: TelegramExchangeCommand,
    ): ApiResult<AuthSessionCredentials> = synchronized(lock) {
        when (val result = api.exchangeTelegram(command)) {
            is ApiResult.Success -> if (persistLocked(result.value) != null) {
                result
            } else {
                ApiResult.Failure(ApiError.Protocol(null, "session_persistence_failed"))
            }
            is ApiResult.Failure -> result
        }
    }

    /**
     * A retryable remote failure must not destroy the only credential that can revoke that session.
     * We therefore keep the local vault intact for network/server failures and clear it only after
     * confirmed logout or when the server already considers the credential invalid/expired.
     */
    override fun logout(): Boolean = synchronized(lock) {
        val current = vault.restore()
            ?: return@synchronized invalidateSessionLocked().isSuccess

        when (val remote = api.logout(current.accessToken)) {
            is ApiResult.Success -> invalidateSessionLocked().isSuccess
            is ApiResult.Failure -> when (remote.error) {
                is ApiError.AuthenticationRequired,
                is ApiError.AuthenticationExpired -> invalidateSessionLocked().isSuccess
                else -> false
            }
        }
    }

    override fun onAuthenticationExpired(requestId: String?) {
        forceRefresh = true
    }

    fun clearLocalSession(): Result<Unit> = synchronized(lock) {
        invalidateSessionLocked()
    }

    private fun createGuestLocked(): AuthSessionCredentials? {
        val publicIdentity = identity.publicIdentity().getOrNull() ?: return null
        val unsignedBody = AuthSessionProofContract.guestUnsignedBody(
            publicIdentity.installationId,
            publicIdentity.keyVersion,
            publicIdentity.signingPublicKeySpki,
            publicIdentity.encryptionPublicKeyRaw,
        )
        val compactProof = try {
            signProof(AuthSessionProofContract.GUEST_PATH, unsignedBody)
        } finally {
            unsignedBody.fill(0)
        } ?: return null
        return when (
            val result = api.createGuest(
                GuestSessionCommand(
                    publicIdentity.installationId,
                    publicIdentity.keyVersion,
                    publicIdentity.signingPublicKeySpki,
                    publicIdentity.encryptionPublicKeyRaw,
                    compactProof,
                ),
            )
        ) {
            is ApiResult.Failure -> null
            is ApiResult.Success -> persistLocked(result.value)
        }
    }

    private fun refreshLocked(current: AuthSessionCredentials): AuthSessionCredentials? {
        if (isExpired(current.refreshTokenExpiresAt)) {
            invalidateSessionLocked()
            return createGuestLocked()
        }
        val unsignedBody = AuthSessionProofContract.refreshUnsignedBody(
            current.deviceId,
            current.refreshToken,
        )
        val compactProof = try {
            signProof(AuthSessionProofContract.REFRESH_PATH, unsignedBody)
        } finally {
            unsignedBody.fill(0)
        } ?: return null
        return when (
            val result = api.refresh(
                RefreshSessionCommand(
                    current.refreshToken,
                    current.deviceId,
                    compactProof,
                ),
            )
        ) {
            is ApiResult.Success -> {
                forceRefresh = false
                persistLocked(result.value)
            }
            is ApiResult.Failure -> when (result.error) {
                is ApiError.AuthenticationExpired -> {
                    val expired = result.error as ApiError.AuthenticationExpired
                    invalidateSessionLocked()
                    if (expired.code == "refresh_token_reuse_detected") null else createGuestLocked()
                }
                is ApiError.Forbidden -> {
                    invalidateSessionLocked()
                    null
                }
                else -> null
            }
        }
    }

    private fun invalidateSessionLocked(): Result<Unit> {
        forceRefresh = false
        val cleared = vault.clear()
        runCatching { sessionRevocationSink.onSessionInvalidated() }
        return cleared
    }

    private fun persistLocked(value: AuthSessionCredentials): AuthSessionCredentials? {
        if (vault.save(value).isFailure) {
            invalidateSessionLocked()
            return null
        }
        return value
    }

    private fun signProof(path: String, unsignedBody: ByteArray): String? {
        val nonceBytes = ByteArray(24)
        random.nextBytes(nonceBytes)
        val nonce = try {
            buildString(nonceBytes.size * 2) {
                nonceBytes.forEach { byte ->
                    val value = byte.toInt() and 0xff
                    append(HEX[value ushr 4])
                    append(HEX[value and 0x0f])
                }
            }
        } finally {
            nonceBytes.fill(0)
        }
        return identity.sign(
            DeviceProofRequest.create(
                "POST",
                path,
                unsignedBody,
                nowMillis() / 1_000L,
                nonce,
            ),
        ).getOrNull()?.compactValue()
    }

    private fun expiresWithin(timestamp: String, windowMillis: Long): Boolean =
        (parseUtcMillis(timestamp) ?: return true) <= nowMillis() + windowMillis

    private fun isExpired(timestamp: String): Boolean =
        (parseUtcMillis(timestamp) ?: return true) <= nowMillis()

    private fun parseUtcMillis(value: String): Long? = runCatching {
        val seconds = value.removeSuffix("Z").substringBefore('.')
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(seconds)?.time
    }.getOrNull()

    override fun toString(): String = "AndroidAuthSessionManager([REDACTED])"

    private companion object {
        const val REFRESH_EARLY_MILLIS = 120_000L
        const val HEX = "0123456789abcdef"
    }
}
