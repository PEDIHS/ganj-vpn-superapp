package com.ganj.vpn.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GanjBotReadPolicyTest {
    @Test fun financeAndServiceReadsCannotServeStaleTimedCache() {
        for (action in listOf(
            "app_snapshot", "wallet_methods", "transactions", "payment_status",
            "wallet_card_session", "wallet_crypto_session", "purchase_quote",
            "purchase", "services", "service", "profile"
        )) assertNull("Unexpected timed cache for $action", GanjBotReadPolicy.ttlMillis(action))
    }
    @Test fun immutableCatalogHasOnlyBoundedTtl() {
        assertEquals(45_000L, GanjBotReadPolicy.ttlMillis("products"))
        assertEquals(30_000L, GanjBotReadPolicy.ttlMillis("product"))
    }
    @Test fun localMutationEmitsInvalidationWithoutPolling() {
        val previous = GanjAccountChangeBus.generation.value
        GanjAccountChangeBus.changed()
        assertEquals(previous + 1L, GanjAccountChangeBus.generation.value)
    }
}
