package com.ganj.vpn.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TcpLatencySamplerTest {
    @Test fun `uses median rather than inflated outlier`() {
        val samples = listOf(43L, 290L, 49L).iterator()
        assertEquals(49L, TcpLatencySampler { samples.next() }.measure())
    }

    @Test fun `failed first connection exits quickly`() {
        var calls = 0
        assertNull(TcpLatencySampler { calls++; null }.measure())
        assertEquals(1, calls)
    }

    @Test fun `one success cannot be misrepresented as stable RTT`() {
        val samples = listOf(36L, null, null).iterator()
        assertNull(TcpLatencySampler { samples.next() }.measure())
    }

    @Test fun `two successes remain useful but are not silently minimized`() {
        val samples = listOf(40L, null, 60L).iterator()
        assertEquals(50L, TcpLatencySampler { samples.next() }.measure())
    }

    @Test fun `rejects invalid and negative measurements`() {
        val samples = listOf(-1L, 50L, 70L).iterator()
        assertNull(TcpLatencySampler { samples.next() }.measure())
    }
}
