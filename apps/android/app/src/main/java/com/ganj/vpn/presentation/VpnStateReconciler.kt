package com.ganj.vpn.presentation

import com.ganj.vpn.core.vpn.ConnectionPhase
import com.ganj.vpn.core.vpn.ConnectionState

internal fun reconcileVpnState(ui: GanjUiState, runtime: ConnectionState): GanjUiState {
    val current = ui.copy(runtimeConnection = runtime)
    // Keep the one-shot launch handle alive until its coroutine finishes.
    if (ui.connection is ConnectionUiState.ProfileReady || ui.connection is ConnectionUiState.Requesting) return current
    val serviceId = runtime.serviceId
    val profileId = runtime.profileId
    val serverId = runtime.serverId
    val connection = when (runtime.phase) {
        ConnectionPhase.CONNECTED -> if (serviceId != null && profileId != null && serverId != null) {
            ConnectionUiState.Connected(serviceId, profileId, serverId)
        } else ui.connection
        ConnectionPhase.DISCONNECTED -> if (ui.connection is ConnectionUiState.Connected) ConnectionUiState.Idle else ui.connection
        ConnectionPhase.ERROR -> ConnectionUiState.Failed(
            runtime.serviceId,
            UiFailure(UiFailureKind.SERVER, "connection.tunnel_start_failed", true),
        )
        else -> ui.connection
    }
    return current.copy(connection = connection)
}
