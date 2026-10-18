package com.ganj.vpn.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class GanjBotAccountReadCacheTest {

    @Test fun financialReadsShareOnlySimultaneousNetworkCallWithoutStaleTtl() = runBlocking {
        val cache = GanjBotAccountReadCache { 500L }
        cache.switchIdentity("account-one")
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val first = async {
            cache.readFresh("app_snapshot", emptyMap()) { calls++; gate.await(); "snapshot" }
        }
        yield()
        val second = async {
            cache.readFresh("app_snapshot", emptyMap()) { calls++; "should-not-run" }
        }
        yield()
        gate.complete(Unit)
        assertEquals("snapshot", first.await())
        assertEquals("snapshot", second.await())
        assertEquals(1, calls)
        assertEquals("updated", cache.readFresh("app_snapshot", emptyMap()) { calls++; "updated" })
        assertEquals(2, calls)
        val field = GanjBotAccountReadCache::class.java.getDeclaredField("freshFlights")
        field.isAccessible = true
        assertEquals(0, (field.get(cache) as Map<*, *>).size)
    }

    @Test fun accountSwitchCancelsPriorInflightPrivateRead() = runBlocking {
        val cache = GanjBotAccountReadCache { 500L }
        cache.switchIdentity("account-a")
        val gate = CompletableDeferred<Unit>()
        val old = async {
            runCatching { cache.readFresh("app_snapshot", emptyMap()) {
                gate.await()
                "private-a"
            } }
        }
        yield()
        cache.switchIdentity("account-b")
        gate.complete(Unit)
        assertEquals(true, old.await().exceptionOrNull() is CancellationException)
        assertEquals("private-b", cache.readFresh("app_snapshot", emptyMap()) { "private-b" })
    }

    @Test fun highCardinalityReadKeysDoNotRetainUnusedMutexes() = runBlocking {
        val cache = GanjBotAccountReadCache { 3L }
        repeat(600) { index ->
            assertEquals("ok", cache.read("products", mapOf("search" to "query-$index"), 10_000) { "ok" })
        }
        val field = GanjBotAccountReadCache::class.java.getDeclaredField("flights")
        field.isAccessible = true
        val flights = field.get(cache) as Map<*, *>
        assertEquals("Each settled read must release its per-key synchronization", 0, flights.size)
    }
    @Test fun repeatReadUsesTheSameAuthoritativeSnapshotUntilExpiry() = runBlocking {
        var now = 100L
        var calls = 0
        val cache = GanjBotAccountReadCache { now }
        cache.switchIdentity("user-one")
        suspend fun read() = cache.read("wallet_methods", emptyMap(), 10_000) { calls++; "balance:"+calls }
        assertEquals("balance:1", read())
        assertEquals("balance:1", read())
        assertEquals(1, calls)
        now = 10_101
        assertEquals("balance:2", read())
        assertEquals(2, calls)
    }

    @Test fun concurrentSameKeyOnlyMakesOneNetworkRequest() = runBlocking {
        val cache = GanjBotAccountReadCache { 1L }
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val first = async {
            cache.read("transactions", mapOf("limit" to "30"), 15_000) { calls++; gate.await(); "db-value" }
        }
        yield()
        val second = async {
            cache.read("transactions", mapOf("limit" to "30"), 15_000) { calls++; "other" }
        }
        gate.complete(Unit)
        assertEquals("db-value", first.await())
        assertEquals("db-value", second.await())
        assertEquals(1, calls)
    }

    @Test fun identitySwitchCannotLeakPriorUserState() = runBlocking {
        val cache = GanjBotAccountReadCache { 100L }
        cache.switchIdentity("alice")
        val gate = CompletableDeferred<Unit>()
        val old = async {
            runCatching { cache.read("wallet_methods", emptyMap(), 20_000) { gate.await(); "alice-secret" } }
        }
        yield()
        cache.switchIdentity("bob")
        gate.complete(Unit)
        assertEquals(true, old.await().exceptionOrNull() is CancellationException)
        val actual = cache.read("wallet_methods", emptyMap(), 20_000) { "bob-value" }
        assertEquals("bob-value", actual)
    }

    @Test fun mutationInvalidatesEvenBeforeCacheTtlExpires() = runBlocking {
        val cache = GanjBotAccountReadCache { 100L }
        cache.switchIdentity("owner")
        assertEquals("before", cache.read("wallet_methods", emptyMap(), 20_000) { "before" })
        cache.invalidate()
        assertEquals("after", cache.read("wallet_methods", emptyMap(), 20_000) { "after" })
    }

    @Test fun differentParametersNeverSharePrivateResponses() = runBlocking {
        val cache = GanjBotAccountReadCache { 100L }
        assertEquals("30", cache.read("transactions", mapOf("limit" to "30"), 10_000) { "30" })
        assertEquals("50", cache.read("transactions", mapOf("limit" to "50"), 10_000) { "50" })
    }
}
