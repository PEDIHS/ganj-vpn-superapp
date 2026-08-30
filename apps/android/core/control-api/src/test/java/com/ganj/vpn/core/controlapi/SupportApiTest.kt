package com.ganj.vpn.core.controlapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportApiTest {
    private val tokenProvider = AuthTokenProvider { TOKEN }
    private val ticketId = "70000000-0000-4000-8000-000000000001"
    private val clientTicketId = "72000000-0000-4000-8000-000000000001"
    private val clientMessageId = "71000000-0000-4000-8000-000000000001"

    @Test
    fun `ticket list maps owner support summaries`() {
        val transport = FakeTransport().apply {
            enqueue(
                200,
                successEnvelope("""[{
                  "id":"$ticketId",
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
                  "id":"$ticketId",
                  "public_code":"SUP-1001",
                  "category":"connection",
                  "priority":"high",
                  "subject":"اتصال برقرار نمی‌شود",
                  "status":"waiting_user",
                  "created_at":"2026-08-30T09:00:00Z",
                  "updated_at":"2026-08-30T09:20:00Z",
                  "messages":[
                    {"id":"71000000-0000-4000-8000-000000000011","sender_role":"user","body":"اتصال روی اینترنت همراه برقرار نمی‌شود.","created_at":"2026-08-30T09:00:00Z"},
                    {"id":"71000000-0000-4000-8000-000000000012","sender_role":"support","body":"لطفاً دوباره اتصال را امتحان کنید.","created_at":"2026-08-30T09:20:00Z"}
                  ]
                }"""),
            )
        }
        val api = DefaultSupportApi(transport, tokenProvider, AuthenticationEventSink.NONE)

        val result = api.ticket(ticketId).requireSuccess()

        assertEquals(SupportPriority.HIGH, result.value.ticket.priority)
        assertEquals(2, result.value.messages.size)
        assertEquals(SupportSenderRole.SUPPORT, result.value.messages.last().senderRole)
    }

    @Test
    fun `create ticket sends caller owned idempotency identifier`() {
        val transport = FakeTransport().apply {
            enqueue(
                201,
                successEnvelope("""{
                  "id":"$ticketId",
                  "public_code":"SUP-1001",
                  "category":"account",
                  "priority":"normal",
                  "subject":"مشکل ورود حساب",
                  "status":"open",
                  "created_at":"2026-08-30T09:00:00Z",
                  "updated_at":"2026-08-30T09:00:00Z"
                }"""),
            )
        }
        val api = DefaultSupportApi(transport, tokenProvider, AuthenticationEventSink.NONE)

        api.createTicket(
            clientTicketId,
            SupportCategory.ACCOUNT,
            SupportPriority.NORMAL,
            "مشکل ورود حساب",
            "ورود حساب در برنامه برای من کامل نمی‌شود.",
        ).requireSuccess()

        val request = transport.requests.single()
        assertEquals("/support/tickets", request.pathAndQuery)
        assertTrue(request.body!!.toString(Charsets.UTF_8).contains(clientTicketId))
    }

    @Test
    fun `reply sends stable caller owned message identifier`() {
        val transport = FakeTransport().apply {
            enqueue(
                201,
                successEnvelope("""{
                  "id":"$clientMessageId",
                  "sender_role":"user",
                  "body":"لطفاً دوباره این درخواست را بررسی کنید.",
                  "created_at":"2026-08-30T09:30:00Z"
                }"""),
            )
        }
        val api = DefaultSupportApi(transport, tokenProvider, AuthenticationEventSink.NONE)

        api.reply(ticketId, clientMessageId, "لطفاً دوباره این درخواست را بررسی کنید.").requireSuccess()

        val request = transport.requests.single()
        assertEquals("/support/tickets/$ticketId/messages", request.pathAndQuery)
        assertTrue(request.body!!.toString(Charsets.UTF_8).contains(clientMessageId))
    }

    @Test
    fun `unknown support status fails closed`() {
        val transport = FakeTransport().apply {
            enqueue(
                200,
                successEnvelope("""[{
                  "id":"$ticketId",
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
    fun `invalid support identifiers are rejected before network`() {
        val transport = FakeTransport()
        val api = DefaultSupportApi(transport, tokenProvider, AuthenticationEventSink.NONE)

        val ticket = api.ticket("not-a-ticket")
        val create = api.createTicket(
            "not-a-client-ticket",
            SupportCategory.OTHER,
            SupportPriority.NORMAL,
            "عنوان معتبر",
            "این متن برای یک درخواست پشتیبانی معتبر است.",
        )
        val reply = api.reply(ticketId, "not-a-message", "متن پاسخ")

        assertTrue(ticket is ApiResult.Failure)
        assertTrue(create is ApiResult.Failure)
        assertTrue(reply is ApiResult.Failure)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `closed ticket conflict remains explicit`() {
        val transport = FakeTransport().apply {
            enqueue(409, errorEnvelope("support_ticket_closed", retryable = false))
        }
        val api = DefaultSupportApi(transport, tokenProvider, AuthenticationEventSink.NONE)

        val result = api.reply(
            ticketId,
            clientMessageId,
            "لطفاً دوباره این درخواست را بررسی کنید.",
        )

        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is ApiError.Conflict)
        assertEquals("support_ticket_closed", result.error.code)
    }
}
