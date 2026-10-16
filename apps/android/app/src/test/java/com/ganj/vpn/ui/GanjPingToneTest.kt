package com.ganj.vpn.ui

import com.ganj.vpn.presentation.ConnectionFailures
import com.ganj.vpn.presentation.LatencyProbeResult
import org.junit.Assert.assertEquals
import org.junit.Test

class GanjPingToneTest {
    @Test fun everySuccessfulLatencyIsGreenIncludingVeryHighValues() {
        listOf(0L, 20L, 80L, 180L, 500L, 2_000L, 60_000L).forEach { millis ->
            assertEquals("All numeric ping measurements must use green: $millis",
                GanjPingTone.Measured, ganjPingTone(LatencyProbeResult.Measured(millis)))
        }
    }

    @Test fun onlyTimeoutIsRedAndOtherFailuresRemainNeutral() {
        assertEquals(GanjPingTone.Timeout,
            ganjPingTone(LatencyProbeResult.Failed(ConnectionFailures.probe("probe.timeout"))))
        listOf("probe.dns_failed", "probe.connection_refused", "probe.network_unreachable",
            "probe.native_failed", "probe.unavailable").forEach { code ->
            assertEquals(GanjPingTone.Unknown,
                ganjPingTone(LatencyProbeResult.Failed(ConnectionFailures.probe(code))))
        }
        assertEquals(GanjPingTone.Unknown, ganjPingTone(null))
    }
}
