package com.ganj.vpn.core.controlapi

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class NotificationKind {
    SUBSCRIPTION_EXPIRY,
    PURCHASE_SUCCESS,
    PAYMENT_FAILURE,
    MAINTENANCE,
    SECURITY_UPDATE,
    SUPPORT_REPLY,
    MARKETING,
}

enum class NotificationActionType {
    OPEN_STORE,
    OPEN_SUBSCRIPTION,
    OPEN_SUPPORT,
    OPEN_WALLET,
    OPEN_SETTINGS,
}

data class NotificationAction(val type: NotificationActionType, val id: String?)

data class UserNotification(
    val id: String,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val read: Boolean,
    val action: NotificationAction?,
    val createdAt: String,
)

data class NotificationPage(
    val items: List<UserNotification>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val unreadCount: Int,
)

data class NotificationPreferences(
    val subscriptionExpiry: Boolean,
    val purchaseSuccess: Boolean,
    val paymentFailure: Boolean,
    val maintenance: Boolean,
    val securityUpdate: Boolean,
    val supportReply: Boolean,
    val marketing: Boolean,
)

interface NotificationApi {
    fun notifications(cursor: String? = null, limit: Int = 30): ApiResult<NotificationPage>
    fun markRead(notificationId: String): ApiResult<Unit>
    fun markAllRead(): ApiResult<Unit>
    fun preferences(): ApiResult<NotificationPreferences>
    fun updatePreferences(value: NotificationPreferences): ApiResult<NotificationPreferences>
}

object NotificationApiFactory {
    fun create(
        baseUrl: String,
        tokenProvider: AuthTokenProvider,
        authenticationEvents: AuthenticationEventSink =
            (tokenProvider as? AuthenticationEventSink) ?: AuthenticationEventSink.NONE,
    ): NotificationApi {
        val transport = RefreshingHttpTransport(
            delegate = UrlConnectionTransport(baseUrl),
            tokenProvider = tokenProvider,
            authenticationEvents = authenticationEvents,
        )
        return DefaultNotificationApi(transport, tokenProvider, authenticationEvents)
    }
}

