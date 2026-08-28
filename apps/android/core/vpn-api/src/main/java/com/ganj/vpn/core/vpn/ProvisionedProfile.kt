package com.ganj.vpn.core.vpn

import java.io.Closeable
import java.net.IDN
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

sealed interface ProvisionedTransport {
    data object Tcp : ProvisionedTransport
    data class WebSocket(val path: String, val host: String? = null) : ProvisionedTransport
    data class Grpc(val serviceName: String) : ProvisionedTransport
}

sealed interface ProvisionedSecurity {
    data object None : ProvisionedSecurity
    data class Tls(
        val serverName: String,
        val fingerprint: String = "chrome",
        val allowInsecure: Boolean = false,
    ) : ProvisionedSecurity

    data class Reality(
        val serverName: String,
        val publicKey: String,
        val shortId: String,
        val fingerprint: String = "chrome",
    ) : ProvisionedSecurity
}

/**
 * A short-lived, server-provisioned profile. This type is deliberately not serializable and its
 * secret never appears in [toString]. The app has no parser or constructor for user-supplied URIs.
 */
class ProvisionedProfile(
    val profileId: String,
    val serviceId: String,
    val serverId: String,
    val endpoint: String,
    val port: Int,
    val protocol: VpnProtocol,
    credential: ByteArray,
    val transport: ProvisionedTransport = ProvisionedTransport.Tcp,
    val security: ProvisionedSecurity = ProvisionedSecurity.None,
    val flow: String? = null,
    val shadowsocksMethod: String? = null,
    val expiresAtEpochMillis: Long,
) : Closeable {
    private val secret = credential.copyOf()

    init {
        try {
            require(profileId.isCanonicalUuid())
            require(serviceId.isCanonicalUuid())
            require(serverId.isCanonicalUuid())
            require(endpoint.isSafeHost())
            require(port in 1..65535)
            require(secret.size in 8..4096)
            require(expiresAtEpochMillis > 0)
            require(protocol != VpnProtocol.SHADOWSOCKS || shadowsocksMethod in ALLOWED_SS_METHODS)
            require(flow == null || protocol == VpnProtocol.VLESS && flow in ALLOWED_VLESS_FLOWS)
            validateCredential(protocol, secret, shadowsocksMethod)
            validateTransport(transport)
            validateSecurity(security)
            validateProtocolCompatibility(protocol, transport, security, flow)
        } catch (error: Throwable) {
            secret.fill(0)
            throw error
        }
    }

    /**
     * Gives the audited runtime a short-lived view of the server-issued credential.
     * There is intentionally no credential getter, serializer, parser, or share-link import surface.
     */
    @JvmSynthetic
    fun <T> useCredential(block: (String) -> T): T {
        check(secret.any { it.toInt() != 0 }) { "Provisioned profile was already destroyed" }
        val value = secret.toString(Charsets.UTF_8)
        return block(value)
    }

    /** Internal byte-level view used only by the sealed recovery codec in this module. */
    internal fun <T> useCredentialBytes(block: (ByteArray) -> T): T {
        check(secret.any { it.toInt() != 0 }) { "Provisioned profile was already destroyed" }
        val copy = secret.copyOf()
        return try {
            block(copy)
        } finally {
            copy.fill(0)
        }
    }

    fun isExpired(nowEpochMillis: Long): Boolean = nowEpochMillis >= expiresAtEpochMillis

    override fun close() {
        secret.fill(0)
    }

    override fun toString(): String =
        "ProvisionedProfile(profileId=$profileId, serviceId=$serviceId, serverId=$serverId, material=[REDACTED])"

    companion object {
        private val ALLOWED_SS_METHODS = setOf(
            "2022-blake3-aes-128-gcm",
            "2022-blake3-aes-256-gcm",
            "aes-128-gcm",
            "aes-256-gcm",
            "chacha20-poly1305",
            "xchacha20-poly1305",
        )
        private val ALLOWED_VLESS_FLOWS = setOf("xtls-rprx-vision")
    }
}

private fun validateCredential(protocol: VpnProtocol, value: ByteArray, shadowsocksMethod: String?) {
    when (protocol) {
        VpnProtocol.VLESS,
        VpnProtocol.VMESS,
        -> require(value.toString(Charsets.US_ASCII).isCanonicalUuid()) {
            "VLESS and VMess credentials must be canonical UUIDs"
        }
        VpnProtocol.TROJAN -> require(value.size in 8..256 && value.isStrictUtf8Secret())
        VpnProtocol.SHADOWSOCKS -> {
            val minimum = if (shadowsocksMethod?.startsWith("2022-") == true) 16 else 8
            require(value.size in minimum..256 && value.isStrictUtf8Secret())
        }
    }
}

private fun validateTransport(value: ProvisionedTransport) {
    when (value) {
        ProvisionedTransport.Tcp -> Unit
        is ProvisionedTransport.WebSocket -> {
            require(value.path.startsWith('/') && value.path.length <= 2048)
            value.host?.let { require(it.isSafeHost()) }
        }
        is ProvisionedTransport.Grpc -> require(
            value.serviceName.length in 1..256 &&
                value.serviceName.matches(Regex("^[A-Za-z0-9._/-]+$")),
        )
    }
}

private fun validateSecurity(value: ProvisionedSecurity) {
    when (value) {
        ProvisionedSecurity.None -> Unit
        is ProvisionedSecurity.Tls -> {
            require(value.serverName.isSafeHost())
            require(!value.allowInsecure) { "TLS certificate verification cannot be disabled" }
            require(value.fingerprint in setOf("chrome", "firefox", "safari", "ios", "android", "randomized"))
        }
        is ProvisionedSecurity.Reality -> {
            require(value.serverName.isSafeHost())
            require(value.publicKey.matches(Regex("^[A-Za-z0-9_-]{43}$")))
            require(value.shortId.matches(Regex("^(?:[a-fA-F0-9]{2}){0,8}$")))
            require(value.fingerprint in setOf("chrome", "firefox", "safari", "ios", "android", "randomized"))
        }
    }
}

private fun validateProtocolCompatibility(
    protocol: VpnProtocol,
    transport: ProvisionedTransport,
    security: ProvisionedSecurity,
    flow: String?,
) {
    require(security !is ProvisionedSecurity.Reality || protocol == VpnProtocol.VLESS) {
        "REALITY is supported only for VLESS"
    }
    require(protocol != VpnProtocol.TROJAN || security is ProvisionedSecurity.Tls) {
        "Trojan requires authenticated TLS"
    }
    if (flow != null) {
        require(transport == ProvisionedTransport.Tcp) { "VLESS flow requires TCP transport" }
        require(security is ProvisionedSecurity.Tls || security is ProvisionedSecurity.Reality) {
            "VLESS flow requires authenticated transport security"
        }
    }
}

private fun String.isCanonicalUuid(): Boolean =
    matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"))

private fun String.isSafeHost(): Boolean {
    if (length !in 1..253 || contains('/') || contains('@') || contains(':')) return false
    if (matches(Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$"))) {
        return split('.').all { it.toIntOrNull() in 0..255 }
    }
    return runCatching {
        val ascii = IDN.toASCII(this, IDN.USE_STD3_ASCII_RULES)
        ascii.length <= 253 && ascii.split('.').all { label ->
            label.length in 1..63 &&
                label.matches(Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?$"))
        }
    }.getOrDefault(false)
}

private fun ByteArray.isStrictUtf8Secret(): Boolean {
    if (any { it == 0.toByte() }) return false
    return runCatching {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(this))
            .all { !it.isISOControl() }
    }.getOrDefault(false)
}
