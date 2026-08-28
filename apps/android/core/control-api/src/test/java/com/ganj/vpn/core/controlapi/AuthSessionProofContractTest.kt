package com.ganj.vpn.core.controlapi

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthSessionProofContractTest {
    @Test
    fun `guest proof binds the absolute server path and canonical alphabetic body`() {
        val body = AuthSessionProofContract.guestUnsignedBody(
            deviceId = DEVICE_ID,
            keyVersion = "v1",
            signingPublicKeySpki = "S".repeat(96),
            encryptionPublicKeyRaw = "E".repeat(43),
        )
        try {
            assertEquals("/v1/auth/guest", AuthSessionProofContract.GUEST_PATH)
            assertEquals(
                "{\"device_id\":\"$DEVICE_ID\",\"encryption_public_key_raw\":\"${"E".repeat(43)}\",\"key_version\":\"v1\",\"signing_public_key_spki\":\"${"S".repeat(96)}\"}",
                String(body, StandardCharsets.UTF_8),
            )
        } finally {
            body.fill(0)
        }
    }

    @Test
    fun `refresh proof includes device and one time refresh token in backend canonical order`() {
        val refresh = RefreshToken.from("r".repeat(43))
        val body = AuthSessionProofContract.refreshUnsignedBody(DEVICE_ID, refresh)
        try {
            assertEquals("/v1/auth/refresh", AuthSessionProofContract.REFRESH_PATH)
            assertEquals(
                "{\"device_id\":\"$DEVICE_ID\",\"refresh_token\":\"${"r".repeat(43)}\"}",
                String(body, StandardCharsets.UTF_8),
            )
        } finally {
            body.fill(0)
        }
    }
}
