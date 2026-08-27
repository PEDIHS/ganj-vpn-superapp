package com.ganj.vpn.core.controlapi

import com.ganj.vpn.core.vpn.ProvisionedSecurity
import com.ganj.vpn.core.vpn.ProvisionedTransport
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.NamedParameterSpec
import java.security.spec.XECPrivateKeySpec
import java.security.spec.XECPublicKeySpec
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionProfileBrokerTest {
    @Test
    fun `canonical AAD is byte-identical to Node canonical JSON`() {
        val value = canonicalAssociatedData(binding())

        assertEquals(NODE_AAD, String(value, StandardCharsets.US_ASCII))
        value.fill(0)
    }

    @Test
    fun `RFC7748 X25519 and Node GVP1 vector decrypt identically on the JVM`() {
        val provider = JvmVectorCryptoProvider(ALICE_PRIVATE.hex())
        val request = Gvp1DecryptRequest(
            keyVersion = "test-rfc7748",
            ephemeralPublicKey = BOB_PUBLIC.hex(),
            nonce = NODE_NONCE.hex(),
            encryptedPayload = NODE_CIPHERTEXT.hex(),
            authenticationTag = NODE_TAG.hex(),
            associatedData = NODE_AAD.toByteArray(StandardCharsets.US_ASCII),
        )

        val result = provider.decrypt(request) as Gvp1CryptoResult.Success

        assertEquals(NODE_PLAINTEXT, String(result.plaintext, StandardCharsets.UTF_8))
        assertArrayEquals(RFC7748_SHARED.hex(), provider.lastSharedSecret)
        assertArrayEquals(NODE_HKDF_KEY.hex(), provider.lastDerivedKey)
        result.plaintext.fill(0)
        request.ephemeralPublicKey.fill(0)
        request.nonce.fill(0)
        request.encryptedPayload.fill(0)
        request.authenticationTag.fill(0)
        request.associatedData.fill(0)
        provider.close()
    }

    @Test
    fun `successful profile is provisioned once and all borrowed buffers are zeroized`() {
        val plaintext = validPlaintext().toByteArray(StandardCharsets.UTF_8)
        val provider = CapturingProvider(expectedAad = NODE_AAD, plaintext = plaintext)
        val fixture = fixture(provider)

        val first = fixture.broker.provision(fixture.lease, binding()) as ProfileProvisioningResult.Success
        val second = fixture.broker.provision(fixture.lease, binding()) as ProfileProvisioningResult.Failure

        assertEquals(ProfileProvisioningError.LEASE_CONSUMED, second.error)
        assertEquals(ProvisionedTransport.Tcp, first.profile.transport)
        assertEquals(ProvisionedSecurity.None, first.profile.security)
        assertEquals(BROKER_CREDENTIAL_ID, first.profile.useCredential { it })
        assertTrue(plaintext.allZero())
        assertTrue(provider.borrowedBuffers.all { it.allZero() })
        assertTrue(fixture.nonce.allZero())
        assertTrue(fixture.sealed.allZero())
        first.profile.close()
    }

    @Test
    fun `profile server and expiry binding mismatches fail before crypto and consume the lease`() {
        val variants = listOf(
            binding().copy(profileId = BROKER_OTHER_PROFILE_ID),
            binding().copy(serverId = BROKER_OTHER_SERVER_ID),
            binding().copy(expiresAt = "2026-08-24T12:06:00.000Z"),
        )
        variants.forEach { changed ->
            val provider = CountingProvider()
            val fixture = fixture(provider)

            assertFailure(fixture.broker.provision(fixture.lease, changed), ProfileProvisioningError.BINDING_MISMATCH)
            assertFailure(fixture.broker.provision(fixture.lease, binding()), ProfileProvisioningError.LEASE_CONSUMED)
            assertEquals(0, provider.calls)
        }
    }

    @Test
    fun `user service and device AAD mismatches fail authentication and consume the lease`() {
        val variants = listOf(
            binding().copy(userId = BROKER_OTHER_USER_ID),
            binding().copy(serviceId = BROKER_OTHER_SERVICE_ID),
            binding().copy(deviceId = BROKER_OTHER_DEVICE_ID),
        )
        variants.forEach { changed ->
            val provider = CapturingProvider(
                expectedAad = NODE_AAD,
                plaintext = validPlaintext().toByteArray(StandardCharsets.UTF_8),
            )
            val fixture = fixture(provider)

            assertFailure(
                fixture.broker.provision(fixture.lease, changed),
                ProfileProvisioningError.AUTHENTICATION_FAILED,
            )
            assertFailure(fixture.broker.provision(fixture.lease, binding()), ProfileProvisioningError.LEASE_CONSUMED)
        }
    }

    @Test
    fun `expired unsupported malformed and unavailable envelopes fail closed`() {
        val expired = fixture(CountingProvider(), now = Long.MAX_VALUE)
        assertFailure(expired.broker.provision(expired.lease, binding()), ProfileProvisioningError.EXPIRED)

        val legacy = fixture(CountingProvider(), algorithm = "X25519+HKDF-SHA256+AES-256-GCM")
        assertFailure(legacy.broker.provision(legacy.lease, binding()), ProfileProvisioningError.UNSUPPORTED_ALGORITHM)

        val malformed = fixture(CountingProvider(), sealedOverride = "GVP0".toByteArray())
        assertFailure(malformed.broker.provision(malformed.lease, binding()), ProfileProvisioningError.MALFORMED_ENVELOPE)

        val unavailable = fixture(UnavailableGvp1CryptoProvider)
        assertFailure(unavailable.broker.provision(unavailable.lease, binding()), ProfileProvisioningError.CRYPTO_UNAVAILABLE)
    }

    @Test
    fun `schema v1 allows explicit websocket reality material`() {
        val profileJson = validPlaintext(
            transport = """{"type":"ws","path":"/vpn","host":"edge.ganj.test"}""",
            security = """{"type":"reality","server_name":"edge.ganj.test","fingerprint":"chrome","public_key":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","short_id":"aabbccdd","allow_insecure":false}""",
            flow = "\"xtls-rprx-vision\"",
        )
        val fixture = fixture(CapturingProvider(NODE_AAD, profileJson.toByteArray()))

        val result = fixture.broker.provision(fixture.lease, binding()) as ProfileProvisioningResult.Success

        assertEquals(ProvisionedTransport.WebSocket("/vpn", "edge.ganj.test"), result.profile.transport)
        assertTrue(result.profile.security is ProvisionedSecurity.Reality)
        result.profile.close()
    }

    @Test
    fun `schema v1 allows explicit grpc tls and shadowsocks method`() {
        val profileJson = validPlaintext(
            protocol = "shadowsocks",
            credential = "server-issued-secret",
            transport = """{"type":"grpc","service_name":"ganj.vpn.Edge"}""",
            security = """{"type":"tls","server_name":"de1.ganj.test","fingerprint":"android","allow_insecure":false}""",
            shadowsocksMethod = "\"aes-256-gcm\"",
        )
        val fixture = fixture(CapturingProvider(NODE_AAD, profileJson.toByteArray()))

        val result = fixture.broker.provision(fixture.lease, binding()) as ProfileProvisioningResult.Success

        assertEquals(ProvisionedTransport.Grpc("ganj.vpn.Edge"), result.profile.transport)
        assertEquals(
            ProvisionedSecurity.Tls("de1.ganj.test", "android", allowInsecure = false),
            result.profile.security,
        )
        assertEquals("aes-256-gcm", result.profile.shadowsocksMethod)
        result.profile.close()
    }

    @Test
    fun `schema never defaults transport security flow or method`() {
        val missingTransport = validPlaintext().replace(",\"transport\":{\"type\":\"tcp\"}", "")
        val unknownField = validPlaintext().replace("\"schema_version\":1", "\"schema_version\":1,\"config_uri\":\"vless://manual\"")
        val insecureTls = validPlaintext(
            security = """{"type":"tls","server_name":"de1.ganj.test","fingerprint":"chrome","allow_insecure":true}""",
        )
        listOf(missingTransport, unknownField, insecureTls).forEach { payload ->
            val fixture = fixture(CapturingProvider(NODE_AAD, payload.toByteArray()))
            assertFailure(fixture.broker.provision(fixture.lease, binding()), ProfileProvisioningError.PAYLOAD_INVALID)
        }
    }

    @Test
    fun `decrypted identifiers and exact expiry are rechecked after authentication`() {
        val variants = listOf(
            validPlaintext().replace(BROKER_PROFILE_ID, BROKER_OTHER_PROFILE_ID),
            validPlaintext().replace(BROKER_SERVICE_ID, BROKER_OTHER_SERVICE_ID),
            validPlaintext().replace(BROKER_SERVER_ID, BROKER_OTHER_SERVER_ID),
            validPlaintext().replace(BROKER_DEVICE_ID, BROKER_OTHER_DEVICE_ID),
            validPlaintext().replace(BROKER_EXPIRES_AT, "2026-08-24T12:06:00.000Z"),
        )
        variants.forEach { payload ->
            val fixture = fixture(CapturingProvider(NODE_AAD, payload.toByteArray()))
            assertFailure(fixture.broker.provision(fixture.lease, binding()), ProfileProvisioningError.PAYLOAD_MISMATCH)
        }
    }

    @Test
    fun `manual URI QR and unsupported protocols are rejected after authenticated decryption`() {
        val manual = validPlaintext(protocol = "trojan", credential = "vless://manual-profile")
        val qr = validPlaintext(protocol = "trojan", credential = "qr:manual-profile-secret")
        listOf(manual, qr).forEach { payload ->
            val fixture = fixture(CapturingProvider(NODE_AAD, payload.toByteArray()))
            assertFailure(fixture.broker.provision(fixture.lease, binding()), ProfileProvisioningError.PAYLOAD_INVALID)
        }
        val wireGuard = fixture(
            CapturingProvider(NODE_AAD, validPlaintext(protocol = "wireguard").toByteArray()),
        )
        assertFailure(
            wireGuard.broker.provision(wireGuard.lease, binding()),
            ProfileProvisioningError.UNSUPPORTED_PROTOCOL,
        )
    }

    @Test
    fun `public broker surface has no manual import or raw configuration operation`() {
        val surface = ConnectionProfileBroker::class.java.methods.joinToString(" ") { it.name }.lowercase()

        listOf("import", "clipboard", "qrcode", "uri", "file", "rawconfig", "manualconfig").forEach {
            assertFalse("Forbidden broker token: $it", surface.contains(it))
        }
    }

    private fun binding() = ProfileProvisioningBinding(
        profileId = BROKER_PROFILE_ID,
        userId = BROKER_USER_ID,
        serviceId = BROKER_SERVICE_ID,
        deviceId = BROKER_DEVICE_ID,
        serverId = BROKER_SERVER_ID,
        expiresAt = BROKER_EXPIRES_AT,
    )

    private fun fixture(
        provider: Gvp1CryptoProvider,
        now: Long = 0,
        algorithm: String = GVP1,
        sealedOverride: ByteArray? = null,
    ): BrokerFixture {
        val nonce = NODE_NONCE.hex()
        val sealed = sealedOverride ?: (
            "47565031" + BOB_PUBLIC + "010203" + "00".repeat(16)
            ).hex()
        val envelope = EncryptedConnectionEnvelope(
            profileId = BROKER_PROFILE_ID,
            serverId = BROKER_SERVER_ID,
            algorithm = algorithm,
            keyVersion = "device-key-v1",
            nonce = nonce,
            ciphertext = sealed,
            expiresAt = BROKER_EXPIRES_AT,
        )
        val vault = InMemoryConnectionEnvelopeVault()
        val handle = vault.put(envelope)
        val lease = ConnectionProfileLease(BROKER_PROFILE_ID, BROKER_SERVER_ID, BROKER_EXPIRES_AT, handle)
        return BrokerFixture(
            broker = OneTimeConnectionProfileBroker(vault, provider) { now },
            lease = lease,
            nonce = nonce,
            sealed = sealed,
        )
    }

    private fun validPlaintext(
        protocol: String = "vless",
        credential: String = BROKER_CREDENTIAL_ID,
        transport: String = """{"type":"tcp"}""",
        security: String = """{"type":"none"}""",
        flow: String = "null",
        shadowsocksMethod: String = "null",
    ): String = """{"schema_version":1,"endpoint":"de1.ganj.test","port":443,"protocol":"$protocol","credential":"$credential","profile_id":"$BROKER_PROFILE_ID","service_id":"$BROKER_SERVICE_ID","server_id":"$BROKER_SERVER_ID","device_id":"$BROKER_DEVICE_ID","expires_at":"$BROKER_EXPIRES_AT","transport":$transport,"security":$security,"flow":$flow,"shadowsocks_method":$shadowsocksMethod}"""

    private fun assertFailure(result: ProfileProvisioningResult, expected: ProfileProvisioningError) {
        assertEquals(expected, (result as ProfileProvisioningResult.Failure).error)
    }
}

