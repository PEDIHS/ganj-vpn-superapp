package com.ganj.vpn.core.controlapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshingHttpTransportTest {
    @Test
    fun `401 refreshes once replays the same request and zeroizes caller body`() {
        val delegate = FakeTransport().apply {
            enqueue(401, errorEnvelope("TOKEN_EXPIRED"), mapOf("X-Request-Id" to REQUEST_ID))
            enqueue(200, successEnvelope("[]"))
        }
        val provider = TestRefreshingProvider()
        var notified: String? = null
        val transport = RefreshingHttpTransport(
            delegate = delegate,
            tokenProvider = provider,
            authenticationEvents = AuthenticationEventSink { notified = it },
        )
        val originalBody = "{\"plan_id\":\"$PLAN_ID\"}".toByteArray()

        val result = transport.execute(
            HttpRequest(
                method = HttpMethod.POST,
                pathAndQuery = "/orders",
                headers = mapOf("Authorization" to provider.current.authorizationValue()),
                body = originalBody,
            ),
        )

        assertTrue(result is TransportResult.Response)
        assertEquals(200, (result as TransportResult.Response).value.statusCode)
        assertEquals(1, provider.refreshCalls)
        assertEquals(REQUEST_ID, notified)
        assertEquals(2, delegate.requests.size)
        assertEquals(provider.rotated.authorizationValue(), delegate.requests[1].headers["Authorization"])
        assertEquals(String(delegate.requests[0].body!!), String(delegate.requests[1].body!!))
        assertTrue("caller plaintext body must be zeroized after transport", originalBody.all { it == 0.toByte() })
    }

    @Test
    fun `a second 401 is returned without another refresh loop`() {
        val delegate = FakeTransport().apply {
            enqueue(401, errorEnvelope("TOKEN_EXPIRED"))
            enqueue(401, errorEnvelope("SESSION_REVOKED"))
        }
        val provider = TestRefreshingProvider()
        val transport = RefreshingHttpTransport(delegate, provider, AuthenticationEventSink.NONE)

        val result = transport.execute(
            HttpRequest(
                method = HttpMethod.GET,
                pathAndQuery = "/services",
                headers = mapOf("Authorization" to provider.current.authorizationValue()),
            ),
        )

        assertEquals(401, (result as TransportResult.Response).value.statusCode)
        assertEquals(1, provider.refreshCalls)
        assertEquals(2, delegate.requests.size)
    }

    @Test
    fun `plain token providers preserve existing no-retry behavior`() {
        val delegate = FakeTransport().apply { enqueue(401, errorEnvelope("TOKEN_EXPIRED")) }
        val transport = RefreshingHttpTransport(
            delegate,
            AuthTokenProvider { TOKEN },
            AuthenticationEventSink.NONE,
        )

        val result = transport.execute(
            HttpRequest(HttpMethod.GET, "/services", mapOf("Authorization" to TOKEN.authorizationValue())),
        )

        assertEquals(401, (result as TransportResult.Response).value.statusCode)
        assertEquals(1, delegate.requests.size)
    }

    private class TestRefreshingProvider : RefreshingAuthTokenProvider {
        val current = AccessToken.from("header.payload.current-signature")
        val rotated = AccessToken.from("header.payload.rotated-signature")
        var refreshCalls = 0

        override fun currentAccessToken(): AccessToken = current

        override fun refreshAccessToken(): AccessToken {
            refreshCalls += 1
            return rotated
        }
    }
}
