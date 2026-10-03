package com.ganj.vpn.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjContentContrastTest {
    @Test
    fun `selected subscription and config text remains AA in both themes`() {
        listOf(GanjLightContent, GanjDarkContent).forEach { palette ->
            listOf(palette.onSelected, palette.selectedMuted, palette.premiumText).forEach { text ->
                assertTrue("Selected-card text contrast must be at least 4.5:1", contrast(text, palette.selectedSurface) >= 4.5f)
            }
        }
    }

    @Test
    fun `premium labels remain readable on unselected cards`() {
        assertTrue(contrast(GanjLightContent.premiumText, GanjLightSurface) >= 4.5f)
        assertTrue(contrast(GanjDarkContent.premiumText, GanjDarkSurface) >= 4.5f)
    }

    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }
}
