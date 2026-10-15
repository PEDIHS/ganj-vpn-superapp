package com.ganj.vpn.presentation

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

data class LatencyKey(val serviceId: String, val serverId: String)
data class LatencyReading(val result: LatencyProbeResult, val measuredAtMillis: Long)
data class LatencySnapshot(
    val readings: Map<LatencyKey, LatencyReading> = emptyMap(),
    val measuring: Set<LatencyKey> = emptySet(),
)

/** Navigation/resume never retriggers measurements. No sensitive profile is cached. */
class SessionLatencyManager(
    private val probe: suspend (String, String) -> LatencyProbeResult,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = System::currentTimeMillis,
    private val probeBatch: (suspend (String, List<String>) -> Map<String, LatencyProbeResult>)? = null,
) : Closeable {
    private val lock = Any()
    private val automaticStarted = AtomicBoolean(false)
    private val pending = mutableMapOf<LatencyKey, Deferred<LatencyProbeResult>>()
    private val batchJobs = mutableSetOf<Job>()
    private val mutableState = MutableStateFlow(LatencySnapshot())
    val state = mutableState.asStateFlow()
    private var generation = 0L

    fun startAutomatic(serviceId: String, servers: suspend () -> List<String>) {
        val expected = synchronized(lock) {
            if (!automaticStarted.compareAndSet(false, true)) return
            generation
        }
        scope.launch {
            val ids = try { servers() } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { emptyList() }
            synchronized(lock) {
                if (expected == generation) measure(serviceId, ids)
            }
        }
    }

    fun measure(serviceId: String, serverIds: List<String>) {
        val ids = serverIds.distinct()
        if (probeBatch == null || ids.size <= 1) {
            ids.forEach { serverId -> task(LatencyKey(serviceId, serverId)) }
        } else {
            // Show partial results as bounded groups finish; avoid an unbounded native batch.
            ids.chunked(8).forEach { group ->
                if (group.size > 1) batchTask(serviceId, group)
                else task(LatencyKey(serviceId, group.single()))
            }
        }
    }

    suspend fun probeNow(serviceId: String, serverId: String): LatencyProbeResult =
        task(LatencyKey(serviceId, serverId)).await()

    /** Used by smart connect after scheduling the batch; never creates a duplicate probe. */
    suspend fun awaitCurrent(serviceId: String, serverId: String): LatencyProbeResult {
        val key = LatencyKey(serviceId, serverId)
        val running = synchronized(lock) { pending[key] }
        return running?.await() ?: state.value.readings[key]?.result ?: task(key).await()
    }

    suspend fun cachedOrProbe(serviceId: String, serverId: String): LatencyProbeResult =
        state.value.readings[LatencyKey(serviceId, serverId)]?.result ?: awaitCurrent(serviceId, serverId)

    private fun batchTask(serviceId: String, serverIds: List<String>) {
        val (expected, fresh) = synchronized(lock) {
            val epoch = generation
            val newTasks = serverIds.mapNotNull { serverId ->
                val key = LatencyKey(serviceId, serverId)
                if (pending.containsKey(key)) null
                else {
                    val deferred = CompletableDeferred<LatencyProbeResult>()
                    pending[key] = deferred
                    key to deferred
                }
            }
            if (newTasks.isNotEmpty()) mutableState.update {
                it.copy(measuring = it.measuring + newTasks.map { pair -> pair.first })
            }
            epoch to newTasks
        }
        if (fresh.isEmpty()) return
        val batch = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                val results = probeBatch!!.invoke(serviceId, fresh.map { it.first.serverId })
                fresh.forEach { (key, deferred) ->
                    val result = results[key.serverId] ?: LatencyProbeResult.Failed(
                        ConnectionFailures.probe("probe.native_failed"),
                    )
                    synchronized(lock) {
                        if (expected == generation && pending[key] === deferred) {
                            mutableState.update {
                                it.copy(readings = it.readings + (key to LatencyReading(result, clock())))
                            }
                            deferred.complete(result)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                fresh.forEach { it.second.cancel() }
                throw cancelled
            } catch (_: Exception) {
                fresh.forEach { (key, deferred) ->
                    val result = LatencyProbeResult.Failed(ConnectionFailures.probe("probe.unavailable"))
                    synchronized(lock) {
                        if (expected == generation && pending[key] === deferred) {
                            mutableState.update {
                                it.copy(readings = it.readings + (key to LatencyReading(result, clock())))
                            }
                            deferred.complete(result)
                        }
                    }
                }
            } finally {
                synchronized(lock) {
                    batchJobs.remove(currentCoroutineContext().job)
                    if (expected == generation) {
                        fresh.forEach { (key, deferred) ->
                            if (pending[key] === deferred) pending.remove(key)
                        }
                        mutableState.update { it.copy(measuring = it.measuring - fresh.map { pair -> pair.first }.toSet()) }
                    }
                }
            }
        }
        synchronized(lock) {
            if (expected == generation) batchJobs.add(batch) else batch.cancel()
        }
        batch.start()
    }

    private fun task(key: LatencyKey): Deferred<LatencyProbeResult> = synchronized(lock) {
        pending[key]?.let { return@synchronized it }
        val expected = generation
        mutableState.update { it.copy(measuring = it.measuring + key) }
        scope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                val result = try { probe(key.serviceId, key.serverId) } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { LatencyProbeResult.Failed(ConnectionFailures.probe("probe.unavailable")) }
                synchronized(lock) {
                    if (expected == generation) mutableState.update {
                        it.copy(readings = it.readings + (key to LatencyReading(result, clock())))
                    }
                }
                result
            } finally {
                synchronized(lock) {
                    if (expected == generation) {
                        pending.remove(key)
                        mutableState.update { it.copy(measuring = it.measuring - key) }
                    }
                }
            }
        }.also { pending[key] = it; it.start() }
    }

    fun clear() = synchronized(lock) {
        generation++
        batchJobs.forEach { it.cancel() }
        batchJobs.clear()
        pending.values.forEach { it.cancel() }
        pending.clear()
        mutableState.value = LatencySnapshot()
        automaticStarted.set(false)
    }

    override fun close() { clear(); scope.cancel() }
}
