package com.ganj.vpn.core.controlapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationApiTest {
    private val tokenProvider = AuthTokenProvider { TOKEN }

    @Test
    fun `notification page maps unread count kind and safe action`() {
        val body = """
            {
              "data":[{
                "id":"70000000-0000-4000-8000-000000000001",
                "kind":"subscription_expiry",
                "title":"تمدید سرویس",
                "body":"سرویس شما به پایان دوره نزدیک شده است.",
                "read":false,
                "action":{"type":"open_subscription","id":"80000000-0000-4000-8000-000000000001"},
                "created_at":"2026-08-30T09:00:00Z"
              }],
              "meta":{
                "request_id":"$REQUEST_ID",
                "server_time":"2026-08-30T09:00:01Z",
                "has_more":true,
                "next_cursor":"YWJjZGVmZ2g",
                "unread_count":3
              },
              "error":null
            }
        """.trimIndent()
        val transport = FakeTransport().apply { enqueue(200, body) }
        val api = DefaultNotificationApi(transport, tokenProvider, AuthenticationEventSink.NONE)

        val result = api.notifications(limit = 30).requireSuccess()

        assertEquals(3, result.value.unreadCount)
        assertTrue(result.value.hasMore)
        assertEquals(NotificationKind.SUBSCRIPTION_EXPIRY, result.value.items.single().kind)
        assertEquals(NotificationActionType.OPEN_SUBSCRIPTION, result.value.items.single().action?.type)
        assertEquals("/notifications?limit=30", transport.requests.single().pathAndQuery)
        assertFalse(transport.requests.single().toString().contains("signature-value"))
    }

    @Test
    fun `mark read uses owner scoped notification endpoint contract`() {
        val transport = FakeTransport().apply { enqueue(204, "") }
        val api = DefaultNotificationApi(transport, tokenProvider, AuthenticationEventSink.NONE)

        api.markRead("70000000-0000-4000-8000-000000000001").requireSuccess()

        assertEquals(HttpMethod.POST, transport.requests.single().method)
        assertEquals(
            "/notifications/70000000-0000-4000-8000-000000000001/read",
            transport.requests.single().pathAndQuery,
        )
    }

    @Test
    fun `preferences update sends complete boolean contract`() {
        val expected = NotificationPreferences(
            subscriptionExpiry = true,
            purchaseSuccess = true,
            paymentFailure = true,
            maintenance = true,
            securityUpdate = true,
            supportReply = true,
            marketing = false,
        )
        val response = """
            {
              "data":{
                "subscription_expiry":true,
                "purchase_success":true,
                "payment_failure":true,
                "maintenance":true,
                "security_update":true,
                "support_reply":true,
                "marketing":false
              },
              "meta":{"request_id":"$REQUEST_ID","server_time":"2026-08-30T09:00:01Z","has_more":false},
              "error":null
            }
        """.trimIndent()
        val transport = FakeTransport().apply { enqueue(200, response) }
        val api = DefaultNotificationApi(transport, tokenProvider, AuthenticationEventSink.NONE)

        val result = api.updatePreferences(expected).requireSuccess()

        assertEquals(expected, result.value)
        assertEquals(HttpMethod.PUT, transport.requests.single().method)
        assertEquals("/notifications/preferences", transport.requests.single().pathAndQuery)
        val sent = transport.requests.single().body!!.toString(Charsets.UTF_8)
        assertTrue(sent.contains("\"marketing\":false"))
        assertTrue(sent.contains("\"security_update\":true"))
    }

    @Test
    fun `unknown notification kind fails closed`() {
        val body = """
            {
              "data":[{
                "id":"70000000-0000-4000-8000-000000000001",
                "kind":"unknown",
                "title":"عنوان",
                "body":"متن",
                "read":false,
                "action":null,
                "created_at":"2026-08-30T09:00:00Z"
              }],
              "meta":{"request_id":"$REQUEST_ID","server_time":"2026-08-30T09:00:01Z","has_more":false,"unread_count":1},
              "error":null
            }
        """.trimIndent()
        val api = DefaultNotificationApi(FakeTransport().apply { enqueue(200, body) }, tokenProvider, AuthenticationEventSink.NONE)

        val result = api.notifications()

        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is ApiError.Protocol)
    }
}
