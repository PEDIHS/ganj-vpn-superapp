package com.ganj.vpn.composition

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ganj.vpn.BuildConfig
import com.ganj.vpn.core.controlapi.AndroidKeystoreSessionVault
import com.ganj.vpn.core.controlapi.AuthSessionApiFactory
import com.ganj.vpn.core.controlapi.TelegramAuthorizationCommand
import com.ganj.vpn.core.controlapi.TelegramExchangeCommand
import com.ganj.vpn.core.deviceidentity.AndroidDeviceIdentity
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
                val authApi = AuthSessionApiFactory.create(endpoint)
                val sessionManager = AndroidAuthSessionManager(
                    api = authApi,
                    vault = AndroidKeystoreSessionVault(application),
                    identity = deviceIdentity,
                )
                val telegramAuth = TelegramBotAuthCoordinator(
                    session = object : TelegramBotAuthSessionGateway {
                        override fun begin(command: TelegramAuthorizationCommand) =
                            sessionManager.beginTelegramBot(command)

                        override fun status(state: String) =
                            sessionManager.telegramBotStatus(state)

                        override fun exchange(command: TelegramExchangeCommand) =
                            sessionManager.exchangeTelegramBot(command)

                        override fun logout(): Boolean = sessionManager.logoutSession()
                    },
                    flowStore = AndroidTelegramBotAuthFlowVault(application),
                    linkState = AndroidTelegramLinkStateStore(application),
                    redirectUri = BuildConfig.TELEGRAM_BOT_REDIRECT_URI,
                )
                GanjCompositionFactory.create(
                    application = application,
                    endpoint = endpoint,
                    tokenProvider = sessionManager,
                    currentUser = sessionManager,
                    connectionContext = AndroidConnectionProfileContextProvider(
                        session = sessionManager,
                        identity = deviceIdentity,
                    ),
                    telegramAuth = telegramAuth,
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
