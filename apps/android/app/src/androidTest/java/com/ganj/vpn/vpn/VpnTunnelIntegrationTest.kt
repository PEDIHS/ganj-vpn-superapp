package com.ganj.vpn.vpn

import android.content.Intent
import android.net.VpnService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import com.ganj.vpn.MainActivity
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.ganj.vpn.core.vpn.ConnectionRequest
import com.ganj.vpn.core.vpn.ProvisionedProfile
import com.ganj.vpn.core.vpn.VpnProtocol
import com.ganj.vpn.core.xray.ReflectiveLibXrayBridge
import com.ganj.vpn.core.xray.XrayConfigCompiler
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VpnTunnelIntegrationTest {
    @Test
    fun consentTunnelTrafficDisconnectAndReconnect() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val activity = ActivityScenario.launch(MainActivity::class.java)
        try {
        // Exercise the real OS denial followed by approval in this isolated emulator.
        val permission = VpnService.prepare(context)
        assertNotNull("fresh emulator must require VPN consent", permission)
        activity.onActivity { it.startActivityForResult(permission!!, 47159) }
        assertTrue(device.wait(Until.hasObject(By.res("android", "button2")), 10_000))
        device.findObject(By.res("android", "button2")).click()
        assertTrue("denied dialog must close before opening another", device.wait(Until.gone(By.pkg("com.android.vpndialogs")), 10_000))
        assertNotNull(VpnService.prepare(context))
        activity.onActivity { it.startActivityForResult(VpnService.prepare(it)!!, 47159) }
        assertTrue(device.wait(Until.hasObject(By.res("android", "button1")), 10_000))
        device.findObject(By.res("android", "button1")).click()
        assertTrue("approved dialog must close", device.wait(Until.gone(By.pkg("com.android.vpndialogs")), 10_000))
        withTimeout(10_000) { while (VpnService.prepare(context) != null) delay(100) }
        assertNull(VpnService.prepare(context))
        println("VPN test: OS denial and approval passed")

        val client = AndroidVpnTunnelClient(context)
        repeat(2) {
            try {
                val started = client.connect(ConnectionRequest(fixtureProfile()))
                assertTrue(started.exceptionOrNull()?.message ?: "TUN start failed", started.isSuccess)
                println("VPN test: native TUN started")
                val latency = fixtureProfile().use { profile ->
                    XrayConfigCompiler().compileProbe(profile).use { config ->
                        ReflectiveLibXrayBridge(context.classLoader).probe(
                            config, context.noBackupFilesDir, "http://198.18.0.1:18080/ganj-tun-check",
                        )
                    }
                }
                assertNotNull("real proxy latency must be measured without stopping VPN", latency)
                println("VPN test: native proxy latency passed")
                assertTrue("switching config must retain a working TUN", client.connect(ConnectionRequest(fixtureProfile())).isSuccess)
                Socket().use { socket ->
                    socket.soTimeout = 8_000
                    // Benchmark-only destination has no listening server in Android or the host.
                    // The marker can arrive only through TUN -> Xray -> loopback VLESS fixture.
                    socket.connect(InetSocketAddress("198.18.0.1", 18080), 8_000)
                    socket.getOutputStream().write("GET /ganj-tun-check HTTP/1.1\r\nHost: test.invalid\r\nConnection: close\r\n\r\n".toByteArray())
                    val response = socket.getInputStream().bufferedReader().readText()
                    assertTrue(response.contains("ganj-tun-vless-roundtrip-ok"))
                    println("VPN test: TUN traffic and config switch passed")
                }
            } finally {
                assertTrue("disconnect must finish successfully", client.disconnect().isSuccess)
                println("VPN test: disconnect passed")
            }
        }
        } catch (failure: Throwable) {
            device.takeScreenshot(java.io.File(context.cacheDir, "vpn-failure.png"))
            device.dumpWindowHierarchy(java.io.File(context.cacheDir, "vpn-failure.xml"))
            throw failure
        } finally { activity.close() }
    }

    private fun fixtureProfile() = ProvisionedProfile(
        profileId = "10000000-0000-4000-8000-000000000001",
        serviceId = "20000000-0000-4000-8000-000000000001",
        serverId = "30000000-0000-4000-8000-000000000001",
        endpoint = "10.0.2.2",
        port = 18081,
        protocol = VpnProtocol.VLESS,
        credential = "40000000-0000-4000-8000-000000000001".encodeToByteArray(),
        expiresAtEpochMillis = System.currentTimeMillis() + 120_000,
    )
}
