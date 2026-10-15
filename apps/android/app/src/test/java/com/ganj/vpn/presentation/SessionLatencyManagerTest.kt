package com.ganj.vpn.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class SessionLatencyManagerTest {
    @Test fun entryRunsOnceAndNavigationReadsTheStoredResult() = runBlocking {
        var probes = 0
        var catalogs = 0
        val manager = SessionLatencyManager({ _, _ -> probes++; LatencyProbeResult.Measured(73) },
            CoroutineScope(coroutineContext + SupervisorJob()), { 1234 })
        try {
            repeat(3) { manager.startAutomatic("service") { catalogs++; listOf("server", "server") } }
            withTimeout(2000) { manager.state.first { it.readings.size == 1 && it.measuring.isEmpty() } }
            assertEquals(1, catalogs)
            assertEquals(1, probes)
            repeat(3) { assertEquals(LatencyProbeResult.Measured(73), manager.cachedOrProbe("service", "server")) }
            assertEquals(1, probes)
            assertEquals(1234L, manager.state.value.readings.getValue(LatencyKey("service", "server")).measuredAtMillis)
            assertEquals(LatencyProbeResult.Measured(73), manager.probeNow("service", "server"))
            assertEquals(2, probes)
        } finally { manager.close() }
    }

    @Test fun overlappingManualRequestsJoinOneMeasurement() = runBlocking {
        var probes = 0
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val manager = SessionLatencyManager({ _, _ -> probes++; entered.complete(Unit); release.await(); LatencyProbeResult.Measured(0) },
            CoroutineScope(coroutineContext + SupervisorJob()))
        try {
            manager.measure("service", listOf("server", "server"))
            withTimeout(2000) { entered.await() }
            manager.measure("service", listOf("server"))
            val joined = async { manager.probeNow("service", "server") }
            kotlinx.coroutines.yield()
            release.complete(Unit)
            assertEquals(LatencyProbeResult.Measured(0), withTimeout(2000) { joined.await() })
            assertEquals(1, probes)
            assertTrue(manager.state.value.measuring.isEmpty())
        } finally { manager.close() }
    }

    @Test fun groupedManualPingSharesNativeBatchAndPreservesPerServerFailures() = runBlocking {
        val entered = CompletableDeferred<List<String>>()
        val release = CompletableDeferred<Unit>()
        var singles = 0
        var batches = 0
        val manager = SessionLatencyManager(
            { _, _ -> singles++; LatencyProbeResult.Measured(999) },
            CoroutineScope(coroutineContext + SupervisorJob()),
            batchProbe = { _, ids ->
                batches++
                entered.complete(ids)
                release.await()
                ids.associateWith { id ->
                    if (id == "bad") LatencyProbeResult.Failed(ConnectionFailures.probe("probe.timeout"))
                    else LatencyProbeResult.Measured(27)
                }
            },
        )
        try {
            manager.measure("service", listOf("a", "bad", "b", "a"))
            assertEquals(listOf("a", "bad", "b"), withTimeout(2000) { entered.await() })
            val joined = async { manager.awaitCurrent("service", "b") }
            manager.measure("service", listOf("a", "bad", "b"))
            release.complete(Unit)
            assertEquals(LatencyProbeResult.Measured(27), withTimeout(2000) { joined.await() })
            withTimeout(2000) { manager.state.first { it.readings.size == 3 && it.measuring.isEmpty() } }
            assertEquals(1, batches)
            assertEquals(0, singles)
            assertEquals(27L, manager.state.value.readings.getValue(LatencyKey("service", "a")).result.latencyMillis)
            val failed = manager.state.value.readings.getValue(LatencyKey("service", "bad")).result
                as LatencyProbeResult.Failed
            assertEquals("probe.timeout", failed.failure.diagnosticCode)
        } finally {
            manager.close()
        }
    }

    @Test fun groupPingNeverExceedsPinnedNativeMaximumOfFive() = runBlocking {
        val batches = java.util.concurrent.ConcurrentLinkedQueue<List<String>>()
        var singles = 0
        val manager = SessionLatencyManager(
            { _, _ -> singles++; LatencyProbeResult.Measured(14) },
            CoroutineScope(coroutineContext + SupervisorJob()),
            batchProbe = { _, ids ->
                batches.add(ids)
                ids.associateWith { LatencyProbeResult.Measured(25) }
            },
        )
        try {
            manager.measure("service", (1..11).map { it.toString() })
            withTimeout(2000) { manager.state.first { it.readings.size == 11 && it.measuring.isEmpty() } }
            assertEquals(2, batches.size)
            assertTrue(batches.all { it.size <= 5 })
            assertEquals(1, singles)
        } finally {
            manager.close()
        }
    }

    @Test fun clearPreventsLateResultsFromThePreviousAccount() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val ended = CompletableDeferred<Unit>()
        val manager = SessionLatencyManager({ _, _ ->
            entered.complete(Unit)
            withContext(NonCancellable) { release.await() }
            ended.complete(Unit)
            LatencyProbeResult.Measured(100)
        }, CoroutineScope(coroutineContext + SupervisorJob()))
        try {
            manager.measure("service", listOf("server"))
            withTimeout(2000) { entered.await() }
            manager.clear()
            release.complete(Unit)
            withTimeout(2000) { ended.await() }
            kotlinx.coroutines.yield()
            assertTrue(manager.state.value.readings.isEmpty())
            assertTrue(manager.state.value.measuring.isEmpty())
        } finally { manager.close() }
    }

    @Test fun allServersKeepFailuresSeparateAndUseServiceScopedKeys() = runBlocking {
        val manager = SessionLatencyManager({ service, _ ->
            if (service == "a") LatencyProbeResult.Measured(42)
            else LatencyProbeResult.Failed(ConnectionFailures.probe("probe.timeout"))
        }, CoroutineScope(coroutineContext + SupervisorJob()))
        try {
            manager.measure("a", listOf("server"))
            manager.measure("b", listOf("server"))
            withTimeout(2000) { manager.state.first { it.readings.size == 2 && it.measuring.isEmpty() } }
            assertEquals(42L, manager.state.value.readings.getValue(LatencyKey("a", "server")).result.latencyMillis)
            val failure = manager.state.value.readings.getValue(LatencyKey("b", "server")).result as LatencyProbeResult.Failed
            assertEquals("probe.timeout", failure.failure.diagnosticCode)
        } finally { manager.close() }
    }
}
