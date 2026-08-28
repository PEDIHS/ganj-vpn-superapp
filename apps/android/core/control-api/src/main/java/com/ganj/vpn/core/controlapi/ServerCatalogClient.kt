package com.ganj.vpn.core.controlapi

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Reads UI-safe server metadata only. Connection material continues to be obtainable exclusively
 * through the device-bound one-time connection-profile endpoint.
 */
internal class ServerCatalogClient(
    private val transport: HttpTransport,
    private val tokenProvider: AuthTokenProvider,
    private val authenticationEvents: AuthenticationEventSink,
) {
    fun getServers(
        tier: SubscriptionTier? = null,
        countryCode: String? = null,
        protocol: VpnProtocol? = null,
    ): ApiResult<List<ManagedServer>> {
        if (countryCode != null && !countryCode.matches(Regex("^[A-Z]{2}$"))) {
            return ApiResult.Failure(ApiError.Validation(field = "country", reason = "Invalid country code"))
        }
        val query = buildList {
            tier?.let { add("tier=${it.name.lowercase()}") }
            countryCode?.let { add("country=$it") }
            protocol?.let { add("protocol=${it.name.lowercase()}") }
        }
        val path = "/servers" + query.takeIf(List<String>::isNotEmpty)?.joinToString("?", "&") .orEmpty()
        return execute(path, ::mapServers)
    }

    private fun <T> execute(path: String, mapData: (JsonValue) -> T): ApiResult<T> {
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
            is TransportResult.Response -> decodeResponse(result.value, mapData)
        }
    }

    private fun <T> decodeResponse(response: HttpResponse, mapData: (JsonValue) -> T): ApiResult<T> {
        val requestIdHeader = response.headers["x-request-id"]
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(response.body))
                .toString()
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
            val data = root.values["data"] ?: throw JsonProtocolException("Missing response data")
            ApiResult.Success(mapData(data), mapMetadata(root.requiredObject("meta"), requestIdHeader))
        } catch (error: Exception) {
            ApiResult.Failure(ApiError.Protocol(requestIdHeader, error.message?.take(300) ?: "Server catalog mapping failed"))
        }
    }

    private fun mapServers(data: JsonValue): List<ManagedServer> {
        val array = (data as? JsonValue.ArrayValue)?.values ?: throw JsonProtocolException("servers must be an array")
        if (array.size > 1_000) throw JsonProtocolException("servers contains too many items")
        return array.map { item ->
            val value = item.asObject()
            val loadRatio = (value.values["load_ratio"] as? JsonValue.NumberValue)?.raw?.toDoubleOrNull()
                ?: throw JsonProtocolException("load_ratio must be numeric")
            if (!loadRatio.isFinite() || loadRatio !in 0.0..1.0) throw JsonProtocolException("load_ratio is invalid")
            val latency = value.optionalLong("latency_hint_ms")?.also {
                if (it < 0 || it > Int.MAX_VALUE) throw JsonProtocolException("latency_hint_ms is invalid")
            }?.toInt()
            val protocols = value.requiredArray("protocols").also {
                if (it.isEmpty() || it.size > 20) throw JsonProtocolException("protocols is invalid")
            }.map { it.asString().toProtocol() }.toSet()
            ManagedServer(
                id = value.requiredString("id").canonicalUuid("id"),
                code = value.requiredString("code").bounded("code", 1, 128),
                name = value.requiredString("name").bounded("name", 1, 200),
                countryCode = value.requiredString("country_code").also {
                    if (!it.matches(Regex("^[A-Z]{2}$"))) throw JsonProtocolException("Invalid country_code")
                },
                city = value.optionalString("city")?.bounded("city", 1, 200),
                tier = value.requiredString("tier").toTier(),
                status = value.requiredString("status").toServerStatus(),
                loadRatio = loadRatio,
                latencyHintMs = latency,
                favorite = value.optionalBoolean("favorite", default = false),
                protocols = protocols,
            )
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
            429 -> ApiError.RateLimited(requestId, null)
            in 500..599 -> ApiError.Server(requestId, response.statusCode, code, retryable = true)
            else -> ApiError.Server(requestId, response.statusCode, code, retryable)
        }
    }

    private fun mapMetadata(value: JsonValue.ObjectValue, requestIdHeader: String?): ResponseMetadata {
        val bodyRequestId = value.optionalString("request_id")?.canonicalUuid("request_id")
        val headerRequestId = requestIdHeader?.canonicalUuid("X-Request-Id")
        if (bodyRequestId != null && headerRequestId != null && bodyRequestId != headerRequestId) {
            throw JsonProtocolException("Request ID mismatch")
        }
        return ResponseMetadata(
            requestId = bodyRequestId ?: headerRequestId,
            serverTime = value.optionalString("server_time"),
            nextCursor = value.optionalString("next_cursor"),
            hasMore = value.optionalBoolean("has_more"),
        )
    }

    private fun String.toTier(): SubscriptionTier = when (this) {
        "free" -> SubscriptionTier.FREE
        "premium" -> SubscriptionTier.PREMIUM
        "vip" -> SubscriptionTier.VIP
        else -> throw JsonProtocolException("Unknown subscription tier")
    }

    private fun String.toServerStatus(): ServerStatus = when (this) {
        "active" -> ServerStatus.ACTIVE
        "busy" -> ServerStatus.BUSY
        "maintenance" -> ServerStatus.MAINTENANCE
        else -> throw JsonProtocolException("Unknown server status")
    }

    private fun String.toProtocol(): VpnProtocol = when (this) {
        "vless" -> VpnProtocol.VLESS
        "vmess" -> VpnProtocol.VMESS
        "trojan" -> VpnProtocol.TROJAN
        "shadowsocks" -> VpnProtocol.SHADOWSOCKS
        "wireguard" -> VpnProtocol.WIREGUARD
        else -> throw JsonProtocolException("Unknown VPN protocol")
    }

    private fun String.canonicalUuid(field: String): String = also {
        try {
            if (length != 36 || UUID.fromString(this).toString() != lowercase()) throw IllegalArgumentException()
        } catch (_: IllegalArgumentException) {
            throw JsonProtocolException("$field must be a canonical UUID")
        }
    }

    private fun String.bounded(field: String, minimum: Int, maximum: Int): String = also {
        if (length !in minimum..maximum) throw JsonProtocolException("$field length is invalid")
    }
}
