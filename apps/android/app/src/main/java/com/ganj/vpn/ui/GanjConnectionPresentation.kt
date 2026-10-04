package com.ganj.vpn.ui

import com.ganj.vpn.core.vpn.ConnectionPhase
import com.ganj.vpn.presentation.ConnectionUiState

/** Native state wins over a stale page snapshot. Pending profile work is never a connection. */
internal fun ganjConnectionVisualState(
    phase: ConnectionPhase,
    request: ConnectionUiState,
    hasActiveService: Boolean,
): GanjConnectionVisualState = when (phase) {
    ConnectionPhase.CONNECTED -> GanjConnectionVisualState.Connected
    ConnectionPhase.RECONNECTING -> GanjConnectionVisualState.Reconnecting
    ConnectionPhase.PREPARING, ConnectionPhase.CONNECTING, ConnectionPhase.DISCONNECTING -> GanjConnectionVisualState.Connecting
    ConnectionPhase.ERROR -> GanjConnectionVisualState.Failed
    ConnectionPhase.DISCONNECTED -> when (request) {
        is ConnectionUiState.Requesting, is ConnectionUiState.ProfileReady -> GanjConnectionVisualState.Connecting
        is ConnectionUiState.Failed -> GanjConnectionVisualState.Failed
        ConnectionUiState.AuthRequired -> GanjConnectionVisualState.Unavailable
        else -> if (hasActiveService) GanjConnectionVisualState.Disconnected else GanjConnectionVisualState.Unavailable
    }
}
