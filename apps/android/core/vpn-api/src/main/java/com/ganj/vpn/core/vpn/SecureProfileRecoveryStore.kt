package com.ganj.vpn.core.vpn

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persists only an already validated, server-provisioned profile for Android process recovery.
 *
 * The envelope is authenticated with an Android Keystore key, is excluded from backup with the
 * application, and is destroyed together with its key on an explicit disconnect or VPN revocation.
 * There is deliberately no String, URI, file, QR, clipboard, or exported-Intent import surface.
 */
class SecureProfileRecoveryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun save(profile: ProvisionedProfile): Result<Unit> {
        val plaintext = runCatching { ProvisionedProfileRecoveryCodec.encode(profile) }
            .getOrElse { return Result.failure(it) }
        var encrypted: ByteArray? = null
        var envelope: ByteArray? = null
        return try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(ASSOCIATED_DATA)
            encrypted = cipher.doFinal(plaintext)
            val iv = cipher.iv
            check(iv.size == GCM_IV_BYTES)
            envelope = ByteArray(2 + iv.size + encrypted.size)
            envelope[0] = ENVELOPE_VERSION
            envelope[1] = iv.size.toByte()
            iv.copyInto(envelope, destinationOffset = 2)
            encrypted.copyInto(envelope, destinationOffset = 2 + iv.size)
            val value = Base64.encodeToString(envelope, Base64.NO_WRAP)
            check(preferences.edit().putString(PROFILE_PREFERENCE, value).commit()) {
                "vpn.profile_recovery_write_failed"
            }
            Result.success(Unit)
        } catch (error: Exception) {
            clear()
            Result.failure(IllegalStateException("vpn.profile_recovery_write_failed", error))
        } finally {
            plaintext.fill(0)
            encrypted?.fill(0)
            envelope?.fill(0)
        }
    }

    @Synchronized
    fun restore(nowEpochMillis: Long = System.currentTimeMillis()): Result<ProvisionedProfile?> {
        val encoded = preferences.getString(PROFILE_PREFERENCE, null)
            ?: return Result.success(null)
        if (encoded.length !in 1..MAXIMUM_ENVELOPE_CHARACTERS) {
            clear()
            return Result.failure(IllegalStateException("vpn.profile_recovery_invalid"))
        }
        var envelope: ByteArray? = null
        var plaintext: ByteArray? = null
        return try {
            envelope = Base64.decode(encoded, Base64.NO_WRAP)
            require(envelope.size > 2 + GCM_IV_BYTES + GCM_TAG_BYTES)
            require(envelope[0] == ENVELOPE_VERSION)
            val ivSize = envelope[1].toInt() and 0xff
            require(ivSize == GCM_IV_BYTES)
            val iv = envelope.copyOfRange(2, 2 + ivSize)
            val ciphertext = envelope.copyOfRange(2 + ivSize, envelope.size)
            try {
                val cipher = Cipher.getInstance(CIPHER)
                cipher.init(Cipher.DECRYPT_MODE, existingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.updateAAD(ASSOCIATED_DATA)
                plaintext = cipher.doFinal(ciphertext)
            } finally {
                iv.fill(0)
                ciphertext.fill(0)
            }
            val profile = ProvisionedProfileRecoveryCodec.decode(requireNotNull(plaintext))
            if (profile.isExpired(nowEpochMillis)) {
                profile.close()
                clear()
                Result.success(null)
            } else {
                Result.success(profile)
            }
        } catch (error: Exception) {
            clear()
            Result.failure(IllegalStateException("vpn.profile_recovery_invalid", error))
        } finally {
            envelope?.fill(0)
            plaintext?.fill(0)
        }
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(PROFILE_PREFERENCE).commit()
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getOrCreateKey(): SecretKey = runCatching { existingKey() }.getOrElse {
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
                .setUserAuthenticationRequired(false)
                .build(),
        )
        generator.generateKey()
    }

    private fun existingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return requireNotNull(keyStore.getKey(KEY_ALIAS, null) as? SecretKey) {
            "vpn.profile_recovery_key_missing"
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "ganj_vpn_recovery"
        const val PROFILE_PREFERENCE = "sealed_server_profile"
        const val KEY_ALIAS = "ganj.vpn.profile.recovery.v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val CIPHER = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        const val MAXIMUM_ENVELOPE_CHARACTERS = 32_768
        val ENVELOPE_VERSION: Byte = 1
        val ASSOCIATED_DATA = "com.ganj.vpn/server-profile-recovery/v1"
            .toByteArray(StandardCharsets.US_ASCII)
    }
}

