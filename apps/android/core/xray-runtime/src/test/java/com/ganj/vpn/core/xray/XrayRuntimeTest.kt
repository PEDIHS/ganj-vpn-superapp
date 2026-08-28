package com.ganj.vpn.core.xray

import com.ganj.vpn.core.vpn.ConnectionPhase
import com.ganj.vpn.core.vpn.ConnectionRequest
import com.ganj.vpn.core.vpn.ProvisionedProfile
import com.ganj.vpn.core.vpn.ProvisionedSecurity
import com.ganj.vpn.core.vpn.ProvisionedTransport
import com.ganj.vpn.core.vpn.VpnProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import libXray.LibXray

class XrayRuntimeTest {
    @Test
    fun `official libxray bridge uses protected DNS and pinned configJSON contract`() {
        LibXray.resetObservations()
        val bridge = ReflectiveLibXrayBridge(javaClass.classLoader!!)

        assertTrue(bridge.installSocketProtector(SocketProtector { it == 91 }).success)
        assertTrue(bridge.start(SensitiveXrayConfig("{\"outbounds\":[]}")).success)
        assertTrue(LibXray.protect(91))
        assertEquals("1.1.1.1:53", LibXray.observedDns)
        assertTrue(LibXray.observedRequest.orEmpty().contains("\"method\":\"runXrayFromJson\""))
        assertTrue(LibXray.observedRequest.orEmpty().contains("\"configJSON\""))
        assertFalse(LibXray.observedRequest.orEmpty().contains("\"xrayJson\""))

        assertTrue(bridge.stop().success)
        assertTrue(LibXray.dnsReset)
    }

    @Test
    fun `compiler creates native tun and vless reality grpc config only from typed profile`() {
        val profile = profile(
            security = ProvisionedSecurity.Reality(
                serverName = "edge.example.com",
                publicKey = "A".repeat(43),
                shortId = "a1b2c3d4",
            ),
            transport = ProvisionedTransport.Grpc("ganj-vpn"),
            flow = null,
        )
        val sensitive = XrayConfigCompiler().compile(profile, tunFileDescriptor = 42)

        assertEquals("SensitiveXrayConfig([REDACTED])", sensitive.toString())
        val json = sensitive.consume()
        assertTrue(json.contains("\"xray.tun.fd\":\"42\""))
        assertTrue(json.contains("\"protocol\":\"tun\""))
        assertTrue(json.contains("\"protocol\":\"vless\""))
        assertTrue(json.contains("\"security\":\"reality\""))
        assertTrue(json.contains("\"network\":\"grpc\""))
        assertTrue(json.contains("40000000-0000-4000-8000-000000000001"))
        assertFalse(profile.toString().contains("40000000-0000-4000-8000-000000000001"))
        assertFalse(sensitive.toString().contains("40000000-0000-4000-8000-000000000001"))
        assertTrue(runCatching { sensitive.consume() }.isFailure)
        profile.close()
    }

    @Test
    fun `engine establishes tun protects sockets starts and destroys profile`() {
        val platform = FakePlatform()
        val native = FakeNative()
        var now = 1_000L
        val engine = AndroidXrayEngine(platform, native, clock = { now })
        val profile = profile(expiresAt = 5_000L)

        assertTrue(engine.connect(ConnectionRequest(profile)).isSuccess)
        assertEquals(ConnectionPhase.CONNECTED, engine.currentState().phase)
        assertEquals(77, native.observedFd)
        assertEquals(listOf("establish", "protect", "start"), platform.events + native.events)
        assertTrue(runCatching { XrayConfigCompiler().compile(profile, 77) }.isFailure)

        now = 2_000L
        assertTrue(engine.disconnect().isSuccess)
        assertTrue(platform.tunnelClosed)
        assertEquals(ConnectionPhase.DISCONNECTED, engine.currentState().phase)
    }

    @Test
    fun `reconnect restarts core while retaining existing tun`() {
        val platform = FakePlatform()
        val native = FakeNative()
        var now = 1_000L
        val engine = AndroidXrayEngine(platform, native, clock = { now })

        assertTrue(engine.connect(ConnectionRequest(profile(expiresAt = 10_000L))).isSuccess)
        val firstConnectedAt = engine.currentState().connectedAtEpochMillis
        now = 2_000L

        assertTrue(engine.reconnect(ConnectionRequest(profile(expiresAt = 10_000L))).isSuccess)

        assertEquals(ConnectionPhase.CONNECTED, engine.currentState().phase)
        assertEquals(firstConnectedAt, engine.currentState().connectedAtEpochMillis)
        assertEquals(1, platform.events.count { it == "establish" })
        assertFalse(platform.tunnelClosed)
        assertEquals(1, native.stopCalls)
        assertEquals(2, native.startCalls)
    }

