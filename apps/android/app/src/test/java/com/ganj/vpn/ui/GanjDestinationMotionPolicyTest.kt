package com.ganj.vpn.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjDestinationMotionPolicyTest {
    @Test fun `low ram and battery modes retain essential interaction feedback`() {
        GanjEffectsTier.entries.forEach {
            assertTrue(GanjDestinationMotionPolicy.shouldAnimate(it, reduceMotion = false))
        }
        assertEquals(280, GanjDestinationMotionPolicy.durationMillis(GanjEffectsTier.Full))
        assertEquals(200, GanjDestinationMotionPolicy.durationMillis(GanjEffectsTier.Balanced))
        assertEquals(160, GanjDestinationMotionPolicy.durationMillis(GanjEffectsTier.Reduced))
    }

    @Test fun `explicit reduced motion disables every tier`() {
        GanjEffectsTier.entries.forEach {
            assertFalse(GanjDestinationMotionPolicy.shouldAnimate(it, reduceMotion = true))
        }
    }

    @Test fun `device memory classification never substitutes for an accessibility choice`() {
        val lowRam = GanjVisualEffectsResolver.resolve(true, false, true)
        assertTrue(lowRam.reduceTransparency)
        assertFalse(lowRam.reduceMotion)
        assertTrue(GanjDestinationMotionPolicy.shouldAnimate(lowRam.tier, lowRam.reduceMotion))
        val systemOff = GanjVisualEffectsResolver.resolve(true, false, false)
        assertFalse(GanjDestinationMotionPolicy.shouldAnimate(systemOff.tier, systemOff.reduceMotion))
    }
}
