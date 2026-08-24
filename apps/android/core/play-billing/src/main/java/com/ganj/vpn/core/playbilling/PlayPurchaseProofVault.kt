package com.ganj.vpn.core.playbilling

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.ganj.vpn.core.billing.ProviderPurchaseReference
import com.ganj.vpn.core.billing.PurchaseProofHandle
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Decrypted proof with bounded lifetime and a permanently redacted string representation. */
class SensitivePlayPurchaseProof internal constructor(private val bytes: ByteArray) : AutoCloseable {
    private var closed = false

    /**
     * Provides the UTF-8 Play purchase token to the authenticated backend transport only.
     * The array is owned by this object and is zeroed when the enclosing `useProof` call returns.
     */
    @Synchronized
    fun <T> useBytes(block: (ByteArray) -> T): T {
        check(!closed) { "Purchase proof is closed" }
        return block(bytes)
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            bytes.fill(0)
            closed = true
        }
    }

    override fun toString(): String = "SensitivePlayPurchaseProof([REDACTED])"

    internal fun fingerprint(): String = useBytes { value ->
        java.security.MessageDigest.getInstance("SHA-256").digest(value).toLowerHex()
    }
}

/** Backend-verifier boundary. Handles are safe to persist; resolved proof is not. */
interface PlayPurchaseProofResolver {
    fun <T> useProof(handle: PurchaseProofHandle, block: (SensitivePlayPurchaseProof) -> T): T
    fun delete(handle: PurchaseProofHandle)
}

internal interface PlayPurchaseProofStore : PlayPurchaseProofResolver {
    fun store(
        purchaseReference: ProviderPurchaseReference,
        token: PlayPurchaseToken,
    ): PurchaseProofHandle
}

class PlayPurchaseProofUnavailableException internal constructor() :
    IllegalStateException("Encrypted Play purchase proof is unavailable")

/**
 * AES-256-GCM proof vault backed by Android Keystore and app-private SharedPreferences.
 *
 * The SDK token is encrypted immediately and only a random, opaque [PurchaseProofHandle] leaves
 * this module. Token values are never used as preference keys, logs, exceptions, analytics fields,
 * purchase references or equality values.
 */
