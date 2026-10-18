package com.ganj.vpn.composition

import android.content.Context
import com.ganj.vpn.core.controlapi.AccessToken
import com.ganj.vpn.core.controlapi.AndroidKeystoreSessionVault
import com.ganj.vpn.core.controlapi.AuthSessionCredentials
import com.ganj.vpn.core.controlapi.RefreshToken
import com.ganj.vpn.core.controlapi.SessionCredentialVault

/**
 * Up to three independently encrypted Telegram-verified sessions. Existing v1
 * credentials are migrated only after an authenticated /me response proves
 * which Telegram account they belong to.
 *
 * Slot credentials use dedicated Android Keystore keys/preferences. IDs in the
 * selector are always reconciled against authenticated saved credentials.
 * No token, refresh token, Telegram password or session cookie enters UI state.
 */
internal class GanjAccountLimitReached : IllegalStateException("Maximum three verified accounts")

internal class GanjMultiAccountVault(context: Context) : SessionCredentialVault {
    private val guard = Any()
    private val prefs = context.applicationContext.getSharedPreferences("ganj_account_slots_v1", Context.MODE_PRIVATE)
    private val legacy = AndroidKeystoreSessionVault(context)
    private val slots = (0 until 3).map { index ->
        AndroidKeystoreSessionVault(
            context,
            keyAlias = "ganj.auth.session.aes.v2.slot." + index,
            preferencesName = "ganj_auth_session_slot_" + index,
        )
    }
    private var active = prefs.getInt("active", -1).takeIf { it in -1..2 } ?: -1

    private fun activeVault() = if (active < 0) legacy else slots[active]

    override fun currentAccessToken(): AccessToken? = restore()?.accessToken
    override fun currentUserId(): String? = restore()?.userId
    override fun currentDeviceId(): String? = restore()?.deviceId
    override fun currentRefreshToken(): RefreshToken? = restore()?.refreshToken
    override fun restore(): AuthSessionCredentials? = synchronized(guard) { activeVault().restore() }

    override fun save(session: AuthSessionCredentials): Result<Unit> = synchronized(guard) {
        if (active == -1 || activeVault().restore()?.userId == session.userId) {
            return@synchronized activeVault().save(session)
        }
        // A newly verified Telegram login can replace the active identity,
        // but must NOT overwrite the previous person's tokens.
        val index = slots.indexOfFirst { it.restore()?.userId == session.userId }
            .takeIf { it >= 0 } ?: slots.indexOfFirst { it.restore() == null }
        if (index < 0) return@synchronized Result.failure(GanjAccountLimitReached())
        val stored = slots[index].save(session)
        if (stored.isFailure) return@synchronized stored
        if (!prefs.edit().putInt("active", index).commit()) {
            // Do not continue with in-memory credentials absent durable pointer.
            if (!prefs.getBoolean("verified_" + index, false)) slots[index].clear()
            return@synchronized Result.failure(IllegalStateException("Active account persistence failed"))
        }
        active = index
        stored
    }

    override fun clear(): Result<Unit> = synchronized(guard) {
        val old = active
        val result = activeVault().clear()
        if (result.isSuccess && old >= 0) {
            prefs.edit().putBoolean("verified_" + old, false).putInt("active", -1).commit()
            active = -1
        }
        result
    }

    /** Called only after /v1/account/current returns telegramLinked=true. */
    fun confirmLinked(userId: String): Boolean = synchronized(guard) {
        val current = activeVault().restore() ?: return@synchronized false
        if (current.userId != userId) return@synchronized false
        if (active < 0) {
            val slot = slots.indexOfFirst { it.restore()?.userId == userId }
                .takeIf { it >= 0 } ?: slots.indexOfFirst { it.restore() == null }
            if (slot < 0 || slots[slot].save(current).isFailure) return@synchronized false
            if (!prefs.edit().putInt("active",slot).putBoolean("verified_" + slot,true).commit()) return@synchronized false
            active = slot
            // Migration is committed. Any stale legacy credentials are now dead.
            legacy.clear()
            return@synchronized true
        }
        prefs.edit().putBoolean("verified_" + active, true).commit()
    }

    fun verifiedAccounts(): List<String> = synchronized(guard) {
        slots.mapIndexedNotNull { index, slot ->
            if (prefs.getBoolean("verified_" + index,false)) slot.restore()?.userId else null
        }.distinct()
    }

    fun selectVerified(userId: String): Boolean = synchronized(guard) {
        val index = slots.indexOfFirst { it.restore()?.userId == userId }
        if (index < 0 || !prefs.getBoolean("verified_" + index,false)) return@synchronized false
        if (index == active) return@synchronized true
        if (!prefs.edit().putInt("active",index).commit()) return@synchronized false
        active = index
        true
    }
}
