package com.ganj.vpn.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjConnectionMotionPolicyTest {
    @Test
    fun `full effects pulse active connection states`() {
        assertTrue(
            GanjConnectionMotionPolicy.shouldPulse(
                state = GanjConnectionVisualState.Connected,
                effectsTier = GanjEffectsTier.Full,
                reduceMotion = false,
            ),
        )
        assertTrue(
            GanjConnectionMotionPolicy.shouldPulse(
                state = GanjConnectionVisualState.Connecting,
                effectsTier = GanjEffectsTier.Full,
                reduceMotion = false,
            ),
        )
    }

    @Test
    fun `reduced motion and lower effect tiers never pulse`() {
        assertFalse(
            GanjConnectionMotionPolicy.shouldPulse(
                state = GanjConnectionVisualState.Connected,
                effectsTier = GanjEffectsTier.Full,
                reduceMotion = true,
            ),
        )
        assertFalse(
            GanjConnectionMotionPolicy.shouldPulse(
                state = GanjConnectionVisualState.Connected,
                effectsTier = GanjEffectsTier.Balanced,
                reduceMotion = false,
            ),
        )
    }

    @Test
    fun `idle and error states remain visually still`() {
        assertFalse(
            GanjConnectionMotionPolicy.shouldPulse(
                state = GanjConnectionVisualState.Disconnected,
                effectsTier = GanjEffectsTier.Full,
                reduceMotion = false,
            ),
        )
        assertFalse(
            GanjConnectionMotionPolicy.shouldPulse(
                state = GanjConnectionVisualState.Failed,
                effectsTier = GanjEffectsTier.Full,
                reduceMotion = false,
            ),
        )
        assertEquals(1f..1f, GanjConnectionMotionPolicy.pulseRange(GanjConnectionVisualState.Failed))
    }
}
