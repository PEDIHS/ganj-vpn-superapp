package com.ganj.vpn.core.xray

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyDelaySamplerTest {
    @Test fun `median of successful real outbound requests ignores one slow sample`() {
        val values = listOf(54L, 800L, 58L).iterator()
        val calls = mutableListOf<List<Int>>()
        val result = ProxyDelaySampler.measure(1, listOf("https://one.test")) { _, indices ->
            calls += indices
            listOf(NativeProbeResult.Measured(values.next()))
        }
        assertEquals(listOf(NativeProbeResult.Measured(58L)), result)
        assertEquals(3, calls.size)
    }

    @Test fun `blocked primary checks second target using identical config`() {
        val targets = mutableListOf<String>()
        val result = ProxyDelaySampler.measure(1, listOf("primary", "fallback")) { target, _ ->
            targets += target
            listOf(if (target == "primary") NativeProbeResult.Failed("probe.timeout")
                else NativeProbeResult.Measured(70L))
        }
        assertEquals(NativeProbeResult.Measured(70L), result.single())
        assertEquals(listOf("primary", "fallback", "fallback", "fallback"), targets)
    }

    @Test fun `all network failures stay failures never zero or optimistic TCP`() {
        val result = ProxyDelaySampler.measure(2, listOf("primary", "fallback")) { _, indexes ->
            indexes.map { NativeProbeResult.Failed("probe.timeout") }
        }
        assertTrue(result.all { it is NativeProbeResult.Failed })
    }

    @Test fun `configuration failures do not retry or leak to other server`() {
        val selections = mutableListOf<List<Int>>()
        val result = ProxyDelaySampler.measure(2, listOf("primary", "fallback")) { _, indexes ->
            selections += indexes
            indexes.map { index ->
                if (index == 0) NativeProbeResult.Failed("xray.config_invalid")
                else NativeProbeResult.Measured(91L)
            }
        }
        assertEquals(NativeProbeResult.Failed("xray.config_invalid"), result[0])
        assertEquals(NativeProbeResult.Measured(91L), result[1])
        assertEquals(listOf(listOf(0, 1), listOf(1), listOf(1)), selections)
    }

    @Test fun `one successful request followed by failures is not reliable latency`() {
        var calls = 0
        val result = ProxyDelaySampler.measure(1, listOf("primary")) { _, _ ->
            calls++
            listOf(if (calls == 1) NativeProbeResult.Measured(40L)
                else NativeProbeResult.Failed("probe.timeout"))
        }
        assertTrue(result.single() is NativeProbeResult.Failed)
    }
}
