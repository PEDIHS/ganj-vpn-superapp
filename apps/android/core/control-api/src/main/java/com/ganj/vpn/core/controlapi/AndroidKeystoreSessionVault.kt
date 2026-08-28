package com.ganj.vpn.core.controlapi

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreSessionVault(
    context: Context,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : AuthTokenProvider {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val lock = Any()

    @Volatile
    private var loaded = false

    @Volatile
    private var cached: AuthSessionCredentials? = null

    override fun currentAccessToken(): AccessToken? = restore()?.accessToken

    fun currentUserId(): String? = restore()?.userId

    fun currentDeviceId(): String? = restore()?.deviceId

    fun currentRefreshToken(): RefreshToken? = restore()?.refreshToken

    fun restore(): AuthSessionCredentials? {
        if (loaded) return cached
        return synchronized(lock) {
            if (loaded) return@synchronized cached
            cached = runCatching {
                val encoded = preferences.getString(ENTRY, null) ?: return@runCatching null
                val envelope = Base64.decode(encoded, Base64.NO_WRAP)
                try {
                    decrypt(envelope)
                } finally {
                    envelope.fill(0)
                }
            }.getOrElse {
                preferences.edit().remove(ENTRY).commit()
                null
            }
            loaded = true
            cached
        }
    }

    /**
     * Persists the new rotated credentials before exposing them through AuthTokenProvider.
     * A failed durable write drops the in-memory session instead of continuing with a refresh token
     * that the server may already have consumed.
     */
    fun save(session: AuthSessionCredentials): Result<Unit> = synchronized(lock) {
        runCatching {
            val payload = SessionVaultCodec.encode(session)
            val envelope = try {
                encrypt(payload)
            } finally {
                payload.fill(0)
            }
            val encoded = try {
                Base64.encodeToString(envelope, Base64.NO_WRAP)
            } finally {
                envelope.fill(0)
            }
            check(preferences.edit().putString(ENTRY, encoded).commit()) {
                "Session credential persistence failed"
            }
            cached = session
            loaded = true
        }.onFailure {
            cached = null
            loaded = true
        }
    }

    fun clear(): Result<Unit> = synchronized(lock) {
        runCatching {
            check(preferences.edit().remove(ENTRY).commit()) { "Session credential deletion failed" }
            cached = null
            loaded = true
        }.onFailure {
            // Never continue serving a cached token after a logout/clear failure.
            cached = null
            loaded = true
        }
    }

    private fun encrypt(payload: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(AAD)
        val ciphertext = cipher.doFinal(payload)
        val iv = cipher.iv
        require(iv.size in 12..32)
        return ByteArray(2 + iv.size + ciphertext.size).also { envelope ->
            envelope[0] = ENVELOPE_VERSION
            envelope[1] = iv.size.toByte()
            System.arraycopy(iv, 0, envelope, 2, iv.size)
            System.arraycopy(ciphertext, 0, envelope, 2 + iv.size, ciphertext.size)
            ciphertext.fill(0)
            iv.fill(0)
        }
    }

    private fun decrypt(envelope: ByteArray): AuthSessionCredentials {
        require(envelope.size in MIN_ENVELOPE_BYTES..MAX_ENVELOPE_BYTES)
        require(envelope[0] == ENVELOPE_VERSION)
        val ivSize = envelope[1].toInt() and 0xff
        require(ivSize in 12..32)
        require(envelope.size > 2 + ivSize + 16)
        val iv = envelope.copyOfRange(2, 2 + ivSize)
        val ciphertext = envelope.copyOfRange(2 + ivSize, envelope.size)
        val plaintext = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            cipher.updateAAD(AAD)
            cipher.doFinal(ciphertext)
        } catch (error: AEADBadTagException) {
            throw IllegalStateException("Session vault authentication failed", error)
        } finally {
            iv.fill(0)
            ciphertext.fill(0)
        }
        return try {
            SessionVaultCodec.decode(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun toString(): String = "AndroidKeystoreSessionVault([REDACTED])"

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_KEY_ALIAS = "ganj.auth.session.aes.v1"
        const val PREFERENCES = "ganj_auth_session_v1"
        const val ENTRY = "encrypted_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val AAD = "GANJ-AUTH-SESSION-VAULT-V1".toByteArray(Charsets.US_ASCII)
        val ENVELOPE_VERSION: Byte = 1
        const val MIN_ENVELOPE_BYTES = 2 + 12 + 16 + 1
        const val MAX_ENVELOPE_BYTES = 40_000
    }
}
