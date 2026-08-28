package com.ganj.vpn.composition

import android.content.Context

/**
 * Non-authoritative local UX marker. Protected API access must never depend on this value.
 */
internal class AndroidTelegramLinkStateStore(context: Context) : TelegramLinkStateStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun isLinked(): Boolean = prefs.getBoolean(ENTRY, false)

    override fun setLinked(linked: Boolean): Result<Unit> = runCatching {
        check(prefs.edit().putBoolean(ENTRY, linked).commit()) {
            "Telegram linked-state persistence failed"
        }
    }

    private companion object {
        const val PREFS = "ganj_telegram_link_state_v1"
        const val ENTRY = "linked"
    }
}
