package com.ganj.vpn.composition

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ganj.vpn.core.controlapi.AndroidKeystoreSessionVault
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.AuthSessionApiFactory
import com.ganj.vpn.core.controlapi.CurrentAccount
import com.ganj.vpn.core.controlapi.DeviceApi
import com.ganj.vpn.core.controlapi.DeviceApiFactory
import com.ganj.vpn.core.controlapi.NotificationApi
import com.ganj.vpn.core.controlapi.NotificationApiFactory
import com.ganj.vpn.core.controlapi.WalletApi
import com.ganj.vpn.core.controlapi.WalletApiFactory
import com.ganj.vpn.core.controlapi.WalletSnapshot
import com.ganj.vpn.core.controlapi.WalletTransactionPage
import com.ganj.vpn.core.deviceidentity.AndroidDeviceIdentity
import com.ganj.vpn.presentation.ConnectionProfileContextProvider
import com.ganj.vpn.vpn.AndroidVpnSessionRevocationSink
import java.net.URI

class GanjCompositionOwner internal constructor(
    val composition: GanjComposition,
    internal val telegramAuth: TelegramAuthCoordinator?,
    private val accountSession: AndroidAuthSessionManager?,
    private val walletApi: WalletApi?,
    private val deviceApi: DeviceApi?,
    private val notificationApi: NotificationApi?,
) : ViewModel() {
    internal fun currentAccount(): ApiResult<CurrentAccount>? = accountSession?.currentAccount()
    internal fun wallet(): ApiResult<WalletSnapshot>? = walletApi?.wallet()
    internal fun walletTransactions(cursor: String? = null): ApiResult<WalletTransactionPage>? =
        walletApi?.transactions(cursor = cursor)

    override fun onCleared() {
        NotificationCompositionRegistry.unbind(composition)
        DeviceCompositionRegistry.unbind(composition)
        WalletCompositionRegistry.unbind(composition)
        composition.close()
    }

    class Factory(
        private val application: Application,
        private val endpoint: String,
        private val telegramRedirectUri: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GanjCompositionOwner::class.java))
            val deviceIdentity = AndroidDeviceIdentity.create(application)

            if (!endpoint.isValidControlApiEndpoint()) {
                return GanjCompositionOwner(
                    composition = GanjCompositionFactory.failClosed(
                        application = application,
                        endpoint = endpoint,
                        cryptoProvider = deviceIdentity,
                    ),
                    telegramAuth = null,
                    accountSession = null,
                    walletApi = null,
                    deviceApi = null,
                    notificationApi = null,
                ) as T
            }

            val sessionManager = AndroidAuthSessionManager(
                api = AuthSessionApiFactory.create(endpoint),
                vault = AndroidKeystoreSessionVault(application),
                identity = deviceIdentity,
                sessionRevocationSink = AndroidVpnSessionRevocationSink(application),
            )
            val composition = GanjCompositionFactory.create(
                application = application,
                endpoint = endpoint,
                tokenProvider = sessionManager,
                currentUser = sessionManager,
                connectionContext = ConnectionProfileContextProvider { null },
                cryptoProvider = deviceIdentity,
            )
            val telegramAuth = TelegramAuthCoordinator(
                session = sessionManager,
                store = AndroidTelegramAuthFlowVault(application),
                linkState = AndroidTelegramLinkStateStore(application),
                redirectUri = telegramRedirectUri,
            )
            val walletApi = WalletApiFactory.create(endpoint, sessionManager)
            val deviceApi = DeviceApiFactory.create(endpoint, sessionManager)
            val notificationApi = NotificationApiFactory.create(endpoint, sessionManager)
            WalletCompositionRegistry.bind(composition, walletApi)
            DeviceCompositionRegistry.bind(composition, deviceApi)
            NotificationCompositionRegistry.bind(composition, notificationApi)
            return GanjCompositionOwner(
                composition = composition,
                telegramAuth = telegramAuth,
                accountSession = sessionManager,
                walletApi = walletApi,
                deviceApi = deviceApi,
                notificationApi = notificationApi,
            ) as T
        }

        private fun String.isValidControlApiEndpoint(): Boolean = runCatching {
            val uri = URI(this)
            isNotBlank() && uri.scheme == "https" && !uri.host.isNullOrBlank() &&
                uri.userInfo == null && uri.query == null && uri.fragment == null
        }.getOrDefault(false)
    }
}
