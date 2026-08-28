package com.ganj.vpn.core.controlapi

import java.util.UUID

enum class SubscriptionTier { FREE, PREMIUM, VIP }
enum class PurchaseChannel { PLAY, DIRECT, WALLET }
enum class ServiceStatus { PENDING, ACTIVE, DISABLED, EXPIRED, REVOKED }
enum class ServerStatus { ACTIVE, BUSY, MAINTENANCE }
enum class VpnProtocol { VLESS, VMESS, TROJAN, SHADOWSOCKS, WIREGUARD }

data class Money(
    val amountMinor: Long,
    val currency: String,
) {
    init {
        require(amountMinor >= 0)
        require(currency.matches(Regex("^[A-Z]{3}$")))
    }
}

data class CatalogProduct(
    val id: String,
    val code: String,
    val name: String,
    val tier: SubscriptionTier,
    val durationDays: Int?,
    val trafficLimitBytes: Long?,
    val deviceLimit: Int,
    val features: List<String>,
    val price: Money,
)

data class UserService(
    val id: String,
    val name: String,
    val status: ServiceStatus,
    val tier: SubscriptionTier,
    val countryCode: String?,
    val trafficLimitBytes: Long?,
    val trafficUsedBytes: Long,
    val expiresAt: String?,
    val deviceLimit: Int,
    val allowedProtocols: Set<VpnProtocol>,
)

/**
 * UI-safe server catalog entry. It intentionally cannot carry endpoint, port, credential,
 * subscription URL, Xray JSON, or any other reusable VPN configuration material.
 */
data class ManagedServer(
    val id: String,
    val code: String,
    val name: String,
    val countryCode: String,
    val city: String?,
    val tier: SubscriptionTier,
    val status: ServerStatus,
    val loadRatio: Double,
    val latencyHintMs: Int?,
    val favorite: Boolean,
    val protocols: Set<VpnProtocol>,
) {
    init {
        requireUuid("id", id)
        require(code.length in 1..128)
        require(name.length in 1..200)
        require(countryCode.matches(Regex("^[A-Z]{2}$")))
        require(city == null || city.length in 1..200)
        require(loadRatio in 0.0..1.0)
        require(latencyHintMs == null || latencyHintMs >= 0)
        require(protocols.isNotEmpty())
    }
}

data class CheckoutCommand(
    val planId: String,
    val channel: PurchaseChannel,
    val idempotencyKey: String,
    val serviceId: String? = null,
) {
    init {
        requireUuid("planId", planId)
        serviceId?.let { requireUuid("serviceId", it) }
        require(idempotencyKey.length in 16..255)
        require(idempotencyKey.matches(Regex("^[A-Za-z0-9._~:-]+$")))
    }
}

enum class OrderStatus { PENDING, AUTHORIZED, PAID, FULFILLED, CANCELLED, REFUNDED, FAILED }

data class CheckoutOrder(
    val id: String,
    val status: OrderStatus,
    val channel: PurchaseChannel,
    val total: Money,
    val entitlementServiceId: String?,
)

/**
 * The only supported profile issuance input. There is intentionally no URI, QR, file, clipboard,
 * subscription-link, host, port, credential, or arbitrary configuration field.
 */
data class ConnectionProfileCommand(
    val serviceId: String,
    val deviceId: String,
    val serverId: String,
    val clientNonce: String,
    val deviceProof: String,
) {
    init {
        requireUuid("serviceId", serviceId)
        requireUuid("deviceId", deviceId)
        requireUuid("serverId", serverId)
        requireOpaqueProof("clientNonce", clientNonce)
        requireOpaqueProof("deviceProof", deviceProof)
    }
}

/** UI-safe result. Encrypted profile material is retained inside the repository-owned vault. */
class ConnectionProfileLease internal constructor(
    val profileId: String,
    val serverId: String,
    val expiresAt: String,
    internal val vaultHandle: String,
) {
    override fun toString(): String =
        "ConnectionProfileLease(profileId=$profileId, serverId=$serverId, expiresAt=$expiresAt, material=[OPAQUE])"
}

internal data class EncryptedConnectionEnvelope(
    val profileId: String,
    val serverId: String,
    val algorithm: String,
    val keyVersion: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val expiresAt: String,
) {
    fun destroy() {
        nonce.fill(0)
        ciphertext.fill(0)
    }

    override fun toString(): String = "EncryptedConnectionEnvelope([REDACTED])"
}

internal fun requireUuid(field: String, value: String) {
    require(value.length == 36 && UUID.fromString(value).toString() == value.lowercase()) {
        "$field must be a canonical UUID"
    }
}

private fun requireOpaqueProof(field: String, value: String) {
    require(value.length in 32..8192) { "$field length is invalid" }
    require(value.matches(Regex("^[A-Za-z0-9._~+/=:-]+$"))) { "$field format is invalid" }
    require(!ManualConfigurationGuard.looksLikeManualConfiguration(value)) {
        "$field cannot contain manual configuration material"
    }
}

internal object ManualConfigurationGuard {
    private val forbiddenPrefixes = listOf(
        "vless://", "vmess://", "trojan://", "ss://", "shadowsocks://",
        "wireguard://", "wg://", "http://", "https://", "data:", "qr:",
    )

    fun looksLikeManualConfiguration(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return forbiddenPrefixes.any { normalized.contains(it) } ||
            normalized.contains("\"outbounds\"") ||
            normalized.contains("\"inbounds\"") ||
            normalized.contains("[interface]") ||
            normalized.contains("[peer]")
    }
}