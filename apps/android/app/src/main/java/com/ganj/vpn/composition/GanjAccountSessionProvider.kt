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
    private var accountVault: GanjMultiAccountVault? = null
    @Synchronized fun get(application: Application, baseUrl: String): AndroidAuthSessionManager {
        check(endpoint==null || endpoint==baseUrl)
        return manager ?: AndroidAuthSessionManager(AuthSessionApiFactory.create(baseUrl),
            (accountVault ?: GanjMultiAccountVault(application).also { accountVault=it }),
            AndroidDeviceIdentity.create(application),
            AndroidVpnSessionRevocationSink(application)).also { manager=it;endpoint=baseUrl }
    }
    @Synchronized fun verifiedAccounts(): List<String> = accountVault?.verifiedAccounts().orEmpty()
    @Synchronized fun rememberVerifiedAccount(userId: String): Boolean = accountVault?.confirmLinked(userId) == true
    @Synchronized fun switchAccount(userId: String): Boolean {
        if (userId !in verifiedAccounts()) return false
        val switched = accountVault?.selectVerified(userId) == true
        if (switched) manager?.onAccountChangedLocally()
        return switched
    }

    fun family(application: Application, baseUrl: String) = get(application,baseUrl).let {session ->
        FamilyApiFactory.create(baseUrl,session,session::signAccountBody)
    }
}
