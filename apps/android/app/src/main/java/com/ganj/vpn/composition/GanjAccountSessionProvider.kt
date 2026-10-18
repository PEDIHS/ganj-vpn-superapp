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
    private var sharedIdentity: GanjSwitchableDeviceIdentity? = null
    @Synchronized fun identity(application: Application): GanjSwitchableDeviceIdentity {
        val vault = accountVault ?: GanjMultiAccountVault(application).also { accountVault = it }
        return sharedIdentity ?: GanjSwitchableDeviceIdentity(application,vault).also { sharedIdentity = it }
    }
    @Synchronized fun get(application: Application, baseUrl: String): AndroidAuthSessionManager {
        check(endpoint==null || endpoint==baseUrl)
        return manager ?: AndroidAuthSessionManager(AuthSessionApiFactory.create(baseUrl),
            (accountVault ?: GanjMultiAccountVault(application).also { accountVault=it }),
            identity(application),
            AndroidVpnSessionRevocationSink(application)).also { manager=it;endpoint=baseUrl }
    }
    @Synchronized fun verifiedAccounts(): List<String> = accountVault?.verifiedAccounts().orEmpty()
    @Synchronized fun beginAdditionalAccount(): Boolean {
        val change = { accountVault?.beginAddition() == true }
        return manager?.transitionAccount(change) ?: change()
    }
    @Synchronized fun abortAdditionalAccount(): Boolean {
        val change = { accountVault?.abortAddition() == true }
        return manager?.transitionAccount(change) ?: change()
    }
    @Synchronized fun hasPendingAccountAddition(): Boolean =
        accountVault?.hasPendingAddition() == true
    @Synchronized fun rememberVerifiedAccount(userId: String): Boolean = accountVault?.confirmLinked(userId) == true
    @Synchronized fun switchAccount(userId: String): Boolean {
        if (userId !in verifiedAccounts()) return false
        val change = { accountVault?.selectVerified(userId) == true }
        return manager?.transitionAccount(change) ?: change()
    }

    fun family(application: Application, baseUrl: String) = get(application,baseUrl).let {session ->
        FamilyApiFactory.create(baseUrl,session,session::signAccountBody)
    }
}