private data class BrokerFixture(
    val broker: ConnectionProfileBroker,
    val lease: ConnectionProfileLease,
    val nonce: ByteArray,
    val sealed: ByteArray,
)

private class CountingProvider : Gvp1CryptoProvider {
    var calls = 0

    override fun decrypt(request: Gvp1DecryptRequest): Gvp1CryptoResult {
        calls++
        return Gvp1CryptoResult.Failure(Gvp1CryptoFailureReason.INTERNAL_FAILURE)
    }
}

private class CapturingProvider(
    private val expectedAad: String,
    private val plaintext: ByteArray,
) : Gvp1CryptoProvider {
    val borrowedBuffers = mutableListOf<ByteArray>()

    override fun decrypt(request: Gvp1DecryptRequest): Gvp1CryptoResult {
        borrowedBuffers += listOf(
            request.ephemeralPublicKey,
            request.nonce,
            request.encryptedPayload,
            request.authenticationTag,
            request.associatedData,
        )
        return if (String(request.associatedData, StandardCharsets.US_ASCII) == expectedAad) {
            Gvp1CryptoResult.Success(plaintext)
        } else {
            plaintext.fill(0)
            Gvp1CryptoResult.Failure(Gvp1CryptoFailureReason.AUTHENTICATION_FAILED)
        }
    }
}

