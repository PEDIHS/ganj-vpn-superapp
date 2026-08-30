package com.ganj.vpn.core.controlapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletApiTest {
    private val tokenProvider = AuthTokenProvider { TOKEN }

    @Test
    fun `wallet maps authenticated balance without exposing token`() {
        val transport = FakeTransport().apply {
            enqueue(
                200,
                successEnvelope("""{"balance":{"amount_minor":5000000,"currency":"IRR"}}"""),
            )
        }
        val api = DefaultWalletApi(transport, tokenProvider, AuthenticationEventSink.NONE)

        val result = api.wallet().requireSuccess()

        assertEquals(5_000_000L, result.value.balance.amountMinor)
        assertEquals("IRR", result.value.balance.currency)
        assertEquals("/wallet", transport.requests.single().pathAndQuery)
        assertEquals("Bearer header.payload.signature-value", transport.requests.single().headers["Authorization"])
        assertFalse(transport.requests.single().toString().contains("signature-value"))
    }

    @Test
    fun `transactions map immutable ledger page and metadata`() {
        val body = """
            {
              "data": [
                {
                  "id":"70000000-0000-4000-8000-000000000001",
                  "type":"purchase",
                  "direction":"debit",
                  "amount":{"amount_minor":2990000,"currency":"IRR"},
                  "balance_after":{"amount_minor":2010000,"currency":"IRR"},
                  "reference_type":"order",
                  "reference_id":"safe-order-reference",
                  "description":"خرید اشتراک",
                  "created_at":"2026-08-30T08:00:00Z"
                }
              ],
              "meta": {
                "request_id":"$REQUEST_ID",
                "server_time":"2026-08-30T08:00:01Z",
                "has_more":true,
                "next_cursor":"YWJjZGVmZ2g"
              },
              "error":null
            }
        """.trimIndent()
        val transport = FakeTransport().apply { enqueue(200, body) }
        val api = DefaultWalletApi(transport, tokenProvider, AuthenticationEventSink.NONE)

        val result = api.transactions(limit = 20).requireSuccess()

        assertEquals(1, result.value.items.size)
        assertEquals(WalletTransactionType.PURCHASE, result.value.items.single().type)
        assertEquals(WalletTransactionDirection.DEBIT, result.value.items.single().direction)
        assertEquals(2_990_000L, result.value.items.single().amount.amountMinor)
        assertEquals("safe-order-reference", result.value.items.single().referenceId)
        assertTrue(result.value.hasMore)
        assertEquals("YWJjZGVmZ2g", result.value.nextCursor)
        assertEquals("/wallet/transactions?limit=20", transport.requests.single().pathAndQuery)
    }

    @Test
    fun `malformed cursor is rejected before transport`() {
        val transport = FakeTransport()
        val api = DefaultWalletApi(transport, tokenProvider, AuthenticationEventSink.NONE)

        val result = api.transactions(cursor = "%%%")

        assertTrue(result is ApiResult.Failure)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `unknown transaction type fails closed as protocol error`() {
        val body = """
            {
              "data": [
                {
                  "id":"70000000-0000-4000-8000-000000000001",
                  "type":"mystery",
                  "direction":"credit",
                  "amount":{"amount_minor":1,"currency":"IRR"},
                  "balance_after":{"amount_minor":1,"currency":"IRR"},
                  "reference_type":null,
                  "reference_id":null,
                  "description":null,
                  "created_at":"2026-08-30T08:00:00Z"
                }
              ],
              "meta":{"request_id":"$REQUEST_ID","server_time":"2026-08-30T08:00:01Z","has_more":false},
              "error":null
            }
        """.trimIndent()
        val transport = FakeTransport().apply { enqueue(200, body) }
        val api = DefaultWalletApi(transport, tokenProvider, AuthenticationEventSink.NONE)

        val result = api.transactions()

        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is ApiError.Protocol)
    }
}
