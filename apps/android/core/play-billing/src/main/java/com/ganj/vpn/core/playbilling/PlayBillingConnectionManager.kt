package com.ganj.vpn.core.playbilling

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.Closeable
import kotlin.math.min
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal fun interface RetryScheduler {
    fun schedule(delayMillis: Long, task: () -> Unit)
}

internal class MainThreadRetryScheduler : RetryScheduler, Closeable {
    private val handler = Handler(Looper.getMainLooper())
    private val owner = Any()

    override fun schedule(delayMillis: Long, task: () -> Unit) {
        handler.postAtTime(task, owner, SystemClock.uptimeMillis() + delayMillis)
    }

    override fun close() {
        handler.removeCallbacksAndMessages(owner)
    }
}

internal data class ExponentialBackoff(
    val initialDelayMillis: Long = 500,
    val maximumDelayMillis: Long = 30_000,
    val maximumAttempts: Int = 6,
) {
    init {
        require(initialDelayMillis > 0)
        require(maximumDelayMillis >= initialDelayMillis)
        require(maximumAttempts > 0)
    }

    fun delayForAttempt(attempt: Int): Long {
        require(attempt >= 1)
        var delay = initialDelayMillis
        repeat(attempt - 1) { delay = min(maximumDelayMillis, delay * 2) }
        return delay
    }
}

/**
 * Coordinates the module's single BillingClient connection.
 *
 * PBL automatic service reconnection handles an established client. Bounded exponential backoff
 * here handles initial setup failures and queued work without creating a reconnect hot loop.
 */
internal class PlayBillingConnectionManager(
    private val client: PlayBillingClientFacade,
    private val scheduler: RetryScheduler,
    private val backoff: ExponentialBackoff = ExponentialBackoff(),
) : Closeable {
    private val lock = Any()
    private val awaiting = mutableListOf<(PlayClientResult) -> Unit>()
    private var connecting = false
    private var closed = false
    private var attempt = 0

    suspend fun awaitReady(): PlayClientResult = suspendCancellableCoroutine { continuation ->
        val callback: (PlayClientResult) -> Unit = { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        ensureReady(callback)
        continuation.invokeOnCancellation {
            synchronized(lock) { awaiting.remove(callback) }
        }
    }

    fun ensureReady(callback: (PlayClientResult) -> Unit) {
        var startNow = false
        var immediate: PlayClientResult? = null
        synchronized(lock) {
            when {
                closed -> immediate = PlayClientResult(PlayResponseCode.SERVICE_DISCONNECTED)
                client.isReady -> immediate = PlayClientResult(PlayResponseCode.OK)
                else -> {
                    awaiting += callback
                    if (!connecting) {
                        connecting = true
                        startNow = true
                    }
                }
            }
        }
        immediate?.let(callback)
        if (startNow) startConnection()
    }

    private fun startConnection() {
        if (synchronized(lock) { closed }) return
        client.startConnection(
            object : PlayBillingClientFacade.ConnectionListener {
                override fun onSetupFinished(result: PlayClientResult) {
                    if (result.responseCode == PlayResponseCode.OK) {
                        val callbacks = synchronized(lock) {
                            connecting = false
                            attempt = 0
                            awaiting.toList().also { awaiting.clear() }
                        }
                        callbacks.forEach { it(result) }
                    } else {
                        retryOrFail(result)
                    }
                }

                override fun onServiceDisconnected() {
                    retryOrFail(PlayClientResult(PlayResponseCode.SERVICE_DISCONNECTED))
                }
            },
        )
    }

    private fun retryOrFail(result: PlayClientResult) {
        var retryDelay: Long? = null
        var failures: List<(PlayClientResult) -> Unit> = emptyList()
        synchronized(lock) {
            if (closed) return
            connecting = false
            if (isRetryable(result.responseCode) && awaiting.isNotEmpty() && attempt < backoff.maximumAttempts) {
                attempt += 1
                connecting = true
                retryDelay = backoff.delayForAttempt(attempt)
            } else {
                attempt = 0
                failures = awaiting.toList()
                awaiting.clear()
            }
        }
        retryDelay?.let { scheduler.schedule(it, ::startConnection) }
        failures.forEach { it(result) }
    }

    override fun close() {
        val callbacks = synchronized(lock) {
            if (closed) return
            closed = true
            connecting = false
            awaiting.toList().also { awaiting.clear() }
        }
        if (scheduler is Closeable) scheduler.close()
        client.endConnection()
        callbacks.forEach { it(PlayClientResult(PlayResponseCode.SERVICE_DISCONNECTED)) }
    }

    private fun isRetryable(code: Int): Boolean = code in setOf(
        PlayResponseCode.SERVICE_DISCONNECTED,
        PlayResponseCode.SERVICE_UNAVAILABLE,
        PlayResponseCode.NETWORK_ERROR,
        PlayResponseCode.ERROR,
    )
}
