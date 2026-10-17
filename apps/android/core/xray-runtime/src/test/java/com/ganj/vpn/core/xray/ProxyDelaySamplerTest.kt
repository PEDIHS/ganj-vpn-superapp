package com.ganj.vpn.core.xray

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyDelaySamplerTest {
    @Test fun `one native invocation returns fast real delay without repeated cold handshakes`() {
        var count = 0
        val result = ProxyDelaySampler.measure(1, listOf("gstatic")) { _, indexes ->
            count++
            indexes.map { NativeProbeResult.Measured(87L) }
        }
        assertEquals(listOf(NativeProbeResult.Measured(87L)), result)
        assertEquals(1, count)
    }

    @Test fun `primary failure falls back to same profile once`() {
        val requests = mutableListOf<Pair<String, List<Int>>>()
        val result = ProxyDelaySampler.measure(1, listOf("gstatic", "google")) { url, indexes ->
            requests += url to indexes
            indexes.map { if (url == "gstatic") NativeProbeResult.Failed("probe.timeout") else NativeProbeResult.Measured(71L) }
        }
        assertEquals(NativeProbeResult.Measured(71L), result.single())
        assertEquals(listOf("gstatic" to listOf(0), "google" to listOf(0)), requests)
    }

    @Test fun `unreachable proxy cannot become a fabricated low latency`() {
        val result = ProxyDelaySampler.measure(2, listOf("gstatic", "google")) { _, indexes ->
            indexes.map { NativeProbeResult.Failed("probe.timeout") }
        }
        assertTrue(result.all { it is NativeProbeResult.Failed })
    }

    @Test fun `a successful server is not retried if another server fails`() {
        val requests = mutableListOf<Pair<String, List<Int>>>()
        val result = ProxyDelaySampler.measure(3, listOf("gstatic", "google")) { url, indexes ->
            requests += url to indexes
            indexes.map { index ->
                when (index) {
                    0 -> NativeProbeResult.Measured(68L)
                    1 -> if (url == "gstatic") NativeProbeResult.Failed("probe.timeout") else NativeProbeResult.Measured(89L)
                    else -> NativeProbeResult.Failed("xray.config_invalid")
                }
            }
        }
        assertEquals(NativeProbeResult.Measured(68L), result[0])
        assertEquals(NativeProbeResult.Measured(89L), result[1])
        assertEquals(NativeProbeResult.Failed("xray.config_invalid"), result[2])
        assertEquals(listOf("gstatic" to listOf(0, 1, 2), "google" to listOf(1)), requests)
    }

    @Test fun `native throw is a per-config failure with fallback`() {
        var count = 0
        val result = ProxyDelaySampler.measure(1, listOf("gstatic", "google")) { _, _ ->
            count++
            if (count == 1) throw IllegalStateException("temporary failure")
            listOf(NativeProbeResult.Measured(111L))
        }
        assertEquals(NativeProbeResult.Measured(111L), result.single())
    }
}