internal object ProvisionedProfileRecoveryCodec {
    private const val MAGIC = 0x47565052
    private const val VERSION = 1
    private const val MAXIMUM_PAYLOAD_BYTES = 16_384
    private const val MAXIMUM_ID_BYTES = 64
    private const val MAXIMUM_HOST_BYTES = 253
    private const val MAXIMUM_CREDENTIAL_BYTES = 4_096
    private const val MAXIMUM_OPTION_BYTES = 2_048

    fun encode(profile: ProvisionedProfile): ByteArray {
        val writer = RecoveryWriter(MAXIMUM_PAYLOAD_BYTES)
        return try {
            writer.int(MAGIC)
            writer.int(VERSION)
            writer.string(profile.profileId, MAXIMUM_ID_BYTES)
            writer.string(profile.serviceId, MAXIMUM_ID_BYTES)
            writer.string(profile.serverId, MAXIMUM_ID_BYTES)
            writer.string(profile.endpoint, MAXIMUM_HOST_BYTES)
            writer.int(profile.port)
            writer.string(profile.protocol.name, 32)
            profile.useCredentialBytes { writer.bytes(it, MAXIMUM_CREDENTIAL_BYTES) }
            when (val transport = profile.transport) {
                ProvisionedTransport.Tcp -> writer.byte(TRANSPORT_TCP)
                is ProvisionedTransport.WebSocket -> {
                    writer.byte(TRANSPORT_WEBSOCKET)
                    writer.string(transport.path, MAXIMUM_OPTION_BYTES)
                    writer.nullableString(transport.host, MAXIMUM_HOST_BYTES)
                }
                is ProvisionedTransport.Grpc -> {
                    writer.byte(TRANSPORT_GRPC)
                    writer.string(transport.serviceName, 256)
                }
            }
            when (val security = profile.security) {
                ProvisionedSecurity.None -> writer.byte(SECURITY_NONE)
                is ProvisionedSecurity.Tls -> {
                    writer.byte(SECURITY_TLS)
                    writer.string(security.serverName, MAXIMUM_HOST_BYTES)
                    writer.string(security.fingerprint, 32)
                    writer.byte(if (security.allowInsecure) 1 else 0)
                }
                is ProvisionedSecurity.Reality -> {
                    writer.byte(SECURITY_REALITY)
                    writer.string(security.serverName, MAXIMUM_HOST_BYTES)
                    writer.string(security.publicKey, 64)
                    writer.string(security.shortId, 16)
                    writer.string(security.fingerprint, 32)
                }
            }
            writer.nullableString(profile.flow, 64)
            writer.nullableString(profile.shadowsocksMethod, 64)
            writer.long(profile.expiresAtEpochMillis)
            writer.finish()
        } finally {
            writer.close()
        }
    }

    fun decode(payload: ByteArray): ProvisionedProfile {
        require(payload.size in 1..MAXIMUM_PAYLOAD_BYTES)
        val reader = RecoveryReader(payload)
        require(reader.int() == MAGIC)
        require(reader.int() == VERSION)
        val profileId = reader.string(MAXIMUM_ID_BYTES)
        val serviceId = reader.string(MAXIMUM_ID_BYTES)
        val serverId = reader.string(MAXIMUM_ID_BYTES)
        val endpoint = reader.string(MAXIMUM_HOST_BYTES)
        val port = reader.int()
        val protocol = VpnProtocol.valueOf(reader.string(32))
        val credential = reader.bytes(MAXIMUM_CREDENTIAL_BYTES)
        return try {
            val transport = when (reader.byte()) {
                TRANSPORT_TCP -> ProvisionedTransport.Tcp
                TRANSPORT_WEBSOCKET -> ProvisionedTransport.WebSocket(
                    path = reader.string(MAXIMUM_OPTION_BYTES),
                    host = reader.nullableString(MAXIMUM_HOST_BYTES),
                )
                TRANSPORT_GRPC -> ProvisionedTransport.Grpc(reader.string(256))
                else -> error("Unsupported recovery transport")
            }
            val security = when (reader.byte()) {
                SECURITY_NONE -> ProvisionedSecurity.None
                SECURITY_TLS -> ProvisionedSecurity.Tls(
                    serverName = reader.string(MAXIMUM_HOST_BYTES),
                    fingerprint = reader.string(32),
                    allowInsecure = when (reader.byte()) {
                        0 -> false
                        1 -> true
                        else -> error("Invalid TLS validation flag")
                    },
                )
                SECURITY_REALITY -> ProvisionedSecurity.Reality(
                    serverName = reader.string(MAXIMUM_HOST_BYTES),
                    publicKey = reader.string(64),
                    shortId = reader.string(16),
                    fingerprint = reader.string(32),
                )
                else -> error("Unsupported recovery security")
            }
            val flow = reader.nullableString(64)
            val shadowsocksMethod = reader.nullableString(64)
            val expiresAt = reader.long()
            require(reader.exhausted())
            ProvisionedProfile(
                profileId = profileId,
                serviceId = serviceId,
                serverId = serverId,
                endpoint = endpoint,
                port = port,
                protocol = protocol,
                credential = credential,
                transport = transport,
                security = security,
                flow = flow,
                shadowsocksMethod = shadowsocksMethod,
                expiresAtEpochMillis = expiresAt,
            )
        } finally {
            credential.fill(0)
        }
    }

