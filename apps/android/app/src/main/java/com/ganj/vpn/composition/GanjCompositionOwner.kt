package com.ganj.vpn.composition

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ganj.vpn.core.controlapi.AndroidKeystoreSessionVault
import com.ganj.vpn.core.controlapi.AuthSessionApiFactory
import com.ganj.vpn.core.deviceidentity.AndroidDeviceIdentity
import com.ganj.vpn.presentation.ConnectionProfileContextProvider
import com.ganj.vpn.vpn.AndroidVpnSessionRevocationSink
import java.net.URI

class GanjCompositionOwner internal constructor(
    val composition: GanjComposition,
) : ViewModel() {
    override fun onCleared() {
        composition.close()
    }

    class Factory(
        private val application: Application,
        private val endpoint: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GanjCompositionOwner::class.java))
            val deviceIdentity = AndroidDeviceIdentity.create(application)
            val composition = if (endpoint.isValidControlApiEndpoint()) {
                val sessionManager = AndroidAuthSessionManager(
                    api = AuthSessionApiFactory.create(endpoint),
                    vault = AndroidKeystoreSessionVault(application),
                    identity = deviceIdentity,
                    sessionRevocationSink = AndroidVpnSessionRevocationSink(application),
                )
                GanjCompositionFactory.create(
                    application = application,
                    endpoint = endpoint,
                    tokenProvider = sessionManager,
                    currentUser = sessionManager,
                    connectionContext = ConnectionProfileContextProvider { null },
                    cryptoProvider = deviceIdentity,
                )
            } else {
                GanjCompositionFactory.failClosed(
                    application = application,
                    endpoint = endpoint,
                    cryptoProvider = deviceIdentity,
                )
            }
            return GanjCompositionOwner(composition) as T
        }

        private fun String.isValidControlApiEndpoint(): Boolean = runCatching {
            val uri = URI(this)
            isNotBlank() && uri.scheme == "https" && !uri.host.isNullOrBlank() &&
                uri.userInfo == null && uri.query == null && uri.fragment == null
        }.getOrDefault(false)
    }
}
