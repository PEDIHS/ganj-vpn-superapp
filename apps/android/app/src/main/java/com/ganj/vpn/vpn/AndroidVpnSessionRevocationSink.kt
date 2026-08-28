package com.ganj.vpn.vpn

import android.app.Application
import android.content.Intent
import com.ganj.vpn.composition.SessionRevocationSink
import com.ganj.vpn.core.vpn.SecureProfileRecoveryStore

/**
 * Ensures a revoked/logged-out account cannot resurrect or retain a previously provisioned tunnel.
 * This path does not start a background service: it destroys recoverable profile state first and
 * then stops any currently running VPN service so [GanjVpnService.onDestroy] closes the engine/TUN.
 */
internal class AndroidVpnSessionRevocationSink(
    private val application: Application,
) : SessionRevocationSink {
    private val recoveryStore by lazy { SecureProfileRecoveryStore(application) }

    override fun onSessionInvalidated() {
        runCatching { recoveryStore.clear() }
        runCatching {
            application.stopService(Intent(application, GanjVpnService::class.java))
        }
    }

    override fun toString(): String = "AndroidVpnSessionRevocationSink([REDACTED])"
}
