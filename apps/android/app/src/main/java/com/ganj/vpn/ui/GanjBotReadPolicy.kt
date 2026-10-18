package com.ganj.vpn.ui

/**
 * Only publicly visible catalog data may be eventually consistent.
 * Money, transactions, service status, entitlements, payment receipts and
 * authenticated account snapshots are read from the bot every time.
 */
internal object GanjBotReadPolicy {
    fun ttlMillis(action: String): Long? = when (action) {
        "products" -> 45_000L
        "product" -> 30_000L
        else -> null
    }
}
