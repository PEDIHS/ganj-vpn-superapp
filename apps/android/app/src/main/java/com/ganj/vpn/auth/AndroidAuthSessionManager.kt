package com.ganj.vpn.auth

import android.util.Base64
import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.AuthSessionApi
import com.ganj.vpn.core.controlapi.AuthSessionCredentials
import com.ganj.vpn.core.controlapi.AuthSessionProofContract
import com.ganj.vpn.core.controlapi.GuestSessionCommand
import com.ganj.vpn.core.controlapi.RefreshSessionCommand
import com.ganj.vpn.core.controlapi.SessionCredentialVault
import com.ganj.vpn.core.deviceidentity.DeviceIdentity
import com.ganj.vpn.core.deviceidentity.DeviceProofRequest
import java.security.SecureRandom

internal enum class AuthBootstrapSource {
    CACHED,
    REFRESHED,
    DEVICE_PROOF,
}

internal enum class AuthBootstrapFailure {
    DEVICE_IDENTITY,
    DEVICE_PROOF,
    NETWORK,
    SERVER,
    SESSION_REUSE,
    DEVICE_CONFLICT,
    PROTOCOL,
    PERSISTENCE,
}

internal sealed interface AuthBootstrapResult {
    data class Ready(
        val userId: String,
        val deviceId: String,
        val source: AuthBootstrapSource,
    ) : AuthBootstrapResult

    data class Failed(
        val reason: AuthBootstrapFailure,
        val retryable: Boolean,
    ) : AuthBootstrapResult
}