    @Test
    fun `reconnect without established tun fails closed`() {
        val platform = FakePlatform()
        val native = FakeNative()
        val engine = AndroidXrayEngine(platform, native, clock = { 1_000L })

        val result = engine.reconnect(ConnectionRequest(profile(expiresAt = 5_000L)))

        assertTrue(result.isFailure)
        assertEquals("vpn.reconnect_without_tunnel", engine.currentState().errorCode)
        assertTrue(platform.events.isEmpty())
        assertEquals(0, native.startCalls)
    }

    @Test
    fun `expired profile fails closed before tun creation`() {
        val platform = FakePlatform()
        val native = FakeNative()
        val engine = AndroidXrayEngine(platform, native, clock = { 10_000L })

        val result = engine.connect(ConnectionRequest(profile(expiresAt = 9_000L)))

        assertTrue(result.isFailure)
        assertEquals("vpn.profile_expired", engine.currentState().errorCode)
        assertTrue(platform.events.isEmpty())
        assertTrue(native.events.isEmpty())
    }

    @Test
    fun `native start failure closes tun and leaves deterministic error`() {
        val platform = FakePlatform()
        val native = FakeNative(startResult = NativeCallResult(false, "xray.native_rejected"))
        val engine = AndroidXrayEngine(platform, native, clock = { 1_000L })

        val result = engine.connect(ConnectionRequest(profile(expiresAt = 5_000L)))

        assertTrue(result.isFailure)
        assertTrue(platform.tunnelClosed)
        assertEquals(ConnectionPhase.ERROR, engine.currentState().phase)
        assertEquals("xray.native_rejected", engine.currentState().errorCode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `manual configuration shaped endpoint is rejected`() {
        profile(endpoint = "vless://manual-config.example")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `vless credential that is not a server issued UUID is rejected`() {
        ProvisionedProfile(
            profileId = "10000000-0000-4000-8000-000000000001",
            serviceId = "20000000-0000-4000-8000-000000000001",
            serverId = "30000000-0000-4000-8000-000000000001",
            endpoint = "vpn.example.com",
            port = 443,
            protocol = VpnProtocol.VLESS,
            credential = "user-entered-secret".encodeToByteArray(),
            security = ProvisionedSecurity.Tls("vpn.example.com"),
            expiresAtEpochMillis = System.currentTimeMillis() + 60_000,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `server cannot disable TLS certificate validation`() {
        profile(security = ProvisionedSecurity.Tls("vpn.example.com", allowInsecure = true))
    }

    private fun profile(
        endpoint: String = "vpn.example.com",
        security: ProvisionedSecurity = ProvisionedSecurity.Tls("vpn.example.com"),
        transport: ProvisionedTransport = ProvisionedTransport.Tcp,
        flow: String? = "xtls-rprx-vision",
        expiresAt: Long = System.currentTimeMillis() + 60_000,
    ) = ProvisionedProfile(
        profileId = "10000000-0000-4000-8000-000000000001",
        serviceId = "20000000-0000-4000-8000-000000000001",
        serverId = "30000000-0000-4000-8000-000000000001",
        endpoint = endpoint,
        port = 443,
        protocol = VpnProtocol.VLESS,
        credential = "40000000-0000-4000-8000-000000000001".encodeToByteArray(),
        transport = transport,
        security = security,
        flow = flow,
        expiresAtEpochMillis = expiresAt,
    )

    private class FakePlatform : TunnelPlatform {
        val events = mutableListOf<String>()
        var tunnelClosed = false

        override fun establish(mtu: Int): Result<TunnelDevice> {
            events += "establish"
            return Result.success(object : TunnelDevice {
                override val fileDescriptor = 77
                override fun close() { tunnelClosed = true }
            })
        }

        override fun protect(fileDescriptor: Int): Boolean {
            events += "protect"
            return fileDescriptor == 77
        }
    }

    private class FakeNative(
        private val startResult: NativeCallResult = NativeCallResult(true),
    ) : XrayNativeBridge {
        val events = mutableListOf<String>()
        var observedFd: Int? = null
        var startCalls = 0
        var stopCalls = 0

        override fun installSocketProtector(protector: SocketProtector): NativeCallResult {
            observedFd = 77
            protector.protect(77)
            return NativeCallResult(true)
        }

        override fun start(config: SensitiveXrayConfig): NativeCallResult {
            events += "start"
            startCalls += 1
            config.consume()
            return startResult
        }

        override fun stop(): NativeCallResult {
            stopCalls += 1
            return NativeCallResult(true)
        }
    }
}
