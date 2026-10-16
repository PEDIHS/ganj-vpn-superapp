package com.ganj.vpn.core.controlapi

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

class FamilyReply(
    val serviceId: String?, val serviceName: String?, val owner: Boolean, val ownInvite: Boolean,
    val deviceLimit: Int, val slotsUsed: Int, val inviterName: String?, val inviteUrl: String?,
    val active: Boolean, val members: List<FamilyMember>, val devices: List<FamilyDevice>, val invites: List<FamilyInvite>,
) { override fun toString() = "FamilyReply([REDACTED])" }
data class FamilyMember(val id: String, val name: String, val current: Boolean)
data class FamilyDevice(val id: String, val name: String, val current: Boolean, val own: Boolean)
data class FamilyInvite(val id: String, val expiresAt: String)

interface FamilyApi {
    fun access(serviceId: String): ApiResult<FamilyReply>
    fun summary(serviceId: String): ApiResult<FamilyReply>
    fun createInvite(serviceId: String): ApiResult<FamilyReply>
    fun preview(token: String): ApiResult<FamilyReply>
    fun accept(token: String): ApiResult<FamilyReply>
    fun remove(serviceId: String, memberId: String): ApiResult<FamilyReply>
    fun revokeInvite(serviceId: String, inviteId: String): ApiResult<FamilyReply>
    fun updateDevice(name: String, appVersion: String, osVersion: String): ApiResult<FamilyReply>
}
object FamilyApiFactory {
    fun create(baseUrl: String, tokenProvider: AuthTokenProvider, signer: (String,ByteArray)->String?): FamilyApi {
        val events = (tokenProvider as? AuthenticationEventSink) ?: AuthenticationEventSink.NONE
        return DefaultFamilyApi(RefreshingHttpTransport(UrlConnectionTransport(baseUrl),tokenProvider,events),tokenProvider,events,signer)
    }
}
internal class DefaultFamilyApi(
    private val transport: HttpTransport, private val tokenProvider: AuthTokenProvider,
    private val authenticationEvents: AuthenticationEventSink, private val signer: (String,ByteArray)->String?,
): FamilyApi {
    override fun access(serviceId: String) = executeJson(HttpMethod.GET,"/services/${UUID.fromString(serviceId)}/family/access",mapper=::mapReply)
    override fun summary(serviceId: String) = executeJson(HttpMethod.GET,"/services/${UUID.fromString(serviceId)}/family",mapper=::mapReply)
    override fun createInvite(serviceId: String) = executeJson(HttpMethod.POST,"/services/${UUID.fromString(serviceId)}/family/invites",mapper=::mapReply)
    override fun preview(token: String) = executeJson(HttpMethod.POST,"/family/preview",mapOf("token" to token),::mapReply)
    override fun accept(token: String) = executeJson(HttpMethod.POST,"/family/accept",mapOf("token" to token),::mapReply)
    override fun remove(serviceId: String,memberId: String) = executeJson(HttpMethod.POST,"/services/${UUID.fromString(serviceId)}/family/members/${UUID.fromString(memberId)}",mapper=::mapReply)
    override fun revokeInvite(serviceId: String,inviteId: String) = executeJson(HttpMethod.POST,"/services/${UUID.fromString(serviceId)}/family/invites/${UUID.fromString(inviteId)}",mapper=::mapReply)
    override fun updateDevice(name: String, appVersion: String, osVersion: String) = executeJson(HttpMethod.POST,"/me/devices/current",mapOf("name" to name,"app_version" to appVersion,"os_version" to osVersion),::mapReply)
    private fun mapReply(data: JsonValue, meta: ResponseMetadata): FamilyReply {
        val v=data.asObject()
        fun rows(key: String) = (v.values[key] as? JsonValue.ArrayValue)?.values.orEmpty().also { if(it.size>100)throw JsonProtocolException("Too many family rows") }
        return FamilyReply(
            v.optionalString("service_id"),v.optionalString("service_name"),v.optionalBoolean("owner",false),v.optionalBoolean("own",false),
            (v.values["device_limit"] as? JsonValue.NumberValue)?.raw?.toIntOrNull() ?: 0,
            (v.values["slots_used"] as? JsonValue.NumberValue)?.raw?.toIntOrNull() ?: 0,
            v.optionalString("inviter_name"),v.optionalString("url"),
            v.optionalBoolean("active",false),
            rows("members").map { it.asObject().let { x -> FamilyMember(x.requiredString("user_id"),x.requiredString("name"),x.optionalBoolean("current",false)) } },
            rows("devices").map { it.asObject().let { x -> FamilyDevice(x.requiredString("id"),x.requiredString("name"),x.optionalBoolean("current",false),x.optionalBoolean("own",false)) } },
            rows("invites").map { it.asObject().let { x -> FamilyInvite(x.requiredString("id"),x.requiredString("expires_at")) } },
        )
    }
    private fun <T> executeJson(
        method: HttpMethod,
        path: String,
        fields: Map<String,String> = emptyMap(),
        mapper: (JsonValue, ResponseMetadata) -> T,
    ): ApiResult<T> {
        val token = tokenProvider.currentAccessToken()
            ?: return ApiResult.Failure(ApiError.AuthenticationRequired())
        val unsigned = JsonEncoder.objectValue(*fields.toSortedMap().map { it.key to it.value }.toTypedArray()).toByteArray(StandardCharsets.UTF_8)
        val signedBody = if (method == HttpMethod.POST) {
            val proof = try { signer("/v1$path", unsigned) } finally { unsigned.fill(0) }
                ?: return ApiResult.Failure(ApiError.Protocol(null,"device_proof_unavailable"))
            JsonEncoder.objectValue(*(fields + ("device_proof" to proof)).toSortedMap().map { it.key to it.value }.toTypedArray()).toByteArray(StandardCharsets.UTF_8)
        } else null
        return when (
            val result = transport.execute(
                HttpRequest(
                    method = method,
                    pathAndQuery = path,
                    headers = mapOf("Authorization" to token.authorizationValue()),
                    body = signedBody,
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
