package com.ganj.vpn.ui

import com.ganj.vpn.core.controlapi.TrustedDevice
import com.ganj.vpn.core.controlapi.TrustedDeviceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjDevicesUiTest {
    @Test
    fun `current device gets human label without exposing full id`() {
        val device = device(current = true, name = null)
        assertEquals("این دستگاه", safeDeviceLabel(device))
    }

    @Test
    fun `real backend device name wins when available`() {
        val device = device(current = false, name = "Pixel Test")
        assertEquals("Pixel Test", safeDeviceLabel(device))
    }

    @Test
    fun `unnamed other device exposes only isolated short suffix`() {
        val device = device(current = false, name = null)
        val label = safeDeviceLabel(device)
        assertTrue(label.startsWith("دستگاه Android · "))
        assertTrue(label.contains("00000042"))
        assertTrue(!label.contains(device.id))
        assertTrue(label.contains("\u2066"))
        assertTrue(label.contains("\u2069"))
    }

    private fun device(current: Boolean, name: String?) = TrustedDevice(
        id = "20000000-0000-4000-8000-000000000042",
        platform = "android",
        name = name,
        appVersion = null,
        status = TrustedDeviceStatus.ACTIVE,
        current = current,
        lastSeenAt = null,
    )
}
