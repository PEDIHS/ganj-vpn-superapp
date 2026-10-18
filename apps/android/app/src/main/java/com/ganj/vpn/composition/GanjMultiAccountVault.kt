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

internal class GanjMultiAccountVault(
    context: Context,
    private val namespaceSuffix: String = "",
) : SessionCredentialVault {
    init { require(namespaceSuffix.matches(Regex("[a-zA-Z0-9_-]{0,40}"))) }
    private val guard = Any()
    private val prefs = context.applicationContext.getSharedPreferences("ganj_account_slots_v1" + namespaceSuffix, Context.MODE_PRIVATE)
    private val legacy = if (namespaceSuffix.isEmpty()) {
        AndroidKeystoreSessionVault(context)
    } else AndroidKeystoreSessionVault(context,
        keyAlias = "ganj.auth.session.aes.test.legacy." + namespaceSuffix,
        preferencesName = "ganj_auth_session_test_legacy_" + namespaceSuffix)
    private val slots = (0 until 3).map { index ->
        AndroidKeystoreSessionVault(
            context,
            keyAlias = "ganj.auth.session.aes.v2.slot." + index + namespaceSuffix,
            preferencesName = "ganj_auth_session_slot_" + index + namespaceSuffix,
        )
    }
    private var active = prefs.getInt("active", -1).takeIf { it in -1..2 } ?: -1

    private fun activeVault() = if (active < 0) legacy else slots[active]

    fun activeIdentitySlot(): Int = synchronized(guard) { active.coerceAtLeast(0) }

    fun hasPendingAddition(): Boolean = synchronized(guard) { prefs.contains("return_to") }

    /** A brand-new device proof and guest login are required for each new Telegram. */
    fun beginAddition(): Boolean = synchronized(guard) {
        if (active < 0 || !prefs.getBoolean("verified_" + active, false) ||
            prefs.contains("return_to")) return@synchronized false
        val next = slots.indices.firstOrNull { index ->
            !prefs.getBoolean("verified_" + index, false) && slots[index].restore() == null
        } ?: return@synchronized false
        if (!prefs.edit().putInt("return_to", active).putInt("active", next).commit())
            return@synchronized false
        active = next
        true
    }

    fun abortAddition(): Boolean = synchronized(guard) {
        if (!prefs.contains("return_to")) return@synchronized false
        val previous = prefs.getInt("return_to", -1)
        if (previous !in 0..2 || !prefs.getBoolean("verified_" + previous, false))
            return@synchronized false
        // On uncertain durability, fail closed rather than restoring an
        // unverified account as though it belonged to the previous user.
        if (active !in 0..2 || slots[active].clear().isFailure) return@synchronized false
        if (!prefs.edit().putInt("active",previous).remove("return_to").commit())
            return@synchronized false
        active = previous
        true
    }

    override fun currentAccessToken(): AccessToken? = restore()?.accessToken
    override fun currentUserId(): String? = restore()?.userId
    override fun currentDeviceId(): String? = restore()?.deviceId
    override fun currentRefreshToken(): RefreshToken? = restore()?.refreshToken
    override fun restore(): AuthSessionCredentials? = synchronized(guard) { activeVault().restore() }

    override fun save(session: AuthSessionCredentials): Result<Unit> = synchronized(guard) {
        if (active == -1 || activeVault().restore()?.userId == session.userId ||
            (active >= 0 && prefs.contains("return_to"))) {
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
            if (prefs.contains("return_to")) {
                // A transient guest logout/expiry must not lose the previous
                // verified account's return pointer.
                prefs.edit().putBoolean("verified_" + old, false).commit()
            } else {
                // Revoking one account never invalidates other saved accounts.
                if (prefs.edit().putBoolean("verified_" + old, false)
                    .putInt("active", -1).commit()) active = -1
            }
        }
        result
    }

    /** Called only after /v1/account/current returns telegramLinked=true. */
    fun confirmLinked(userId: String): Boolean = synchronized(guard) {
        val current = activeVault().restore() ?: return@synchronized false
        if (current.userId != userId) return@synchronized false
        // One verified Telegram account occupies at most one slot.
        if (active >= 0 && prefs.contains("return_to") &&
            slots.indices.any { index ->
                index != active && prefs.getBoolean("verified_" + index, false) &&
                    slots[index].restore()?.userId == userId
            }) return@synchronized false
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
        prefs.edit().putBoolean("verified_" + active, true).remove("return_to").commit()
    }

    fun verifiedAccounts(): List<String> = synchronized(guard) {
        slots.mapIndexedNotNull { index, slot ->
            if (prefs.getBoolean("verified_" + index,false)) slot.restore()?.userId else null
        }.distinct()
    }

    fun selectVerified(userId: String): Boolean = synchronized(guard) {
        if (prefs.contains("return_to")) return@synchronized false
        val index = slots.indexOfFirst { it.restore()?.userId == userId }
        if (index < 0 || !prefs.getBoolean("verified_" + index,false)) return@synchronized false
        if (index == active) return@synchronized true
        if (!prefs.edit().putInt("active",index).commit()) return@synchronized false
        active = index
        true
    }
}
