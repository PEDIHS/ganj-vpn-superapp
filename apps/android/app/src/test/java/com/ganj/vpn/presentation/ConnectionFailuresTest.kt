package com.ganj.vpn.presentation

import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ProfileProvisioningError
import com.ganj.vpn.core.vpn.ConnectionPhase
import com.ganj.vpn.core.vpn.ConnectionState
import org.junit.Assert.*
import org.junit.Test

class ConnectionFailuresTest {
    @Test fun runtimeErrorRetainsTheActualSafeCause() {
        val updated = reconcileVpnState(GanjUiState(), ConnectionState(ConnectionPhase.ERROR, errorCode = "vpn.tun_establish_failed"))
        val failure = (updated.connection as ConnectionUiState.Failed).failure
        assertEquals("connection.tun_failed", failure.messageKey)
        assertEquals("vpn.tun_establish_failed", failure.diagnosticCode)
    }

    @Test fun unknownExceptionsAndServerCodesNeverBecomeDiagnosticSecrets() {
        val private = "vless://private-password@example.invalid"
        assertEquals("vpn.unknown_failure", ConnectionFailures.runtime(private).diagnosticCode)
        assertNull(ConnectionFailures.api(ApiError.Forbidden(null, private)).diagnosticCode)
    }

    @Test fun forbiddenReasonsAreDifferentAndKeepRequestReference() {
        val unavailable = ConnectionFailures.api(ApiError.Forbidden("request", "server_unavailable"))
        val device = ConnectionFailures.api(ApiError.Forbidden("request", "device_limit_reached"))
        val proof = ConnectionFailures.api(ApiError.Forbidden("request", "invalid_device_proof"))
        assertEquals("connection.server_unavailable", unavailable.messageKey)
        assertEquals("connection.device_limit_reached", device.messageKey)
        assertEquals("connection.device_proof_invalid", proof.messageKey)
        assertEquals("request", unavailable.requestId)
        assertFalse(device.retryable)
    }

    @Test fun provisioningStagesDoNotCollapseIntoOneMessage() {
        val keys = ProfileProvisioningError.entries.map { ConnectionFailures.provisioning(it).messageKey }
        assertEquals(keys.size, keys.toSet().size)
    }
}
