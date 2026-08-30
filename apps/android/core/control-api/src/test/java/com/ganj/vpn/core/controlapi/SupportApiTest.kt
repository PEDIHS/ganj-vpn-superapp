package com.ganj.vpn.core.controlapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportApiTest {
    private val tokenProvider = AuthTokenProvider { TOKEN }

    @Test
    fun `ticket list maps owner support summaries`() {
        val transport = FakeTransport().apply {
            enqueue(
                200,
                successEnvelope("""[{
                  "id":"70000000-0000-4000-8000-000000000001",
                  "public_code":"SUP-1001",
                  "category":"account",
                  "priority":"normal",
                  "subject":"مشکل ورود حساب",
                  "status":"waiting_support",
                  "created_at":"2026-08-30T09:00:00Z",
                  "updated_at":"2026-08-30T09:10:00Z"
                }]"""),
            )
        }
        val api = DefaultSupportApi(transport, tokenProvider, AuthenticationEventSink.NONE)

        val result = api.tickets().requireSuccess()

        assertEquals(1, result.value.size)
        assertEquals(SupportCategory.ACCOUNT, result.value.single().category)
        assertEquals(SupportStatus.WAITING_SUPPORT, result.value.single().status)
        assertEquals("/support/tickets", transport.requests.single().pathAndQuery)
    }

    @Test
    fun `ticket detail maps ordered conversation roles`() {
        val transport = FakeTransport().apply {
            enqueue(
                200,
                successEnvelope("""{
                  "id":"70000000-0000-4000-8000-000000000001",
                  "public_code":"SUP-1001",
                  "category":"connection",
                  "priority":"high",
                  "subject":"اتصال برقرار نمی‌شود",
                  "status":"waiting_user",
                  "created_at":"2026-08-30T09:00:00Z",
                  "updated_at":"2026-08-30T09:20:00Z",
                  "messages":[
                    {"id":"71000000-0000-4000-8000-000000000001","sender_role":"user","body":"اتصال روی اینترنت همراه برقرار نمی‌شود.","created_at":"2026-08-30T09:00:00Z"},
                    {"id":"71000000-0000-4000-8000-000000000002","sender_role":"support","body":"لطفاً دوباره اتصال را امتحان کنید.","created_at":"2026-08-30T09:20:00Z"}
                  ]
                }"""),
            )
        }
        val api = DefaultSupportApi(transport, tokenProvider, AuthenticationEventSink.NONE)

        val result = api.ticket("70000000-0000-4000-8000-000000000001").requireSuccess()

        assertEquals(SupportPriority.HIGH, result.value.ticket.priority)
        assertEquals(2, result.value.messages.size)
        assertEquals(SupportSenderRole.SUPPORT, result.value.messages.last().senderRole)
    }

    @Test
    fun `unknown support status fails closed`() {
        val transport = FakeTransport().apply {
            enqueue(
                200,
                successEnvelope("""[{
                  "id":"70000000-0000-4000-8000-000000000001",
                  "public_code":"SUP-1001",
                  "category":"account",
                  "priority":"normal",
                  "subject":"مشکل ورود حساب",
                  "status":"mystery",
                  "created_at":"2026-08-30T09:00:00Z",
                  "updated_at":"2026-08-30T09:10:00Z"
                }]"""),
            )
        }
        val api = DefaultSupportApi(transport, tokenProvider, AuthenticationEventSink.NONE)

        val result = api.tickets()

        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is ApiError.Protocol)
    }

    @Test
    fun `invalid ticket id is rejected before network`() {
        val transport = FakeTransport()
        val api = DefaultSupportApi(transport, tokenProvider, AuthenticationEventSink.NONE)

        val result = api.ticket("not-a-ticket")

        assertTrue(result is ApiResult.Failure)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `closed ticket conflict remains explicit`() {
        val transport = FakeTransport().apply {
            enqueue(409, errorEnvelope("support_ticket_closed", retryable = false))
        }
        val api = DefaultSupportApi(transport, tokenProvider, AuthenticationEventSink.NONE)

        val result = api.reply(
            "70000000-0000-4000-8000-000000000001",
            "لطفاً دوباره این درخواست را بررسی کنید.",
        )

        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is ApiError.Conflict)
        assertEquals("support_ticket_closed", result.error.code)
    }
}
