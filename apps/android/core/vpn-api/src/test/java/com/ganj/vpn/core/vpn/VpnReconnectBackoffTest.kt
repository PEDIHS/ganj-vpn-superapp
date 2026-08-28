package com.ganj.vpn.core.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnReconnectBackoffTest {
    @Test
    fun doublesUntilMaximumAndThenStaysBounded() {
        val policy = VpnReconnectBackoff(initialDelayMillis = 1_000, maximumDelayMillis = 8_000)

        assertEquals(1_000, policy.delayMillis(0))
        assertEquals(2_000, policy.delayMillis(1))
        assertEquals(4_000, policy.delayMillis(2))
        assertEquals(8_000, policy.delayMillis(3))
        assertEquals(8_000, policy.delayMillis(30))
        assertEquals(8_000, policy.delayMillis(Int.MAX_VALUE))
    }

    @Test
    fun rejectsNegativeAttemptAndInvalidConfiguration() {
        assertTrue(runCatching { VpnReconnectBackoff(0, 1_000) }.isFailure)
        assertTrue(runCatching { VpnReconnectBackoff(2_000, 1_000) }.isFailure)
        assertTrue(runCatching { VpnReconnectBackoff(1_000, 300_001) }.isFailure)
        assertTrue(runCatching { VpnReconnectBackoff().delayMillis(-1) }.isFailure)
    }
}
