package com.ganj.vpn.core.controlapi

import com.ganj.vpn.core.vpn.ProvisionedProfile
import com.ganj.vpn.core.vpn.ProvisionedSecurity
import com.ganj.vpn.core.vpn.ProvisionedTransport
import java.nio.charset.StandardCharsets
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Trusted entitlement identity used to authenticate a server-issued connection profile.
 *
 * All six values are part of the AES-GCM associated data. The broker requires byte-for-byte
 * equality for timestamps and canonical equality for identifiers; it never infers or substitutes
 * an identity from decrypted profile material.
 */
data class ProfileProvisioningBinding(
    val profileId: String,
    val userId: String,
    val serviceId: String,
    val deviceId: String,
    val serverId: String,
    val expiresAt: String,
) {
    init {
        requireUuid("profileId", profileId)
        requireUuid("userId", userId)
        requireUuid("serviceId", serviceId)
        requireUuid("deviceId", deviceId)
        requireUuid("serverId", serverId)
        require(parseUtcMillisOrNull(expiresAt) != null) { "expiresAt must be a valid RFC 3339 UTC timestamp" }
    }
}

enum class ProfileProvisioningError {
    LEASE_CONSUMED,
    BINDING_MISMATCH,
    EXPIRED,
    UNSUPPORTED_ALGORITHM,
    MALFORMED_ENVELOPE,
    CRYPTO_UNAVAILABLE,
    AUTHENTICATION_FAILED,
    CRYPTO_FAILURE,
    PAYLOAD_INVALID,
    PAYLOAD_MISMATCH,
    UNSUPPORTED_PROTOCOL,
}

sealed interface ProfileProvisioningResult {
    data class Success(val profile: ProvisionedProfile) : ProfileProvisioningResult
    data class Failure(val error: ProfileProvisioningError) : ProfileProvisioningResult
}

interface ConnectionProfileBroker {
    /** Consumes [lease] regardless of success or failure. A lease can never be replayed. */
    fun provision(
        lease: ConnectionProfileLease,
        binding: ProfileProvisioningBinding,
    ): ProfileProvisioningResult
}

/**
 * A borrowed crypto request. Providers must not retain or mutate any byte array. The broker
 * zeroizes every request buffer immediately after [Gvp1CryptoProvider.decrypt] returns.
 */
class Gvp1DecryptRequest(
    val keyVersion: String,
    val ephemeralPublicKey: ByteArray,
    val nonce: ByteArray,
    val encryptedPayload: ByteArray,
    val authenticationTag: ByteArray,
    val associatedData: ByteArray,
) {
    init {
        require(keyVersion.length in 1..64)
        require(ephemeralPublicKey.size == GVP1_PUBLIC_KEY_BYTES)
        require(nonce.size == GVP1_NONCE_BYTES)
        require(authenticationTag.size == GVP1_TAG_BYTES)
        require(encryptedPayload.isNotEmpty())
        require(associatedData.isNotEmpty())
    }

    override fun toString(): String = "Gvp1DecryptRequest(keyVersion=[REDACTED], material=[REDACTED])"
}

enum class Gvp1CryptoFailureReason {
    PROVIDER_UNAVAILABLE,
    KEY_NOT_FOUND,
    AUTHENTICATION_FAILED,
    INVALID_KEY_MATERIAL,
    INTERNAL_FAILURE,
}

sealed interface Gvp1CryptoResult {
    /** The broker assumes ownership and zeroizes [plaintext] after strict mapping. */
    class Success(val plaintext: ByteArray) : Gvp1CryptoResult {
        override fun toString(): String = "Gvp1CryptoResult.Success([REDACTED])"
    }

    data class Failure(val reason: Gvp1CryptoFailureReason) : Gvp1CryptoResult
}

/**
 * Injection boundary for a device-bound X25519 private key and GVP1 authenticated decryption.
 * Implementations must perform X25519, HKDF-SHA256(info = `ganj-vpn-profile-v1`) and AES-256-GCM.
 */
fun interface Gvp1CryptoProvider {
    fun decrypt(request: Gvp1DecryptRequest): Gvp1CryptoResult
}

