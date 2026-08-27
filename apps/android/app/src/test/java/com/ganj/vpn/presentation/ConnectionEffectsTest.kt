package com.ganj.vpn.presentation

import com.ganj.vpn.core.controlapi.ConnectionProfileBroker
import com.ganj.vpn.core.controlapi.ConnectionProfileLease
import com.ganj.vpn.core.controlapi.ProfileProvisioningBinding
import com.ganj.vpn.core.controlapi.ProfileProvisioningError
import com.ganj.vpn.core.controlapi.ProfileProvisioningResult
import com.ganj.vpn.core.vpn.ConnectionRequest
import com.ganj.vpn.core.vpn.ProvisionedProfile
import com.ganj.vpn.core.vpn.VpnProtocol
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionEffectsTest {
    @Test
    fun `valid one-time action provisions and starts tunnel exactly once`() {
        val vault = vault()
        val broker = FakeBroker { ProfileProvisioningResult.Success(profile()) }
        val tunnel = FakeTunnel()
        val handle = vault.store(lease(), binding())
        val executor = ConnectionEffectExecutor(vault, broker, tunnel)

        val first = runSuspend { executor.execute(handle) }
        val replay = runSuspend { executor.execute(handle) }

        assertEquals(ConnectionEffectResult.Connected(PROFILE_ID, SERVER_ID), first)
        assertEquals("connection.action_expired", (replay as ConnectionEffectResult.Failed).failure.messageKey)
        assertEquals(1, broker.calls)
        assertEquals(1, tunnel.connectCalls)
    }

    @Test
    fun `authenticated profile failure never reaches tunnel`() {
        val vault = vault()
        val tunnel = FakeTunnel()
        val handle = vault.store(lease(), binding())
        val executor = ConnectionEffectExecutor(
            vault,
            FakeBroker { ProfileProvisioningResult.Failure(ProfileProvisioningError.AUTHENTICATION_FAILED) },
            tunnel,
        )

        val result = runSuspend { executor.execute(handle) }

        assertEquals("connection.profile_authentication_failed", (result as ConnectionEffectResult.Failed).failure.messageKey)
        assertEquals(0, tunnel.connectCalls)
    }

    @Test
    fun `tunnel error is retryable and profile material is destroyed`() {
        lateinit var issued: ProvisionedProfile
        val vault = vault()
        val tunnel = FakeTunnel(connectResult = Result.failure(IllegalStateException("offline")))
        val handle = vault.store(lease(), binding())
        val executor = ConnectionEffectExecutor(
            vault,
            FakeBroker {
                issued = profile()
                ProfileProvisioningResult.Success(issued)
            },
            tunnel,
        )

        val result = runSuspend { executor.execute(handle) }

        assertTrue((result as ConnectionEffectResult.Failed).failure.retryable)
        assertEquals("connection.tunnel_start_failed", result.failure.messageKey)
        val destroyed = runCatching { issued.useCredential { it } }.exceptionOrNull()
        assertTrue(destroyed is IllegalStateException)
    }

    private fun vault() = InMemoryConnectionActionVault(
        tokenFactory = { "00000000000000000000000000000001" },
    )

    private fun lease(): ConnectionProfileLease {
        val constructor = ConnectionProfileLease::class.java.declaredConstructors
            .single { it.parameterTypes.size == 4 }
            .apply { isAccessible = true }
        return constructor.newInstance(
            PROFILE_ID,
            SERVER_ID,
            EXPIRES_AT,
            "profile-vault-handle",
        ) as ConnectionProfileLease
    }

    private fun binding() = ProfileProvisioningBinding(
        profileId = PROFILE_ID,
        userId = USER_ID,
        serviceId = SERVICE_ID,
        deviceId = DEVICE_ID,
        serverId = SERVER_ID,
        expiresAt = EXPIRES_AT,
    )

    private fun profile() = ProvisionedProfile(
        profileId = PROFILE_ID,
        serviceId = SERVICE_ID,
        serverId = SERVER_ID,
        endpoint = "vpn.example.test",
        port = 443,
        protocol = VpnProtocol.VLESS,
        credential = "60000000-0000-4000-8000-000000000004".toByteArray(),
        expiresAtEpochMillis = 1_900_000_000_000,
    )

    private class FakeBroker(
        private val result: () -> ProfileProvisioningResult,
    ) : ConnectionProfileBroker {
        var calls = 0
        override fun provision(
            lease: ConnectionProfileLease,
            binding: ProfileProvisioningBinding,
        ): ProfileProvisioningResult {
            calls += 1
            return result()
        }
    }

    private class FakeTunnel(
        private val connectResult: Result<Unit> = Result.success(Unit),
    ) : TunnelConnector {
        var connectCalls = 0
        override suspend fun connect(request: ConnectionRequest): Result<Unit> {
            connectCalls += 1
            return connectResult
        }

        override suspend fun disconnect(): Result<Unit> = Result.success(Unit)
    }

    private companion object {
        const val PROFILE_ID = "70000000-0000-4000-8000-000000000001"
        const val USER_ID = "70000000-0000-4000-8000-000000000002"
        const val SERVICE_ID = "70000000-0000-4000-8000-000000000003"
        const val DEVICE_ID = "70000000-0000-4000-8000-000000000004"
        const val SERVER_ID = "70000000-0000-4000-8000-000000000005"
        const val EXPIRES_AT = "2027-01-01T00:00:00Z"

        fun <T> runSuspend(block: suspend () -> T): T {
            var outcome: Result<T>? = null
            block.startCoroutine(
                object : Continuation<T> {
                    override val context = EmptyCoroutineContext
                    override fun resumeWith(result: Result<T>) {
                        outcome = result
                    }
                },
            )
            return requireNotNull(outcome).getOrThrow()
        }
    }
}
