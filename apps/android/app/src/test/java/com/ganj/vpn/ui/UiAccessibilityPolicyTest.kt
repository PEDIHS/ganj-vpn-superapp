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
    fun destinationDescription_announcesSelectionWithoutChangingLabel() {
        val idle = UiAccessibilityPolicy.destinationDescription("خانه", selected = false)
        val selected = UiAccessibilityPolicy.destinationDescription("خانه", selected = true)

        assertEquals("خانه", idle)
        assertTrue(selected.startsWith("خانه"))
        assertNotEquals(idle, selected)
    }

    @Test
    fun connectionDescription_neverClaimsConnectedWithoutReducerState() {
        val unavailable = UiAccessibilityPolicy.connectionStateDescription(
            connected = false,
            busy = false,
            hasActiveService = false,
        )
        val connected = UiAccessibilityPolicy.connectionStateDescription(
            connected = true,
            busy = false,
            hasActiveService = true,
        )

        assertTrue(unavailable.contains("دردسترس نیست"))
        assertTrue(connected.contains("متصل است"))
        assertNotEquals(unavailable, connected)
    }
}
