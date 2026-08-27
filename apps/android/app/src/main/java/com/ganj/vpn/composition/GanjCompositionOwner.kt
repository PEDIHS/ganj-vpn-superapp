package com.ganj.vpn.composition

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ganj.vpn.core.deviceidentity.AndroidDeviceIdentity

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
            return GanjCompositionOwner(
                GanjCompositionFactory.failClosed(
                    application = application,
                    endpoint = endpoint,
                    cryptoProvider = deviceIdentity,
                ),
            ) as T
        }
    }
}
