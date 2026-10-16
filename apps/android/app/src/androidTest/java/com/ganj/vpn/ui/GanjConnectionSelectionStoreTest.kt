package com.ganj.vpn.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ganj.vpn.core.controlapi.ConnectionServer
import com.ganj.vpn.core.controlapi.SubscriptionTier
import com.ganj.vpn.core.controlapi.VpnProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GanjConnectionSelectionStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val a = "b3ea72d9-87fd-4c90-8cd8-d3ce73e27988"
    private val b = "a59f6f2b-993b-4e31-94a1-a35fa2c0050e"
    private val service = "1ce87519-e8df-45bf-9485-0cae73eff82a"
    private val server = ConnectionServer(
        id = "5ae62983-6b42-4735-b584-405c32c6e35a",
        code = "de-1",
        name = "Germany",
        countryCode = "DE",
        city = "Frankfurt",
        tier = SubscriptionTier.PREMIUM,
        protocols = setOf(VpnProtocol.VLESS),
    )

    @Test fun selectedSubscriptionAndServerSurviveStoreRecreationForSameAccountOnly() {
        val store = GanjConnectionSelectionStore(context)
        store.forgetAccount(a)
        store.forgetAccount(b)
        try {
            store.rememberService(a, service)
            store.rememberServer(a, service, server.id)
            store.rememberCatalog(a, service, listOf(server))
            val reopened = GanjConnectionSelectionStore(context)
            assertEquals(service, reopened.preferredService(a))
            assertEquals(server.id, reopened.preferredServer(a, service))
            assertEquals(listOf(server), reopened.cachedCatalog(a, service))
            assertNull(reopened.preferredService(b))
            assertNull(reopened.preferredServer(b, service))
            assertTrue(reopened.cachedCatalog(b, service).isEmpty())
        } finally {
            store.forgetAccount(a)
            store.forgetAccount(b)
        }
    }

    @Test fun authoritativeEmptyServerListRemovesStaleDisplayEntries() {
        val store = GanjConnectionSelectionStore(context)
        store.forgetAccount(a)
        try {
            store.rememberCatalog(a, service, listOf(server))
            assertEquals(1, store.cachedCatalog(a, service).size)
            store.rememberCatalog(a, service, emptyList())
            assertTrue(store.cachedCatalog(a, service).isEmpty())
        } finally {
            store.forgetAccount(a)
        }
    }
}
