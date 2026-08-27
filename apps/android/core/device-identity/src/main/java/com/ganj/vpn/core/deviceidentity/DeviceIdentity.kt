package com.ganj.vpn.core.deviceidentity

import com.ganj.vpn.core.controlapi.Gvp1CryptoProvider
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

data class DevicePublicIdentity(
    val installationId: String,
    val keyVersion: String,
    val signingAlgorithm: String,
    val signingPublicKeySpki: String,
    val encryptionAlgorithm: String,
    val encryptionPublicKeyRaw: String,
    val signingKeyHardwareBacked: Boolean,
) {
    init {
        require(INSTALLATION_ID.matches(installationId))
        require(KEY_VERSION.matches(keyVersion))
        require(signingAlgorithm == "ES256")
        require(encryptionAlgorithm == "X25519")
        require(signingPublicKeySpki.length in 80..512)
        require(encryptionPublicKeyRaw.length == 43)
    }

    private companion object {
        val INSTALLATION_ID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        val KEY_VERSION = Regex("^v[1-9][0-9]{0,8}$")
    }
}

data class DeviceProofRequest(
    val method: String,
    val pathAndQuery: String,
    val bodySha256: String,
    val timestampEpochSeconds: Long,
    val nonce: String,
) {
    init {
        require(method.matches(Regex("^[A-Z]{3,10}$")))
        require(pathAndQuery.startsWith('/'))
        require(pathAndQuery.length in 1..2_048)
        require('\n' !in pathAndQuery && '\r' !in pathAndQuery)
        require(bodySha256.matches(Regex("^[A-Za-z0-9_-]{43}$")))
        require(timestampEpochSeconds > 0)
        require(nonce.matches(Regex("^[A-Za-z0-9_-]{22,128}$")))
    }

    companion object {
        fun create(
            method: String,
            pathAndQuery: String,
            body: ByteArray,
            timestampEpochSeconds: Long,
            nonce: String,
        ): DeviceProofRequest {
            val digest = MessageDigest.getInstance("SHA-256").digest(body)
            return try {
                DeviceProofRequest(
                    method = method.uppercase(Locale.US),
                    pathAndQuery = pathAndQuery,
                    bodySha256 = Base64Url.encode(digest),
                    timestampEpochSeconds = timestampEpochSeconds,
                    nonce = nonce,
                )
            } finally {
                digest.fill(0)
            }
        }
    }
}

data class SignedDeviceProof(
    val algorithm: String,
    val keyVersion: String,
    val timestampEpochSeconds: Long,
    val nonce: String,
    val bodySha256: String,
    val signature: String,
) {
    init {
        require(algorithm == "ES256")
        require(KEY_VERSION.matches(keyVersion))
        require(signature.matches(Regex("^[A-Za-z0-9_-]{80,128}$")))
    }

    private companion object {
        val KEY_VERSION = Regex("^v[1-9][0-9]{0,8}$")
    }
}

interface DeviceIdentity : Gvp1CryptoProvider {
    fun publicIdentity(): Result<DevicePublicIdentity>
    fun sign(request: DeviceProofRequest): Result<SignedDeviceProof>
    fun rotate(): Result<DevicePublicIdentity>
}

object DeviceProofCanonicalizer {
    private const val SCHEMA = "GANJ-DEVICE-PROOF-V1"

    fun canonicalBytes(request: DeviceProofRequest, keyVersion: String): ByteArray {
        require(keyVersion.matches(Regex("^v[1-9][0-9]{0,8}$")))
        return buildString(2_400) {
            append(SCHEMA).append('\n')
            append(request.method).append('\n')
            append(request.pathAndQuery).append('\n')
            append(request.bodySha256).append('\n')
            append(request.timestampEpochSeconds).append('\n')
            append(request.nonce).append('\n')
            append(keyVersion)
        }.toByteArray(StandardCharsets.US_ASCII)
    }
}

internal object Base64Url {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encode(source: ByteArray): String {
        if (source.isEmpty()) return ""
        val output = StringBuilder((source.size * 4 + 2) / 3)
        var index = 0
        while (index + 2 < source.size) {
            val value = ((source[index].toInt() and 0xff) shl 16) or
                ((source[index + 1].toInt() and 0xff) shl 8) or
                (source[index + 2].toInt() and 0xff)
            output.append(ALPHABET[(value ushr 18) and 63])
            output.append(ALPHABET[(value ushr 12) and 63])
            output.append(ALPHABET[(value ushr 6) and 63])
            output.append(ALPHABET[value and 63])
            index += 3
        }
        val remaining = source.size - index
        if (remaining == 1) {
            val value = (source[index].toInt() and 0xff) shl 16
            output.append(ALPHABET[(value ushr 18) and 63])
            output.append(ALPHABET[(value ushr 12) and 63])
        } else if (remaining == 2) {
            val value = ((source[index].toInt() and 0xff) shl 16) or
                ((source[index + 1].toInt() and 0xff) shl 8)
            output.append(ALPHABET[(value ushr 18) and 63])
            output.append(ALPHABET[(value ushr 12) and 63])
            output.append(ALPHABET[(value ushr 6) and 63])
        }
        return output.toString()
    }

    fun decode(value: String): ByteArray {
        require('=' !in value && value.length % 4 != 1) { "Invalid base64url encoding" }
        val output = ByteArray(value.length * 6 / 8)
        var accumulator = 0
        var bits = 0
        var outputIndex = 0
        value.forEach { character ->
            val decoded = ALPHABET.indexOf(character)
            require(decoded >= 0) { "Invalid base64url character" }
            accumulator = (accumulator shl 6) or decoded
            bits += 6
            if (bits >= 8) {
                bits -= 8
                output[outputIndex++] = (accumulator ushr bits).toByte()
                accumulator = accumulator and ((1 shl bits) - 1)
            }
        }
        require(bits == 0 || accumulator == 0) { "Non-canonical base64url encoding" }
        val decoded = output.copyOf(outputIndex)
        require(encode(decoded) == value) { "Non-canonical base64url encoding" }
        output.fill(0)
        return decoded
    }
}
