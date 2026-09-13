package com.ganj.vpn.presentation

import com.ganj.vpn.core.controlapi.*
import com.ganj.vpn.core.vpn.ConnectionRequest
import com.ganj.vpn.core.vpn.ProvisionedProfile
import com.ganj.vpn.core.vpn.VpnProtocol
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ConnectionLatencyProberTest {
    @Test
    fun `unauthorized server never requests a lease or probes`() = runBlocking {
        val fixture = Fixture(authorized = false)
        assertNull(fixture.prober.probe(SERVICE, SERVER))
        assertEquals(0, fixture.requests)
        assertEquals(0, fixture.probes)
    }

    @Test
    fun `mismatched lease never reaches crypto or native probe`() = runBlocking {
        val fixture = Fixture(leaseServer = OTHER)
        assertNull(fixture.prober.probe(SERVICE, SERVER))
        assertEquals(0, fixture.provisions)
        assertEquals(0, fixture.probes)
    }

    @Test
    fun `authenticated probe binds selected server and destroys credentials`() = runBlocking {
        val fixture = Fixture()
        assertEquals(123L, fixture.prober.probe(SERVICE, SERVER))
        assertEquals(SERVER, fixture.command?.serverId)
        assertEquals(SERVER, fixture.binding?.serverId)
        assertEquals(1, fixture.probes)
        assertTrue(runCatching { fixture.profile.useCredential { it } }.isFailure)
    }

    @Test
    fun `cancellation propagates and destroys provisioned credentials`() = runBlocking {
        val fixture = Fixture(cancelProbe = true)
        val failure = runCatching { fixture.prober.probe(SERVICE, SERVER) }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertTrue(runCatching { fixture.profile.useCredential { it } }.isFailure)
    }

    private class Fixture(authorized: Boolean = true, leaseServer: String = SERVER, cancelProbe: Boolean = false) {
        var requests = 0
        var provisions = 0
        var probes = 0
        var command: ConnectionProfileCommand? = null
        var binding: ProfileProvisioningBinding? = null
        val profile = ProvisionedProfile(PROFILE, SERVICE, SERVER, "test.invalid", 443, VpnProtocol.VLESS,
            "40000000-0000-4000-8000-000000000001".toByteArray(), expiresAtEpochMillis = 1_900_000_000_000)
        private val repository = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ControlApiRepository::class.java)) { _, method, args ->
            check(method.name == "prepareConnection")
            requests++
            command = args!![0] as ConnectionProfileCommand
            val constructor = ConnectionProfileLease::class.java.declaredConstructors.single { it.parameterTypes.size == 4 }
                .apply { isAccessible = true }
            ApiResult.Success(constructor.newInstance(PROFILE, leaseServer, "2027-01-01T00:00:00Z", "test-handle") as ConnectionProfileLease)
        } as ControlApiRepository
        val prober = ConnectionLatencyProber(repository, object : ConnectionProfileBroker {
            override fun provision(lease: ConnectionProfileLease, binding: ProfileProvisioningBinding): ProfileProvisioningResult {
                provisions++
                this@Fixture.binding = binding
                return ProfileProvisioningResult.Success(profile)
            }
        }, CurrentUserIdProvider { USER }, object : ConnectionProfileContextProvider {
            override fun forEntitlement(entitlementId: String): ConnectionProfileContext? = error("probe must not change current selection")
            override fun forServer(entitlementId: String, serverId: String): ConnectionProfileContext? =
                if (authorized) ConnectionProfileContext(DEVICE, serverId, "nonce", "proof") else null
        }, object : TunnelConnector {
            override suspend fun connect(request: ConnectionRequest): Result<Unit> = error("probe must not start VPN")
            override suspend fun disconnect(): Result<Unit> = error("probe must not stop VPN")
            override suspend fun probe(profile: ProvisionedProfile): Long? {
                probes++
                if (cancelProbe) throw CancellationException("test cancellation")
                return 123L
            }
        })
    }

    private companion object {
        const val PROFILE = "70000000-0000-4000-8000-000000000001"
        const val USER = "70000000-0000-4000-8000-000000000002"
        const val SERVICE = "70000000-0000-4000-8000-000000000003"
        const val DEVICE = "70000000-0000-4000-8000-000000000004"
        const val SERVER = "70000000-0000-4000-8000-000000000005"
        const val OTHER = "70000000-0000-4000-8000-000000000006"
    }
}
