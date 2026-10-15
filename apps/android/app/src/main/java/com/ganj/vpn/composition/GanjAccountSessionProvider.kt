package com.ganj.vpn.composition

import android.app.Application
import com.ganj.vpn.core.controlapi.AndroidKeystoreSessionVault
import com.ganj.vpn.core.controlapi.AuthSessionApiFactory
import com.ganj.vpn.core.controlapi.FamilyApiFactory
import com.ganj.vpn.core.deviceidentity.AndroidDeviceIdentity
import com.ganj.vpn.vpn.AndroidVpnSessionRevocationSink

/** One refresh lock for UI and foreground service; no competing refresh-token rotations. */
internal object GanjAccountSessionProvider {
    private var manager: AndroidAuthSessionManager? = null
    private var endpoint: String? = null
    @Synchronized fun get(application: Application, baseUrl: String): AndroidAuthSessionManager {
        check(endpoint==null || endpoint==baseUrl)
        return manager ?: AndroidAuthSessionManager(AuthSessionApiFactory.create(baseUrl),
            AndroidKeystoreSessionVault(application),AndroidDeviceIdentity.create(application),
            AndroidVpnSessionRevocationSink(application)).also {manager=it;endpoint=baseUrl}
    }
    fun family(application: Application, baseUrl: String) = get(application,baseUrl).let {session ->
        FamilyApiFactory.create(baseUrl,session,session::signAccountBody)
    }
}
