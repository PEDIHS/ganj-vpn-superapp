package com.ganj.vpn.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjLiquidGlassPolicyTest {
    @Test
    fun productionMaturity_isLevelThreeOrHigher() {
        assertTrue(GanjLiquidGlassPolicy.RequiredMaturityLevel >= 3)
    }

    @Test
    fun contentCards_areNotGlassByDefault() {
        assertFalse(GanjLiquidGlassPolicy.ContentCardsGlassByDefault)
    }

    @Test
    fun nestedGlass_isNotTheDefaultComposition() {
        assertFalse(GanjLiquidGlassPolicy.NestedGlassByDefault)
    }

    @Test
    fun accessibilityAndPerformanceFallbacks_areMandatory() {
        assertTrue(GanjLiquidGlassPolicy.ReduceTransparencyFallbackRequired)
        assertTrue(GanjLiquidGlassPolicy.PerformanceTieringRequired)
        assertTrue(GanjLiquidGlassPolicy.MinimumTouchTargetDp >= 48)
    }
}
