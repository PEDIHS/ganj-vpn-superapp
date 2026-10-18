package com.ganj.vpn.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Cheap, local signal only — never a shadow balance or a polling loop.
 * The real source is the Bot MySQL database. Caller should re-read
 * an authenticated account snapshot when receiving a new generation.
 */
internal object GanjAccountChangeBus {
    private val state = MutableStateFlow(0L)
    val generation: StateFlow<Long> = state

    fun changed() {
        state.update { it + 1L }
    }
}