internal class AndroidAuthSessionManager(
    private val api: AuthSessionApi,
    private val deviceIdentity: DeviceIdentity,
    private val vault: SessionCredentialVault,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    private val nonceFactory: () -> String = ::secureNonce,
) {
    fun ensureSession(): AuthBootstrapResult {
        val stored = vault.restore()
        if (stored != null) {
            when (val refreshed = refresh(stored)) {
                is ApiResult.Success -> return persist(refreshed.value, AuthBootstrapSource.REFRESHED)
                is ApiResult.Failure -> when (val error = refreshed.error) {
                    is ApiError.Network,
                    is ApiError.RateLimited,
                    is ApiError.Server,
                    -> return AuthBootstrapResult.Ready(
                        userId = stored.userId,
                        deviceId = stored.deviceId,
                        source = AuthBootstrapSource.CACHED,
                    )
                    is ApiError.AuthenticationExpired -> {
                        if (error.code == "refresh_token_reuse_detected") {
                            vault.clear()
                            return AuthBootstrapResult.Failed(
                                AuthBootstrapFailure.SESSION_REUSE,
                                retryable = false,
                            )
                        }
                        vault.clear()
                    }
                    is ApiError.Conflict -> {
                        if (error.code == "device_proof_replayed") {
                            return AuthBootstrapResult.Failed(
                                AuthBootstrapFailure.DEVICE_PROOF,
                                retryable = true,
                            )
                        }
                        vault.clear()
                    }
                    is ApiError.Forbidden -> {
                        vault.clear()
                        return AuthBootstrapResult.Failed(
                            AuthBootstrapFailure.DEVICE_CONFLICT,
                            retryable = false,
                        )
                    }
                    else -> vault.clear()
                }
            }
        }
        return registerDeviceSession()
    }

    private fun refresh(session: AuthSessionCredentials): ApiResult<AuthSessionCredentials> {
        val unsignedBody = AuthSessionProofContract.refreshUnsignedBody(
            deviceId = session.deviceId,
            refreshToken = session.refreshToken,
        )
        val proof = try {
            signProof(
                path = AuthSessionProofContract.REFRESH_PATH,
                unsignedBody = unsignedBody,
            )
        } finally {
            unsignedBody.fill(0)
        } ?: return ApiResult.Failure(ApiError.Protocol(null, "device_proof_unavailable"))
        return api.refresh(
            RefreshSessionCommand(
                refreshToken = session.refreshToken,
                deviceId = session.deviceId,
                deviceProof = proof,
            ),
        )
    }

    private fun registerDeviceSession(): AuthBootstrapResult {
        val identity = deviceIdentity.publicIdentity().getOrElse {
            return AuthBootstrapResult.Failed(AuthBootstrapFailure.DEVICE_IDENTITY, retryable = false)
        }
        val unsignedBody = AuthSessionProofContract.guestUnsignedBody(
            deviceId = identity.installationId,
            keyVersion = identity.keyVersion,
            signingPublicKeySpki = identity.signingPublicKeySpki,
            encryptionPublicKeyRaw = identity.encryptionPublicKeyRaw,
        )
        val proof = try {
            signProof(
                path = AuthSessionProofContract.GUEST_PATH,
                unsignedBody = unsignedBody,
            )
        } finally {
            unsignedBody.fill(0)
        } ?: return AuthBootstrapResult.Failed(AuthBootstrapFailure.DEVICE_PROOF, retryable = false)

        return when (
            val result = api.createGuest(
                GuestSessionCommand(
                    deviceId = identity.installationId,
                    keyVersion = identity.keyVersion,
                    signingPublicKeySpki = identity.signingPublicKeySpki,
                    encryptionPublicKeyRaw = identity.encryptionPublicKeyRaw,
                    deviceProof = proof,
                ),
            )
        ) {
            is ApiResult.Success -> persist(result.value, AuthBootstrapSource.DEVICE_PROOF)
            is ApiResult.Failure -> AuthBootstrapResult.Failed(
                reason = result.error.bootstrapFailure(),
                retryable = result.error.isRetryableBootstrapFailure(),
            )
        }
    }

    private fun signProof(path: String, unsignedBody: ByteArray): String? {
        val request = runCatching {
            DeviceProofRequest.create(
                method = "POST",
                pathAndQuery = path,
                body = unsignedBody,
                timestampEpochSeconds = nowEpochSeconds(),
                nonce = nonceFactory(),
            )
        }.getOrNull() ?: return null
        return deviceIdentity.sign(request).getOrNull()?.compactValue()
    }

    private fun persist(
        credentials: AuthSessionCredentials,
        source: AuthBootstrapSource,
    ): AuthBootstrapResult {
        if (vault.save(credentials).isFailure) {
            vault.clear()
            return AuthBootstrapResult.Failed(AuthBootstrapFailure.PERSISTENCE, retryable = false)
        }
        return AuthBootstrapResult.Ready(
            userId = credentials.userId,
            deviceId = credentials.deviceId,
            source = source,
        )
    }

    override fun toString(): String = "AndroidAuthSessionManager([REDACTED])"
}

private fun ApiError.bootstrapFailure(): AuthBootstrapFailure = when (this) {
    is ApiError.Network, is ApiError.RateLimited -> AuthBootstrapFailure.NETWORK
    is ApiError.Server -> AuthBootstrapFailure.SERVER
    is ApiError.AuthenticationExpired -> if (code == "refresh_token_reuse_detected") {
        AuthBootstrapFailure.SESSION_REUSE
    } else {
        AuthBootstrapFailure.PROTOCOL
    }
    is ApiError.Conflict -> if (code == "device_identity_conflict") {
        AuthBootstrapFailure.DEVICE_CONFLICT
    } else {
        AuthBootstrapFailure.DEVICE_PROOF
    }
    is ApiError.Forbidden -> AuthBootstrapFailure.DEVICE_CONFLICT
    else -> AuthBootstrapFailure.PROTOCOL
}

private fun ApiError.isRetryableBootstrapFailure(): Boolean = when (this) {
    is ApiError.Network, is ApiError.RateLimited -> true
    is ApiError.Server -> retryable
    is ApiError.Conflict -> code == "device_proof_replayed"
    else -> false
}

private fun secureNonce(): String {
    val bytes = ByteArray(24)
    SecureRandom().nextBytes(bytes)
    return try {
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    } finally {
        bytes.fill(0)
    }
}
