package com.ganj.vpn.ui

import com.ganj.vpn.presentation.LatencyKey
import com.ganj.vpn.presentation.LatencyReading
import com.ganj.vpn.presentation.LatencySnapshot
import com.ganj.vpn.presentation.LatencyProbeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjLatencyDisplayTest {
    @Test fun unrelatedPingDoesNotChangeVisibleServerProjection() {
        val one = LatencyKey("service-a", "server-1")
        val two = LatencyKey("service-a", "server-2")
        val unrelated = LatencyKey("service-b", "server-3")
        val first = LatencyReading(LatencyProbeResult.Measured(45), 100)
        val initial = LatencySnapshot(readings = mapOf(one to first))
        val othersChanged = LatencySnapshot(
            readings = mapOf(one to first, two to LatencyReading(LatencyProbeResult.Measured(71), 101)),
            measuring = setOf(two, unrelated),
        )
        assertEquals(initial.probeVisual(one), othersChanged.probeVisual(one))
        assertFalse(initial.serviceProbing("service-a"))
        assertTrue(othersChanged.serviceProbing("service-a"))
        assertTrue(othersChanged.serviceProbing("service-b"))
        assertFalse(othersChanged.probeVisual(one).measuring)
        assertTrue(othersChanged.probeVisual(two).measuring)
    }

    @Test fun rowReflectsOnlyItsActualProbeTransitions() {
        val key = LatencyKey("a", "b")
        val before = LatencySnapshot().probeVisual(key)
        val measuring = LatencySnapshot(measuring = setOf(key)).probeVisual(key)
        val reading = LatencyReading(LatencyProbeResult.Measured(18), 123)
        val done = LatencySnapshot(readings = mapOf(key to reading)).probeVisual(key)
        assertEquals(GanjProbeVisual(), before)
        assertTrue(measuring.measuring)
        assertEquals(reading, done.reading)
        assertFalse(done.measuring)
    }
}