/** Pure-JVM vector provider. It is test-only and is never shipped as a production key provider. */
private class JvmVectorCryptoProvider(private val privateKey: ByteArray) : Gvp1CryptoProvider, AutoCloseable {
    var lastSharedSecret = byteArrayOf()
        private set
    var lastDerivedKey = byteArrayOf()
        private set

    override fun decrypt(request: Gvp1DecryptRequest): Gvp1CryptoResult {
        val parameters = NamedParameterSpec.X25519
        val privateSpec = XECPrivateKeySpec(parameters, privateKey)
        val publicU = BigInteger(1, request.ephemeralPublicKey.reversedArray())
        val publicSpec = XECPublicKeySpec(parameters, publicU)
        val factory = KeyFactory.getInstance("XDH")
        val agreement = KeyAgreement.getInstance("X25519")
        agreement.init(factory.generatePrivate(privateSpec))
        agreement.doPhase(factory.generatePublic(publicSpec), true)
        val shared = agreement.generateSecret()
        lastSharedSecret = shared.copyOf()
        val salt = MessageDigest.getInstance("SHA-256").digest(request.associatedData)
        val key = hkdfSha256(shared, salt, "ganj-vpn-profile-v1".toByteArray(StandardCharsets.US_ASCII))
        lastDerivedKey = key.copyOf()
        val sealed = request.encryptedPayload + request.authenticationTag
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, request.nonce))
            cipher.updateAAD(request.associatedData)
            Gvp1CryptoResult.Success(cipher.doFinal(sealed))
        } catch (_: AEADBadTagException) {
            Gvp1CryptoResult.Failure(Gvp1CryptoFailureReason.AUTHENTICATION_FAILED)
        } finally {
            shared.fill(0)
            salt.fill(0)
            key.fill(0)
            sealed.fill(0)
        }
    }

    override fun close() {
        privateKey.fill(0)
        lastSharedSecret.fill(0)
        lastDerivedKey.fill(0)
    }

    private fun hkdfSha256(input: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val pseudoRandomKey = mac.doFinal(input)
        return try {
            mac.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
            mac.update(info)
            mac.doFinal(byteArrayOf(1)).copyOf(32)
        } finally {
            pseudoRandomKey.fill(0)
            info.fill(0)
        }
    }
}

