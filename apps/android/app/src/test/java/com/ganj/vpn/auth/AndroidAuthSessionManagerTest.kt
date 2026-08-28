package com.ganj.vpn.auth

import com.ganj.vpn.core.controlapi.AccessToken
import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.AuthSessionApi
import com.ganj.vpn.core.controlapi.AuthSessionCredentials
import com.ganj.vpn.core.controlapi.GuestSessionCommand
import com.ganj.vpn.core.controlapi.Gvp1CryptoFailureReason
import com.ganj.vpn.core.controlapi.Gvp1CryptoResult
import com.ganj.vpn.core.controlapi.Gvp1DecryptRequest
import com.ganj.vpn.core.controlapi.NetworkFailure
import com.ganj.vpn.core.controlapi.RefreshSessionCommand
import com.ganj.vpn.core.controlapi.RefreshToken
import com.ganj.vpn.core.controlapi.ResponseMetadata
import com.ganj.vpn.core.controlapi.SessionCredentialVault
import com.ganj.vpn.core.controlapi.TelegramAuthorization
import com.ganj.vpn.core.controlapi.TelegramAuthorizationCommand
import com.ganj.vpn.core.controlapi.TelegramExchangeCommand
import com.ganj.vpn.core.deviceidentity.DeviceIdentity
import com.ganj.vpn.core.deviceidentity.DeviceProofRequest
import com.ganj.vpn.core.deviceidentity.DevicePublicIdentity
import com.ganj.vpn.core.deviceidentity.SignedDeviceProof
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAuthSessionManagerTest {
    private val metadata = ResponseMetadata(null, null)

    @Test
    fun `first bootstrap registers device with GDP1 and persists exact server session`() {
        val issued = session("a")
        val vault = FakeVault()
        val api = FakeApi(guestResult = ApiResult.Success(issued, metadata))
        val identity = FakeIdentity()
        val manager = manager(api, identity, vault)

        val result = manager.ensureSession() as AuthBootstrapResult.Ready

        assertEquals(AuthBootstrapSource.DEVICE_PROOF, result.source)
        assertEquals(DEVICE_ID, result.deviceId)
        assertEquals("/v1/auth/guest", identity.lastRequest?.pathAndQuery)
        assertEquals(1, api.guestCalls)
        assertSame(issued, vault.lastSaved)
        assertSame(issued, vault.restore())
    }

    @Test
    fun `cold start rotates stored refresh token before using session`() {
        val original = session("a")
        val rotated = session("b")
        val vault = FakeVault(original)
        val api = FakeApi(refreshResult = ApiResult.Success(rotated, metadata))
        val identity = FakeIdentity()
        val manager = manager(api, identity, vault)

        val result = manager.ensureSession() as AuthBootstrapResult.Ready

        assertEquals(AuthBootstrapSource.REFRESHED, result.source)
        assertEquals(1, api.refreshCalls)
        assertEquals(0, api.guestCalls)
        assertSame(original.refreshToken, api.lastRefreshCommand?.refreshToken)
        assertEquals("/v1/auth/refresh", identity.lastRequest?.pathAndQuery)
        assertSame(rotated, vault.lastSaved)
        assertSame(rotated, vault.restore())
    }

    @Test
    fun `temporary network failure keeps encrypted cached session instead of destroying identity`() {
        val original = session("a")
        val vault = FakeVault(original)
        val api = FakeApi(refreshResult = ApiResult.Failure(ApiError.Network(kind = NetworkFailure.TIMEOUT)))
        val manager = manager(api, FakeIdentity(), vault)

        val result = manager.ensureSession() as AuthBootstrapResult.Ready

        assertEquals(AuthBootstrapSource.CACHED, result.source)
        assertSame(original, vault.restore())
        assertEquals(0, api.guestCalls)
        assertFalse(vault.cleared)
    }

    @Test
    fun `refresh token reuse is fail closed and never bypassed through guest registration`() {
        val vault = FakeVault(session("a"))
        val api = FakeApi(
            refreshResult = ApiResult.Failure(
                ApiError.AuthenticationExpired(null, "refresh_token_reuse_detected"),
            ),
        )
        val manager = manager(api, FakeIdentity(), vault)

        val result = manager.ensureSession() as AuthBootstrapResult.Failed

        assertEquals(AuthBootstrapFailure.SESSION_REUSE, result.reason)
        assertFalse(result.retryable)
        assertTrue(vault.cleared)
        assertEquals(0, api.guestCalls)
        assertNull(vault.restore())
    }

    @Test
    fun `ordinary expired refresh can recover same device through a new GDP1 session`() {
        val recovered = session("c")
        val vault = FakeVault(session("a"))
        val api = FakeApi(
            refreshResult = ApiResult.Failure(ApiError.AuthenticationExpired(null, "invalid_refresh_token")),
            guestResult = ApiResult.Success(recovered, metadata),
        )
        val manager = manager(api, FakeIdentity(), vault)

        val result = manager.ensureSession() as AuthBootstrapResult.Ready

        assertEquals(AuthBootstrapSource.DEVICE_PROOF, result.source)
        assertTrue(vault.cleared)
        assertEquals(1, api.guestCalls)
        assertSame(recovered, vault.lastSaved)
        assertSame(recovered, vault.restore())
    }

    @Test
    fun `durable vault failure does not expose server issued token in memory`() {
        val issued = session("a")
        val vault = FakeVault(saveFails = true)
        val api = FakeApi(guestResult = ApiResult.Success(issued, metadata))
        val manager = manager(api, FakeIdentity(), vault)

        val result = manager.ensureSession() as AuthBootstrapResult.Failed

        assertEquals(AuthBootstrapFailure.PERSISTENCE, result.reason)
        assertNull(vault.currentAccessToken())
        assertNull(vault.restore())
    }

    private fun manager(api: FakeApi, identity: FakeIdentity, vault: FakeVault) = AndroidAuthSessionManager(
        api = api,
        deviceIdentity = identity,
        vault = vault,
        nowEpochSeconds = { 1_787_889_600L },
        nonceFactory = { "n".repeat(32) },
    )

    private fun session(refreshCharacter: String) = AuthSessionCredentials(
        userId = USER_ID,
        deviceId = DEVICE_ID,
        accessToken = AccessToken.from("header.payload.signature-value"),
        accessTokenExpiresAt = "2026-08-28T04:15:00Z",
        refreshToken = RefreshToken.from(refreshCharacter.repeat(43)),
        refreshTokenExpiresAt = "2026-09-27T04:00:00Z",
    )

    private class FakeIdentity : DeviceIdentity {
        var lastRequest: DeviceProofRequest? = null

        override fun publicIdentity(): Result<DevicePublicIdentity> = Result.success(
            DevicePublicIdentity(
                installationId = DEVICE_ID,
                keyVersion = "v1",
                signingAlgorithm = "ES256",
                signingPublicKeySpki = "S".repeat(96),
                encryptionAlgorithm = "X25519",
                encryptionPublicKeyRaw = "E".repeat(43),
                signingKeyHardwareBacked = true,
            ),
        )

        override fun sign(request: DeviceProofRequest): Result<SignedDeviceProof> {
            lastRequest = request
            return Result.success(
                SignedDeviceProof(
                    algorithm = "ES256",
                    keyVersion = "v1",
                    timestampEpochSeconds = request.timestampEpochSeconds,
                    nonce = request.nonce,
                    bodySha256 = request.bodySha256,
                    signature = "s".repeat(86),
                ),
            )
        }

        override fun rotate(): Result<DevicePublicIdentity> = publicIdentity()

        override fun decrypt(request: Gvp1DecryptRequest): Gvp1CryptoResult =
            Gvp1CryptoResult.Failure(Gvp1CryptoFailureReason.PROVIDER_UNAVAILABLE)
    }

    private class FakeApi(
        private val guestResult: ApiResult<AuthSessionCredentials> = ApiResult.Failure(
            ApiError.Server(null, 503, "guest", true),
        ),
        private val refreshResult: ApiResult<AuthSessionCredentials> = ApiResult.Failure(
            ApiError.Server(null, 503, "refresh", true),
        ),
    ) : AuthSessionApi {
        var guestCalls = 0
        var refreshCalls = 0
        var lastGuestCommand: GuestSessionCommand? = null
        var lastRefreshCommand: RefreshSessionCommand? = null

        override fun createGuest(command: GuestSessionCommand): ApiResult<AuthSessionCredentials> {
            guestCalls++
            lastGuestCommand = command
            return guestResult
        }

        override fun refresh(command: RefreshSessionCommand): ApiResult<AuthSessionCredentials> {
            refreshCalls++
            lastRefreshCommand = command
            return refreshResult
        }

        override fun beginTelegram(
            accessToken: AccessToken,
            command: TelegramAuthorizationCommand,
        ): ApiResult<TelegramAuthorization> = error("not used")

        override fun exchangeTelegram(command: TelegramExchangeCommand): ApiResult<AuthSessionCredentials> = error("not used")

        override fun logout(accessToken: AccessToken): ApiResult<Boolean> = error("not used")
    }

    private class FakeVault(
        initial: AuthSessionCredentials? = null,
        private val saveFails: Boolean = false,
    ) : SessionCredentialVault {
        private var value = initial
        var cleared = false
        var lastSaved: AuthSessionCredentials? = null

        override fun currentAccessToken(): AccessToken? = value?.accessToken
        override fun currentUserId(): String? = value?.userId
        override fun currentDeviceId(): String? = value?.deviceId
        override fun currentRefreshToken(): RefreshToken? = value?.refreshToken
        override fun restore(): AuthSessionCredentials? = value

        override fun save(session: AuthSessionCredentials): Result<Unit> = if (saveFails) {
            value = null
            Result.failure(IllegalStateException("disk"))
        } else {
            value = session
            lastSaved = session
            Result.success(Unit)
        }

        override fun clear(): Result<Unit> {
            value = null
            cleared = true
            return Result.success(Unit)
        }
    }

    private companion object {
        const val USER_ID = "00000000-0000-4000-8000-000000000010"
        const val DEVICE_ID = "00000000-0000-4000-8000-000000000004"
    }
}
