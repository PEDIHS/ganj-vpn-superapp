package com.ganj.vpn.core.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureProfileRecoveryCodecTest {
    @Test
    fun roundTripPreservesValidatedProfileAndDestroyedProfilesRejectCredentialUse() {
        val original = profile()
        val encoded = ProvisionedProfileRecoveryCodec.encode(original)
        val decoded = ProvisionedProfileRecoveryCodec.decode(encoded)

        assertEquals(original.profileId, decoded.profileId)
        assertEquals(original.serviceId, decoded.serviceId)
        assertEquals(original.serverId, decoded.serverId)
        assertEquals(VpnProtocol.VLESS, decoded.protocol)
        assertEquals("40000000-0000-4000-8000-000000000001", decoded.useCredential { it })

        original.close()
        decoded.close()
        encoded.fill(0)
        assertTrue(runCatching { original.useCredential { it } }.isFailure)
        assertTrue(runCatching { decoded.useCredential { it } }.isFailure)
    }

    @Test
    fun rejectsUnsupportedVersionTrailingBytesAndMalformedPayload() {
        val profile = profile()
        val encoded = ProvisionedProfileRecoveryCodec.encode(profile)
        profile.close()

        val unsupported = encoded.copyOf().also { it[7] = 2 }
        assertTrue(runCatching { ProvisionedProfileRecoveryCodec.decode(unsupported) }.isFailure)
        assertTrue(runCatching { ProvisionedProfileRecoveryCodec.decode(encoded + 0) }.isFailure)
        assertTrue(runCatching { ProvisionedProfileRecoveryCodec.decode(byteArrayOf(1, 2, 3)) }.isFailure)

        encoded.fill(0)
        unsupported.fill(0)
    }

    @Test
    fun rejectsUnsupportedProtocolSecurityCombinations() {
        assertTrue(
            runCatching {
                profile(
                    protocol = VpnProtocol.TROJAN,
                    credential = "server-password".encodeToByteArray(),
                    security = ProvisionedSecurity.None,
                    flow = null,
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                profile(
                    protocol = VpnProtocol.VMESS,
                    security = ProvisionedSecurity.Reality(
                        serverName = "vpn.example.com",
                        publicKey = "A".repeat(43),
                        shortId = "a1b2",
                    ),
                    flow = null,
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                profile(
                    transport = ProvisionedTransport.Grpc("service"),
                    flow = "xtls-rprx-vision",
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                profile(
                    transport = ProvisionedTransport.WebSocket("/vpn", "vpn.example.com"),
                    security = ProvisionedSecurity.Reality(
                        serverName = "vpn.example.com",
                        publicKey = "A".repeat(43),
                        shortId = "a1b2",
                    ),
                    flow = null,
                )
            }.isFailure,
        )
    }

    @Test
    fun rejectsMalformedPasswordBytesAndNeverPrintsSecrets() {
        val malformed = byteArrayOf(
            0x73, 0x65, 0x63, 0x72, 0x65, 0x74, 0x2d, 0xc3.toByte(), 0x28,
        )
        assertTrue(
            runCatching {
                profile(
                    protocol = VpnProtocol.TROJAN,
                    credential = malformed,
                    security = ProvisionedSecurity.Tls("vpn.example.com"),
                    flow = null,
                )
            }.isFailure,
        )

        val profile = profile()
        assertFalse(profile.toString().contains("40000000-0000-4000-8000-000000000001"))
        profile.close()
        malformed.fill(0)
    }

    private fun profile(
        protocol: VpnProtocol = VpnProtocol.VLESS,
        credential: ByteArray = "40000000-0000-4000-8000-000000000001".encodeToByteArray(),
        transport: ProvisionedTransport = ProvisionedTransport.Tcp,
        security: ProvisionedSecurity = ProvisionedSecurity.Tls("vpn.example.com"),
        flow: String? = "xtls-rprx-vision",
    ) = ProvisionedProfile(
        profileId = "10000000-0000-4000-8000-000000000001",
        serviceId = "20000000-0000-4000-8000-000000000001",
        serverId = "30000000-0000-4000-8000-000000000001",
        endpoint = "vpn.example.com",
        port = 443,
        protocol = protocol,
        credential = credential,
        transport = transport,
        security = security,
        flow = flow,
        expiresAtEpochMillis = System.currentTimeMillis() + 60_000,
    )
}
