package com.ganj.vpn.composition

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class AndroidTelegramBotAuthFlowVault(context: Context) : TelegramBotAuthFlowStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun restore(): TelegramBotAuthFlow? = runCatching {
        val encoded = prefs.getString(ENTRY, null) ?: return null
        val envelope = Base64.decode(encoded, Base64.NO_WRAP)
        try { decode(decrypt(envelope)) } finally { envelope.fill(0) }
    }.getOrElse {
        prefs.edit().remove(ENTRY).commit()
        null
    }

    override fun save(flow: TelegramBotAuthFlow): Result<Unit> = runCatching {
        val plain = encode(flow)
        val encrypted = try { encrypt(plain) } finally { plain.fill(0) }
        val encoded = try { Base64.encodeToString(encrypted, Base64.NO_WRAP) } finally { encrypted.fill(0) }
        check(prefs.edit().putString(ENTRY, encoded).commit())
    }

    override fun clear(): Result<Unit> = runCatching {
        check(prefs.edit().remove(ENTRY).commit())
    }

    private fun encode(flow: TelegramBotAuthFlow): ByteArray {
        val fields = listOf(flow.state, flow.codeVerifier, flow.redirectUri, flow.expiresAt)
        val encoded = fields.map { it.toByteArray(Charsets.UTF_8) }
        val size = 4 + encoded.sumOf { 4 + it.size }
        return ByteBuffer.allocate(size).apply {
            putInt(encoded.size)
            encoded.forEach { putInt(it.size); put(it) }
        }.array().also { encoded.forEach { bytes -> bytes.fill(0) } }
    }

    private fun decode(bytes: ByteArray): TelegramBotAuthFlow {
        try {
            val buffer = ByteBuffer.wrap(bytes)
            require(buffer.int == 4)
            val fields = (0 until 4).map {
                val size = buffer.int
                require(size in 1..4096 && size <= buffer.remaining())
                ByteArray(size).also(buffer::get).let { raw ->
                    try { raw.toString(Charsets.UTF_8) } finally { raw.fill(0) }
                }
            }
            require(!buffer.hasRemaining())
            return TelegramBotAuthFlow(fields[0], fields[1], fields[2], fields[3])
        } finally {
            bytes.fill(0)
        }
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(plain)
        val iv = cipher.iv
        return ByteArray(2 + iv.size + encrypted.size).also {
            it[0] = 1
            it[1] = iv.size.toByte()
            System.arraycopy(iv, 0, it, 2, iv.size)
            System.arraycopy(encrypted, 0, it, 2 + iv.size, encrypted.size)
            iv.fill(0)
            encrypted.fill(0)
        }
    }

    private fun decrypt(envelope: ByteArray): ByteArray {
        require(envelope.size in 32..32768 && envelope[0].toInt() == 1)
        val ivSize = envelope[1].toInt() and 0xff
        require(ivSize in 12..32 && envelope.size > 2 + ivSize + 16)
        val iv = envelope.copyOfRange(2, 2 + ivSize)
        val encrypted = envelope.copyOfRange(2 + ivSize, envelope.size)
        return try {
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                updateAAD(AAD)
                doFinal(encrypted)
            }
        } finally {
            iv.fill(0)
            encrypted.fill(0)
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFS = "ganj_telegram_bot_auth_flow_v1"
        const val ENTRY = "flow"
        const val KEY_ALIAS = "ganj.telegram.bot.auth.flow.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val AAD = "ganj-telegram-bot-auth-flow-v1".toByteArray(Charsets.US_ASCII)
    }
}
