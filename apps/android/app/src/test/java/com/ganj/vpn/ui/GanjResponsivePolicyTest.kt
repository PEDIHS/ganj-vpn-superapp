package com.ganj.vpn.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjResponsivePolicyTest {
    @Test
    fun compactPhones_stackPrimaryActions() {
        assertEquals(GanjWidthClass.Compact, GanjResponsivePolicy.widthClass(320))
        assertTrue(GanjResponsivePolicy.shouldStackPrimaryActions(320, 1f))
        assertFalse(GanjResponsivePolicy.shouldShowAllNavigationLabels(320, 1f))
        assertEquals(1, GanjResponsivePolicy.categoryColumns(320, 1f))
    }

    @Test
    fun regularPhones_keepTwoColumnContentButCompactNavigationWhenNeeded() {
        assertEquals(GanjWidthClass.Regular, GanjResponsivePolicy.widthClass(390))
        assertFalse(GanjResponsivePolicy.shouldStackPrimaryActions(390, 1f))
        assertFalse(GanjResponsivePolicy.shouldShowAllNavigationLabels(390, 1f))
        assertEquals(2, GanjResponsivePolicy.categoryColumns(390, 1f))
        assertTrue(GanjResponsivePolicy.shouldShowAllNavigationLabels(411, 1f))
    }

    @Test
    fun largeFontScale_prioritizesReadableStackedLayout() {
        assertTrue(GanjResponsivePolicy.shouldStackPrimaryActions(430, 1.3f))
        assertFalse(GanjResponsivePolicy.shouldShowAllNavigationLabels(430, 1.3f))
        assertEquals(1, GanjResponsivePolicy.categoryColumns(430, 1.3f))
    }

    @Test
    fun wideLayout_usesMoreBreathingRoom() {
        assertEquals(GanjWidthClass.Wide, GanjResponsivePolicy.widthClass(720))
        assertEquals(28, GanjResponsivePolicy.horizontalPagePaddingDp(720))
    }

    @Test
    fun stitchReferenceWidths_keepExpectedPageGutters() {
        assertEquals(18, GanjResponsivePolicy.stitchHorizontalPaddingDp(360))
        assertEquals(20, GanjResponsivePolicy.stitchHorizontalPaddingDp(390))
        assertEquals(22, GanjResponsivePolicy.stitchHorizontalPaddingDp(412))
        assertEquals(22, GanjResponsivePolicy.stitchHorizontalPaddingDp(430))
    }

    @Test
    fun stitchConnectOrb_scalesAcrossReferencePhones() {
        assertEquals(180, GanjResponsivePolicy.stitchConnectOrbSizeDp(360))
        assertEquals(198, GanjResponsivePolicy.stitchConnectOrbSizeDp(390))
        assertEquals(212, GanjResponsivePolicy.stitchConnectOrbSizeDp(412))
        assertEquals(212, GanjResponsivePolicy.stitchConnectOrbSizeDp(430))
    }

    @Test
    fun stitchNavigationPadding_scalesWithoutChangingArchitecture() {
        assertEquals(8, GanjResponsivePolicy.stitchNavigationHorizontalPaddingDp(360))
        assertEquals(12, GanjResponsivePolicy.stitchNavigationHorizontalPaddingDp(390))
        assertEquals(14, GanjResponsivePolicy.stitchNavigationHorizontalPaddingDp(412))
        assertEquals(14, GanjResponsivePolicy.stitchNavigationHorizontalPaddingDp(430))
    }
}
