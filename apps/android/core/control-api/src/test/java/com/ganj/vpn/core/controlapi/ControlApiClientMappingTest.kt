package com.ganj.vpn.core.controlapi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlApiClientMappingTest {
    private val transport = FakeTransport()
    private val tokenProvider = AuthTokenProvider { TOKEN }
    private val client = ControlApiClient(transport, tokenProvider, AuthenticationEventSink.NONE)
    private val serverCatalog = ServerCatalogClient(transport, tokenProvider, AuthenticationEventSink.NONE)

    @Test
    fun `catalog is mapped from the OpenAPI envelope`() {
        transport.enqueue(
            200,
            successEnvelope(
                """
                [{
                  "id":"$PLAN_ID",
                  "code":"vip-monthly",
                  "name":"VIP Monthly",
                  "tier":"vip",
                  "duration_days":30,
                  "traffic_limit_bytes":null,
                  "device_limit":3,
                  "features":["all_locations","smart_connect"],
                  "price":{"amount_minor":1299,"currency":"EUR"}
                }]
                """.trimIndent(),
            ),
        )

        val result = client.getCatalog(PurchaseChannel.PLAY).requireSuccess()

        assertEquals(1, result.value.size)
        assertEquals(SubscriptionTier.VIP, result.value.single().tier)
        assertEquals(Money(1299, "EUR"), result.value.single().price)
        assertEquals(REQUEST_ID, result.metadata.requestId)
        assertEquals("/store/plans?channel=play", transport.requests.single().pathAndQuery)
    }

    @Test
    fun `my services maps entitlement fields without raw configuration`() {
        transport.enqueue(
            200,
            successEnvelope(
                """
                [{
                  "id":"$SERVICE_ID",
                  "name":"Germany VIP",
                  "status":"active",
                  "tier":"vip",
                  "country_code":"DE",
                  "traffic_limit_bytes":107374182400,
                  "traffic_used_bytes":1024,
                  "expires_at":"2026-09-24T12:00:00Z",
                  "device_limit":3,
                  "allowed_protocols":["vless","trojan"]
                }]
                """.trimIndent(),
            ),
        )

        val result = client.getMyServices().requireSuccess()
        val service = result.value.single()

        assertEquals(SERVICE_ID, service.id)
        assertEquals(setOf(VpnProtocol.VLESS, VpnProtocol.TROJAN), service.allowedProtocols)
        assertFalse(UserService::class.java.declaredFields.any { it.name.contains("config", ignoreCase = true) })
        assertFalse(UserService::class.java.declaredFields.any { it.name.contains("uri", ignoreCase = true) })
    }

    @Test
    fun `checkout sends idempotency key and maps fulfilled order`() {
        transport.enqueue(
            201,
            successEnvelope(
                """
                {
                  "id":"$ORDER_ID",
                  "status":"fulfilled",
                  "channel":"play",
                  "total":{"amount_minor":1299,"currency":"EUR"},
                  "entitlement_service_id":"$SERVICE_ID"
                }
                """.trimIndent(),
            ),
        )

        val result = client.createCheckout(
            CheckoutCommand(
                planId = PLAN_ID,
                channel = PurchaseChannel.PLAY,
                idempotencyKey = "0198f06f-7e98-7000-8000-000000000001",
            ),
        ).requireSuccess()

        assertEquals(OrderStatus.FULFILLED, result.value.status)
        assertEquals(SERVICE_ID, result.value.entitlementServiceId)
        val request = transport.requests.single()
        assertEquals("0198f06f-7e98-7000-8000-000000000001", request.headers["Idempotency-Key"])
        assertTrue(String(request.body!!).contains("\"plan_id\":\"$PLAN_ID\""))
        assertFalse(request.toString().contains("header.payload"))
    }

    @Test
    fun `connection response is retained as an opaque one-time vault lease`() {
        transport.enqueue(
            201,
            successEnvelope(
                """
                {
                  "profile_id":"$PROFILE_ID",
                  "server_id":"$SERVER_ID",
                  "algorithm":"X25519+HKDF-SHA256+AES-256-GCM",
                  "key_version":"kms-v3",
                  "nonce":"AQIDBA==",
                  "ciphertext":"BQYHCA==",
                  "expires_at":"2026-08-24T12:05:00Z"
                }
                """.trimIndent(),
            ),
        )
        val vault = InMemoryConnectionEnvelopeVault()
        val repository = DefaultControlApiRepository(client, serverCatalog, vault)

        val result = repository.prepareConnection(validConnectionCommand()).requireSuccess()
        val lease = result.value

        assertEquals(PROFILE_ID, lease.profileId)
        assertFalse(lease.toString().contains("AQIDBA"))
        assertFalse(ConnectionProfileLease::class.java.declaredFields.any { it.name.contains("cipher", true) })
        assertFalse(ConnectionProfileLease::class.java.declaredFields.any { it.name.contains("nonce", true) })
        val stored = vault.take(lease.vaultHandle)
        assertNotNull(stored)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), stored!!.nonce)
        assertArrayEquals(byteArrayOf(5, 6, 7, 8), stored.ciphertext)
        assertEquals(null, vault.take(lease.vaultHandle))
    }

    @Test
    fun `profile for a different server is destroyed and rejected`() {
        val differentServer = "00000000-0000-4000-8000-000000000088"
        transport.enqueue(
            201,
            successEnvelope(
                """
                {
                  "profile_id":"$PROFILE_ID",
                  "server_id":"$differentServer",
                  "algorithm":"X25519+HKDF-SHA256+AES-256-GCM",
                  "key_version":"kms-v3",
                  "nonce":"AQIDBA==",
                  "ciphertext":"BQYHCA==",
                  "expires_at":"2026-08-24T12:05:00Z"
                }
                """.trimIndent(),
            ),
        )

        val result = DefaultControlApiRepository(client, serverCatalog, InMemoryConnectionEnvelopeVault())
            .prepareConnection(validConnectionCommand())

        assertTrue(result is ApiResult.Failure)
        assertTrue(result.requireFailure().error is ApiError.Protocol)
    }

    private fun validConnectionCommand() = ConnectionProfileCommand(
        serviceId = SERVICE_ID,
        deviceId = DEVICE_ID,
        serverId = SERVER_ID,
        clientNonce = "0123456789abcdef0123456789abcdef",
        deviceProof = "abcdef0123456789abcdef0123456789",
    )
}
