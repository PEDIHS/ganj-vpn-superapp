package com.ganj.vpn.core.controlapi

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

data class ConnectionServer(
    val id: String,
    val code: String,
    val name: String,
    val countryCode: String,
    val city: String?,
    val tier: SubscriptionTier,
    val protocols: Set<VpnProtocol>,
)

interface ServerApi {
    fun servers(serviceId: String? = null): ApiResult<List<ConnectionServer>>
}

object ServerApiFactory {
    fun create(
        baseUrl: String,
        tokenProvider: AuthTokenProvider,
        authenticationEvents: AuthenticationEventSink =
            (tokenProvider as? AuthenticationEventSink) ?: AuthenticationEventSink.NONE,
    ): ServerApi {
        val transport = RefreshingHttpTransport(
            delegate = UrlConnectionTransport(baseUrl),
            tokenProvider = tokenProvider,
            authenticationEvents = authenticationEvents,
        )
        return DefaultServerApi(transport, tokenProvider, authenticationEvents)
    }
}

internal class DefaultServerApi(
    private val transport: HttpTransport,
    private val tokenProvider: AuthTokenProvider,
    private val authenticationEvents: AuthenticationEventSink,
) : ServerApi {
    override fun servers(serviceId: String?): ApiResult<List<ConnectionServer>> {
        val token = tokenProvider.currentAccessToken()
            ?: return ApiResult.Failure(ApiError.AuthenticationRequired())
        val path = when {
            serviceId == null -> "/servers"
            runCatching { UUID.fromString(serviceId).toString() }.getOrNull() == serviceId.lowercase() ->
                "/servers?service_id=$serviceId"
            else -> return ApiResult.Failure(ApiError.Validation(field = "service_id", reason = "invalid_uuid"))
        }
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
            is TransportResult.Response -> decode(result.value)
        }
    }

    private fun decode(response: HttpResponse): ApiResult<List<ConnectionServer>> {
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
            val meta = root.requiredObject("meta")
            val requestId = meta.optionalString("request_id") ?: requestIdHeader
            val data = root.values["data"] as? JsonValue.ArrayValue
                ?: throw JsonProtocolException("Server response data must be an array")
            if (data.values.size > 200) throw JsonProtocolException("Server list contains too many items")
            ApiResult.Success(
                data.values.map(::mapServer),
                ResponseMetadata(requestId = requestId, serverTime = meta.optionalString("server_time")),
            )
        } catch (error: Exception) {
            ApiResult.Failure(ApiError.Protocol(requestIdHeader, error.message?.take(300) ?: "Response mapping failed"))
        }
    }

    private fun mapServer(item: JsonValue): ConnectionServer {
        val value = item as? JsonValue.ObjectValue ?: throw JsonProtocolException("Server item must be an object")
        val protocols = value.requiredArray("protocols").map { protocol ->
            when (protocol.asString().lowercase()) {
                "vless" -> VpnProtocol.VLESS
                "vmess" -> VpnProtocol.VMESS
                "trojan" -> VpnProtocol.TROJAN
                "shadowsocks" -> VpnProtocol.SHADOWSOCKS
                else -> throw JsonProtocolException("Unsupported server protocol")
            }
        }.toSet()
        if (protocols.isEmpty()) throw JsonProtocolException("Server must expose at least one protocol")
        val id = value.requiredString("id")
        if (id.length != 36 || runCatching { UUID.fromString(id).toString() }.getOrNull() != id.lowercase()) {
            throw JsonProtocolException("Server id is invalid")
        }
        val country = value.requiredString("country_code")
        if (!country.matches(Regex("^[A-Z]{2}$"))) throw JsonProtocolException("Server country is invalid")
        return ConnectionServer(
            id = id,
            code = value.requiredString("code").takeBounded("code", 1, 128),
            name = value.requiredString("name").takeBounded("name", 1, 160),
            countryCode = country,
            city = value.optionalString("city")?.takeBounded("city", 1, 120),
            tier = when (value.requiredString("tier")) {
                "free" -> SubscriptionTier.FREE
                "premium" -> SubscriptionTier.PREMIUM
                "vip" -> SubscriptionTier.VIP
                else -> throw JsonProtocolException("Unknown server tier")
            },
            protocols = protocols,
        )
    }

    private fun mapHttpError(response: HttpResponse, root: JsonValue.ObjectValue?): ApiError {
        val requestIdHeader = response.headers["x-request-id"]
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

    private fun decodeUtf8(bytes: ByteArray): String {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }

    private fun String.takeBounded(field: String, minimum: Int, maximum: Int): String {
        if (length !in minimum..maximum) throw JsonProtocolException("$field length is invalid")
        return this
    }
}
