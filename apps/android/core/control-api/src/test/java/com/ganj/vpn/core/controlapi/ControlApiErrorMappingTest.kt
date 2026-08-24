package com.ganj.vpn.core.controlapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlApiErrorMappingTest {
    @Test
    fun `missing token fails locally without a network request`() {
        val transport = FakeTransport()
        val client = ControlApiClient(transport, AuthTokenProvider { null }, AuthenticationEventSink.NONE)

        val result = client.getMyServices()

        assertTrue(result.requireFailure().error is ApiError.AuthenticationRequired)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `401 expires authentication and notifies the session owner`() {
        val transport = FakeTransport().apply { enqueue(401, errorEnvelope("TOKEN_EXPIRED")) }
        var expiredRequestId: String? = null
        val client = ControlApiClient(
            transport,
            AuthTokenProvider { TOKEN },
            AuthenticationEventSink { expiredRequestId = it },
        )

        val error = client.getMyServices().requireFailure().error

        assertTrue(error is ApiError.AuthenticationExpired)
        assertEquals(REQUEST_ID, expiredRequestId)
    }

    @Test
    fun `403 maps to entitlement forbidden`() {
        assertTrue(errorFor(403, "ENTITLEMENT_EXPIRED") is ApiError.Forbidden)
    }

    @Test
    fun `409 maps to idempotency conflict`() {
        assertTrue(errorFor(409, "IDEMPOTENCY_CONFLICT") is ApiError.Conflict)
    }

    @Test
    fun `429 preserves bounded numeric retry-after`() {
        val transport = FakeTransport().apply {
            enqueue(429, errorEnvelope("RATE_LIMITED", true), mapOf("Retry-After" to "120"))
        }
        val client = ControlApiClient(transport, AuthTokenProvider { TOKEN }, AuthenticationEventSink.NONE)

        val error = client.getMyServices().requireFailure().error as ApiError.RateLimited

        assertEquals(120L, error.retryAfterSeconds)
    }

    @Test
    fun `all 5xx responses are retryable server failures`() {
        listOf(500, 502, 503, 504).forEach { status ->
            val error = errorFor(status, "UPSTREAM_UNAVAILABLE") as ApiError.Server
            assertEquals(status, error.statusCode)
            assertTrue(error.retryable)
        }
    }

    @Test
    fun `network taxonomy is preserved without throwing transport details`() {
        val transport = FakeTransport().apply { enqueueFailure(NetworkFailure.TIMEOUT) }
        val client = ControlApiClient(transport, AuthTokenProvider { TOKEN }, AuthenticationEventSink.NONE)

        val error = client.getMyServices().requireFailure().error as ApiError.Network

        assertEquals(NetworkFailure.TIMEOUT, error.kind)
    }

    private fun errorFor(status: Int, code: String): ApiError {
        val transport = FakeTransport().apply { enqueue(status, errorEnvelope(code)) }
        val client = ControlApiClient(transport, AuthTokenProvider { TOKEN }, AuthenticationEventSink.NONE)
        return client.getMyServices().requireFailure().error
    }
}
