package com.ganj.vpn.core.controlapi

import java.nio.charset.StandardCharsets

internal class FakeTransport : HttpTransport {
    private val queued = ArrayDeque<TransportResult>()
    val requests = mutableListOf<HttpRequest>()

    fun enqueue(
        status: Int,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ) {
        queued += TransportResult.Response(
            HttpResponse(
                statusCode = status,
                headers = headers.mapKeys { it.key.lowercase() },
                body = body.toByteArray(StandardCharsets.UTF_8),
            ),
        )
    }

    fun enqueueFailure(kind: NetworkFailure) {
        queued += TransportResult.Failure(kind)
    }

    override fun execute(request: HttpRequest): TransportResult {
        requests += request
        return queued.removeFirstOrNull() ?: error("No fake response queued")
    }
}

internal fun successEnvelope(data: String): String = """
    {
      "data": $data,
      "meta": {
        "request_id": "$REQUEST_ID",
        "server_time": "2026-08-24T12:00:00Z",
        "has_more": false
      },
      "error": null
    }
""".trimIndent()

internal fun errorEnvelope(code: String, retryable: Boolean = false): String = """
    {
      "data": null,
      "meta": {
        "request_id": "$REQUEST_ID",
        "server_time": "2026-08-24T12:00:00Z"
      },
      "error": {
        "code": "$code",
        "message": "safe error",
        "retryable": $retryable,
        "details": {}
      }
    }
""".trimIndent()

internal val TOKEN = AccessToken.from("header.payload.signature-value")
internal const val REQUEST_ID = "00000000-0000-4000-8000-000000000099"
internal const val PLAN_ID = "00000000-0000-4000-8000-000000000001"
internal const val SERVICE_ID = "00000000-0000-4000-8000-000000000002"
internal const val SERVER_ID = "00000000-0000-4000-8000-000000000003"
internal const val DEVICE_ID = "00000000-0000-4000-8000-000000000004"
internal const val PROFILE_ID = "00000000-0000-4000-8000-000000000005"
internal const val ORDER_ID = "00000000-0000-4000-8000-000000000006"

@Suppress("UNCHECKED_CAST")
internal fun <T> ApiResult<T>.requireSuccess(): ApiResult.Success<T> =
    this as? ApiResult.Success<T> ?: error("Expected success, got $this")

internal fun <T> ApiResult<T>.requireFailure(): ApiResult.Failure =
    this as? ApiResult.Failure ?: error("Expected failure, got $this")
