package com.ganj.vpn.composition

import com.ganj.vpn.core.controlapi.AccessToken
import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.AuthSessionApi
import com.ganj.vpn.core.controlapi.AuthSessionCredentials
import com.ganj.vpn.core.controlapi.AuthSessionProofContract
import com.ganj.vpn.core.controlapi.AuthenticationEventSink
import com.ganj.vpn.core.controlapi.GuestSessionCommand
import com.ganj.vpn.core.controlapi.RefreshSessionCommand
import com.ganj.vpn.core.controlapi.RefreshingAuthTokenProvider
import com.ganj.vpn.core.controlapi.SessionCredentialVault
import com.ganj.vpn.core.controlapi.TelegramAuthorization
import com.ganj.vpn.core.controlapi.TelegramAuthorizationCommand
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

    override fun logout(): Boolean = synchronized(lock) {
        val token = vault.restore()?.accessToken
        val remote = token?.let { api.logout(it) }
        forceRefresh = false
        val localCleared = vault.clear().isSuccess
        localCleared && (remote == null || remote is ApiResult.Success)
    }

    override fun onAuthenticationExpired(requestId: String?) {
        forceRefresh = true
    }

    fun clearLocalSession(): Result<Unit> = synchronized(lock) {
        forceRefresh = false
        vault.clear()
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
            vault.clear()
            forceRefresh = false
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
                    vault.clear()
                    forceRefresh = false
                    if (expired.code == "refresh_token_reuse_detected") null else createGuestLocked()
                }
                is ApiError.Forbidden -> {
                    vault.clear()
                    forceRefresh = false
                    null
                }
                else -> null
            }
        }
    }

    private fun persistLocked(value: AuthSessionCredentials): AuthSessionCredentials? {
        if (vault.save(value).isFailure) {
            vault.clear()
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
