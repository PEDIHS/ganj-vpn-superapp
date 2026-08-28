package com.ganj.vpn.core.controlapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ServerCatalogClientTest {
    private val transport = FakeTransport()
    private val client = ServerCatalogClient(
        transport = transport,
        tokenProvider = AuthTokenProvider { TOKEN },
        authenticationEvents = AuthenticationEventSink.NONE,
    )

    @Test
    fun `free server catalog maps only safe metadata`() {
        transport.enqueue(
            200,
            successEnvelope(
                """
                [{
                  "id":"$SERVER_ID",
                  "code":"free-de-1",
                  "name":"Germany Free",
                  "country_code":"DE",
                  "city":"Frankfurt",
                  "tier":"free",
                  "status":"active",
                  "load_ratio":0.31,
                  "latency_hint_ms":72,
                  "favorite":false,
                  "protocols":["vless"]
                }]
                """.trimIndent(),
            ),
        )

        val result = client.getServers(tier = SubscriptionTier.FREE).requireSuccess()
        val server = result.value.single()

        assertEquals(SERVER_ID, server.id)
        assertEquals(SubscriptionTier.FREE, server.tier)
        assertEquals(ServerStatus.ACTIVE, server.status)
        assertEquals(setOf(VpnProtocol.VLESS), server.protocols)
        assertEquals("/servers?tier=free", transport.requests.single().pathAndQuery)
        assertFalse(ManagedServer::class.java.declaredFields.any { field ->
            val name = field.name.lowercase()
            name.contains("credential") || name.contains("endpoint") || name.contains("port") ||
                name.contains("subscription") || name.contains("config") || name.contains("uri")
        })
    }

    @Test
    fun `catalog filters compose without manual config fields`() {
        transport.enqueue(200, successEnvelope("[]"))

        client.getServers(
            tier = SubscriptionTier.FREE,
            countryCode = "TR",
            protocol = VpnProtocol.VLESS,
        ).requireSuccess()

        assertEquals("/servers?tier=free&country=TR&protocol=vless", transport.requests.single().pathAndQuery)
    }

    @Test
    fun `invalid country is rejected before network`() {
        client.getServers(countryCode = "de").requireFailure()
        assertEquals(0, transport.requests.size)
    }
}
