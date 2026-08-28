package com.ganj.vpn.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiAccessibilityPolicyTest {
    @Test
    fun touchTarget_meetsAndroidAccessibilityMinimum() {
        assertTrue(UiAccessibilityPolicy.MinimumTouchTargetDp >= 48)
    }

    @Test
    fun destinationDescription_announcesSelectionUsingLocalizedSuffix() {
        val idle = UiAccessibilityPolicy.destinationDescription(
            label = "خانه",
            selected = false,
            selectedSuffix = "انتخاب‌شده",
        )
        val selected = UiAccessibilityPolicy.destinationDescription(
            label = "خانه",
            selected = true,
            selectedSuffix = "انتخاب‌شده",
        )

        assertEquals("خانه", idle)
        assertTrue(selected.startsWith("خانه"))
        assertTrue(selected.contains("انتخاب‌شده"))
        assertNotEquals(idle, selected)
    }

    @Test
    fun connectionDescription_neverClaimsConnectedWithoutConnectedState() {
        val unavailable = UiAccessibilityPolicy.connectionStateDescription(
            connected = false,
            busy = false,
            hasActiveService = false,
            connectedText = "connected",
            busyText = "busy",
            readyText = "ready",
            unavailableText = "unavailable",
        )
        val connected = UiAccessibilityPolicy.connectionStateDescription(
            connected = true,
            busy = false,
            hasActiveService = true,
            connectedText = "connected",
            busyText = "busy",
            readyText = "ready",
            unavailableText = "unavailable",
        )

        assertEquals("unavailable", unavailable)
        assertEquals("connected", connected)
        assertNotEquals(unavailable, connected)
    }

    @Test
    fun busyState_precedesReadyState() {
        val description = UiAccessibilityPolicy.connectionStateDescription(
            connected = false,
            busy = true,
            hasActiveService = true,
            connectedText = "connected",
            busyText = "busy",
            readyText = "ready",
            unavailableText = "unavailable",
        )

        assertEquals("busy", description)
    }
}
