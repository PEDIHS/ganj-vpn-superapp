package com.ganj.vpn.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GanjConfigIdentityTest {
    @Test fun extractsLeadingFlagWithoutMutatingName() {
        val raw = "🇩🇪 | Germany - Berlin"
        assertEquals(GanjConfigIdentity("Germany - Berlin", "🇩🇪"), ganjConfigIdentity(raw, "US"))
        assertEquals("🇩🇪 | Germany - Berlin", raw)
    }

    @Test fun extractsFlagAppearingInTheMiddle() {
        assertEquals(GanjConfigIdentity("VIP • Germany Berlin", "🇩🇪"),
            ganjConfigIdentity("VIP • 🇩🇪 Germany Berlin", "TR"))
    }

    @Test fun fallsBackToCountryCodeForUnflaggedConfig() {
        assertEquals(GanjConfigIdentity("کانفیگ دوم", "🇩🇪"),
            ganjConfigIdentity("کانفیگ دوم", "de"))
        assertEquals("🇬🇧", ganjCountryFlag("uk"))
    }

    @Test fun preservesOtherEmojiAndSupportsUnknownCountry() {
        assertEquals(GanjConfigIdentity("⚡ کانفیگ ویژه", "🌐"),
            ganjConfigIdentity("⚡ کانفیگ ویژه", "unknown"))
        assertEquals(GanjConfigIdentity("Japan • Tokyo", "🇯🇵"),
            ganjConfigIdentity("🇯🇵 Japan • Tokyo", null))
    }
}