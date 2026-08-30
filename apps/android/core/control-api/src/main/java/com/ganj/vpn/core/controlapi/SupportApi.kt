package com.ganj.vpn.core.controlapi

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class SupportCategory { CONNECTION, BILLING, ACCOUNT, SECURITY, FEEDBACK, OTHER }
enum class SupportPriority { URGENT, HIGH, NORMAL }
enum class SupportStatus { OPEN, WAITING_USER, WAITING_SUPPORT, RESOLVED, CLOSED }
enum class SupportSenderRole { USER, SUPPORT, SYSTEM }

data class SupportTicket(
    val id: String,
    val publicCode: String,
    val category: SupportCategory,
    val priority: SupportPriority,
    val subject: String,
    val status: SupportStatus,
    val createdAt: String,
    val updatedAt: String,
)

data class SupportMessage(
    val id: String,
    val senderRole: SupportSenderRole,
    val body: String,
    val createdAt: String,
)

data class SupportTicketDetail(
    val ticket: SupportTicket,
    val messages: List<SupportMessage>,
)

interface SupportApi {
    fun tickets(): ApiResult<List<SupportTicket>>
    fun createTicket(category: SupportCategory, priority: SupportPriority, subject: String, body: String): ApiResult<SupportTicket>
    fun ticket(ticketId: String): ApiResult<SupportTicketDetail>
    fun reply(ticketId: String, body: String): ApiResult<SupportMessage>
    fun reopen(ticketId: String): ApiResult<SupportTicket>
}

object SupportApiFactory {
    fun create(
        baseUrl: String,
        tokenProvider: AuthTokenProvider,
        authenticationEvents: AuthenticationEventSink =
            (tokenProvider as? AuthenticationEventSink) ?: AuthenticationEventSink.NONE,
    ): SupportApi {
        val transport = RefreshingHttpTransport(
            delegate = UrlConnectionTransport(baseUrl),
            tokenProvider = tokenProvider,
            authenticationEvents = authenticationEvents,
        )
        return DefaultSupportApi(transport, tokenProvider, authenticationEvents)
    }
}

