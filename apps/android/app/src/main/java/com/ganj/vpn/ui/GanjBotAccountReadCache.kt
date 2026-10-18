package com.ganj.vpn.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
    private data class Flight(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val flights = mutableMapOf<String, Flight>()
    private val freshFlights = mutableMapOf<String, CompletableDeferred<String>>()

    fun switchIdentity(accountId: String?) = synchronized(guard) {
        if (identity != accountId) {
            identity = accountId
            generation++
            entries.clear()
            flights.clear()
            freshFlights.clear()
        }
    }

    fun invalidate() = synchronized(guard) {
        generation++
        entries.clear()
        flights.clear()
        freshFlights.clear()
    }

    private fun key(action: String, parameters: Map<String, String>): String =
        action + parameters.toSortedMap().entries.joinToString("") {
            "\u001f" + it.key.length + ":" + it.key + "=" + it.value.length + ":" + it.value
        }

    /**
     * Financial reads MUST NOT have a TTL, but concurrent consumers of the
     * same authenticated account snapshot can safely share ONE network call.
     * The temporary result is released once the in-flight request settles.
     */
    suspend fun readFresh(
        action: String,
        parameters: Map<String, String>,
        load: suspend () -> String,
    ): String {
        val cacheKey = key(action, parameters)
        val epoch: Long
        val pending: CompletableDeferred<String>
        var owner = false
        synchronized(guard) {
            epoch = generation
            val existing = freshFlights[cacheKey]
            if (existing != null) {
                pending = existing
            } else {
                pending = CompletableDeferred()
                freshFlights[cacheKey] = pending
                owner = true
            }
        }
        if (owner) {
            try {
                val response = load()
                synchronized(guard) {
                    if (epoch != generation) throw CancellationException("Bot account scope changed")
                }
                pending.complete(response)
                return response
            } catch (failure: Throwable) {
                pending.completeExceptionally(failure)
                throw failure
            } finally {
                synchronized(guard) {
                    if (freshFlights[cacheKey] === pending) freshFlights.remove(cacheKey)
                }
            }
        }
        val result = pending.await()
        synchronized(guard) {
            if (epoch != generation) throw CancellationException("Bot account scope changed")
        }
        return result
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
        val flight: Flight
        synchronized(guard) {
            epoch = generation
            flight = flights.getOrPut(cacheKey) { Flight() }
            flight.users++
        }
        try {
            flight.mutex.lock()
        } catch (cancelled: CancellationException) {
            synchronized(guard) {
                flight.users--
                if (flight.users == 0 && flights[cacheKey] === flight) flights.remove(cacheKey)
            }
            throw cancelled
        }
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
            flight.mutex.unlock()
            synchronized(guard) {
                flight.users--
                if (flight.users == 0 && flights[cacheKey] === flight) flights.remove(cacheKey)
            }
        }
    }
}
