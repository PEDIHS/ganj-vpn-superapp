package com.ganj.vpn.presentation

import com.ganj.vpn.core.vpn.ConnectionPhase
import com.ganj.vpn.core.vpn.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnStateReconcilerTest {
    @Test fun processRestoreReadsTheLiveService() {
        val runtime = ConnectionState(ConnectionPhase.CONNECTED, serverId = "server", serviceId = "service", profileId = "profile")
        val restored = reconcileVpnState(GanjUiState(), runtime)
        assertEquals(ConnectionUiState.Connected("service", "profile", "server"), restored.connection)
        assertEquals(ConnectionUiState.Idle, reconcileVpnState(restored, ConnectionState()).connection)
    }

    @Test fun runtimeUpdatesDoNotCancelAnInFlightPermissionOrLaunch() {
        val pending = ConnectionUiState.ProfileReady("service", "profile", "expiry",
            ConnectionSafeAction.StartTunnel(ConnectionActionHandle("vpn_action_" + "a".repeat(32))))
        val ui = GanjUiState(connection = pending)
        val updated = reconcileVpnState(ui, ConnectionState(ConnectionPhase.CONNECTING))
        assertEquals(pending, updated.connection)
        assertEquals(ConnectionPhase.CONNECTING, updated.runtimeConnection.phase)
    }

    @Test fun runtimeErrorDoesNotRemainGreen() {
        val state = GanjUiState(connection = ConnectionUiState.Connected("service", "profile", "server"))
        assertTrue(reconcileVpnState(state, ConnectionState(ConnectionPhase.ERROR)).connection is ConnectionUiState.Failed)
    }
}
