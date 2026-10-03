package com.ganj.vpn.presentation

import com.ganj.vpn.core.vpn.ConnectionPhase
import com.ganj.vpn.core.vpn.ConnectionState

/** Anchors the native session timestamp once; wall-clock edits cannot move a running timer. */
internal class ConnectionSessionTimer(
    private val wallClock: () -> Long,
    private val monotonicClock: () -> Long,
) {
    private var start: Long? = null
    private var anchor: Long = 0
    private var age: Long = 0

    fun elapsedMillis(state: ConnectionState): Long? {
        if (state.phase !in setOf(ConnectionPhase.CONNECTED, ConnectionPhase.RECONNECTING)) {
            if (state.phase == ConnectionPhase.DISCONNECTED || state.phase == ConnectionPhase.ERROR) start = null
            return null
        }
        val nativeStart = state.connectedAtEpochMillis?.takeIf { it > 0 } ?: return null
        val now = monotonicClock()
        if (start != nativeStart || now < anchor) {
            val elapsed = wallClock() - nativeStart
            if (elapsed < 0) return null
            start = nativeStart
            anchor = now
            age = elapsed
        }
        return age + (now - anchor).coerceAtLeast(0)
    }
}

internal fun formatConnectionDuration(elapsedMillis: Long): String {
    val seconds = elapsedMillis.coerceAtLeast(0) / 1000
    return listOf(seconds / 3600, seconds / 60 % 60, seconds % 60)
        .joinToString(":") { it.toString().padStart(2, '0') }
}
