package com.ganj.vpn.ui

/**
 * Only publicly visible catalog data may be eventually consistent.
 * Money, transactions, service status, entitlements, payment receipts and
 * authenticated account snapshots are read from the bot every time.
 */
internal object GanjBotReadPolicy {
    /** Quotes and support actions are POST requests, not settled money changes. */
    fun changesFinancialState(action: String): Boolean = action in setOf(
        "purchase", "wallet_card", "wallet_crypto", "wallet_gateway",
        "renew", "custom_purchase", "gift_create", "balance_code_redeem",
    )

    fun ttlMillis(action: String): Long? = when (action) {
        "products" -> 45_000L
        "product" -> 30_000L
        else -> null
    }
}
