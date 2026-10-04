package com.ganj.vpn.presentation

import com.ganj.vpn.core.vpn.ConnectionPhase
import com.ganj.vpn.core.vpn.ConnectionState
import org.junit.Assert.*
import org.junit.Test

class ConnectionSessionTimerTest {
    private var wall = 10_000_000L
    private var monotonic = 50_000L
    private val timer = ConnectionSessionTimer({ wall }, { monotonic })
    private val connected = ConnectionState(ConnectionPhase.CONNECTED, connectedAtEpochMillis = 6_275_000L)

    @Test fun durationUsesNativeSuccessTimeAndMonotonicClockAcrossWallClockEdits() {
        assertEquals("01:02:05", formatConnectionDuration(timer.elapsedMillis(connected)!!))
        monotonic += 1_000
        wall -= 8_000_000
        assertEquals(3_726_000L, timer.elapsedMillis(connected))
        wall += 20_000_000
        monotonic += 2_000
        assertEquals(3_728_000L, timer.elapsedMillis(connected))
    }

    @Test fun tabAbsenceConfigSelectionAndTunnelRecoveryKeepTheSessionClock() {
        assertEquals(3_725_000L, timer.elapsedMillis(connected))
        // No reads while the connection destination is unmounted.
        monotonic += 45_000
        assertEquals(3_770_000L, timer.elapsedMillis(connected.copy(
            phase = ConnectionPhase.RECONNECTING, serverId = "replacement", profileId = "replacement")))
        monotonic += 5_000
        assertEquals(3_775_000L, timer.elapsedMillis(connected))
    }

    @Test fun newTunnelResetsToItsOwnNativeTimestampAndDisconnectedShowsNoDuration() {
        timer.elapsedMillis(connected)
        assertNull(timer.elapsedMillis(connected.copy(phase = ConnectionPhase.DISCONNECTED)))
        wall += 60_000
        monotonic += 60_000
        assertEquals(2_000L, timer.elapsedMillis(connected.copy(connectedAtEpochMillis = wall - 2_000)))
    }

    @Test fun missingInvalidOrNotYetConnectedTimestampNeverFabricatesZero() {
        assertNull(timer.elapsedMillis(connected.copy(connectedAtEpochMillis = null)))
        assertNull(timer.elapsedMillis(connected.copy(connectedAtEpochMillis = 0)))
        assertNull(timer.elapsedMillis(connected.copy(connectedAtEpochMillis = wall + 1_000)))
        assertNull(timer.elapsedMillis(connected.copy(phase = ConnectionPhase.CONNECTING)))
        assertNull(timer.elapsedMillis(connected.copy(phase = ConnectionPhase.PREPARING)))
        assertEquals(0L, timer.elapsedMillis(connected.copy(connectedAtEpochMillis = wall)))
    }
}
