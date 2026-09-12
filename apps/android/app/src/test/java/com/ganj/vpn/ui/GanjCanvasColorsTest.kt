package com.ganj.vpn.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjCanvasColorsTest {
    @Test
    fun canvasIsOpaqueInBothThemesAndEffectModes() {
        for (background in listOf(GanjLightCanvas, GanjDarkCanvas)) {
            for (ambient in listOf(true, false)) {
                val colors = ganjCanvasColors(background, GanjEmerald, GanjGold, ambient)
                assertEquals(background, colors.last())
                colors.forEach { assertEquals(1f, it.alpha, 0.001f) }
            }
        }
    }

    @Test
    fun lightCanvasRetainsReadableLightThemeContrast() {
        val colors = ganjCanvasColors(GanjLightCanvas, Color(0xFF005133), GanjGold, true)
        for (background in colors) {
            val contrast = (background.luminance() + 0.05f) / (GanjLightText.luminance() + 0.05f)
            assertTrue("Light text colors must not sit on the dark splash window", contrast >= 4.5f)
        }
    }
}
