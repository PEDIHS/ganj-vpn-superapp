package com.ganj.vpn.composition

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveConnectionProfileContextProviderTest {
    @Test
    fun `connection profile proof signs the public versioned route`() {
        val serviceId = "431491ae-6f28-4c04-b062-d17019b9562e"

        assertEquals(
            "/v1/services/$serviceId/connection-profile",
            connectionProfileProofPath(serviceId),
        )
    }
}
