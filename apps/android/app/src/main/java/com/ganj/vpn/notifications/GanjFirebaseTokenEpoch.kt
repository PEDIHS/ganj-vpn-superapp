package com.ganj.vpn.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** On token rotation, the foreground verified account renews its device binding. */
internal object GanjFirebaseTokenEpoch {
    val value = MutableStateFlow(0L)
    fun updated() { value.update { it + 1L } }
}
