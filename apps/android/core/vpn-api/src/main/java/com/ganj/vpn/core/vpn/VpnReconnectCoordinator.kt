package com.ganj.vpn.core.vpn

/** A reconnect attempt bound to one observed network epoch. */
data class VpnReconnectPlan(
    val epoch: Long,
    val attempt: Int,
    val delayMillis: Long,
)

/**
 * Pure thread-safe policy for cancellation-safe reconnect loops. A newer network epoch invalidates
 * every plan created for an older network so stale callbacks cannot restart the tunnel later.
 */
class VpnReconnectCoordinator(
    private val backoff: VpnReconnectBackoff = VpnReconnectBackoff(),
    private val maximumAttempts: Int = 8,
) {
    private var epoch = 0L
    private var attempts = 0

    init {
        require(maximumAttempts in 1..32)
    }

    @Synchronized
    fun beginEpoch(): Long {
        epoch = if (epoch == Long.MAX_VALUE) 1L else epoch + 1L
        attempts = 0
        return epoch
    }

    @Synchronized
    fun nextPlan(forEpoch: Long): VpnReconnectPlan? {
        if (forEpoch != epoch || attempts >= maximumAttempts) return null
        val attempt = attempts++
        return VpnReconnectPlan(
            epoch = epoch,
            attempt = attempt,
            delayMillis = backoff.delayMillis(attempt),
        )
    }

    @Synchronized
    fun isCurrent(forEpoch: Long): Boolean = forEpoch == epoch

    @Synchronized
    fun markConnected(forEpoch: Long) {
        if (forEpoch == epoch) attempts = 0
    }

    @Synchronized
    fun invalidate() {
        epoch = if (epoch == Long.MAX_VALUE) 1L else epoch + 1L
        attempts = 0
    }
}
