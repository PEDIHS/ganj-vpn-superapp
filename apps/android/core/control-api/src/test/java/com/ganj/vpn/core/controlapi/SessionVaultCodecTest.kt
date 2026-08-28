package com.ganj.vpn.core.controlapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionVaultCodecTest {
    private fun session() = AuthSessionCredentials(
        userId = "00000000-0000-4000-8000-000000000010",
        deviceId = DEVICE_ID,
        accessToken = AccessToken.from("header.payload.signature-value"),
        accessTokenExpiresAt = "2026-08-28T04:15:00Z",
        refreshToken = RefreshToken.from("r".repeat(43)),
        refreshTokenExpiresAt = "2026-09-27T04:00:00Z",
    )

    @Test
    fun `codec round trip preserves credentials without exposing them in toString`() {
        val original = session()
        val bytes = SessionVaultCodec.encode(original)
        val restored = SessionVaultCodec.decode(bytes)

        assertEquals(original.userId, restored.userId)
        assertEquals(original.deviceId, restored.deviceId)
        assertEquals(original.accessToken.authorizationValue(), restored.accessToken.authorizationValue())
        assertEquals(original.refreshToken.rawValue(), restored.refreshToken.rawValue())
        assertFalse(restored.toString().contains("signature-value"))
        assertFalse(restored.toString().contains("r".repeat(20)))
        bytes.fill(0)
    }

    @Test
    fun `codec rejects trailing bytes and unsupported versions`() {
        val encoded = SessionVaultCodec.encode(session())
        val trailing = encoded + byteArrayOf(1)
        assertThrows(IllegalArgumentException::class.java) { SessionVaultCodec.decode(trailing) }

        val wrongVersion = encoded.copyOf().also { it[3] = 9 }
        assertThrows(IllegalArgumentException::class.java) { SessionVaultCodec.decode(wrongVersion) }
        encoded.fill(0)
        trailing.fill(0)
        wrongVersion.fill(0)
    }
}
