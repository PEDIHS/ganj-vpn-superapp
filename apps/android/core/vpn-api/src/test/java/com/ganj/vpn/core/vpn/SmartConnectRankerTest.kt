package com.ganj.vpn.core.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartConnectRankerTest {
    private val ranker = SmartConnectRanker()

    @Test
    fun `unavailable servers are excluded`() {
        val result = ranker.rank(
            listOf(
                probe("offline", latency = 5, available = false),
                probe("online", latency = 80),
            ),
        )

        assertEquals(listOf("online"), result.map { it.serverId })
    }

    @Test
    fun `healthy low latency server wins`() {
        val result = ranker.rank(
            listOf(
                probe("fast", latency = 35, loss = 0.0, load = 18.0, speed = 340.0),
                probe("busy", latency = 140, loss = 4.0, load = 88.0, speed = 110.0),
            ),
        )

        assertEquals("fast", result.first().serverId)
        assertTrue(result.first().score > result.last().score)
    }

    @Test
    fun `tie is deterministic`() {
        val result = ranker.rank(listOf(probe("b", 50), probe("a", 50)))
        assertEquals(listOf("a", "b"), result.map { it.serverId })
    }

    private fun probe(
        id: String,
        latency: Int,
        loss: Double = 1.0,
        load: Double = 25.0,
        region: Double = 0.2,
        speed: Double = 200.0,
        available: Boolean = true,
    ) = ServerProbe(id, latency, loss, load, region, speed, available)
}
