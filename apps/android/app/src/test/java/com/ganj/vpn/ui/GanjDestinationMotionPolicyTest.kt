package com.ganj.vpn.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjDestinationMotionPolicyTest {
    @Test
    fun `full and balanced tiers animate when motion is allowed`() {
        assertTrue(GanjDestinationMotionPolicy.shouldAnimate(GanjEffectsTier.Full, reduceMotion = false))
        assertTrue(GanjDestinationMotionPolicy.shouldAnimate(GanjEffectsTier.Balanced, reduceMotion = false))
        assertEquals(220, GanjDestinationMotionPolicy.durationMillis(GanjEffectsTier.Full))
        assertEquals(150, GanjDestinationMotionPolicy.durationMillis(GanjEffectsTier.Balanced))
    }

    @Test
    fun `reduced tier and reduced motion disable transitions`() {
        assertFalse(GanjDestinationMotionPolicy.shouldAnimate(GanjEffectsTier.Reduced, reduceMotion = false))
        assertFalse(GanjDestinationMotionPolicy.shouldAnimate(GanjEffectsTier.Full, reduceMotion = true))
        assertEquals(0, GanjDestinationMotionPolicy.durationMillis(GanjEffectsTier.Reduced))
    }
}
