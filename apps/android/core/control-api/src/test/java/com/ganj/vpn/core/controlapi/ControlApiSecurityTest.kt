package com.ganj.vpn.core.controlapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlApiSecurityTest {
    @Test
    fun `manual URI cannot be used as a service or server identifier`() {
        assertThrows(IllegalArgumentException::class.java) {
            validCommand().copy(serviceId = "vless://user@example.invalid:443")
        }
        assertThrows(IllegalArgumentException::class.java) {
            validCommand().copy(serverId = "vmess://eyJhZGQiOiJldmlsIn0=")
        }
    }

    @Test
    fun `QR and manual configuration payloads are rejected as proof material`() {
        assertThrows(IllegalArgumentException::class.java) {
            validCommand().copy(clientNonce = "qr:" + "a".repeat(40))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validCommand().copy(deviceProof = "{\"outbounds\":[]}" + "a".repeat(32))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validCommand().copy(deviceProof = "[Interface]" + "a".repeat(32))
        }
    }

    @Test
    fun `public repository surface contains no manual import API`() {
        val surface = ControlApiRepository::class.java.methods.joinToString(" ") { method ->
            method.name + method.parameterTypes.joinToString { it.simpleName }
        }.lowercase()

        listOf("import", "clipboard", "qrcode", "subscriptionurl", "manualconfig").forEach {
            assertFalse("Forbidden API token: $it", surface.contains(it))
        }
    }

    @Test
    fun `duplicate JSON keys fail closed`() {
        val transport = FakeTransport().apply {
            enqueue(
                200,
                """{"data":[],"data":[],"meta":{"request_id":"$REQUEST_ID"},"error":null}""",
            )
        }
        val client = ControlApiClient(transport, AuthTokenProvider { TOKEN }, AuthenticationEventSink.NONE)

        val error = client.getMyServices().requireFailure().error

        assertTrue(error is ApiError.Protocol)
    }

    @Test
    fun `invalid ciphertext base64 fails closed and never creates a lease`() {
        val transport = FakeTransport().apply {
            enqueue(
                201,
                successEnvelope(
                    """
                    {
                      "profile_id":"$PROFILE_ID",
                      "server_id":"$SERVER_ID",
                      "algorithm":"X25519+HKDF-SHA256+AES-256-GCM",
                      "key_version":"kms-v3",
                      "nonce":"AQIDBA==",
                      "ciphertext":"vless://manual-config",
                      "expires_at":"2026-08-24T12:05:00Z"
                    }
                    """.trimIndent(),
                ),
            )
        }
        val client = ControlApiClient(transport, AuthTokenProvider { TOKEN }, AuthenticationEventSink.NONE)

        val result = client.issueConnectionProfile(validCommand())

        assertTrue(result is ApiResult.Failure)
        assertTrue(result.requireFailure().error is ApiError.Protocol)
    }

    @Test
    fun `vault eviction destroys encrypted material and take is one-time`() {
        val firstNonce = byteArrayOf(1, 2)
        val firstCiphertext = byteArrayOf(3, 4)
        val vault = InMemoryConnectionEnvelopeVault(maximumEntries = 1)
        val firstHandle = vault.put(envelope(firstNonce, firstCiphertext))
        val secondHandle = vault.put(envelope(byteArrayOf(5), byteArrayOf(6)))

        assertTrue(firstNonce.all { it == 0.toByte() })
        assertTrue(firstCiphertext.all { it == 0.toByte() })
        assertEquals(null, vault.take(firstHandle))
        assertTrue(vault.take(secondHandle) != null)
        assertEquals(null, vault.take(secondHandle))
    }

    @Test
    fun `access token string representation is always redacted`() {
        assertEquals("AccessToken([REDACTED])", TOKEN.toString())
        assertFalse(TOKEN.toString().contains("header.payload"))
    }

    private fun validCommand() = ConnectionProfileCommand(
        serviceId = SERVICE_ID,
        deviceId = DEVICE_ID,
        serverId = SERVER_ID,
        clientNonce = "0123456789abcdef0123456789abcdef",
        deviceProof = "abcdef0123456789abcdef0123456789",
    )

    private fun envelope(nonce: ByteArray, ciphertext: ByteArray) = EncryptedConnectionEnvelope(
        profileId = PROFILE_ID,
        serverId = SERVER_ID,
        algorithm = "X25519+HKDF-SHA256+AES-256-GCM",
        keyVersion = "kms-v3",
        nonce = nonce,
        ciphertext = ciphertext,
        expiresAt = "2026-08-24T12:05:00Z",
    )
}
