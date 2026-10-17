package com.ganj.vpn.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

/**
 * Bounded, session-scoped client read model. The bot database stays authoritative.
 * This caches serialized read responses only; it never writes an entitlement,
 * balance, subscription or billing transaction and never persists them to disk.
 *
 * Same-key requests coalesce behind one mutex. Logout, account switch or writes
 * invalidate the generation, so an in-flight prior-user response cannot repopulate
 * a new user's cache. The backend is always queried again after a short TTL.
 */
internal class GanjBotAccountReadCache(
    private val nowMillis: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    private data class Row(val body: String, val validUntil: Long)
    private val guard = Any()
    private var generation = 0L
    private var identity: String? = null
    private val entries = linkedMapOf<String, Row>()
    private val flights = mutableMapOf<String, Mutex>()

    fun switchIdentity(accountId: String?) = synchronized(guard) {
        if (identity != accountId) {
            identity = accountId
            generation++
            entries.clear()
            flights.clear()
        }
    }

    fun invalidate() = synchronized(guard) {
        generation++
        entries.clear()
        flights.clear()
    }

    private fun key(action: String, parameters: Map<String, String>): String =
        action + parameters.toSortedMap().entries.joinToString("") {
            "\u001f" + it.key.length + ":" + it.key + "=" + it.value.length + ":" + it.value
        }

    suspend fun read(
        action: String,
        parameters: Map<String, String>,
        ttlMillis: Long,
        load: suspend () -> String,
    ): String {
        require(ttlMillis in 1..120_000)
        val cacheKey = key(action, parameters)
        val epoch: Long
        val flight: Mutex
        synchronized(guard) {
            epoch = generation
            flight = flights.getOrPut(cacheKey) { Mutex() }
        }
        flight.lock()
        try {
            val cached = synchronized(guard) {
                if (epoch != generation) throw CancellationException("Bot account scope changed")
                val row = entries[cacheKey]
                val now = nowMillis()
                row?.takeIf { now >= 0 && now < it.validUntil }?.body
            }
            if (cached != null) return cached
            val incoming = load()
            synchronized(guard) {
                if (epoch != generation) throw CancellationException("Bot account scope changed")
                val now = nowMillis()
                // Limit both byte size and retained actions; don't cache raw
                // configuration material, large pages or financial mutations.
                if (incoming.length <= 512_000) {
                    entries.remove(cacheKey)
                    if (entries.size >= 16) entries.remove(entries.keys.first())
                    entries[cacheKey] = Row(incoming, now + ttlMillis)
                }
            }
            return incoming
        } finally {
            flight.unlock()
        }
    }
}
