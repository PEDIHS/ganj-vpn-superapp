package com.ganj.vpn.enterprise

enum class EnterpriseFeatureKey {
    BUG_REPORTS,
    SUPPORT_DIAGNOSTICS,
}

data class FeatureAssignment(
    val key: EnterpriseFeatureKey,
    val enabled: Boolean,
    val variant: String,
    val configVersion: Long,
) {
    init {
        require(variant.matches(Regex("^[a-z0-9._-]{1,64}$")))
        require(configVersion > 0)
    }
}

/** Repository output after signature verification and allowlisted value mapping. */
data class VerifiedRuntimeConfiguration(
    val version: Long,
    val expiresAtEpochMillis: Long,
    val maintenance: Boolean,
    val maintenanceMessage: String?,
    val assignments: List<FeatureAssignment>,
    val signatureVerified: Boolean,
) {
    init {
        require(version > 0)
        require(expiresAtEpochMillis > 0)
        require(signatureVerified) { "Unsigned runtime configuration is forbidden" }
        require(maintenanceMessage == null || maintenanceMessage.length <= 500)
        require(assignments.distinctBy { it.key }.size == assignments.size)
    }

    fun enabled(key: EnterpriseFeatureKey): Boolean =
        assignments.singleOrNull { it.key == key }?.enabled == true
}

enum class UpdateRequirement { NONE, OPTIONAL, FORCED }
enum class ReleaseChannel { STABLE, BETA }

/** Store links/signatures remain repository-owned and are intentionally absent from UI models. */
data class VerifiedUpdatePolicy(
    val channel: ReleaseChannel,
    val currentVersion: String,
    val minimumVersion: String,
    val latestVersion: String,
    val requirement: UpdateRequirement,
    val reasonCode: String?,
    val signatureVerified: Boolean,
) {
    init {
        require(signatureVerified) { "Unsigned update policy is forbidden" }
        require(listOf(currentVersion, minimumVersion, latestVersion).all { it.matches(VERSION) })
        require(reasonCode == null || reasonCode.matches(Regex("^[a-z0-9._-]{1,64}$")))
    }

    private companion object {
        val VERSION = Regex("^[0-9]+(?:\\.[0-9]+){1,3}(?:-[a-z0-9.-]+)?$")
    }
}

enum class BugCategory { CONNECTION, PURCHASE, ACCOUNT, UI, PERFORMANCE, SECURITY, OTHER }
enum class BugSeverity { CRITICAL, HIGH, MEDIUM, LOW }
enum class BugStatus { NEW, INVESTIGATING, ASSIGNED, FIXING, TESTING, RELEASED, CLOSED }
enum class SafeConnectionState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, UNKNOWN }
enum class SafeNetworkType { WIFI, CELLULAR, ETHERNET, UNKNOWN }
enum class DeviceClass { PHONE, TABLET, TV, UNKNOWN }

data class PrivacySafeDeviceContext(
    val deviceId: String,
    val appVersion: String,
    val osMajorVersion: String,
    val deviceClass: DeviceClass,
    val connectionState: SafeConnectionState,
    val networkType: SafeNetworkType,
    val serverId: String?,
    val errorCode: String?,
) {
    init {
        requireCanonicalUuid(deviceId)
        serverId?.let(::requireCanonicalUuid)
        require(appVersion.length in 1..32)
        require(osMajorVersion.length in 1..32)
        require(errorCode == null || errorCode.matches(Regex("^[A-Za-z0-9._-]{1,64}$")))
    }
}

data class CreateBugReportCommand(
    val clientReportId: String,
    val title: String,
    val description: String,
    val category: BugCategory,
    val occurredAtEpochMillis: Long,
    val automaticContextConsent: Boolean,
    val context: PrivacySafeDeviceContext,
) {
    init {
        requireCanonicalUuid(clientReportId)
        require(title.length in 3..200)
        require(description.length in 3..12_000)
        require(occurredAtEpochMillis > 0)
        require(automaticContextConsent) { "Automatic context requires explicit consent" }
        PrivacyTextPolicy.requireSafe(title)
        PrivacyTextPolicy.requireSafe(description)
    }
}

data class BugReportReceipt(
    val id: String,
    val publicCode: String,
    val severity: BugSeverity,
    val status: BugStatus,
) {
    init {
        requireCanonicalUuid(id)
        require(publicCode.matches(Regex("^[A-Za-z0-9-]{3,32}$")))
    }
}

enum class DiagnosticKind {
    NETWORK,
    DNS_RESOLVER,
    SERVER_PING,
    PACKET_LOSS,
    VPN_STATE,
    DEVICE_INTEGRITY,
}

enum class DiagnosticOutcome { PASSED, WARNING, FAILED, UNAVAILABLE }

data class PrivacySafeDiagnosticTest(
    val kind: DiagnosticKind,
    val outcome: DiagnosticOutcome,
    val latencyMillis: Long? = null,
    val packetLossRatio: Double? = null,
    val resultCode: String? = null,
) {
    init {
        require(latencyMillis == null || latencyMillis in 0..120_000)
        require(packetLossRatio == null || packetLossRatio in 0.0..1.0)
        require(resultCode == null || resultCode.matches(Regex("^[A-Za-z0-9._-]{1,64}$")))
    }
}

data class CreateDiagnosticReportCommand(
    val clientReportId: String,
    val deviceId: String,
    val explicitConsent: Boolean,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long,
    val tests: List<PrivacySafeDiagnosticTest>,
) {
    init {
        requireCanonicalUuid(clientReportId)
        requireCanonicalUuid(deviceId)
        require(explicitConsent) { "Diagnostic upload requires explicit consent" }
        require(startedAtEpochMillis > 0 && completedAtEpochMillis >= startedAtEpochMillis)
        require(tests.size in 1..50)
        require(tests.distinctBy { it.kind }.size == tests.size)
    }
}

data class DiagnosticReportReceipt(
    val id: String,
    val redactionVersion: String,
    val expiresAtEpochMillis: Long,
) {
    init {
        requireCanonicalUuid(id)
        require(redactionVersion.matches(Regex("^[A-Za-z0-9._-]{1,32}$")))
        require(expiresAtEpochMillis > 0)
    }
}

internal object PrivacyTextPolicy {
    private val forbidden = listOf(
        "vless://", "vmess://", "trojan://", "ss://", "shadowsocks://", "wireguard://",
        "wg://", "http://", "https://", "qr:", "data:", "\"outbounds\"", "\"inbounds\"",
        "[interface]", "[peer]",
        "authorization: bearer", "-----begin", "private_key", "privatekey", "password=",
        "password:", "token=", "token:", "access_token", "refresh_token", "purchase_token",
    )
    private val jwt = Regex("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b")

    fun requireSafe(value: String) {
        val normalized = value.lowercase()
        require(value.none { it == '\u0000' || (it.code < 0x20 && it !in "\n\r\t") })
        require(forbidden.none(normalized::contains)) {
            "Report text contains configuration or credential material"
        }
        require(!jwt.containsMatchIn(value)) { "Report text contains token material" }
    }
}

internal fun requireCanonicalUuid(value: String) {
    require(value.length == 36 && runCatching { java.util.UUID.fromString(value).toString() }.getOrNull() == value.lowercase())
}
