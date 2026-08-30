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
    val count: StateFlow<Int?> = mutableCount.asStateFlow()

    fun update(value: Int) {
        require(value >= 0) { "Unread notification count cannot be negative." }
        mutableCount.value = value
    }

    fun clear() {
        mutableCount.value = null
    }
}
