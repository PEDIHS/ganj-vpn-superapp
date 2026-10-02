package com.ganj.vpn.presentation

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LatencyKey(val serviceId: String, val serverId: String)
data class LatencyReading(val result: LatencyProbeResult, val measuredAtMillis: Long)
data class LatencySnapshot(
    val readings: Map<LatencyKey, LatencyReading> = emptyMap(),
    val measuring: Set<LatencyKey> = emptySet(),
)

/** Session-owned results: navigation/resume never starts a new network test. No profile is cached. */
class SessionLatencyManager(
    private val probe: suspend (String, String) -> LatencyProbeResult,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = System::currentTimeMillis,
) : Closeable {
    private val lock = Any()
    private val automaticStarted = AtomicBoolean(false)
    private val pending = mutableMapOf<LatencyKey, Deferred<LatencyProbeResult>>()
    private val mutableState = MutableStateFlow(LatencySnapshot())
    val state = mutableState.asStateFlow()
    private var generation = 0L

    fun startAutomatic(serviceId: String, servers: suspend () -> List<String>) {
        if (!automaticStarted.compareAndSet(false, true)) return
        val expected = synchronized(lock) { generation }
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
        serverIds.distinct().forEach { serverId -> task(LatencyKey(serviceId, serverId)) }
    }

    suspend fun probeNow(serviceId: String, serverId: String): LatencyProbeResult =
        task(LatencyKey(serviceId, serverId)).await()

    suspend fun cachedOrProbe(serviceId: String, serverId: String): LatencyProbeResult =
        state.value.readings[LatencyKey(serviceId, serverId)]?.result ?: probeNow(serviceId, serverId)

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
        pending.values.forEach { it.cancel() }
        pending.clear()
        mutableState.value = LatencySnapshot()
        automaticStarted.set(false)
    }

    override fun close() { clear(); scope.cancel() }
}
