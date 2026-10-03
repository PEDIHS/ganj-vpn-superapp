package com.ganj.vpn.ui

internal fun notificationBadgeText(unreadCount: Int): String? = when {
    unreadCount <= 0 -> null
    unreadCount > 99 -> "۹۹+"
    else -> unreadCount.toPersianDigits()
}
