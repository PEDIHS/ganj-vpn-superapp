package com.ganj.vpn.core.controlapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceApiTest {
    @Test
    fun `device list maps only contract fields and current state`() {
        val transport = FakeTransport().apply {
            enqueue(
                200,
                successEnvelope(
                    """[
                      {
                        "id":"$DEVICE_ID",
                        "platform":"android",
                        "name":null,
                        "app_version":null,
                        "status":"active",
                        "current":true,
                        "last_seen_at":"2026-08-30T08:00:00Z"
                      }
                    ]""",
                ),
            )
        }
        val api = DefaultDeviceApi(transport, AuthTokenProvider { TOKEN }, AuthenticationEventSink.NONE)

        val result = api.devices().requireSuccess().value

        assertEquals(1, result.size)
        assertEquals(DEVICE_ID, result.single().id)
        assertEquals("android", result.single().platform)
        assertEquals(TrustedDeviceStatus.ACTIVE, result.single().status)
        assertTrue(result.single().current)
        assertEquals(null, result.single().name)
        assertEquals(null, result.single().appVersion)
        assertEquals("2026-08-30T08:00:00Z", result.single().lastSeenAt)
        assertEquals(HttpMethod.GET, transport.requests.single().method)
        assertEquals("/me/devices", transport.requests.single().pathAndQuery)
    }

    @Test
    fun `missing current boolean fails closed`() {
        val transport = FakeTransport().apply {
            enqueue(
                200,
                successEnvelope(
                    """[
                      {
                        "id":"$DEVICE_ID",
                        "platform":"android",
                        "status":"active",
                        "last_seen_at":null
                      }
                    ]""",
                ),
            )
        }
        val api = DefaultDeviceApi(transport, AuthTokenProvider { TOKEN }, AuthenticationEventSink.NONE)

        val error = api.devices().requireFailure().error

        assertTrue(error is ApiError.Protocol)
    }

    @Test
    fun `revoke sends delete and accepts only 204`() {
        val transport = FakeTransport().apply {
            enqueue(204, "", headers = mapOf("x-request-id" to REQUEST_ID))
        }
        val api = DefaultDeviceApi(transport, AuthTokenProvider { TOKEN }, AuthenticationEventSink.NONE)

        api.revoke(DEVICE_ID).requireSuccess()

        assertEquals(HttpMethod.DELETE, transport.requests.single().method)
        assertEquals("/me/devices/$DEVICE_ID", transport.requests.single().pathAndQuery)
    }

    @Test
    fun `revoke current-device conflict remains explicit`() {
        val transport = FakeTransport().apply {
            enqueue(409, errorEnvelope("cannot_revoke_current_device"))
        }
        val api = DefaultDeviceApi(transport, AuthTokenProvider { TOKEN }, AuthenticationEventSink.NONE)

        val error = api.revoke(DEVICE_ID).requireFailure().error

        assertTrue(error is ApiError.Conflict)
        assertEquals("cannot_revoke_current_device", (error as ApiError.Conflict).code)
    }

    @Test
    fun `invalid device id is rejected before network`() {
        val transport = FakeTransport()
        val api = DefaultDeviceApi(transport, AuthTokenProvider { TOKEN }, AuthenticationEventSink.NONE)

        val error = api.revoke("not-a-device").requireFailure().error

        assertTrue(error is ApiError.Validation)
        assertTrue(transport.requests.isEmpty())
    }
}
