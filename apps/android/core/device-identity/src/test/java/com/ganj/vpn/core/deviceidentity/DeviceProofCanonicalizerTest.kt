package com.ganj.vpn.core.deviceidentity

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceProofCanonicalizerTest {
    @Test
    fun `canonical proof is deterministic and binds request fields`() {
        val request = DeviceProofRequest.create(
            method = "post",
            pathAndQuery = "/services/00000000-0000-4000-8000-000000000001/connection-profile",
            body = ByteArray(0),
            timestampEpochSeconds = 1_800_000_000,
            nonce = "AAAAAAAAAAAAAAAAAAAAAA",
        )

        val canonical = String(
            DeviceProofCanonicalizer.canonicalBytes(request, "v7"),
            StandardCharsets.US_ASCII,
        )

        assertEquals(
            listOf(
                "GANJ-DEVICE-PROOF-V1",
                "POST",
                "/services/00000000-0000-4000-8000-000000000001/connection-profile",
                "47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU",
                "1800000000",
                "AAAAAAAAAAAAAAAAAAAAAA",
                "v7",
            ).joinToString("\n"),
            canonical,
        )
    }

    @Test
    fun `path newline and padded base64 are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceProofRequest(
                method = "POST",
                pathAndQuery = "/services\n/admin",
                bodySha256 = "47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU",
                timestampEpochSeconds = 1,
                nonce = "AAAAAAAAAAAAAAAAAAAAAA",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            Base64Url.decode("AA==")
        }
    }

    @Test
    fun `base64url codec is canonical for x25519 material`() {
        val source = ByteArray(32) { it.toByte() }
        val encoded = Base64Url.encode(source)

        assertEquals(43, encoded.length)
        assertEquals(source.toList(), Base64Url.decode(encoded).toList())
    }
}
