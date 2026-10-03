package com.ganj.vpn.core.controlapi

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class TrustedDeviceStatus { ACTIVE, REVOKED }

data class TrustedDevice(
    val id: String,
    val platform: String,
    val name: String?,
    val appVersion: String?,
    val status: TrustedDeviceStatus,
    val current: Boolean,
    val lastSeenAt: String?,
)

interface DeviceApi {
    fun devices(): ApiResult<List<TrustedDevice>>
    fun revoke(deviceId: String): ApiResult<Unit>
}

object DeviceApiFactory {
    fun create(
        baseUrl: String,
        tokenProvider: AuthTokenProvider,
        authenticationEvents: AuthenticationEventSink =
            (tokenProvider as? AuthenticationEventSink) ?: AuthenticationEventSink.NONE,
    ): DeviceApi {
        val transport = RefreshingHttpTransport(
            delegate = UrlConnectionTransport(baseUrl),
            tokenProvider = tokenProvider,
            authenticationEvents = authenticationEvents,
        )
        return DefaultDeviceApi(transport, tokenProvider, authenticationEvents)
    }
}

internal class DefaultDeviceApi(
    private val transport: HttpTransport,
    private val tokenProvider: AuthTokenProvider,
    private val authenticationEvents: AuthenticationEventSink,
) : DeviceApi {
    override fun devices(): ApiResult<List<TrustedDevice>> = executeJson(
        HttpMethod.GET,
        "/me/devices",
    ) { data, _ ->
        data.asArray().also {
            if (it.size > 100) throw JsonProtocolException("Device list contains too many items")
        }.map(::mapDevice)
    }

    override fun revoke(deviceId: String): ApiResult<Unit> {
        val canonical = try {
            if (deviceId.length != 36 || UUID.fromString(deviceId).toString() != deviceId.lowercase()) throw IllegalArgumentException()
            deviceId
        } catch (_: IllegalArgumentException) {
            return ApiResult.Failure(ApiError.Validation(field = "device_id", reason = "invalid_device_id"))
        }
        val token = tokenProvider.currentAccessToken()
            ?: return ApiResult.Failure(ApiError.AuthenticationRequired())
        return when (
            val result = transport.execute(
                HttpRequest(
                    method = HttpMethod.DELETE,
                    pathAndQuery = "/me/devices/$canonical",
                    headers = mapOf("Authorization" to token.authorizationValue()),
                ),
            )
        ) {
            is TransportResult.Failure -> ApiResult.Failure(ApiError.Network(kind = result.kind))
            is TransportResult.Response -> {
                if (result.value.statusCode == 204) {
                    ApiResult.Success(
                        Unit,
                        ResponseMetadata(
                            requestId = result.value.headers["x-request-id"],
                            serverTime = null,
                        ),
                    )
                } else {
                    ApiResult.Failure(mapHttpError(result.value))
                }
            }
        }
    }

    private fun <T> executeJson(
        method: HttpMethod,
        path: String,
        mapper: (JsonValue, ResponseMetadata) -> T,
    ): ApiResult<T> {
        val token = tokenProvider.currentAccessToken()
            ?: return ApiResult.Failure(ApiError.AuthenticationRequired())
        return when (
            val result = transport.execute(
                HttpRequest(
                    method = method,
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
        if (response.statusCode !in 200..299) return ApiResult.Failure(mapHttpError(response, root))
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

    private fun mapDevice(item: JsonValue): TrustedDevice {
        val value = item.asObject()
        val current = (value.values["current"] as? JsonValue.BooleanValue)?.value
            ?: throw JsonProtocolException("Missing boolean 'current'")
        return TrustedDevice(
            id = value.requiredString("id").canonicalUuid("id"),
            platform = value.requiredString("platform").bounded("platform", 1, 32),
            name = value.optionalString("name")?.bounded("name", 1, 120),
            appVersion = value.optionalString("app_version")?.bounded("app_version", 1, 64),
            status = when (value.requiredString("status")) {
                "active" -> TrustedDeviceStatus.ACTIVE
                "revoked" -> TrustedDeviceStatus.REVOKED
                else -> throw JsonProtocolException("Unknown device status")
            },
            current = current,
            lastSeenAt = value.optionalString("last_seen_at")?.utcTimestamp("last_seen_at"),
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
        val validatedHeaderRequestId = requestIdHeader?.canonicalUuid("X-Request-Id")
        if (validatedHeaderRequestId != null && bodyRequestId != null && validatedHeaderRequestId != bodyRequestId) {
            throw JsonProtocolException("Request ID mismatch")
        }
        return ResponseMetadata(
            requestId = bodyRequestId ?: validatedHeaderRequestId,
            serverTime = value.optionalString("server_time")?.utcTimestamp("server_time"),
            nextCursor = null,
            hasMore = false,
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
