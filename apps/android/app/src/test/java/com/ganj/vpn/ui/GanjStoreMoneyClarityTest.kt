package com.ganj.vpn.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjStoreMoneyClarityTest {
    @Test fun amountsShowHumanReadablePersianWithoutDroppingZeros() {
        assertEquals("دویست هزار تومان", botTomanInWords(200_000))
        assertEquals("بیست هزار تومان", botTomanInWords(20_000))
        assertEquals("دو میلیون تومان", botTomanInWords(2_000_000))
        assertEquals("یک میلیون و دویست و پنجاه هزار تومان", botTomanInWords(1_250_000))
        assertEquals("صفر تومان", botTomanInWords(0))
        assertEquals("مبلغ نامعتبر", botTomanInWords(-1))
    }

    @Test fun presetsRespectRealBotLimitsAndNeverInventAOneTomanPreset() {
        assertEquals(listOf(200_000L, 500_000L, 1_000_000L, 2_000_000L),
            botTopUpSuggestions(0, 0))
        assertEquals(listOf(250_000L, 500_000L, 1_000_000L, 2_000_000L),
            botTopUpSuggestions(250_000, 2_000_000))
        assertEquals(listOf(750_000L, 1_000_000L),
            botTopUpSuggestions(750_000, 1_000_000))
        assertTrue(botTopUpSuggestions(3_000_000, 2_000_000).isEmpty())
        assertFalse(botTopUpSuggestions(200_000, 500_000).contains(2_000_000))
    }
}
