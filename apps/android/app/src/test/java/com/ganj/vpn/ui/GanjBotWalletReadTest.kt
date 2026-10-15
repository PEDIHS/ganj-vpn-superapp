package com.ganj.vpn.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjBotWalletReadTest {
    @Test fun amountIsReadFromServerPayloadNotClientPlaceholder() {
        assertEquals(100_000L, botReadVerifiedBalance(100_000.0))
        assertEquals(0L, botReadVerifiedBalance(0.0))
    }

    @Test fun missingOrMalformedWalletResponseDoesNotBecomeZero() {
        assertThrows(IllegalArgumentException::class.java) { botReadVerifiedBalance(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { botReadVerifiedBalance(Double.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) {
            botReadVerifiedBalance(1000.5)
        }
    }

    @Test fun boundedGuestCatalogSnapshotKeepsOnlyPreviouslyFetchedRows() {
        val plan = GanjBotProduct("1", "de", "Germany", "retail", "Test", "", 100_000, 10.0, 30, "", false, false)
        GanjPublicCatalogMemory.update(listOf(plan), now = 1_000L)
        assertEquals(listOf(plan), GanjPublicCatalogMemory.get())
        assertTrue(GanjPublicCatalogMemory.fresh(now = 45_999L))
        assertFalse(GanjPublicCatalogMemory.fresh(now = 46_000L))
    }
}
