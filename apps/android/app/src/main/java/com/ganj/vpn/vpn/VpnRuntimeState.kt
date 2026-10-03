package com.ganj.vpn.vpn

import com.ganj.vpn.core.vpn.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-process, credential-free state of the privileged service. */
internal object VpnRuntimeState {
    private val mutable = MutableStateFlow(ConnectionState())
    val state = mutable.asStateFlow()
    @Volatile var tunnelActive = false
        private set
    fun publish(value: ConnectionState, hasTunnel: Boolean = false) {
        tunnelActive = hasTunnel
        if (value.phase == com.ganj.vpn.core.vpn.ConnectionPhase.ERROR && mutable.value != value) {
            val code = com.ganj.vpn.presentation.ConnectionFailures.runtime(value.errorCode).diagnosticCode
            android.util.Log.w("GanjVpn", "VPN_FAILED code=$code")
        }
        mutable.value = value
    }
}
