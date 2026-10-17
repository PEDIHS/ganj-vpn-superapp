package com.ganj.vpn.vpn

import com.ganj.vpn.core.xray.NativeProbeResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveTunnelDelaySamplerTest {
    @Test fun `lowest of two real GET delays is displayed`() {
        val samples = mutableListOf(700L, 100L)
        assertEquals(
            NativeProbeResult.Measured(100L),
            ActiveTunnelDelaySampler.measure(listOf("gstatic")) { NativeProbeResult.Measured(samples.removeAt(0)) },
        )
    }

    @Test fun `no successful request means no invented latency`() {
        val result = ActiveTunnelDelaySampler.measure(listOf("gstatic", "google")) {
            NativeProbeResult.Failed("probe.timeout")
        }
        assertEquals(NativeProbeResult.Failed("probe.timeout"), result)
    }

    @Test fun `alternate destination only after two real failures`() {
        val calls = mutableListOf<String>()
        val result = ActiveTunnelDelaySampler.measure(listOf("gstatic", "google")) { url ->
            calls += url
            if (url == "gstatic") NativeProbeResult.Failed("probe.http_failed")
            else NativeProbeResult.Measured(110L)
        }
        assertEquals(NativeProbeResult.Measured(110L), result)
        assertEquals(listOf("gstatic", "gstatic", "google", "google"), calls)
    }
}
