package com.ganj.vpn.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GanjConfigIdentityTest {
    @Test fun extractsLeadingFlagWithoutMutatingName() {
        val raw = "🇩🇪 | Germany - Berlin"
        assertEquals(GanjConfigIdentity("Germany - Berlin", "DE"), ganjConfigIdentity(raw, "US"))
        assertEquals("🇩🇪 | Germany - Berlin", raw)
    }

    @Test fun extractsFlagAppearingInTheMiddle() {
        assertEquals(GanjConfigIdentity("VIP • Germany Berlin", "DE"),
            ganjConfigIdentity("VIP • 🇩🇪 Germany Berlin", "TR"))
    }

    @Test fun fallsBackToCountryCodeForUnflaggedConfig() {
        assertEquals(GanjConfigIdentity("کانفیگ دوم", "DE"),
            ganjConfigIdentity("کانفیگ دوم", "de"))
        assertEquals("GB", ganjNormalizedCountry("uk"))
    }

    @Test fun preservesOtherSymbolsAndSupportsUnknownCountry() {
        assertEquals(GanjConfigIdentity("⚡ کانفیگ ویژه", null),
            ganjConfigIdentity("⚡ کانفیگ ویژه", "unknown"))
        assertEquals(GanjConfigIdentity("Japan • Tokyo", "JP"),
            ganjConfigIdentity("🇯🇵 Japan • Tokyo", null))
    }

    @Test fun unsupportedCountryReturnsGlobalImage() {
        assertEquals(null, ganjNormalizedCountry(null))
        assertEquals(null, ganjNormalizedCountry("Worldwide"))
        assertEquals("GB", ganjNormalizedCountry("UK"))
    }
}
