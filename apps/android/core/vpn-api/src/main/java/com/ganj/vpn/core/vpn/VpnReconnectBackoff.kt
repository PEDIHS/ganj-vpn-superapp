package com.ganj.vpn.core.vpn

/**
 * Bounded exponential retry policy for underlying-network changes and process recovery.
 *
 * Attempts are zero based. The maximum delay prevents retry storms while still allowing an
 * always-on service to recover after a long network outage.
 */
class VpnReconnectBackoff(
    private val initialDelayMillis: Long = 1_000,
    private val maximumDelayMillis: Long = 30_000,
) {
    init {
        require(initialDelayMillis in 1..maximumDelayMillis)
        require(maximumDelayMillis <= 5 * 60_000)
    }

    fun delayMillis(attempt: Int): Long {
        require(attempt >= 0)
        var delay = initialDelayMillis
        repeat(attempt.coerceAtMost(MAXIMUM_EXPONENT)) {
            delay = (delay * 2).coerceAtMost(maximumDelayMillis)
        }
        return delay
    }

    private companion object {
        const val MAXIMUM_EXPONENT = 30
    }
}
