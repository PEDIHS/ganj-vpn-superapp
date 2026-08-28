package com.ganj.vpn.core.vpn

enum class VpnProtocol {
    VLESS,
    VMESS,
    TROJAN,
    SHADOWSOCKS,
}

enum class ConnectionPhase {
    DISCONNECTED,
    PREPARING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTING,
    ERROR,
}

data class ConnectionState(
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val serverId: String? = null,
    val connectedAtEpochMillis: Long? = null,
    val bytesUp: Long = 0,
    val bytesDown: Long = 0,
    val errorCode: String? = null,
)

class ConnectionRequest(val profile: ProvisionedProfile) {
    override fun toString(): String = "ConnectionRequest(profile=[REDACTED])"
}

interface VpnEngine {
    fun currentState(): ConnectionState
    fun connect(request: ConnectionRequest): Result<Unit>

    /**
     * Restart the encrypted core over the already-established VPN TUN. Implementations must not
     * close the TUN during a recoverable network handover, avoiding a direct-routing bypass window.
     */
    fun reconnect(request: ConnectionRequest): Result<Unit>

    fun disconnect(): Result<Unit>
}

data class ServerProbe(
    val serverId: String,
    val latencyMs: Int,
    val packetLossPercent: Double,
    val loadPercent: Double,
    val regionDistanceScore: Double,
    val measuredMbps: Double,
    val available: Boolean = true,
)

data class RankedServer(
    val serverId: String,
    val score: Double,
)

class SmartConnectRanker(
    private val latencyWeight: Double = 0.35,
    private val lossWeight: Double = 0.25,
    private val loadWeight: Double = 0.15,
    private val regionWeight: Double = 0.10,
    private val speedWeight: Double = 0.15,
) {
    fun rank(probes: List<ServerProbe>): List<RankedServer> = probes
        .asSequence()
        .filter { it.available }
        .map { probe ->
            val latency = 1.0 - (probe.latencyMs.coerceIn(0, 800) / 800.0)
            val loss = 1.0 - (probe.packetLossPercent.coerceIn(0.0, 100.0) / 100.0)
            val load = 1.0 - (probe.loadPercent.coerceIn(0.0, 100.0) / 100.0)
            val region = 1.0 - probe.regionDistanceScore.coerceIn(0.0, 1.0)
            val speed = probe.measuredMbps.coerceIn(0.0, 500.0) / 500.0
            RankedServer(
                serverId = probe.serverId,
                score = latency * latencyWeight +
                    loss * lossWeight +
                    load * loadWeight +
                    region * regionWeight +
                    speed * speedWeight,
            )
        }
        .sortedWith(compareByDescending<RankedServer> { it.score }.thenBy { it.serverId })
        .toList()
}
