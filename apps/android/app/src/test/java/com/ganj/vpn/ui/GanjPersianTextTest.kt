package com.ganj.vpn.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjPersianTextTest {
    @Test
    fun `user facing numbers use Persian digits`() {
        assertEquals("۱۲۳۴۵۶۷۸۹۰", "1234567890".toPersianDigits())
        assertEquals("۲۴ روز", "24 روز".toPersianDigits())
        assertEquals("۳", 3.toPersianDigits())
    }

    @Test
    fun `technical values are directionally isolated`() {
        val isolated = isolateTechnicalLtr("185.25.10.7")
        assertTrue(isolated.startsWith("\u2066"))
        assertTrue(isolated.endsWith("\u2069"))
        assertEquals("\u2066185.25.10.7\u2069", isolated)
    }

    @Test
    fun `blank technical values remain blank`() {
        assertEquals("", isolateTechnicalLtr(""))
        assertEquals("   ", isolateTechnicalLtr("   "))
    }

    @Test
    fun `metric keeps latin unit inside a single ltr isolate`() {
        assertEquals("\u206624 ms\u2069", persianTechnicalMetric("24", "ms"))
        assertEquals("\u2066256 Mbps\u2069", persianTechnicalMetric("256", "Mbps"))
    }
}
