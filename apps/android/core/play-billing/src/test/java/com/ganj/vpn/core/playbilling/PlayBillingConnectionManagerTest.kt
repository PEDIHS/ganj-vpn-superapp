package com.ganj.vpn.core.playbilling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayBillingConnectionManagerTest {
    @Test
    fun `initial transient failures use bounded exponential backoff and complete queued caller`() {
        val client = FakePlayBillingClient().apply {
            isReady = false
            autoCompleteConnection = false
        }
        val scheduler = FakeRetryScheduler()
        val manager = PlayBillingConnectionManager(
            client,
            scheduler,
            ExponentialBackoff(initialDelayMillis = 100, maximumDelayMillis = 400, maximumAttempts = 3),
        )
        val results = mutableListOf<PlayClientResult>()

        manager.ensureReady(results::add)
        client.connectionListener!!.onSetupFinished(PlayClientResult(PlayResponseCode.NETWORK_ERROR))
        assertEquals(listOf(100L), scheduler.scheduled.map { it.delayMillis })

        scheduler.runNext()
        client.connectionListener!!.onSetupFinished(PlayClientResult(PlayResponseCode.SERVICE_UNAVAILABLE))
        assertEquals(listOf(200L), scheduler.scheduled.map { it.delayMillis })

        scheduler.runNext()
        client.isReady = true
        client.connectionListener!!.onSetupFinished(PlayClientResult(PlayResponseCode.OK))

        assertEquals(3, client.startConnectionCount)
        assertEquals(listOf(PlayClientResult(PlayResponseCode.OK)), results)
    }

    @Test
    fun `non retryable setup failure fails all queued callers without scheduling`() {
        val client = FakePlayBillingClient().apply {
            isReady = false
            autoCompleteConnection = false
        }
        val scheduler = FakeRetryScheduler()
        val manager = PlayBillingConnectionManager(client, scheduler)
        val results = mutableListOf<PlayClientResult>()

        manager.ensureReady(results::add)
        manager.ensureReady(results::add)
        client.connectionListener!!.onSetupFinished(PlayClientResult(PlayResponseCode.BILLING_UNAVAILABLE))

        assertEquals(1, client.startConnectionCount)
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(2, results.size)
        assertTrue(results.all { it.responseCode == PlayResponseCode.BILLING_UNAVAILABLE })
    }

    @Test
    fun `close releases sdk connection and fails outstanding work`() {
        val client = FakePlayBillingClient().apply {
            isReady = false
            autoCompleteConnection = false
        }
        val manager = PlayBillingConnectionManager(client, FakeRetryScheduler())
        val results = mutableListOf<PlayClientResult>()
        manager.ensureReady(results::add)

        manager.close()

        assertEquals(1, client.endConnectionCount)
        assertEquals(listOf(PlayResponseCode.SERVICE_DISCONNECTED), results.map { it.responseCode })
    }

    @Test
    fun `retry budget is capped and caller receives final transient failure`() {
        val client = FakePlayBillingClient().apply {
            isReady = false
            autoCompleteConnection = false
        }
        val scheduler = FakeRetryScheduler()
        val manager = PlayBillingConnectionManager(
            client,
            scheduler,
            ExponentialBackoff(initialDelayMillis = 10, maximumDelayMillis = 20, maximumAttempts = 2),
        )
        val results = mutableListOf<PlayClientResult>()
        manager.ensureReady(results::add)

        client.connectionListener!!.onSetupFinished(PlayClientResult(PlayResponseCode.NETWORK_ERROR))
        scheduler.runNext()
        client.connectionListener!!.onSetupFinished(PlayClientResult(PlayResponseCode.NETWORK_ERROR))
        scheduler.runNext()
        client.connectionListener!!.onSetupFinished(PlayClientResult(PlayResponseCode.NETWORK_ERROR))

        assertEquals(3, client.startConnectionCount)
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(listOf(PlayResponseCode.NETWORK_ERROR), results.map { it.responseCode })
    }
}
