package com.ganj.vpn.composition

/**
 * Security boundary invoked whenever the durable application session is invalidated.
 * Implementations must not throw: authentication cleanup must complete even when a secondary
 * cleanup action is unavailable.
 */
internal fun interface SessionRevocationSink {
    fun onSessionInvalidated()

    companion object {
        val NoOp = SessionRevocationSink { }
    }
}
