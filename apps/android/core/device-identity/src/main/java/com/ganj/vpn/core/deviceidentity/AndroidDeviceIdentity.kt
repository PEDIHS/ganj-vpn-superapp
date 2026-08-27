package com.ganj.vpn.core.deviceidentity

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import com.ganj.vpn.core.controlapi.Gvp1CryptoFailureReason
import com.ganj.vpn.core.controlapi.Gvp1CryptoResult
import com.ganj.vpn.core.controlapi.Gvp1DecryptRequest
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters

class AndroidDeviceIdentity private constructor(
    context: Context,
    private val random: SecureRandom,
) : DeviceIdentity {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val lock = Any()

    override fun publicIdentity(): Result<DevicePublicIdentity> = runCatching {
        synchronized(lock) { ensureMaterial() }
    }

    override fun sign(request: DeviceProofRequest): Result<SignedDeviceProof> = runCatching {
        synchronized(lock) {
            val identity = ensureMaterial()
            val canonical = DeviceProofCanonicalizer.canonicalBytes(request, identity.keyVersion)
            val derSignature = try {
                val privateKey = keyStore.getKey(SIGNING_ALIAS, null) as? PrivateKey
                    ?: throw IllegalStateException("Device signing key is unavailable")
                Signature.getInstance("SHA256withECDSA").run {
                    initSign(privateKey, random)
                    update(canonical)
                    sign()
                }
            } finally {
                canonical.fill(0)
            }
            val joseSignature = try {
                derEcdsaToJose(derSignature)
            } finally {
                derSignature.fill(0)
            }
            try {
                SignedDeviceProof(
                    algorithm = "ES256",
                    keyVersion = identity.keyVersion,
                    timestampEpochSeconds = request.timestampEpochSeconds,
                    nonce = request.nonce,
                    bodySha256 = request.bodySha256,
                    signature = Base64Url.encode(joseSignature),
                )
            } finally {
                joseSignature.fill(0)
            }
        }
    }

    override fun rotate(): Result<DevicePublicIdentity> = runCatching {
        synchronized(lock) {
            val nextVersion = preferences.getInt(KEY_VERSION_PREFERENCE, 1)
                .coerceAtLeast(1)
                .let { current ->
                    check(current < MAXIMUM_KEY_VERSION) { "Device key version exhausted" }
                    current + 1
                }
            resetMaterial(nextVersion)
            ensureMaterial()
        }
    }

    override fun decrypt(request: Gvp1DecryptRequest): Gvp1CryptoResult {
        val privateKey = try {
            synchronized(lock) {
                val identity = ensureMaterial()
                if (request.keyVersion != identity.keyVersion) {
                    return Gvp1CryptoResult.Failure(Gvp1CryptoFailureReason.KEY_NOT_FOUND)
                }
                unwrapEncryptionPrivateKey()
            }
        } catch (_: SecurityException) {
            return Gvp1CryptoResult.Failure(Gvp1CryptoFailureReason.PROVIDER_UNAVAILABLE)
        } catch (_: RuntimeException) {
            return Gvp1CryptoResult.Failure(Gvp1CryptoFailureReason.INTERNAL_FAILURE)
        }
        return try {
            Gvp1Decryptor.decrypt(privateKey, request)
        } finally {
            privateKey.fill(0)
        }
    }

    private fun ensureMaterial(): DevicePublicIdentity {
        val installationId = ensureInstallationId()
        var version = preferences.getInt(KEY_VERSION_PREFERENCE, 1).coerceAtLeast(1)
        val initialized = preferences.getBoolean(MATERIAL_INITIALIZED_PREFERENCE, false)

        if (!initialized) {
            resetMaterial(version)
            generateMaterial()
        } else if (!validateExistingMaterial()) {
            check(version < MAXIMUM_KEY_VERSION) { "Device key version exhausted" }
            version += 1
            resetMaterial(version)
            generateMaterial()
        }

        val certificate = keyStore.getCertificate(SIGNING_ALIAS)
            ?: throw IllegalStateException("Device signing certificate is unavailable")
        val encryptionPublicKey = Base64Url.decode(
            preferences.getString(ENCRYPTION_PUBLIC_PREFERENCE, null)
                ?: throw IllegalStateException("Device encryption key is unavailable"),
        )
        return try {
            DevicePublicIdentity(
                installationId = installationId,
                keyVersion = "v$version",
                signingAlgorithm = "ES256",
                signingPublicKeySpki = Base64Url.encode(certificate.publicKey.encoded),
                encryptionAlgorithm = "X25519",
                encryptionPublicKeyRaw = Base64Url.encode(encryptionPublicKey),
                signingKeyHardwareBacked = isSigningKeyHardwareBacked(),
            )
        } finally {
            encryptionPublicKey.fill(0)
        }
    }

    private fun generateMaterial() {
        generateSigningKey()
        ensureWrappingKey()
        generateEncryptionKey()
        check(
            preferences.edit()
                .putBoolean(MATERIAL_INITIALIZED_PREFERENCE, true)
                .commit(),
        ) { "Device identity metadata could not be committed" }
    }

    private fun validateExistingMaterial(): Boolean = runCatching {
        if (!keyStore.containsAlias(SIGNING_ALIAS) || !keyStore.containsAlias(WRAPPING_ALIAS)) return false
        val expectedPublic = Base64Url.decode(
            preferences.getString(ENCRYPTION_PUBLIC_PREFERENCE, null) ?: return false,
        )
        val privateKey = unwrapEncryptionPrivateKey()
        try {
            val actualPublic = X25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded
            try {
                MessageDigest.isEqual(expectedPublic, actualPublic)
            } finally {
                actualPublic.fill(0)
            }
        } finally {
            expectedPublic.fill(0)
            privateKey.fill(0)
        }
    }.getOrDefault(false)

    private fun ensureInstallationId(): String {
        preferences.getString(INSTALLATION_ID_PREFERENCE, null)?.let { existing ->
            if (INSTALLATION_ID_PATTERN.matches(existing)) return existing
        }
        val created = UUID.randomUUID().toString()
        check(
            preferences.edit().putString(INSTALLATION_ID_PREFERENCE, created).commit(),
        ) { "Installation identity could not be committed" }
        return created
    }

    private fun resetMaterial(version: Int) {
        runCatching { keyStore.deleteEntry(SIGNING_ALIAS) }
        runCatching { keyStore.deleteEntry(WRAPPING_ALIAS) }
        check(
            preferences.edit()
                .putInt(KEY_VERSION_PREFERENCE, version)
                .putBoolean(MATERIAL_INITIALIZED_PREFERENCE, false)
                .remove(ENCRYPTION_PUBLIC_PREFERENCE)
                .remove(ENCRYPTION_WRAPPED_PRIVATE_PREFERENCE)
                .remove(ENCRYPTION_WRAP_IV_PREFERENCE)
                .commit(),
        ) { "Device identity reset could not be committed" }
    }

    private fun generateSigningKey() {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(
                SIGNING_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        generator.generateKeyPair()
    }

    private fun ensureWrappingKey(): SecretKey {
        (keyStore.getKey(WRAPPING_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                WRAPPING_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private fun generateEncryptionKey() {
        val privateKey = X25519PrivateKeyParameters(random)
        val privateBytes = privateKey.encoded
        val publicBytes = privateKey.generatePublicKey().encoded
        val cipher = Cipher.getInstance(AES_GCM)
        val wrapped: ByteArray
        val iv: ByteArray
        try {
            cipher.init(Cipher.ENCRYPT_MODE, ensureWrappingKey(), random)
            wrapped = cipher.doFinal(privateBytes)
            iv = cipher.iv.copyOf()
        } finally {
            privateBytes.fill(0)
        }
        try {
            check(iv.size == GCM_IV_BYTES)
            check(
                preferences.edit()
                    .putString(ENCRYPTION_PUBLIC_PREFERENCE, Base64Url.encode(publicBytes))
                    .putString(ENCRYPTION_WRAPPED_PRIVATE_PREFERENCE, Base64Url.encode(wrapped))
                    .putString(ENCRYPTION_WRAP_IV_PREFERENCE, Base64Url.encode(iv))
                    .commit(),
            ) { "Encrypted device key could not be committed" }
        } finally {
            publicBytes.fill(0)
            wrapped.fill(0)
            iv.fill(0)
        }
    }

    private fun unwrapEncryptionPrivateKey(): ByteArray {
        val wrapped = Base64Url.decode(
            preferences.getString(ENCRYPTION_WRAPPED_PRIVATE_PREFERENCE, null)
                ?: throw IllegalStateException("Wrapped encryption key is unavailable"),
        )
        val iv = Base64Url.decode(
            preferences.getString(ENCRYPTION_WRAP_IV_PREFERENCE, null)
                ?: throw IllegalStateException("Encryption key IV is unavailable"),
        )
        return try {
            require(iv.size == GCM_IV_BYTES)
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, ensureWrappingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(wrapped).also { require(it.size == X25519_KEY_BYTES) }
        } finally {
            wrapped.fill(0)
            iv.fill(0)
        }
    }

    private fun isSigningKeyHardwareBacked(): Boolean = runCatching {
        val privateKey = keyStore.getKey(SIGNING_ALIAS, null) as PrivateKey
        val factory = KeyFactory.getInstance(privateKey.algorithm, ANDROID_KEYSTORE)
        factory.getKeySpec(privateKey, KeyInfo::class.java).isInsideSecureHardware
    }.getOrDefault(false)

    companion object {
        fun create(context: Context): AndroidDeviceIdentity =
            AndroidDeviceIdentity(context.applicationContext, SecureRandom())

        private const val PREFERENCES_NAME = "ganj_device_identity_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val SIGNING_ALIAS = "ganj.device.signing.v1"
        private const val WRAPPING_ALIAS = "ganj.device.encryption.wrap.v1"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
        private const val X25519_KEY_BYTES = 32
        private const val MAXIMUM_KEY_VERSION = 999_999_999
        private const val INSTALLATION_ID_PREFERENCE = "installation_id"
        private const val KEY_VERSION_PREFERENCE = "key_version"
        private const val MATERIAL_INITIALIZED_PREFERENCE = "material_initialized"
        private const val ENCRYPTION_PUBLIC_PREFERENCE = "encryption_public"
        private const val ENCRYPTION_WRAPPED_PRIVATE_PREFERENCE = "encryption_wrapped_private"
        private const val ENCRYPTION_WRAP_IV_PREFERENCE = "encryption_wrap_iv"
        private val INSTALLATION_ID_PATTERN =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}

internal object Gvp1Decryptor {
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val TAG_BYTES = 16
    private val HKDF_INFO = "ganj-vpn-profile-v1".toByteArray(StandardCharsets.US_ASCII)

    fun decrypt(
        recipientPrivateKey: ByteArray,
        request: Gvp1DecryptRequest,
    ): Gvp1CryptoResult {
        if (recipientPrivateKey.size != 32) {
            return Gvp1CryptoResult.Failure(Gvp1CryptoFailureReason.INVALID_KEY_MATERIAL)
        }
        val sharedSecret = ByteArray(32)
        val contentKey = ByteArray(32)
        val salt = MessageDigest.getInstance("SHA-256").digest(request.associatedData)
        val sealedPayload = ByteArray(request.encryptedPayload.size + TAG_BYTES)
        return try {
            val privateKey = X25519PrivateKeyParameters(recipientPrivateKey, 0)
            val publicKey = X25519PublicKeyParameters(request.ephemeralPublicKey, 0)
            privateKey.generateSecret(publicKey, sharedSecret, 0)
            HkdfSha256.derive(sharedSecret, salt, HKDF_INFO, contentKey)
            request.encryptedPayload.copyInto(sealedPayload)
            request.authenticationTag.copyInto(sealedPayload, request.encryptedPayload.size)
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(contentKey, "AES"),
                GCMParameterSpec(128, request.nonce),
            )
            cipher.updateAAD(request.associatedData)
            Gvp1CryptoResult.Success(cipher.doFinal(sealedPayload))
        } catch (_: AEADBadTagException) {
            Gvp1CryptoResult.Failure(Gvp1CryptoFailureReason.AUTHENTICATION_FAILED)
        } catch (_: IllegalArgumentException) {
            Gvp1CryptoResult.Failure(Gvp1CryptoFailureReason.INVALID_KEY_MATERIAL)
        } catch (_: IllegalStateException) {
            Gvp1CryptoResult.Failure(Gvp1CryptoFailureReason.INVALID_KEY_MATERIAL)
        } catch (_: Exception) {
            Gvp1CryptoResult.Failure(Gvp1CryptoFailureReason.INTERNAL_FAILURE)
        } finally {
            sharedSecret.fill(0)
            contentKey.fill(0)
            salt.fill(0)
            sealedPayload.fill(0)
        }
    }
}

internal object HkdfSha256 {
    private const val HASH_BYTES = 32

    fun derive(inputKeyMaterial: ByteArray, salt: ByteArray, info: ByteArray, output: ByteArray) {
        require(output.size in 1..HASH_BYTES)
        require(salt.isNotEmpty())
        val pseudoRandomKey = hmac(salt, inputKeyMaterial)
        val blockInput = ByteArray(info.size + 1)
        val block: ByteArray
        try {
            info.copyInto(blockInput)
            blockInput[blockInput.lastIndex] = 1
            block = hmac(pseudoRandomKey, blockInput)
            block.copyInto(output, endIndex = output.size)
            block.fill(0)
        } finally {
            pseudoRandomKey.fill(0)
            blockInput.fill(0)
        }
    }

    private fun hmac(key: ByteArray, value: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(value)
        }
}

private fun derEcdsaToJose(der: ByteArray): ByteArray {
    require(der.size in 68..72 && der[0].toInt() == 0x30) { "Invalid ECDSA signature" }
    var index = 1
    val sequenceLength = der[index++].toInt() and 0xff
    require(sequenceLength == der.size - index)
    require(der[index++].toInt() == 0x02)
    val rLength = der[index++].toInt() and 0xff
    require(rLength in 32..33 && index + rLength < der.size)
    val rOffset = index
    index += rLength
    require(der[index++].toInt() == 0x02)
    val sLength = der[index++].toInt() and 0xff
    require(sLength in 32..33 && index + sLength == der.size)
    val output = ByteArray(64)
    copyUnsignedInteger(der, rOffset, rLength, output, 0)
    copyUnsignedInteger(der, index, sLength, output, 32)
    return output
}

private fun copyUnsignedInteger(
    source: ByteArray,
    sourceOffset: Int,
    sourceLength: Int,
    destination: ByteArray,
    destinationOffset: Int,
) {
    val trimmedOffset = sourceOffset + if (sourceLength == 33) 1 else 0
    val trimmedLength = sourceLength - if (sourceLength == 33) 1 else 0
    require(trimmedLength <= 32)
    source.copyInto(
        destination,
        destinationOffset + 32 - trimmedLength,
        trimmedOffset,
        trimmedOffset + trimmedLength,
    )
}
