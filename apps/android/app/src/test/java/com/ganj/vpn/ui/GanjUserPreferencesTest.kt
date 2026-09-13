package com.ganj.vpn.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjUserPreferencesTest {
    @Test
    fun `system theme follows system value`() {
        assertTrue(
            GanjUserPreferencesPolicy.resolveDarkTheme(
                GanjThemePreference.SYSTEM,
                systemDark = true,
            ),
        )
        assertFalse(
            GanjUserPreferencesPolicy.resolveDarkTheme(
                GanjThemePreference.SYSTEM,
                systemDark = false,
            ),
        )
    }

    @Test
    fun `explicit theme overrides system theme`() {
        assertFalse(
            GanjUserPreferencesPolicy.resolveDarkTheme(
                GanjThemePreference.LIGHT,
                systemDark = true,
            ),
        )
        assertTrue(
            GanjUserPreferencesPolicy.resolveDarkTheme(
                GanjThemePreference.DARK,
                systemDark = false,
            ),
        )
    }

    @Test
    fun `user accessibility settings can only add reductions`() {
        val systemReduced = GanjVisualEffectsPolicy(
            tier = GanjEffectsTier.Reduced,
            reduceTransparency = true,
            reduceMotion = true,
            ambientBackgroundEffects = false,
        )
        val result = GanjUserPreferencesPolicy.applyVisualOverrides(
            base = systemReduced,
            preferences = GanjUserPreferences(
                reduceMotion = false,
                reduceTransparency = false,
            ),
        )

        assertTrue(result.reduceMotion)
        assertTrue(result.reduceTransparency)
        assertFalse(result.ambientBackgroundEffects)
    }

    @Test
    fun `user reduce transparency disables ambient effects`() {
        val full = GanjVisualEffectsPolicy(
            tier = GanjEffectsTier.Full,
            reduceTransparency = false,
            reduceMotion = false,
            ambientBackgroundEffects = true,
        )
        val result = GanjUserPreferencesPolicy.applyVisualOverrides(
            base = full,
            preferences = GanjUserPreferences(reduceTransparency = true),
        )

        assertTrue(result.reduceTransparency)
        assertFalse(result.ambientBackgroundEffects)
    }
}
