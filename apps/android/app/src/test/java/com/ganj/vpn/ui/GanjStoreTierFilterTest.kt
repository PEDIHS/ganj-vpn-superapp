package com.ganj.vpn.ui

import com.ganj.vpn.presentation.PlanUiModel
import com.ganj.vpn.presentation.UiTier
import org.junit.Assert.assertEquals
import org.junit.Test

class GanjStoreTierFilterTest {
    private fun plan(id: String, tier: UiTier) = PlanUiModel(
        id = id, code = id, title = id, tier = tier, durationDays = 30,
        trafficLimitBytes = 5_000_000_000L, deviceLimit = 1,
        benefits = emptyList(), amountMinor = 1000L, currency = "IRR",
    )

    @Test fun filtersPreserveBackendTiersAndOrder() {
        val plans = listOf(plan("basic", UiTier.FREE), plan("vip-1", UiTier.VIP),
            plan("plus", UiTier.PREMIUM), plan("vip-2", UiTier.VIP))
        assertEquals(plans, filterStorePlans(plans, StoreTierFilter.ALL))
        assertEquals(listOf("plus"), filterStorePlans(plans, StoreTierFilter.PREMIUM).map { it.id })
        assertEquals(listOf("vip-1", "vip-2"),
            filterStorePlans(plans, StoreTierFilter.VIP).map { it.id })
    }

    @Test fun emptyCatalogHasNoSyntheticVipOffer() {
        StoreTierFilter.entries.forEach { tier ->
            assertEquals(emptyList<PlanUiModel>(), filterStorePlans(emptyList(), tier))
        }
    }

    @Test fun onlyRealVipPlansCanBeListed() {
        assertEquals(emptyList<PlanUiModel>(),
            filterStorePlans(listOf(plan("plus", UiTier.PREMIUM)), StoreTierFilter.VIP))
    }
}