    private const val TRANSPORT_TCP = 1
    private const val TRANSPORT_WEBSOCKET = 2
    private const val TRANSPORT_GRPC = 3
    private const val SECURITY_NONE = 1
    private const val SECURITY_TLS = 2
    private const val SECURITY_REALITY = 3
}

private class RecoveryWriter(capacity: Int) : AutoCloseable {
    private val destination = ByteArray(capacity)
    private var position = 0
    private var finished = false

    fun byte(value: Int) {
        require(value in 0..255)
        ensure(1)
        destination[position++] = value.toByte()
    }

    fun int(value: Int) {
        ensure(Int.SIZE_BYTES)
        ByteBuffer.wrap(destination, position, Int.SIZE_BYTES).putInt(value)
        position += Int.SIZE_BYTES
    }

    fun long(value: Long) {
        ensure(Long.SIZE_BYTES)
        ByteBuffer.wrap(destination, position, Long.SIZE_BYTES).putLong(value)
        position += Long.SIZE_BYTES
    }

    fun string(value: String, maximumBytes: Int) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        try {
            bytes(bytes, maximumBytes)
        } finally {
            bytes.fill(0)
        }
    }

    fun nullableString(value: String?, maximumBytes: Int) {
        byte(if (value == null) 0 else 1)
        if (value != null) string(value, maximumBytes)
    }

    fun bytes(value: ByteArray, maximumBytes: Int) {
        require(value.size in 0..maximumBytes)
        int(value.size)
        ensure(value.size)
        value.copyInto(destination, destinationOffset = position)
        position += value.size
    }

    fun finish(): ByteArray {
        check(!finished)
        finished = true
        return destination.copyOf(position)
    }

    override fun close() {
        destination.fill(0)
        position = 0
    }

    private fun ensure(byteCount: Int) {
        check(!finished)
        require(byteCount >= 0 && position <= destination.size - byteCount) {
            "Recovery profile exceeds maximum size"
        }
    }
}

private class RecoveryReader(private val source: ByteArray) {
    private var position = 0

    fun byte(): Int {
        ensure(1)
        return source[position++].toInt() and 0xff
    }

    fun int(): Int {
        ensure(Int.SIZE_BYTES)
        val value = ByteBuffer.wrap(source, position, Int.SIZE_BYTES).int
        position += Int.SIZE_BYTES
        return value
    }

    fun long(): Long {
        ensure(Long.SIZE_BYTES)
        val value = ByteBuffer.wrap(source, position, Long.SIZE_BYTES).long
        position += Long.SIZE_BYTES
        return value
    }

    fun string(maximumBytes: Int): String {
        val bytes = bytes(maximumBytes)
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } finally {
            bytes.fill(0)
        }
    }

    fun nullableString(maximumBytes: Int): String? = when (byte()) {
        0 -> null
        1 -> string(maximumBytes)
        else -> error("Invalid nullable field")
    }

    fun bytes(maximumBytes: Int): ByteArray {
        val size = int()
        require(size in 0..maximumBytes)
        ensure(size)
        return source.copyOfRange(position, position + size).also { position += size }
    }

    fun exhausted(): Boolean = position == source.size

    private fun ensure(byteCount: Int) {
        require(byteCount >= 0 && position <= source.size - byteCount) {
            "Truncated recovery profile"
        }
    }
}
