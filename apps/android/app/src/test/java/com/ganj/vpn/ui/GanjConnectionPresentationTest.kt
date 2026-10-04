package com.ganj.vpn.ui

import com.ganj.vpn.core.vpn.ConnectionPhase
import com.ganj.vpn.presentation.ConnectionUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class GanjConnectionPresentationTest {
    @Test fun `native disconnect and recovery override stale connected UI`() {
        val stale = ConnectionUiState.Connected("service", "profile", "server")
        assertEquals(GanjConnectionVisualState.Disconnected, ganjConnectionVisualState(ConnectionPhase.DISCONNECTED, stale, true))
        assertEquals(GanjConnectionVisualState.Connecting, ganjConnectionVisualState(ConnectionPhase.DISCONNECTING, stale, true))
        assertEquals(GanjConnectionVisualState.Reconnecting, ganjConnectionVisualState(ConnectionPhase.RECONNECTING, stale, true))
        assertEquals(GanjConnectionVisualState.Failed, ganjConnectionVisualState(ConnectionPhase.ERROR, stale, true))
    }
    @Test fun `pending profile never reports success and native confirmation wins`() {
        val request = ConnectionUiState.Requesting("service")
        assertEquals(GanjConnectionVisualState.Connecting, ganjConnectionVisualState(ConnectionPhase.DISCONNECTED, request, true))
        assertEquals(GanjConnectionVisualState.Connected, ganjConnectionVisualState(ConnectionPhase.CONNECTED, request, true))
        assertEquals(GanjConnectionVisualState.Unavailable, ganjConnectionVisualState(ConnectionPhase.DISCONNECTED, ConnectionUiState.Idle, false))
    }
    @Test fun `secondary destinations retain a path to their primary tab`() {
        assertEquals(GanjDestination.Servers, GanjDestination.Store.primaryDestination())
        assertEquals(GanjDestination.Settings, GanjDestination.Account.primaryDestination())
        assertEquals(GanjDestination.Settings, GanjDestination.Home.primaryDestination())
        assertEquals(listOf(GanjDestination.Connect, GanjDestination.Servers, GanjDestination.Settings), GanjPrimaryDestinations)
    }
}
