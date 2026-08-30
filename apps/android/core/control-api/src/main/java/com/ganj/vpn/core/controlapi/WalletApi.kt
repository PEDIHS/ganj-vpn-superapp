package com.ganj.vpn.core.controlapi

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

data class WalletSnapshot(val balance: Money)

enum class WalletTransactionType { TOPUP, PURCHASE, REFUND, ADJUSTMENT, REVERSAL }
enum class WalletTransactionDirection { CREDIT, DEBIT }

data class WalletTransaction(
    val id: String,
    val type: WalletTransactionType,
    val direction: WalletTransactionDirection,
    val amount: Money,
    val balanceAfter: Money,
    val referenceType: String?,
    val referenceId: String?,
    val description: String?,
    val createdAt: String,
)

data class WalletTransactionPage(
    val items: List<WalletTransaction>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

interface WalletApi {
    fun wallet(): ApiResult<WalletSnapshot>
    fun transactions(cursor: String? = null, limit: Int = 20): ApiResult<WalletTransactionPage>
}

object WalletApiFactory {
    fun create(
        baseUrl: String,
        tokenProvider: AuthTokenProvider,
        authenticationEvents: AuthenticationEventSink =
            (tokenProvider as? AuthenticationEventSink) ?: AuthenticationEventSink.NONE,
    ): WalletApi {
        val transport = RefreshingHttpTransport(
            delegate = UrlConnectionTransport(baseUrl),
            tokenProvider = tokenProvider,
            authenticationEvents = authenticationEvents,
        )
        return DefaultWalletApi(transport, tokenProvider, authenticationEvents)
    }
}

internal class DefaultWalletApi(
    private val transport: HttpTransport,
    private val tokenProvider: AuthTokenProvider,
    private val authenticationEvents: AuthenticationEventSink,
) : WalletApi {
    override fun wallet(): ApiResult<WalletSnapshot> = execute("/wallet") { data, _ ->
        val root = data.asObject()
        WalletSnapshot(balance = mapMoney(root.requiredObject("balance")))
    }

    override fun transactions(cursor: String?, limit: Int): ApiResult<WalletTransactionPage> {
        if (limit !in 1..50) {
            return ApiResult.Failure(ApiError.Validation(field = "limit", reason = "limit_out_of_range"))
        }
        if (cursor != null && !cursor.matches(Regex("^[A-Za-z0-9_-]{8,512}$"))) {
            return ApiResult.Failure(ApiError.Validation(field = "cursor", reason = "invalid_cursor"))
        }
        val path = buildString {
            append("/wallet/transactions?limit=")
            append(limit)
            if (cursor != null) {
                append("&cursor=")
                append(cursor)
            }
        }
        return execute(path) { data, metadata ->
            val items = data.asArray().also {
                if (it.size > 50) throw JsonProtocolException("Wallet page contains too many items")
            }.map(::mapTransaction)
            WalletTransactionPage(
                items = items,
                nextCursor = metadata.nextCursor,
                hasMore = metadata.hasMore,
            )
        }
    }

    private fun <T> execute(
        path: String,
        mapper: (JsonValue, ResponseMetadata) -> T,
    ): ApiResult<T> {
        val token = tokenProvider.currentAccessToken()
            ?: return ApiResult.Failure(ApiError.AuthenticationRequired())
        return when (
            val result = transport.execute(
                HttpRequest(
                    method = HttpMethod.GET,
                    pathAndQuery = path,
                    headers = mapOf("Authorization" to token.authorizationValue()),
                ),
            )
        ) {
            is TransportResult.Failure -> ApiResult.Failure(ApiError.Network(kind = result.kind))
            is TransportResult.Response -> decode(result.value, mapper)
        }
    }

    private fun <T> decode(
        response: HttpResponse,
        mapper: (JsonValue, ResponseMetadata) -> T,
    ): ApiResult<T> {
        val requestIdHeader = response.headers["x-request-id"]
        val text = try {
            decodeUtf8(response.body)
        } catch (_: Exception) {
            return ApiResult.Failure(ApiError.Protocol(requestIdHeader, "Response is not valid UTF-8"))
        }
        val root = if (text.isBlank()) null else try {
            StrictJsonParser(text).parseObject()
        } catch (error: JsonProtocolException) {
            return ApiResult.Failure(ApiError.Protocol(requestIdHeader, error.message ?: "Invalid JSON"))
        }
        if (response.statusCode !in 200..299) {
            return ApiResult.Failure(mapHttpError(response, root, requestIdHeader))
        }
        if (root == null) return ApiResult.Failure(ApiError.Protocol(requestIdHeader, "Successful response has no body"))
        return try {
            if (root.values["error"] !is JsonValue.NullValue) throw JsonProtocolException("Successful response contains an error")
            val metadata = mapMetadata(root.requiredObject("meta"), requestIdHeader)
            val data = root.values["data"] ?: throw JsonProtocolException("Missing response data")
            ApiResult.Success(mapper(data, metadata), metadata)
        } catch (error: Exception) {
            ApiResult.Failure(ApiError.Protocol(requestIdHeader, error.message?.take(300) ?: "Response mapping failed"))
        }
    }

    private fun mapHttpError(
        response: HttpResponse,
        root: JsonValue.ObjectValue?,
        requestIdHeader: String?,
    ): ApiError {
        val meta = runCatching { root?.optionalObject("meta") }.getOrNull()
        val requestId = runCatching { meta?.optionalString("request_id") }.getOrNull() ?: requestIdHeader
        val error = runCatching { root?.optionalObject("error") }.getOrNull()
        val code = runCatching { error?.optionalString("code") }.getOrNull()
        val retryable = runCatching { error?.optionalBoolean("retryable") }.getOrNull() ?: false
        return when (response.statusCode) {
            400, 422 -> ApiError.Validation(requestId, "request", code ?: "invalid_request")
            401 -> ApiError.AuthenticationExpired(requestId, code).also {
                runCatching { authenticationEvents.onAuthenticationExpired(requestId) }
            }
            403 -> ApiError.Forbidden(requestId, code)
            404 -> ApiError.NotFound(requestId, code)
            409 -> ApiError.Conflict(requestId, code)
            429 -> ApiError.RateLimited(requestId, response.headers["retry-after"]?.toLongOrNull()?.coerceIn(0, 86_400))
            in 500..599 -> ApiError.Server(requestId, response.statusCode, code, retryable = true)
            else -> ApiError.Server(requestId, response.statusCode, code, retryable)
        }
    }

    private fun mapTransaction(item: JsonValue): WalletTransaction {
        val value = item.asObject()
        return WalletTransaction(
            id = value.requiredString("id").canonicalUuid("id"),
            type = when (value.requiredString("type")) {
                "topup" -> WalletTransactionType.TOPUP
                "purchase" -> WalletTransactionType.PURCHASE
                "refund" -> WalletTransactionType.REFUND
                "adjustment" -> WalletTransactionType.ADJUSTMENT
                "reversal" -> WalletTransactionType.REVERSAL
                else -> throw JsonProtocolException("Unknown wallet transaction type")
            },
            direction = when (value.requiredString("direction")) {
                "credit" -> WalletTransactionDirection.CREDIT
                "debit" -> WalletTransactionDirection.DEBIT
                else -> throw JsonProtocolException("Unknown wallet transaction direction")
            },
            amount = mapMoney(value.requiredObject("amount")),
            balanceAfter = mapMoney(value.requiredObject("balance_after")),
            referenceType = value.optionalString("reference_type")?.bounded("reference_type", 1, 64),
            referenceId = value.optionalString("reference_id")?.bounded("reference_id", 1, 200),
            description = value.optionalString("description")?.bounded("description", 1, 500),
            createdAt = value.requiredString("created_at").utcTimestamp("created_at"),
        )
    }

    private fun mapMoney(value: JsonValue.ObjectValue): Money = Money(
        amountMinor = value.requiredLong("amount_minor").also {
            if (it < 0) throw JsonProtocolException("amount_minor cannot be negative")
        },
        currency = value.requiredString("currency").also {
            if (!it.matches(Regex("^[A-Z]{3}$"))) throw JsonProtocolException("Invalid currency")
        },
    )

    private fun mapMetadata(value: JsonValue.ObjectValue, requestIdHeader: String?): ResponseMetadata {
        val bodyRequestId = value.optionalString("request_id")?.canonicalUuid("request_id")
        val validatedHeaderRequestId = requestIdHeader?.canonicalUuid("X-Request-Id")
        if (validatedHeaderRequestId != null && bodyRequestId != null && validatedHeaderRequestId != bodyRequestId) {
            throw JsonProtocolException("Request ID mismatch")
        }
        return ResponseMetadata(
            requestId = bodyRequestId ?: validatedHeaderRequestId,
            serverTime = value.optionalString("server_time")?.utcTimestamp("server_time"),
            nextCursor = value.optionalString("next_cursor")?.also {
                if (!it.matches(Regex("^[A-Za-z0-9_-]{8,512}$"))) throw JsonProtocolException("Invalid next_cursor")
            },
            hasMore = value.optionalBoolean("has_more"),
        )
    }

    private fun JsonValue.asArray(): List<JsonValue> =
        (this as? JsonValue.ArrayValue)?.values ?: throw JsonProtocolException("Response data must be an array")

    private fun String.canonicalUuid(field: String): String = try {
        if (length != 36 || UUID.fromString(this).toString() != lowercase()) throw IllegalArgumentException()
        this
    } catch (_: IllegalArgumentException) {
        throw JsonProtocolException("$field must be a canonical UUID")
    }

    private fun String.bounded(field: String, minimum: Int, maximum: Int): String = also {
        if (length !in minimum..maximum) throw JsonProtocolException("$field length is invalid")
    }

    private fun String.utcTimestamp(field: String): String = also {
        if (!matches(UTC_TIMESTAMP)) throw JsonProtocolException("$field must be an RFC 3339 UTC timestamp")
    }

    private fun decodeUtf8(bytes: ByteArray): String = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

    private companion object {
        val UTC_TIMESTAMP = Regex(
            "^[0-9]{4}-(0[1-9]|1[0-2])-([0-2][0-9]|3[01])T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\\.[0-9]{1,9})?Z$",
        )
    }
}
