package com.ganj.vpn.core.controlapi

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

internal class ControlApiClient(
    private val transport: HttpTransport,
    private val tokenProvider: AuthTokenProvider,
    private val authenticationEvents: AuthenticationEventSink,
) {
    fun getCatalog(channel: PurchaseChannel): ApiResult<List<CatalogProduct>> {
        if (channel == PurchaseChannel.WALLET) {
            return ApiResult.Failure(
                ApiError.Validation(field = "channel", reason = "Wallet is not a catalog pricing channel"),
            )
        }
        return execute(
            method = HttpMethod.GET,
            path = "/store/plans?channel=${channel.name.lowercase()}",
            mapData = ::mapCatalog,
        )
    }

    fun getMyServices(): ApiResult<List<UserService>> = execute(
        method = HttpMethod.GET,
        path = "/services",
        mapData = ::mapServices,
    )

    fun createCheckout(command: CheckoutCommand): ApiResult<CheckoutOrder> {
        val body = JsonEncoder.objectValue(
            "plan_id" to command.planId,
            "channel" to command.channel.name.lowercase(),
            "service_id" to command.serviceId,
        )
        return execute(
            method = HttpMethod.POST,
            path = "/orders",
            additionalHeaders = mapOf("Idempotency-Key" to command.idempotencyKey),
            body = body,
            mapData = ::mapCheckoutOrder,
        )
    }

    fun issueConnectionProfile(command: ConnectionProfileCommand): ApiResult<EncryptedConnectionEnvelope> {
        val body = JsonEncoder.objectValue(
            "device_id" to command.deviceId,
            "server_id" to command.serverId,
            "client_nonce" to command.clientNonce,
            "device_proof" to command.deviceProof,
        )
        return execute(
            method = HttpMethod.POST,
            path = "/services/${command.serviceId}/connection-profile",
            body = body,
            mapData = ::mapConnectionEnvelope,
        )
    }

    private fun <T> execute(
        method: HttpMethod,
        path: String,
        additionalHeaders: Map<String, String> = emptyMap(),
        body: String? = null,
        mapData: (JsonValue) -> T,
    ): ApiResult<T> {
        val token = tokenProvider.currentAccessToken()
            ?: return ApiResult.Failure(ApiError.AuthenticationRequired())
        val headers = additionalHeaders + ("Authorization" to token.authorizationValue())
        return when (
            val result = transport.execute(
                HttpRequest(
                    method = method,
                    pathAndQuery = path,
                    headers = headers,
                    body = body?.toByteArray(StandardCharsets.UTF_8),
                ),
            )
        ) {
            is TransportResult.Failure -> ApiResult.Failure(ApiError.Network(kind = result.kind))
            is TransportResult.Response -> decodeResponse(result.value, mapData)
        }
    }

    private fun <T> decodeResponse(
        response: HttpResponse,
        mapData: (JsonValue) -> T,
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
        if (root == null) {
            return ApiResult.Failure(ApiError.Protocol(requestIdHeader, "Successful response has no body"))
        }
        return try {
            if (root.values["error"] !is JsonValue.NullValue) {
                throw JsonProtocolException("Successful response contains an error")
            }
            val metadata = mapMetadata(root.requiredObject("meta"), requestIdHeader)
            val data = root.values["data"] ?: throw JsonProtocolException("Missing response data")
            ApiResult.Success(mapData(data), metadata)
        } catch (error: Exception) {
            ApiResult.Failure(
                ApiError.Protocol(
                    requestId = requestIdHeader,
                    reason = error.message?.take(300) ?: "Response mapping failed",
                ),
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
            429 -> ApiError.RateLimited(requestId, parseRetryAfter(response.headers["retry-after"]))
            in 500..599 -> ApiError.Server(requestId, response.statusCode, code, retryable = true)
            else -> ApiError.Server(requestId, response.statusCode, code, retryable)
        }
    }

    private fun mapCatalog(data: JsonValue): List<CatalogProduct> = data.asBoundedArray("catalog", 500).map { item ->
        val value = item.asObject()
        val id = value.requiredString("id").canonicalUuid("id")
        val deviceLimit = value.requiredLong("device_limit").positiveInt("device_limit")
        CatalogProduct(
            id = id,
            code = value.requiredString("code").bounded("code", 1, 128),
            name = value.requiredString("name").bounded("name", 1, 200),
            tier = value.requiredString("tier").toTier(),
            durationDays = value.optionalLong("duration_days")?.positiveInt("duration_days"),
            trafficLimitBytes = value.optionalLong("traffic_limit_bytes")?.also {
                if (it < 0) throw JsonProtocolException("traffic_limit_bytes cannot be negative")
            },
            deviceLimit = deviceLimit,
            features = value.requiredArray("features").also {
                if (it.size > 100) throw JsonProtocolException("Too many features")
            }.map { it.asString().bounded("feature", 1, 200) },
            price = mapMoney(value.requiredObject("price")),
        )
    }

    private fun mapServices(data: JsonValue): List<UserService> = data.asBoundedArray("services", 1_000).map { item ->
        val value = item.asObject()
        val protocols = value.requiredArray("allowed_protocols").map { it.asString().toProtocol() }.toSet()
        if (protocols.isEmpty()) throw JsonProtocolException("Service must allow at least one protocol")
        UserService(
            id = value.requiredString("id").canonicalUuid("id"),
            name = value.requiredString("name").bounded("name", 1, 200),
            status = value.requiredString("status").toServiceStatus(),
            tier = value.requiredString("tier").toTier(),
            countryCode = value.optionalString("country_code")?.also {
                if (!it.matches(Regex("^[A-Z]{2}$"))) throw JsonProtocolException("Invalid country_code")
            },
            trafficLimitBytes = value.optionalLong("traffic_limit_bytes")?.nonNegative("traffic_limit_bytes"),
            trafficUsedBytes = value.requiredLong("traffic_used_bytes").nonNegative("traffic_used_bytes"),
            expiresAt = value.optionalString("expires_at")?.utcTimestamp("expires_at"),
            deviceLimit = value.requiredLong("device_limit").positiveInt("device_limit"),
            allowedProtocols = protocols,
        )
    }

    private fun mapCheckoutOrder(data: JsonValue): CheckoutOrder {
        val value = data.asObject()
        return CheckoutOrder(
            id = value.requiredString("id").canonicalUuid("id"),
            status = value.requiredString("status").toOrderStatus(),
            channel = value.requiredString("channel").toPurchaseChannel(),
            total = mapMoney(value.requiredObject("total")),
            entitlementServiceId = value.optionalString("entitlement_service_id")?.canonicalUuid("entitlement_service_id"),
        )
    }

    private fun mapConnectionEnvelope(data: JsonValue): EncryptedConnectionEnvelope {
        val value = data.asObject()
        val algorithm = value.requiredString("algorithm").bounded("algorithm", 1, 128)
        if (algorithm !in ALLOWED_ENVELOPE_ALGORITHMS) throw JsonProtocolException("Unsupported envelope algorithm")
        return EncryptedConnectionEnvelope(
            profileId = value.requiredString("profile_id").canonicalUuid("profile_id"),
            serverId = value.requiredString("server_id").canonicalUuid("server_id"),
            algorithm = algorithm,
            keyVersion = value.requiredString("key_version").bounded("key_version", 1, 64),
            nonce = StrictBase64.decode(value.requiredString("nonce"), maxDecodedBytes = 64),
            ciphertext = StrictBase64.decode(value.requiredString("ciphertext"), maxDecodedBytes = 65_536),
            expiresAt = value.requiredString("expires_at").utcTimestamp("expires_at"),
        )
    }

    private fun mapMoney(value: JsonValue.ObjectValue): Money = Money(
        amountMinor = value.requiredLong("amount_minor").nonNegative("amount_minor"),
        currency = value.requiredString("currency"),
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
            nextCursor = value.optionalString("next_cursor"),
            hasMore = value.optionalBoolean("has_more"),
        )
    }

    private fun JsonValue.asArray(): List<JsonValue> =
        (this as? JsonValue.ArrayValue)?.values ?: throw JsonProtocolException("Response data must be an array")

    private fun JsonValue.asBoundedArray(field: String, maximum: Int): List<JsonValue> = asArray().also {
        if (it.size > maximum) throw JsonProtocolException("$field contains too many items")
    }

    private fun String.canonicalUuid(field: String): String {
        return try {
            if (length != 36 || UUID.fromString(this).toString() != lowercase()) throw IllegalArgumentException()
            this
        } catch (_: IllegalArgumentException) {
            throw JsonProtocolException("$field must be a canonical UUID")
        }
    }

    private fun String.bounded(field: String, minimum: Int, maximum: Int): String = also {
        if (length !in minimum..maximum) throw JsonProtocolException("$field length is invalid")
    }

    private fun String.utcTimestamp(field: String): String = also {
        if (!matches(UTC_TIMESTAMP)) throw JsonProtocolException("$field must be an RFC 3339 UTC timestamp")
    }

    private fun Long.nonNegative(field: String): Long = also {
        if (this < 0) throw JsonProtocolException("$field cannot be negative")
    }

    private fun Long.positiveInt(field: String): Int {
        if (this !in 1..Int.MAX_VALUE.toLong()) throw JsonProtocolException("$field must be a positive integer")
        return toInt()
    }

    private fun String.toTier(): SubscriptionTier = when (this) {
        "free" -> SubscriptionTier.FREE
        "premium" -> SubscriptionTier.PREMIUM
        "vip" -> SubscriptionTier.VIP
        else -> throw JsonProtocolException("Unknown subscription tier")
    }

    private fun String.toProtocol(): VpnProtocol = when (this) {
        "vless" -> VpnProtocol.VLESS
        "vmess" -> VpnProtocol.VMESS
        "trojan" -> VpnProtocol.TROJAN
        "shadowsocks" -> VpnProtocol.SHADOWSOCKS
        "wireguard" -> VpnProtocol.WIREGUARD
        else -> throw JsonProtocolException("Unknown VPN protocol")
    }

    private fun String.toServiceStatus(): ServiceStatus = when (this) {
        "pending" -> ServiceStatus.PENDING
        "active" -> ServiceStatus.ACTIVE
        "disabled" -> ServiceStatus.DISABLED
        "expired" -> ServiceStatus.EXPIRED
        "revoked" -> ServiceStatus.REVOKED
        else -> throw JsonProtocolException("Unknown service status")
    }

    private fun String.toOrderStatus(): OrderStatus = when (this) {
        "pending" -> OrderStatus.PENDING
        "authorized" -> OrderStatus.AUTHORIZED
        "paid" -> OrderStatus.PAID
        "fulfilled" -> OrderStatus.FULFILLED
        "cancelled" -> OrderStatus.CANCELLED
        "refunded" -> OrderStatus.REFUNDED
        "failed" -> OrderStatus.FAILED
        else -> throw JsonProtocolException("Unknown order status")
    }

    private fun String.toPurchaseChannel(): PurchaseChannel = when (this) {
        "play" -> PurchaseChannel.PLAY
        "direct" -> PurchaseChannel.DIRECT
        "wallet" -> PurchaseChannel.WALLET
        else -> throw JsonProtocolException("Unknown purchase channel")
    }

    private fun parseRetryAfter(value: String?): Long? = value?.toLongOrNull()?.coerceIn(0, 86_400)

    private fun decodeUtf8(bytes: ByteArray): String = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

    private companion object {
        val ALLOWED_ENVELOPE_ALGORITHMS = setOf(
            "X25519+HKDF-SHA256+AES-256-GCM",
            "X25519+HKDF-SHA256+CHACHA20-POLY1305",
        )
        val UTC_TIMESTAMP = Regex(
            "^[0-9]{4}-(0[1-9]|1[0-2])-([0-2][0-9]|3[01])T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\\.[0-9]{1,9})?Z$",
        )
    }
}
