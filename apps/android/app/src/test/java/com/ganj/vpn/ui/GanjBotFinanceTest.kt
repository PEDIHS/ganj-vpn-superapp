package com.ganj.vpn.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjBotFinanceTest {
    @Test fun onlyOfficialGanjHttpsSiteIsAllowedInsidePaymentWebView() {
        assertTrue(botFinanceIsTrusted("https://bot.pedramhs.ir/site/?page=wallet"))
        assertTrue(botFinanceIsTrusted("https://bot.pedramhs.ir/site/?page=product&id=abc&panel=123"))
        assertFalse(botFinanceIsTrusted("http://bot.pedramhs.ir/site/?page=wallet"))
        assertFalse(botFinanceIsTrusted("https://bot.pedramhs.ir.evil.test/site/"))
        assertFalse(botFinanceIsTrusted("https://bot.pedramhs.ir@evil.test/site/"))
        assertFalse(botFinanceIsTrusted("https://bot.pedramhs.ir/admin.php"))
    }

    @Test fun sourceOfTruthPaymentStatesAreHumanReadable() {
        assertTrue(botFinanceStatusText("waiting").contains("بررسی"))
        assertTrue(botFinanceStatusText("processing_smart").contains("هوشمند"))
        assertTrue(botFinanceStatusText("manual_review").contains("مدیر"))
        assertTrue(botFinanceStatusText("paid").contains("تأیید"))
        assertTrue(botFinanceStatusText("rejected").contains("تأیید نشد"))
    }

    @Test fun walletAndTransactionsAreHostedBySameBot() {
        assertEquals("https://bot.pedramhs.ir/site/?page=wallet", GANJ_WEB_WALLET)
        assertEquals("https://bot.pedramhs.ir/site/?page=transactions", GANJ_WEB_TRANSACTIONS)
    }
}
