package com.ganj.vpn.composition

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

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
            return GanjCompositionOwner(
                GanjCompositionFactory.failClosed(application, endpoint),
            ) as T
        }
    }
}