internal class DefaultNotificationApi(
    private val transport: HttpTransport,
    private val tokenProvider: AuthTokenProvider,
    private val authenticationEvents: AuthenticationEventSink,
) : NotificationApi {
    override fun notifications(cursor: String?, limit: Int): ApiResult<NotificationPage> {
        if (limit !in 1..50) return ApiResult.Failure(ApiError.Validation(field = "limit", reason = "limit_out_of_range"))
        if (cursor != null && !cursor.matches(Regex("^[A-Za-z0-9_-]{8,512}$"))) {
            return ApiResult.Failure(ApiError.Validation(field = "cursor", reason = "invalid_cursor"))
        }
        val path = buildString {
            append("/notifications?limit=")
            append(limit)
            if (cursor != null) {
                append("&cursor=")
                append(cursor)
            }
        }
        return executeJson(HttpMethod.GET, path) { data, metadata, metaObject ->
            val items = data.asArray().also {
                if (it.size > 50) throw JsonProtocolException("Notification page contains too many items")
            }.map(::mapNotification)
            val unread = metaObject.optionalLong("unread_count") ?: 0L
            if (unread < 0 || unread > Int.MAX_VALUE) throw JsonProtocolException("Invalid unread_count")
            NotificationPage(items, metadata.nextCursor, metadata.hasMore, unread.toInt())
        }
    }

    override fun markRead(notificationId: String): ApiResult<Unit> {
        val id = notificationId.canonicalUuidOrNull()
            ?: return ApiResult.Failure(ApiError.Validation(field = "notification_id", reason = "invalid_notification_id"))
        return executeEmpty(HttpMethod.POST, "/notifications/$id/read")
    }

    override fun markAllRead(): ApiResult<Unit> = executeEmpty(HttpMethod.POST, "/notifications/read-all")

    override fun preferences(): ApiResult<NotificationPreferences> =
        executeJson(HttpMethod.GET, "/notifications/preferences") { data, _, _ -> mapPreferences(data.asObject()) }

    override fun updatePreferences(value: NotificationPreferences): ApiResult<NotificationPreferences> {
        val body = JsonEncoder.objectValue(
            "subscription_expiry" to value.subscriptionExpiry,
            "purchase_success" to value.purchaseSuccess,
            "payment_failure" to value.paymentFailure,
            "maintenance" to value.maintenance,
            "security_update" to value.securityUpdate,
            "support_reply" to value.supportReply,
            "marketing" to value.marketing,
        ).toByteArray(StandardCharsets.UTF_8)
        return executeJson(HttpMethod.PUT, "/notifications/preferences", body) { data, _, _ ->
            mapPreferences(data.asObject())
        }
    }

    private fun executeEmpty(method: HttpMethod, path: String): ApiResult<Unit> {
        val token = tokenProvider.currentAccessToken() ?: return ApiResult.Failure(ApiError.AuthenticationRequired())
        return when (val result = transport.execute(HttpRequest(method, path, mapOf("Authorization" to token.authorizationValue())))) {
            is TransportResult.Failure -> ApiResult.Failure(ApiError.Network(kind = result.kind))
            is TransportResult.Response -> {
                if (result.value.statusCode == 204) {
                    ApiResult.Success(Unit, ResponseMetadata(result.value.headers["x-request-id"], null))
                } else {
                    ApiResult.Failure(mapHttpError(result.value))
                }
            }
        }
    }

    private fun <T> executeJson(
        method: HttpMethod,
        path: String,
        body: ByteArray? = null,
        mapper: (JsonValue, ResponseMetadata, JsonValue.ObjectValue) -> T,
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

    private fun <T> decode(
        response: HttpResponse,
        mapper: (JsonValue, ResponseMetadata, JsonValue.ObjectValue) -> T,
    ): ApiResult<T> {
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
            val metaObject = root.requiredObject("meta")
            val metadata = mapMetadata(metaObject, requestIdHeader)
            val data = root.values["data"] ?: throw JsonProtocolException("Missing response data")
            ApiResult.Success(mapper(data, metadata, metaObject), metadata)
        } catch (error: Exception) {
            ApiResult.Failure(ApiError.Protocol(requestIdHeader, error.message?.take(300) ?: "Response mapping failed"))
        }
    }

    private fun mapNotification(item: JsonValue): UserNotification {
        val value = item.asObject()
        return UserNotification(
            id = value.requiredString("id").canonicalUuid("id"),
            kind = when (value.requiredString("kind")) {
                "subscription_expiry" -> NotificationKind.SUBSCRIPTION_EXPIRY
                "purchase_success" -> NotificationKind.PURCHASE_SUCCESS
                "payment_failure" -> NotificationKind.PAYMENT_FAILURE
                "maintenance" -> NotificationKind.MAINTENANCE
                "security_update" -> NotificationKind.SECURITY_UPDATE
                "support_reply" -> NotificationKind.SUPPORT_REPLY
                "marketing" -> NotificationKind.MARKETING
                else -> throw JsonProtocolException("Unknown notification kind")
            },
            title = value.requiredString("title").bounded("title", 1, 160),
            body = value.requiredString("body").bounded("body", 1, 2000),
            read = value.requiredBooleanStrict("read"),
            action = value.optionalObject("action")?.let(::mapAction),
            createdAt = value.requiredString("created_at").utcTimestamp("created_at"),
        )
    }

    private fun mapAction(value: JsonValue.ObjectValue): NotificationAction {
        val type = when (value.requiredString("type")) {
            "open_store" -> NotificationActionType.OPEN_STORE
            "open_subscription" -> NotificationActionType.OPEN_SUBSCRIPTION
            "open_support" -> NotificationActionType.OPEN_SUPPORT
            "open_wallet" -> NotificationActionType.OPEN_WALLET
            "open_settings" -> NotificationActionType.OPEN_SETTINGS
            else -> throw JsonProtocolException("Unknown notification action")
        }
        return NotificationAction(type, value.optionalString("id")?.canonicalUuid("action.id"))
    }

    private fun mapPreferences(value: JsonValue.ObjectValue): NotificationPreferences = NotificationPreferences(
        subscriptionExpiry = value.requiredBooleanStrict("subscription_expiry"),
        purchaseSuccess = value.requiredBooleanStrict("purchase_success"),
        paymentFailure = value.requiredBooleanStrict("payment_failure"),
        maintenance = value.requiredBooleanStrict("maintenance"),
        securityUpdate = value.requiredBooleanStrict("security_update"),
        supportReply = value.requiredBooleanStrict("support_reply"),
        marketing = value.requiredBooleanStrict("marketing"),
    )

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
            nextCursor = value.optionalString("next_cursor")?.also {
                if (!it.matches(Regex("^[A-Za-z0-9_-]{8,512}$"))) throw JsonProtocolException("Invalid next_cursor")
            },
            hasMore = value.optionalBoolean("has_more"),
        )
    }

    private fun JsonValue.asArray(): List<JsonValue> =
        (this as? JsonValue.ArrayValue)?.values ?: throw JsonProtocolException("Response data must be an array")

    private fun JsonValue.ObjectValue.requiredBooleanStrict(name: String): Boolean =
        (values[name] as? JsonValue.BooleanValue)?.value ?: throw JsonProtocolException("Missing boolean '$name'")

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
