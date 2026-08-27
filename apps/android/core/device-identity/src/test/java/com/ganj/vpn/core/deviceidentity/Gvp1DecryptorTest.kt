package com.ganj.vpn.core.deviceidentity

import com.ganj.vpn.core.controlapi.Gvp1CryptoFailureReason
import com.ganj.vpn.core.controlapi.Gvp1CryptoResult
import com.ganj.vpn.core.controlapi.Gvp1DecryptRequest
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Gvp1DecryptorTest {
    @Test
    fun `decrypts authenticated profile and rejects tag replay mutation`() {
        val recipientBytes = ByteArray(32) { (it + 1).toByte() }
        val ephemeralBytes = ByteArray(32) { (it + 65).toByte() }
        val recipient = X25519PrivateKeyParameters(recipientBytes, 0)
        val ephemeral = X25519PrivateKeyParameters(ephemeralBytes, 0)
        val shared = ByteArray(32)
        ephemeral.generateSecret(
            X25519PublicKeyParameters(recipient.generatePublicKey().encoded, 0),
            shared,
            0,
        )
        val key = referenceHkdf(shared, "ganj-vpn-profile-v1".toByteArray(StandardCharsets.US_ASCII))
        val nonce = ByteArray(12) { (it + 9).toByte() }
        val associatedData = "{\"deviceId\":\"test\"}".toByteArray(StandardCharsets.US_ASCII)
        val plaintext = "{\"schema_version\":1}".toByteArray(StandardCharsets.UTF_8)
        val sealed = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(associatedData)
            doFinal(plaintext)
        }
        val encrypted = sealed.copyOfRange(0, sealed.size - 16)
        val tag = sealed.copyOfRange(sealed.size - 16, sealed.size)
        val request = Gvp1DecryptRequest(
            keyVersion = "v1",
            ephemeralPublicKey = ephemeral.generatePublicKey().encoded,
            nonce = nonce,
            encryptedPayload = encrypted,
            authenticationTag = tag,
            associatedData = associatedData,
        )

        val success = Gvp1Decryptor.decrypt(recipientBytes, request)
        assertArrayEquals(plaintext, (success as Gvp1CryptoResult.Success).plaintext)

        tag[0] = (tag[0].toInt() xor 1).toByte()
        val rejected = Gvp1Decryptor.decrypt(
            recipientBytes,
            Gvp1DecryptRequest(
                keyVersion = "v1",
                ephemeralPublicKey = ephemeral.generatePublicKey().encoded,
                nonce = nonce,
                encryptedPayload = encrypted,
                authenticationTag = tag,
                associatedData = associatedData,
            ),
        )
        assertEquals(
            Gvp1CryptoFailureReason.AUTHENTICATION_FAILED,
            (rejected as Gvp1CryptoResult.Failure).reason,
        )

        recipientBytes.fill(0)
        ephemeralBytes.fill(0)
        shared.fill(0)
        key.fill(0)
        nonce.fill(0)
        associatedData.fill(0)
        plaintext.fill(0)
        sealed.fill(0)
        encrypted.fill(0)
        tag.fill(0)
    }

    private fun referenceHkdf(input: ByteArray, info: ByteArray): ByteArray {
        val zeroSalt = ByteArray(32)
        val extract = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(zeroSalt, "HmacSHA256"))
            doFinal(input)
        }
        return try {
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(extract, "HmacSHA256"))
                doFinal(info + byteArrayOf(1)).copyOf(32)
            }
        } finally {
            zeroSalt.fill(0)
            extract.fill(0)
        }
    }
}