private fun String.hex(): ByteArray {
    require(length % 2 == 0)
    return ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}

private fun ByteArray.allZero(): Boolean = all { it == 0.toByte() }

private const val BROKER_USER_ID = "10000000-0000-4000-8000-000000000001"
private const val BROKER_SERVICE_ID = "20000000-0000-4000-8000-000000000001"
private const val BROKER_DEVICE_ID = "30000000-0000-4000-8000-000000000001"
private const val BROKER_SERVER_ID = "40000000-0000-4000-8000-000000000001"
private const val BROKER_PROFILE_ID = "50000000-0000-4000-8000-000000000001"
private const val BROKER_CREDENTIAL_ID = "60000000-0000-4000-8000-000000000001"
private const val BROKER_OTHER_USER_ID = "10000000-0000-4000-8000-000000000002"
private const val BROKER_OTHER_SERVICE_ID = "20000000-0000-4000-8000-000000000002"
private const val BROKER_OTHER_DEVICE_ID = "30000000-0000-4000-8000-000000000002"
private const val BROKER_OTHER_SERVER_ID = "40000000-0000-4000-8000-000000000002"
private const val BROKER_OTHER_PROFILE_ID = "50000000-0000-4000-8000-000000000002"
private const val BROKER_EXPIRES_AT = "2026-08-24T12:05:00.000Z"
private const val GVP1 = "X25519+HKDF-SHA256+AES-256-GCM/GVP1"
private const val ALICE_PRIVATE = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"
private const val BOB_PUBLIC = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f"
private const val RFC7748_SHARED = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"
private const val NODE_NONCE = "000102030405060708090a0b"
private const val NODE_HKDF_KEY = "5e89301f449708fa753dd8d423f90ab314704e95c463870e58f9b551f9d6cf72" // gitleaks:allow -- public compatibility test vector
private const val NODE_TAG = "4d9dc83095cbd218fa06aed3fbe2a2a3"
private const val NODE_AAD = "{\"deviceId\":\"30000000-0000-4000-8000-000000000001\",\"expiresAt\":\"2026-08-24T12:05:00.000Z\",\"profileId\":\"50000000-0000-4000-8000-000000000001\",\"serverId\":\"40000000-0000-4000-8000-000000000001\",\"serviceId\":\"20000000-0000-4000-8000-000000000001\",\"userId\":\"10000000-0000-4000-8000-000000000001\"}"
private const val NODE_PLAINTEXT = "{\"endpoint\":\"de1.ganj.test\",\"port\":443,\"protocol\":\"vless\",\"credential\":\"credential-123456\",\"profile_id\":\"50000000-0000-4000-8000-000000000001\",\"service_id\":\"20000000-0000-4000-8000-000000000001\",\"server_id\":\"40000000-0000-4000-8000-000000000001\",\"device_id\":\"30000000-0000-4000-8000-000000000001\",\"expires_at\":\"2026-08-24T12:05:00.000Z\"}"
private const val NODE_CIPHERTEXT = "a3aa65859c768e564b880df22508f7ee2353d035c8aaac54230384cf506a61e42d1167a1f36ed29f4c97155056874886af699d00b021b8965d745cf559e96a4af9a3d930f364ee3d40113e3af1be7285cd71abf75c02604e14e6196bb3e0e2c37fca3960753860571f667735e16c9d33d825ea5578da29f99f4917745baa4d44ca838dead0d3e634bf319b0b41060003b1f2693536c048f313897b2f7cf4e39f6fa63ed9b4500d714a183ac85837b6924cdfec91de6669ca9dc3d6a998cc8d92747b6351671b457388ab03cc4d27d4c550eb9d79806ad0bc9d8b2136ac7d79a280348a05958eb596c3c4e90207bcb83c1e65fffb71f358e6224224b4398c01e0e82615d7a5a7f9c8ac1c310a65ea49fe7bfbe8d45628ddfe0ba27c0c3f63fa258b91ad8e027e9d00deb1af2aab649b4257022fa3fd04d94084f80162ab6e25a09d055f8c65e2e99c5c649275315f51cb82"