/**
 * Safe production default for minSdk 24. No software key, fake key, or downgraded cipher is used
 * when an audited device-bound X25519 provider is absent.
 */
object UnavailableGvp1CryptoProvider : Gvp1CryptoProvider {
    override fun decrypt(request: Gvp1DecryptRequest): Gvp1CryptoResult =
        Gvp1CryptoResult.Failure(Gvp1CryptoFailureReason.PROVIDER_UNAVAILABLE)
}

internal class OneTimeConnectionProfileBroker(
    private val envelopeVault: ConnectionEnvelopeVault,
    private val cryptoProvider: Gvp1CryptoProvider,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : ConnectionProfileBroker {
    override fun provision(
        lease: ConnectionProfileLease,
        binding: ProfileProvisioningBinding,
    ): ProfileProvisioningResult {
        // Taking first makes every path below, including malformed bindings and crypto failures,
        // consume the envelope exactly once.
        val envelope = envelopeVault.take(lease.vaultHandle)
            ?: return failure(ProfileProvisioningError.LEASE_CONSUMED)
        return try {
            provisionEnvelope(lease, binding, envelope)
        } catch (_: RuntimeException) {
            failure(ProfileProvisioningError.PAYLOAD_INVALID)
        } finally {
            envelope.destroy()
        }
    }

    private fun provisionEnvelope(
        lease: ConnectionProfileLease,
        binding: ProfileProvisioningBinding,
        envelope: EncryptedConnectionEnvelope,
    ): ProfileProvisioningResult {
        if (
            lease.profileId != binding.profileId ||
            lease.serverId != binding.serverId ||
            lease.expiresAt != binding.expiresAt ||
            envelope.profileId != binding.profileId ||
            envelope.serverId != binding.serverId ||
            envelope.expiresAt != binding.expiresAt
        ) {
            return failure(ProfileProvisioningError.BINDING_MISMATCH)
        }

        val expiresAtMillis = parseUtcMillisOrNull(binding.expiresAt)
            ?: return failure(ProfileProvisioningError.PAYLOAD_INVALID)
        if (nowEpochMillis() >= expiresAtMillis) return failure(ProfileProvisioningError.EXPIRED)
        if (envelope.algorithm != GVP1_ALGORITHM) {
            return failure(ProfileProvisioningError.UNSUPPORTED_ALGORITHM)
        }
        if (envelope.nonce.size != GVP1_NONCE_BYTES) {
            return failure(ProfileProvisioningError.MALFORMED_ENVELOPE)
        }

        val sealed = envelope.ciphertext
        if (sealed.size <= GVP1_HEADER_BYTES + GVP1_PUBLIC_KEY_BYTES + GVP1_TAG_BYTES) {
            return failure(ProfileProvisioningError.MALFORMED_ENVELOPE)
        }
        if (!sealed.startsWith(GVP1_HEADER)) {
            return failure(ProfileProvisioningError.MALFORMED_ENVELOPE)
        }

        val ephemeralPublicKey = sealed.copyOfRange(GVP1_HEADER_BYTES, GVP1_HEADER_BYTES + GVP1_PUBLIC_KEY_BYTES)
        val encryptedPayload = sealed.copyOfRange(
            GVP1_HEADER_BYTES + GVP1_PUBLIC_KEY_BYTES,
            sealed.size - GVP1_TAG_BYTES,
        )
        val authenticationTag = sealed.copyOfRange(sealed.size - GVP1_TAG_BYTES, sealed.size)
        val nonce = envelope.nonce.copyOf()
        val associatedData = canonicalAssociatedData(binding)
        return try {
            val request = Gvp1DecryptRequest(
                keyVersion = envelope.keyVersion,
                ephemeralPublicKey = ephemeralPublicKey,
                nonce = nonce,
                encryptedPayload = encryptedPayload,
                authenticationTag = authenticationTag,
                associatedData = associatedData,
            )
            val cryptoResult = try {
                cryptoProvider.decrypt(request)
            } catch (_: RuntimeException) {
                Gvp1CryptoResult.Failure(Gvp1CryptoFailureReason.INTERNAL_FAILURE)
            }
            when (cryptoResult) {
                is Gvp1CryptoResult.Failure -> failure(cryptoResult.reason.toProvisioningError())
                is Gvp1CryptoResult.Success -> {
                    try {
                        mapPlaintext(cryptoResult.plaintext, binding, expiresAtMillis)
                    } finally {
                        cryptoResult.plaintext.fill(0)
                    }
                }
            }
        } finally {
            ephemeralPublicKey.fill(0)
            encryptedPayload.fill(0)
            authenticationTag.fill(0)
            nonce.fill(0)
            associatedData.fill(0)
        }
    }

    private fun mapPlaintext(
        plaintext: ByteArray,
        binding: ProfileProvisioningBinding,
        expiresAtMillis: Long,
    ): ProfileProvisioningResult {
        val profile = try {
            StrictProvisionedProfileParser(plaintext).parse()
        } catch (_: ProfilePayloadException) {
            return failure(ProfileProvisioningError.PAYLOAD_INVALID)
        }
        return try {
            if (
                profile.profileId != binding.profileId ||
                profile.serviceId != binding.serviceId ||
                profile.serverId != binding.serverId ||
                profile.deviceId != binding.deviceId ||
                profile.expiresAt != binding.expiresAt
            ) {
                return failure(ProfileProvisioningError.PAYLOAD_MISMATCH)
            }
            if (ManualProfileMaterialGuard.looksLikeImportMaterial(profile.credential)) {
                return failure(ProfileProvisioningError.PAYLOAD_INVALID)
            }
            val protocol = when (profile.protocol) {
                "vless" -> com.ganj.vpn.core.vpn.VpnProtocol.VLESS
                "vmess" -> com.ganj.vpn.core.vpn.VpnProtocol.VMESS
                "trojan" -> com.ganj.vpn.core.vpn.VpnProtocol.TROJAN
                "shadowsocks" -> com.ganj.vpn.core.vpn.VpnProtocol.SHADOWSOCKS
                // WireGuard is not implemented by the Xray runtime profile contract.
                "wireguard" -> return failure(ProfileProvisioningError.UNSUPPORTED_PROTOCOL)
                else -> return failure(ProfileProvisioningError.UNSUPPORTED_PROTOCOL)
            }
            try {
                ProfileProvisioningResult.Success(
                    ProvisionedProfile(
                        profileId = profile.profileId,
                        serviceId = profile.serviceId,
                        serverId = profile.serverId,
                        endpoint = profile.endpoint,
                        port = profile.port,
                        protocol = protocol,
                        credential = profile.credential,
                        transport = profile.transport,
                        security = profile.security,
                        flow = profile.flow,
                        shadowsocksMethod = profile.shadowsocksMethod,
                        expiresAtEpochMillis = expiresAtMillis,
                    ),
                )
            } catch (_: IllegalArgumentException) {
                failure(ProfileProvisioningError.PAYLOAD_INVALID)
            }
        } finally {
            profile.close()
        }
    }

    private fun failure(error: ProfileProvisioningError) = ProfileProvisioningResult.Failure(error)
}

private fun Gvp1CryptoFailureReason.toProvisioningError(): ProfileProvisioningError = when (this) {
    Gvp1CryptoFailureReason.PROVIDER_UNAVAILABLE -> ProfileProvisioningError.CRYPTO_UNAVAILABLE
    Gvp1CryptoFailureReason.AUTHENTICATION_FAILED -> ProfileProvisioningError.AUTHENTICATION_FAILED
    Gvp1CryptoFailureReason.KEY_NOT_FOUND,
    Gvp1CryptoFailureReason.INVALID_KEY_MATERIAL,
    Gvp1CryptoFailureReason.INTERNAL_FAILURE,
    -> ProfileProvisioningError.CRYPTO_FAILURE
}

internal fun canonicalAssociatedData(binding: ProfileProvisioningBinding): ByteArray {
    // Lexicographic key ordering intentionally matches canonical(JSON.stringify(...)) in Node.
    val json = buildString(320) {
        append("{\"deviceId\":\"").append(binding.deviceId)
        append("\",\"expiresAt\":\"").append(binding.expiresAt)
        append("\",\"profileId\":\"").append(binding.profileId)
        append("\",\"serverId\":\"").append(binding.serverId)
        append("\",\"serviceId\":\"").append(binding.serviceId)
        append("\",\"userId\":\"").append(binding.userId).append("\"}")
    }
    return json.toByteArray(StandardCharsets.US_ASCII)
}

private data class ParsedProvisionedProfile(
    val schemaVersion: Int,
    val endpoint: String,
    val port: Int,
    val protocol: String,
    val credential: ByteArray,
    val profileId: String,
    val serviceId: String,
    val serverId: String,
    val deviceId: String,
    val expiresAt: String,
    val transport: ProvisionedTransport,
    val security: ProvisionedSecurity,
    val flow: String?,
    val shadowsocksMethod: String?,
) : AutoCloseable {
    override fun close() {
        credential.fill(0)
    }
}

/** An exact-schema JSON parser that never materializes the credential as an immutable String. */
private class StrictProvisionedProfileParser(private val source: ByteArray) {
    private var index = 0

    fun parse(): ParsedProvisionedProfile {
        val strings = linkedMapOf<String, String>()
        var credential: ByteArray? = null
        var schemaVersion: Int? = null
        var port: Int? = null
        var transport: ProvisionedTransport? = null
        var security: ProvisionedSecurity? = null
        var flow: String? = null
        var shadowsocksMethod: String? = null
        val seen = mutableSetOf<String>()
        try {
            readObject(REQUIRED_PROFILE_FIELDS, seen) { key ->
                when (key) {
                    "schema_version" -> schemaVersion = readUnsignedInteger(maximumDigits = 2)
                    "port" -> port = readPort()
                    "credential" -> credential = readStringBytes()
                    "transport" -> transport = readTransport()
                    "security" -> security = readSecurity()
                    "flow" -> flow = readNullableString()
                    "shadowsocks_method" -> shadowsocksMethod = readNullableString()
                    else -> strings[key] = readString()
                }
            }
            skipWhitespace()
            if (index != source.size || seen != REQUIRED_PROFILE_FIELDS) throw ProfilePayloadException()
            if (schemaVersion != PROFILE_SCHEMA_VERSION) throw ProfilePayloadException()
            val ownedCredential = credential ?: throw ProfilePayloadException()
            credential = null
            return ParsedProvisionedProfile(
                schemaVersion = PROFILE_SCHEMA_VERSION,
                endpoint = strings.getValue("endpoint"),
                port = port ?: throw ProfilePayloadException(),
                protocol = strings.getValue("protocol"),
                credential = ownedCredential,
                profileId = strings.getValue("profile_id"),
                serviceId = strings.getValue("service_id"),
                serverId = strings.getValue("server_id"),
                deviceId = strings.getValue("device_id"),
                expiresAt = strings.getValue("expires_at"),
                transport = transport ?: throw ProfilePayloadException(),
                security = security ?: throw ProfilePayloadException(),
                flow = flow,
                shadowsocksMethod = shadowsocksMethod,
            )
        } catch (error: RuntimeException) {
            if (error is ProfilePayloadException) throw error
            throw ProfilePayloadException()
        } finally {
            credential?.fill(0)
        }
    }

    private fun readTransport(): ProvisionedTransport {
        val seen = mutableSetOf<String>()
        var type: String? = null
        var path: String? = null
        var host: String? = null
        var serviceName: String? = null
        readObject(TRANSPORT_FIELDS, seen) { key ->
            when (key) {
                "type" -> type = readString()
                "path" -> path = readString()
                "host" -> host = readNullableString()
                "service_name" -> serviceName = readString()
            }
        }
        return when (type) {
            "tcp" -> {
                if (seen != TCP_TRANSPORT_FIELDS) throw ProfilePayloadException()
                ProvisionedTransport.Tcp
            }
            "ws" -> {
                if (seen != WS_TRANSPORT_FIELDS) throw ProfilePayloadException()
                ProvisionedTransport.WebSocket(path = path ?: throw ProfilePayloadException(), host = host)
            }
            "grpc" -> {
                if (seen != GRPC_TRANSPORT_FIELDS) throw ProfilePayloadException()
                ProvisionedTransport.Grpc(serviceName = serviceName ?: throw ProfilePayloadException())
            }
            else -> throw ProfilePayloadException()
        }
    }

    private fun readSecurity(): ProvisionedSecurity {
        val seen = mutableSetOf<String>()
        var type: String? = null
        var serverName: String? = null
        var fingerprint: String? = null
        var publicKey: String? = null
        var shortId: String? = null
        var allowInsecure: Boolean? = null
        readObject(SECURITY_FIELDS, seen) { key ->
            when (key) {
                "type" -> type = readString()
                "server_name" -> serverName = readString()
                "fingerprint" -> fingerprint = readString()
                "public_key" -> publicKey = readString()
                "short_id" -> shortId = readString()
                "allow_insecure" -> allowInsecure = readBoolean()
            }
        }
        return when (type) {
            "none" -> {
                if (seen != NONE_SECURITY_FIELDS) throw ProfilePayloadException()
                ProvisionedSecurity.None
            }
            "tls" -> {
                if (seen != TLS_SECURITY_FIELDS || allowInsecure != false) throw ProfilePayloadException()
                ProvisionedSecurity.Tls(
                    serverName = serverName ?: throw ProfilePayloadException(),
                    fingerprint = fingerprint ?: throw ProfilePayloadException(),
                    allowInsecure = false,
                )
            }
            "reality" -> {
                if (seen != REALITY_SECURITY_FIELDS || allowInsecure != false) throw ProfilePayloadException()
                ProvisionedSecurity.Reality(
                    serverName = serverName ?: throw ProfilePayloadException(),
                    fingerprint = fingerprint ?: throw ProfilePayloadException(),
                    publicKey = publicKey ?: throw ProfilePayloadException(),
                    shortId = shortId ?: throw ProfilePayloadException(),
                )
            }
            else -> throw ProfilePayloadException()
        }
    }

    private inline fun readObject(
        allowedFields: Set<String>,
        seen: MutableSet<String>,
        readValue: (String) -> Unit,
    ) {
        skipWhitespace()
        expect('{'.code)
        skipWhitespace()
        if (peek() == '}'.code) {
            index++
            return
        }
        while (true) {
            val key = readString()
            if (key !in allowedFields || !seen.add(key)) throw ProfilePayloadException()
            skipWhitespace()
            expect(':'.code)
            skipWhitespace()
            readValue(key)
            skipWhitespace()
            when (peek()) {
                ','.code -> {
                    index++
                    skipWhitespace()
                }
                '}'.code -> {
                    index++
                    return
                }
                else -> throw ProfilePayloadException()
            }
        }
    }

    private fun readPort(): Int {
        val value = readUnsignedInteger(maximumDigits = 5)
        if (value !in 1..65535) throw ProfilePayloadException()
        return value
    }

    private fun readUnsignedInteger(maximumDigits: Int): Int {
        val start = index
        while (peek() in '0'.code..'9'.code) index++
        if (
            index == start ||
            index - start > maximumDigits ||
            (index - start > 1 && source[start] == '0'.code.toByte())
        ) {
            throw ProfilePayloadException()
        }
        var value = 0
        for (position in start until index) value = value * 10 + (source[position].toInt() - '0'.code)
        return value
    }

    private fun readBoolean(): Boolean = when {
        consumeLiteral("true") -> true
        consumeLiteral("false") -> false
        else -> throw ProfilePayloadException()
    }

    private fun readNullableString(): String? = if (consumeLiteral("null")) null else readString()

    private fun readString(): String {
        val bytes = readStringBytes()
        return try {
            String(bytes, StandardCharsets.UTF_8)
        } finally {
            bytes.fill(0)
        }
    }

    private fun consumeLiteral(literal: String): Boolean {
        if (index + literal.length > source.size) return false
        if (literal.indices.any { source[index + it] != literal[it].code.toByte() }) return false
        index += literal.length
        return true
    }

    private fun readStringBytes(): ByteArray {
        expect('"'.code)
        val output = SecureByteAccumulator()
        try {
            while (index < source.size) {
                val current = source[index].toInt() and 0xff
                when (current) {
                    '"'.code -> {
                        index++
                        return output.copyValue()
                    }
                    '\\'.code -> {
                        index++
                        readEscape(output)
                    }
                    in 0..0x1f -> throw ProfilePayloadException()
                    in 0x20..0x7f -> {
                        output.append(current)
                        index++
                    }
                    else -> copyValidatedUtf8(output)
                }
            }
            throw ProfilePayloadException()
        } finally {
            output.close()
        }
    }

    private fun readEscape(output: SecureByteAccumulator) {
        val escaped = takeByte()
        when (escaped) {
            '"'.code, '\\'.code, '/'.code -> output.append(escaped)
            'b'.code -> output.append(0x08)
            'f'.code -> output.append(0x0c)
            'n'.code -> output.append(0x0a)
            'r'.code -> output.append(0x0d)
            't'.code -> output.append(0x09)
            'u'.code -> {
                val first = readHexUnit()
                val codePoint = when (first) {
                    in 0xd800..0xdbff -> {
                        if (takeByte() != '\\'.code || takeByte() != 'u'.code) throw ProfilePayloadException()
                        val second = readHexUnit()
                        if (second !in 0xdc00..0xdfff) throw ProfilePayloadException()
                        0x10000 + ((first - 0xd800) shl 10) + (second - 0xdc00)
                    }
                    in 0xdc00..0xdfff -> throw ProfilePayloadException()
                    else -> first
                }
                output.appendCodePoint(codePoint)
            }
            else -> throw ProfilePayloadException()
        }
    }

    private fun readHexUnit(): Int {
        var value = 0
        repeat(4) {
            val digit = takeByte()
            value = (value shl 4) or when (digit) {
                in '0'.code..'9'.code -> digit - '0'.code
                in 'a'.code..'f'.code -> digit - 'a'.code + 10
                in 'A'.code..'F'.code -> digit - 'A'.code + 10
                else -> throw ProfilePayloadException()
            }
        }
        return value
    }

    private fun copyValidatedUtf8(output: SecureByteAccumulator) {
        val first = source[index].toInt() and 0xff
        val length = when (first) {
            in 0xc2..0xdf -> 2
            in 0xe0..0xef -> 3
            in 0xf0..0xf4 -> 4
            else -> throw ProfilePayloadException()
        }
        if (index + length > source.size) throw ProfilePayloadException()
        val second = source[index + 1].toInt() and 0xff
        if (second !in 0x80..0xbf) throw ProfilePayloadException()
        if (first == 0xe0 && second < 0xa0 || first == 0xed && second > 0x9f) throw ProfilePayloadException()
        if (first == 0xf0 && second < 0x90 || first == 0xf4 && second > 0x8f) throw ProfilePayloadException()
        for (offset in 2 until length) {
            if ((source[index + offset].toInt() and 0xff) !in 0x80..0xbf) throw ProfilePayloadException()
        }
        repeat(length) { output.append(source[index++].toInt() and 0xff) }
    }

    private fun skipWhitespace() {
        while (peek() == ' '.code || peek() == '\n'.code || peek() == '\r'.code || peek() == '\t'.code) index++
    }

    private fun expect(expected: Int) {
        if (takeByte() != expected) throw ProfilePayloadException()
    }

    private fun takeByte(): Int {
        if (index >= source.size) throw ProfilePayloadException()
        return source[index++].toInt() and 0xff
    }

    private fun peek(): Int = if (index < source.size) source[index].toInt() and 0xff else -1
}

private class SecureByteAccumulator(initialCapacity: Int = 64) : AutoCloseable {
    private var value = ByteArray(initialCapacity)
    private var size = 0

    fun append(byte: Int) {
        ensureCapacity(1)
        value[size++] = byte.toByte()
    }

    fun appendCodePoint(codePoint: Int) {
        when (codePoint) {
            in 0..0x7f -> append(codePoint)
            in 0x80..0x7ff -> {
                append(0xc0 or (codePoint shr 6))
                append(0x80 or (codePoint and 0x3f))
            }
            in 0x800..0xffff -> {
                append(0xe0 or (codePoint shr 12))
                append(0x80 or ((codePoint shr 6) and 0x3f))
                append(0x80 or (codePoint and 0x3f))
            }
            in 0x10000..0x10ffff -> {
                append(0xf0 or (codePoint shr 18))
                append(0x80 or ((codePoint shr 12) and 0x3f))
                append(0x80 or ((codePoint shr 6) and 0x3f))
                append(0x80 or (codePoint and 0x3f))
            }
            else -> throw ProfilePayloadException()
        }
    }

    fun copyValue(): ByteArray = value.copyOf(size)

    private fun ensureCapacity(additional: Int) {
        if (size + additional <= value.size) return
        val previous = value
        value = previous.copyOf((previous.size * 2).coerceAtMost(MAX_PROFILE_PLAINTEXT_BYTES))
        previous.fill(0)
        if (size + additional > value.size) throw ProfilePayloadException()
    }

    override fun close() {
        value.fill(0)
        size = 0
    }
}

private object ManualProfileMaterialGuard {
    private val forbidden = listOf(
        "vless://", "vmess://", "trojan://", "ss://", "shadowsocks://", "wireguard://",
        "wg://", "http://", "https://", "data:", "qr:", "\"outbounds\"", "[interface]", "[peer]",
    ).map { it.toByteArray(StandardCharsets.US_ASCII) }

    fun looksLikeImportMaterial(value: ByteArray): Boolean = forbidden.any { needle ->
        if (needle.size > value.size) return@any false
        (0..value.size - needle.size).any { start ->
            needle.indices.all { offset -> value[start + offset].asciiLowercase() == needle[offset] }
        }
    }

    private fun Byte.asciiLowercase(): Byte = if (this.toInt() in 'A'.code..'Z'.code) {
        (toInt() + ('a'.code - 'A'.code)).toByte()
    } else {
        this
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun parseUtcMillisOrNull(value: String): Long? {
    val match = UTC_TIMESTAMP_PARTS.matchEntire(value) ?: return null
    val fraction = match.groupValues[2].padEnd(3, '0').take(3)
    val normalized = "${match.groupValues[1]}.${fraction}Z"
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val position = ParsePosition(0)
    val parsed = formatter.parse(normalized, position) ?: return null
    return parsed.time.takeIf { position.index == normalized.length }
}

private class ProfilePayloadException : RuntimeException()

private const val GVP1_ALGORITHM = "X25519+HKDF-SHA256+AES-256-GCM/GVP1"
private const val GVP1_HEADER_BYTES = 4
private const val GVP1_PUBLIC_KEY_BYTES = 32
private const val GVP1_NONCE_BYTES = 12
private const val GVP1_TAG_BYTES = 16
private const val MAX_PROFILE_PLAINTEXT_BYTES = 65_536
private const val PROFILE_SCHEMA_VERSION = 1
private val GVP1_HEADER = byteArrayOf('G'.code.toByte(), 'V'.code.toByte(), 'P'.code.toByte(), '1'.code.toByte())
private val UTC_TIMESTAMP_PARTS = Regex(
    "^([0-9]{4}-(?:0[1-9]|1[0-2])-(?:[0-2][0-9]|3[01])T(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9])(?:\\.([0-9]{1,9}))?Z$",
)
private val REQUIRED_PROFILE_FIELDS = setOf(
    "schema_version",
    "endpoint",
    "port",
    "protocol",
    "credential",
    "profile_id",
    "service_id",
    "server_id",
    "device_id",
    "expires_at",
    "transport",
    "security",
    "flow",
    "shadowsocks_method",
)
private val TRANSPORT_FIELDS = setOf("type", "path", "host", "service_name")
private val TCP_TRANSPORT_FIELDS = setOf("type")
private val WS_TRANSPORT_FIELDS = setOf("type", "path", "host")
private val GRPC_TRANSPORT_FIELDS = setOf("type", "service_name")
private val SECURITY_FIELDS = setOf(
    "type",
    "server_name",
    "fingerprint",
    "public_key",
    "short_id",
    "allow_insecure",
)
private val NONE_SECURITY_FIELDS = setOf("type")
private val TLS_SECURITY_FIELDS = setOf("type", "server_name", "fingerprint", "allow_insecure")
private val REALITY_SECURITY_FIELDS = setOf(
    "type",
    "server_name",
    "fingerprint",
    "public_key",
    "short_id",
    "allow_insecure",
)