class AndroidKeystorePurchaseProofVault(
    context: Context,
) : PlayPurchaseProofResolver {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val lock = Any()
    private val random = SecureRandom()

    init {
        purgeExpiredProofs()
    }

    internal fun store(
        purchaseReference: ProviderPurchaseReference,
        token: PlayPurchaseToken,
    ): PurchaseProofHandle {
        purgeExpiredProofs()
        return synchronized(lock) {
            val indexKey = indexKey(purchaseReference)
            preferences.getString(indexKey, null)?.let { existingReference ->
                if (preferences.contains(proofKey(existingReference))) {
                    return@synchronized PurchaseProofHandle.fromSecureVault(existingReference)
                }
            }

            val handleReference = "gp.proof.${randomHex(16)}"
            val handle = PurchaseProofHandle.fromSecureVault(handleReference)
            val encrypted = token.use { raw -> encrypt(raw, handleReference) }
            val encoded = try {
                "${System.currentTimeMillis()}:" +
                    Base64.encodeToString(encrypted, Base64.NO_WRAP)
            } finally {
                encrypted.fill(0)
            }
            if (!preferences.edit()
                    .putString(proofKey(handleReference), encoded)
                    .putString(indexKey, handleReference)
                    .commit()
            ) {
                throw PlayPurchaseProofUnavailableException()
            }
            handle
        }
    }

    override fun <T> useProof(
        handle: PurchaseProofHandle,
        block: (SensitivePlayPurchaseProof) -> T,
    ): T {
        val proof = synchronized(lock) {
            val encoded = preferences.getString(proofKey(handle.reference), null)
                ?: throw PlayPurchaseProofUnavailableException()
            val separator = encoded.indexOf(':')
            if (separator <= 0 || separator == encoded.lastIndex) {
                throw PlayPurchaseProofUnavailableException()
            }
            val encrypted = try {
                Base64.decode(encoded.substring(separator + 1), Base64.NO_WRAP)
            } catch (_: IllegalArgumentException) {
                throw PlayPurchaseProofUnavailableException()
            }
            val decrypted = try {
                decrypt(encrypted, handle.reference)
            } catch (_: Exception) {
                throw PlayPurchaseProofUnavailableException()
            } finally {
                encrypted.fill(0)
            }
            SensitivePlayPurchaseProof(decrypted)
        }
        try {
            return block(proof)
        } finally {
            proof.close()
        }
    }

    override fun delete(handle: PurchaseProofHandle) {
        synchronized(lock) {
            val editor = preferences.edit().remove(proofKey(handle.reference))
            preferences.all.forEach { (key, value) ->
                if (key.startsWith(INDEX_PREFIX) && value == handle.reference) editor.remove(key)
            }
            editor.commit()
        }
    }

    private fun purgeExpiredProofs(nowEpochMillis: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val expiredHandles = preferences.all.mapNotNull { (key, value) ->
                if (!key.startsWith(PROOF_PREFIX) || value !is String) return@mapNotNull null
                val createdAt = value.substringBefore(':').toLongOrNull() ?: return@mapNotNull key
                key.takeIf { nowEpochMillis - createdAt !in 0..MAX_PROOF_AGE_MILLIS }
            }.toSet()
            if (expiredHandles.isEmpty()) return
            val expiredReferences = expiredHandles.map { it.removePrefix(PROOF_PREFIX) }.toSet()
            val editor = preferences.edit()
            expiredHandles.forEach(editor::remove)
            preferences.all.forEach { (key, value) ->
                if (key.startsWith(INDEX_PREFIX) && value in expiredReferences) editor.remove(key)
            }
            editor.commit()
        }
    }

    private fun encrypt(raw: String, handleReference: String): ByteArray {
        val plaintext = raw.toByteArray(Charsets.UTF_8)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(handleReference.toByteArray(Charsets.UTF_8))
            val ciphertext = cipher.doFinal(plaintext)
            ByteBuffer.allocate(1 + 1 + cipher.iv.size + ciphertext.size)
                .put(FORMAT_VERSION)
                .put(cipher.iv.size.toByte())
                .put(cipher.iv)
                .put(ciphertext)
                .array()
                .also { ciphertext.fill(0) }
        } catch (_: Exception) {
            throw PlayPurchaseProofUnavailableException()
        } finally {
            plaintext.fill(0)
        }
    }

    private fun decrypt(encrypted: ByteArray, handleReference: String): ByteArray {
        val buffer = ByteBuffer.wrap(encrypted)
        if (buffer.remaining() < 3 || buffer.get() != FORMAT_VERSION) {
            throw PlayPurchaseProofUnavailableException()
        }
        val ivSize = buffer.get().toInt() and 0xff
        if (ivSize !in 12..32 || buffer.remaining() <= ivSize) {
            throw PlayPurchaseProofUnavailableException()
        }
        val iv = ByteArray(ivSize).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.updateAAD(handleReference.toByteArray(Charsets.UTF_8))
            cipher.doFinal(ciphertext)
        } finally {
            iv.fill(0)
            ciphertext.fill(0)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun randomHex(byteCount: Int): String = ByteArray(byteCount)
        .also(random::nextBytes)
        .toLowerHex()

    private fun indexKey(reference: ProviderPurchaseReference): String = "$INDEX_PREFIX${reference.value}"
    private fun proofKey(reference: String): String = "$PROOF_PREFIX$reference"

    private companion object {
        const val PREFERENCES_NAME = "ganj_play_billing_proofs_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ganj.play.billing.proof.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
        const val INDEX_PREFIX = "index."
        const val PROOF_PREFIX = "proof."
        const val MAX_PROOF_AGE_MILLIS = 7 * 24 * 60 * 60 * 1_000L
    }
}

internal class AndroidPlayPurchaseProofStore(
    private val vault: AndroidKeystorePurchaseProofVault,
) : PlayPurchaseProofStore {
    override fun store(
        purchaseReference: ProviderPurchaseReference,
        token: PlayPurchaseToken,
    ): PurchaseProofHandle = vault.store(purchaseReference, token)

    override fun <T> useProof(
        handle: PurchaseProofHandle,
        block: (SensitivePlayPurchaseProof) -> T,
    ): T = vault.useProof(handle, block)

    override fun delete(handle: PurchaseProofHandle) = vault.delete(handle)
}
