package com.ganj.vpn.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjVisualEffectsResolverTest {
    @Test
    fun fullTier_keepsAmbientEffectsOnCapableDevice() {
        val policy = GanjVisualEffectsResolver.resolve(
            isLowRamDevice = false,
            powerSaveMode = false,
            systemAnimationsEnabled = true,
        )

        assertEquals(GanjEffectsTier.Full, policy.tier)
        assertFalse(policy.reduceTransparency)
        assertFalse(policy.reduceMotion)
        assertTrue(policy.ambientBackgroundEffects)
    }

    @Test
    fun lowRamDevice_forcesReadableOpaqueFallback() {
        val policy = GanjVisualEffectsResolver.resolve(
            isLowRamDevice = true,
            powerSaveMode = false,
            systemAnimationsEnabled = true,
        )

        assertEquals(GanjEffectsTier.Reduced, policy.tier)
        assertTrue(policy.reduceTransparency)
        assertFalse(policy.ambientBackgroundEffects)
    }

    @Test
    fun batterySaver_usesBalancedTierWithoutForcingOpaqueUi() {
        val policy = GanjVisualEffectsResolver.resolve(
            isLowRamDevice = false,
            powerSaveMode = true,
            systemAnimationsEnabled = true,
        )

        assertEquals(GanjEffectsTier.Balanced, policy.tier)
        assertFalse(policy.reduceTransparency)
        assertFalse(policy.ambientBackgroundEffects)
    }

    @Test
    fun disabledSystemAnimations_reduceMotionIndependently() {
        val policy = GanjVisualEffectsResolver.resolve(
            isLowRamDevice = false,
            powerSaveMode = false,
            systemAnimationsEnabled = false,
        )

        assertTrue(policy.reduceMotion)
        assertEquals(GanjEffectsTier.Full, policy.tier)
    }
}
