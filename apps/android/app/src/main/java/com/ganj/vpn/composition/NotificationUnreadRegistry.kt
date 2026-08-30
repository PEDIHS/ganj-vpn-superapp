package com.ganj.vpn.composition

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Presentation-facing unread notification count backed only by owner-scoped Control API results.
 * It intentionally carries no notification content and is cleared across composition/account changes.
 */
internal object NotificationUnreadRegistry {
    private val mutableCount = MutableStateFlow<Int?>(null)
    private val mutableRefreshGeneration = MutableStateFlow(0L)

    val count: StateFlow<Int?> = mutableCount.asStateFlow()
    val refreshGeneration: StateFlow<Long> = mutableRefreshGeneration.asStateFlow()

    fun update(value: Int) {
        require(value >= 0) { "Unread notification count cannot be negative." }
        mutableCount.value = value
    }

    fun clear() {
        mutableCount.value = null
    }

    fun requestRefresh() {
        mutableRefreshGeneration.value = if (mutableRefreshGeneration.value == Long.MAX_VALUE) {
            0L
        } else {
            mutableRefreshGeneration.value + 1L
        }
    }

    fun resetForAccountBoundary() {
        clear()
        requestRefresh()
    }
}