internal class DefaultSupportApi(
    private val transport: HttpTransport,
    private val tokenProvider: AuthTokenProvider,
    private val authenticationEvents: AuthenticationEventSink,
) : SupportApi {
    override fun tickets(): ApiResult<List<SupportTicket>> = executeJson(HttpMethod.GET, "/support/tickets") { data ->
        data.asArray().also { if (it.size > 500) throw JsonProtocolException("Too many support tickets") }.map(::mapTicket)
    }

    override fun createTicket(
        category: SupportCategory,
        priority: SupportPriority,
        subject: String,
        body: String,
    ): ApiResult<SupportTicket> {
        val cleanSubject = subject.trim()
        val cleanBody = body.trim()
        if (cleanSubject.length !in 3..160) return validation("subject", "invalid_subject")
        if (cleanBody.length !in 10..5000) return validation("body", "invalid_body")
        val payload = JsonEncoder.objectValue(
            "client_ticket_id" to UUID.randomUUID().toString(),
            "category" to category.wire(),
            "priority" to priority.wire(),
            "subject" to cleanSubject,
            "body" to cleanBody,
        ).toByteArray(StandardCharsets.UTF_8)
        return executeJson(HttpMethod.POST, "/support/tickets", payload, ::mapTicket)
    }

    override fun ticket(ticketId: String): ApiResult<SupportTicketDetail> {
        val id = ticketId.canonicalUuidOrNull() ?: return validation("ticket_id", "invalid_ticket_id")
        return executeJson(HttpMethod.GET, "/support/tickets/$id") { data ->
            val value = data.asObject()
            val ticket = mapTicket(value)
            val messages = value.requiredArray("messages")
                .also { if (it.size > 500) throw JsonProtocolException("Too many support messages") }
                .map(::mapMessage)
            SupportTicketDetail(ticket, messages)
        }
    }

    override fun reply(ticketId: String, body: String): ApiResult<SupportMessage> {
        val id = ticketId.canonicalUuidOrNull() ?: return validation("ticket_id", "invalid_ticket_id")
        val cleanBody = body.trim()
        if (cleanBody.length !in 1..8000) return validation("body", "invalid_body")
        val payload = JsonEncoder.objectValue(
            "client_message_id" to UUID.randomUUID().toString(),
            "body" to cleanBody,
        ).toByteArray(StandardCharsets.UTF_8)
        return executeJson(HttpMethod.POST, "/support/tickets/$id/messages", payload, ::mapMessage)
    }

    override fun reopen(ticketId: String): ApiResult<SupportTicket> {
        val id = ticketId.canonicalUuidOrNull() ?: return validation("ticket_id", "invalid_ticket_id")
        return executeJson(HttpMethod.POST, "/support/tickets/$id/reopen", mapper = ::mapTicket)
    }

    private fun <T> validation(field: String, reason: String): ApiResult<T> =
        ApiResult.Failure(ApiError.Validation(field = field, reason = reason))

    private fun <T> executeJson(
        method: HttpMethod,
        path: String,
        body: ByteArray? = null,
        mapper: (JsonValue) -> T,
    ): ApiResult<T> {
        val token = tokenProvider.currentAccessToken() ?: return ApiResult.Failure(ApiError.AuthenticationRequired())
        return when (
            val result = transport.execute(
                HttpRequest(
                    method = method,
                    pathAndQuery = path,
                    headers = mapOf("Authorization" to token.authorizationValue()),
                    body = body,
                ),
            )
        ) {
            is TransportResult.Failure -> ApiResult.Failure(ApiError.Network(kind = result.kind))
            is TransportResult.Response -> decode(result.value, mapper)
        }
    }

    private fun <T> decode(response: HttpResponse, mapper: (JsonValue) -> T): ApiResult<T> {
        val requestIdHeader = response.headers["x-request-id"]
        val text = try { decodeUtf8(response.body) } catch (_: Exception) {
            return ApiResult.Failure(ApiError.Protocol(requestIdHeader, "Response is not valid UTF-8"))
        }
        val root = if (text.isBlank()) null else try {
            StrictJsonParser(text).parseObject()
        } catch (error: JsonProtocolException) {
            return ApiResult.Failure(ApiError.Protocol(requestIdHeader, error.message ?: "Invalid JSON"))
        }
        if (response.statusCode !in 200..299) return ApiResult.Failure(mapHttpError(response, root))
        if (root == null) return ApiResult.Failure(ApiError.Protocol(requestIdHeader, "Successful response has no body"))
        return try {
            if (root.values["error"] !is JsonValue.NullValue) throw JsonProtocolException("Successful response contains an error")
            val metadata = mapMetadata(root.requiredObject("meta"), requestIdHeader)
            val data = root.values["data"] ?: throw JsonProtocolException("Missing response data")
            ApiResult.Success(mapper(data), metadata)
        } catch (error: Exception) {
            ApiResult.Failure(ApiError.Protocol(requestIdHeader, error.message?.take(300) ?: "Response mapping failed"))
        }
    }

    private fun mapTicket(item: JsonValue): SupportTicket {
        val value = item.asObject()
        return SupportTicket(
            id = value.requiredString("id").canonicalUuid("ticket.id"),
            publicCode = value.requiredString("public_code").bounded("public_code", 1, 80),
            category = when (value.requiredString("category")) {
                "connection" -> SupportCategory.CONNECTION
                "billing" -> SupportCategory.BILLING
                "account" -> SupportCategory.ACCOUNT
                "security" -> SupportCategory.SECURITY
                "feedback" -> SupportCategory.FEEDBACK
                "other" -> SupportCategory.OTHER
                else -> throw JsonProtocolException("Unknown support category")
            },
            priority = when (value.requiredString("priority")) {
                "urgent" -> SupportPriority.URGENT
                "high" -> SupportPriority.HIGH
                "normal" -> SupportPriority.NORMAL
                else -> throw JsonProtocolException("Unknown support priority")
            },
            subject = value.requiredString("subject").bounded("subject", 3, 160),
            status = when (value.requiredString("status")) {
                "open" -> SupportStatus.OPEN
                "waiting_user" -> SupportStatus.WAITING_USER
                "waiting_support" -> SupportStatus.WAITING_SUPPORT
                "resolved" -> SupportStatus.RESOLVED
                "closed" -> SupportStatus.CLOSED
                else -> throw JsonProtocolException("Unknown support status")
            },
            createdAt = value.requiredString("created_at").utcTimestamp("created_at"),
            updatedAt = value.requiredString("updated_at").utcTimestamp("updated_at"),
        )
    }

    private fun mapMessage(item: JsonValue): SupportMessage {
        val value = item.asObject()
        return SupportMessage(
            id = value.requiredString("id").canonicalUuid("message.id"),
            senderRole = when (value.requiredString("sender_role")) {
                "user" -> SupportSenderRole.USER
                "support" -> SupportSenderRole.SUPPORT
                "system" -> SupportSenderRole.SYSTEM
                else -> throw JsonProtocolException("Unknown support sender role")
            },
            body = value.requiredString("body").bounded("message.body", 1, 8000),
            createdAt = value.requiredString("created_at").utcTimestamp("message.created_at"),
        )
    }

    private fun mapHttpError(response: HttpResponse, parsedRoot: JsonValue.ObjectValue? = null): ApiError {
        val requestIdHeader = response.headers["x-request-id"]
        val root = parsedRoot ?: runCatching {
            val text = decodeUtf8(response.body)
            if (text.isBlank()) null else StrictJsonParser(text).parseObject()
        }.getOrNull()
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

    private fun mapMetadata(value: JsonValue.ObjectValue, requestIdHeader: String?): ResponseMetadata {
        val bodyRequestId = value.optionalString("request_id")?.canonicalUuid("request_id")
        val headerRequestId = requestIdHeader?.canonicalUuid("X-Request-Id")
        if (headerRequestId != null && bodyRequestId != null && headerRequestId != bodyRequestId) {
            throw JsonProtocolException("Request ID mismatch")
        }
        return ResponseMetadata(
            requestId = bodyRequestId ?: headerRequestId,
            serverTime = value.optionalString("server_time")?.utcTimestamp("server_time"),
        )
    }

    private fun JsonValue.asArray(): List<JsonValue> =
        (this as? JsonValue.ArrayValue)?.values ?: throw JsonProtocolException("Response data must be an array")

    private fun JsonValue.ObjectValue.requiredArray(name: String): List<JsonValue> =
        (values[name] as? JsonValue.ArrayValue)?.values ?: throw JsonProtocolException("Missing array '$name'")

    private fun String.canonicalUuidOrNull(): String? = try {
        if (length != 36 || UUID.fromString(this).toString() != lowercase()) null else this
    } catch (_: IllegalArgumentException) { null }

    private fun String.canonicalUuid(field: String): String = canonicalUuidOrNull()
        ?: throw JsonProtocolException("$field must be a canonical UUID")

    private fun String.bounded(field: String, minimum: Int, maximum: Int): String = also {
        if (length !in minimum..maximum) throw JsonProtocolException("$field length is invalid")
    }

    private fun String.utcTimestamp(field: String): String = also {
        if (!matches(UTC_TIMESTAMP)) throw JsonProtocolException("$field must be an RFC 3339 UTC timestamp")
    }

    private fun SupportCategory.wire(): String = name.lowercase()
    private fun SupportPriority.wire(): String = name.lowercase()

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
