package com.ganj.vpn.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Presentation-only unread notification count. The Control API remains authoritative; this registry
 * only lets independently composed surfaces reflect the latest owner-scoped count immediately.
 */
internal object GanjNotificationUnreadRegistry {
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

internal fun notificationBadgeText(unreadCount: Int): String? = when {
    unreadCount <= 0 -> null
    unreadCount > 99 -> "۹۹+"
    else -> unreadCount.toPersianDigits()
}
