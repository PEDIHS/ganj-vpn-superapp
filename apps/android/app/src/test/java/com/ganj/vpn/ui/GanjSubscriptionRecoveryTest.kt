package com.ganj.vpn.ui

import com.ganj.vpn.presentation.ServiceUiModel
import com.ganj.vpn.presentation.ServiceUiStatus
import com.ganj.vpn.presentation.UiTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A process recreation must not require choosing the same subscription again. */
class GanjSubscriptionRecoveryTest {
    private fun service(id: String, active: Boolean = true) = ServiceUiModel(
        entitlementId = id,
        displayName = id,
        status = if (active) ServiceUiStatus.ACTIVE else ServiceUiStatus.EXPIRED,
        tier = UiTier.PREMIUM,
        countryCode = "DE",
        trafficLimitBytes = null,
        trafficUsedBytes = 0,
        expiresAt = null,
        deviceLimit = 1,
        allowedProtocols = setOf("VLESS"),
    )

    @Test fun savedChoiceWinsOverTransientFirstServiceOnColdLaunch() {
        val services = listOf(service("default"), service("user-choice"))
        assertEquals("user-choice", ganjRecoveredServiceId(services, "user-choice", "default"))
    }

    @Test fun formerlySelectedExpiredPlanCannotBeRestored() {
        val services = listOf(service("expired", false), service("active"))
        assertEquals("active", ganjRecoveredServiceId(services, "expired", "expired"))
    }

    @Test fun withoutSavedChoiceFirstActivePlanIsSelectedAutomatically() {
        assertEquals("second", ganjRecoveredServiceId(
            listOf(service("first", false), service("second")), null, null))
    }

    @Test fun noActivePlanKeepsConnectUnselected() {
        assertNull(ganjRecoveredServiceId(listOf(service("expired", false)), "expired", "expired"))
    }
}
