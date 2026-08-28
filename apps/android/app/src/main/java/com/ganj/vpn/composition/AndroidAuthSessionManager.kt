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
import com.ganj.vpn.core.deviceidentity.DeviceIdentity
import com.ganj.vpn.core.deviceidentity.DeviceProofRequest
import com.ganj.vpn.presentation.CurrentUserIdProvider
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Device-bound Android authentication lifecycle.
 *
 * Network-backed bootstrap/refresh calls are deliberately serialized under one lock so a rotating
 * refresh token can never be consumed concurrently by two app requests. The durable vault is
 * committed before a new access token is exposed to the rest of the app.
 *
 * Session invalidation also crosses the VPN revocation boundary. A revoked, forbidden or locally
 * cleared session must never leave a paid tunnel or a recoverable server profile alive.
 */
internal class AndroidAuthSessionManager(
    private val api: AuthSessionApi,
    private val vault: SessionCredentialVault,
    private val identity: DeviceIdentity,
    private val sessionRevocationSink: SessionRevocationSink = SessionRevocationSink.NoOp,
    private val random: SecureRandom = SecureRandom(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : RefreshingAuthTokenProvider, AuthenticationEventSink, CurrentUserIdProvider {
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
        val current = vault.restore() ?: createGuestLocked()
        current?.userId
    }

    fun currentDeviceId(): String? = synchronized(lock) {
        val current = vault.restore() ?: createGuestLocked()
        current?.deviceId
    }

    override fun onAuthenticationExpired(requestId: String?) {
        // Never perform I/O on the response callback. The next token read performs one serialized refresh.
        forceRefresh = true
    }

    fun clearLocalSession(): Result<Unit> = synchronized(lock) {
        invalidateSessionLocked()
    }

    private fun createGuestLocked(): AuthSessionCredentials? {
        val publicIdentity = identity.publicIdentity().getOrNull() ?: return null
        val unsignedBody = AuthSessionProofContract.guestUnsignedBody(
            deviceId = publicIdentity.installationId,
            keyVersion = publicIdentity.keyVersion,
            signingPublicKeySpki = publicIdentity.signingPublicKeySpki,
            encryptionPublicKeyRaw = publicIdentity.encryptionPublicKeyRaw,
        )
        val compactProof = try {
            signProof(AuthSessionProofContract.GUEST_PATH, unsignedBody)
        } finally {
            unsignedBody.fill(0)
        } ?: return null
        return when (
            val result = api.createGuest(
                GuestSessionCommand(
                    deviceId = publicIdentity.installationId,
                    keyVersion = publicIdentity.keyVersion,
                    signingPublicKeySpki = publicIdentity.signingPublicKeySpki,
                    encryptionPublicKeyRaw = publicIdentity.encryptionPublicKeyRaw,
                    deviceProof = compactProof,
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
            deviceId = current.deviceId,
            refreshToken = current.refreshToken,
        )
        val compactProof = try {
            signProof(AuthSessionProofContract.REFRESH_PATH, unsignedBody)
        } finally {
            unsignedBody.fill(0)
        } ?: return null
        return when (
            val result = api.refresh(
                RefreshSessionCommand(
                    refreshToken = current.refreshToken,
                    deviceId = current.deviceId,
                    deviceProof = compactProof,
                ),
            )
        ) {
            is ApiResult.Success -> {
                forceRefresh = false
                persistLocked(result.value)
            }
            is ApiResult.Failure -> {
                when (result.error) {
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
    }

    private fun invalidateSessionLocked(): Result<Unit> {
        forceRefresh = false
        val cleared = vault.clear()
        // Cleanup of the privileged VPN boundary must not be skipped merely because local vault
        // deletion reported an error. The sink itself is required to be non-throwing.
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
        val proofRequest = DeviceProofRequest.create(
            method = "POST",
            pathAndQuery = path,
            body = unsignedBody,
            timestampEpochSeconds = nowMillis() / 1_000L,
            nonce = nonce,
        )
        return identity.sign(proofRequest).getOrNull()?.compactValue()
    }

    private fun expiresWithin(timestamp: String, windowMillis: Long): Boolean {
        val expiry = parseUtcMillis(timestamp) ?: return true
        return expiry <= nowMillis() + windowMillis
    }

    private fun isExpired(timestamp: String): Boolean {
        val expiry = parseUtcMillis(timestamp) ?: return true
        return expiry <= nowMillis()
    }

    private fun parseUtcMillis(value: String): Long? = runCatching {
        val withoutZulu = value.removeSuffix("Z")
        val seconds = withoutZulu.substringBefore('.')
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
