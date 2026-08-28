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
    fun regularPhones_keepTwoColumnLayoutAtNormalFontScale() {
        assertEquals(GanjWidthClass.Regular, GanjResponsivePolicy.widthClass(390))
        assertFalse(GanjResponsivePolicy.shouldStackPrimaryActions(390, 1f))
        assertTrue(GanjResponsivePolicy.shouldShowAllNavigationLabels(390, 1f))
        assertEquals(2, GanjResponsivePolicy.categoryColumns(390, 1f))
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
}
