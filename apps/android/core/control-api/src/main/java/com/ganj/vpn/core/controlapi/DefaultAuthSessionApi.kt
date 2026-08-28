package com.ganj.vpn.core.controlapi

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

internal class DefaultAuthSessionApi(
    private val transport: HttpTransport,
) : AuthSessionApi {
    override fun createGuest(command: GuestSessionCommand): ApiResult<AuthSessionCredentials> = execute(
        method = HttpMethod.POST,
        path = "/auth/guest",
        body = JsonEncoder.objectValue(
            "device_id" to command.deviceId,
            "key_version" to command.keyVersion,
            "signing_public_key_spki" to command.signingPublicKeySpki,
            "encryption_public_key_raw" to command.encryptionPublicKeyRaw,
            "device_proof" to command.deviceProof,
        ),
        mapData = ::mapSession,
    )

    override fun refresh(command: RefreshSessionCommand): ApiResult<AuthSessionCredentials> = execute(
        method = HttpMethod.POST,
        path = "/auth/refresh",
        body = JsonEncoder.objectValue(
            "refresh_token" to command.refreshToken.rawValue(),
            "device_id" to command.deviceId,
            "device_proof" to command.deviceProof,
        ),
        mapData = ::mapSession,
    )

    override fun beginTelegram(
        accessToken: AccessToken,
        command: TelegramAuthorizationCommand,
    ): ApiResult<TelegramAuthorization> = execute(
        method = HttpMethod.POST,
        path = "/auth/telegram/start",
        headers = mapOf("Authorization" to accessToken.authorizationValue()),
        body = JsonEncoder.objectValue(
            "code_challenge" to command.codeChallenge,
            "redirect_uri" to command.redirectUri,
        ),
        mapData = ::mapTelegramAuthorization,
    )

    override fun exchangeTelegram(command: TelegramExchangeCommand): ApiResult<AuthSessionCredentials> = execute(
        method = HttpMethod.POST,
        path = "/auth/telegram/exchange",
        body = JsonEncoder.objectValue(
            "code" to command.code,
            "state" to command.state,
            "code_verifier" to command.codeVerifier,
        ),
        mapData = ::mapSession,
    )

    override fun beginTelegramBot(
        accessToken: AccessToken,
        command: TelegramAuthorizationCommand,
    ): ApiResult<TelegramBotAuthorization> = execute(
        method = HttpMethod.POST,
        path = "/auth/telegram/bot/start",
        headers = mapOf("Authorization" to accessToken.authorizationValue()),
        body = JsonEncoder.objectValue(
            "code_challenge" to command.codeChallenge,
            "redirect_uri" to command.redirectUri,
        ),
        mapData = ::mapTelegramBotAuthorization,
    )

    override fun telegramBotStatus(
        accessToken: AccessToken,
        state: String,
    ): ApiResult<TelegramBotApprovalStatus> {
        require(state.matches(BASE64URL_32_BYTES)) { "Bot approval state is invalid" }
        return execute(
            method = HttpMethod.POST,
            path = "/auth/telegram/bot/status",
            headers = mapOf("Authorization" to accessToken.authorizationValue()),
            body = JsonEncoder.objectValue("state" to state),
            mapData = ::mapTelegramBotStatus,
        )
    }

    override fun exchangeTelegramBot(
        accessToken: AccessToken,
        command: TelegramExchangeCommand,
    ): ApiResult<AuthSessionCredentials> = execute(
        method = HttpMethod.POST,
        path = "/auth/telegram/bot/exchange",
        headers = mapOf("Authorization" to accessToken.authorizationValue()),
        body = JsonEncoder.objectValue(
            "code" to command.code,
            "state" to command.state,
            "code_verifier" to command.codeVerifier,
        ),
        mapData = ::mapSession,
    )

    override fun logout(accessToken: AccessToken): ApiResult<Boolean> = execute(
        method = HttpMethod.POST,
        path = "/auth/logout",
        headers = mapOf("Authorization" to accessToken.authorizationValue()),
        body = JsonEncoder.objectValue(),
    ) { data ->
        data.asObject().optionalBoolean("logged_out", default = false)
    }

    private fun <T> execute(
        method: HttpMethod,
        path: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        mapData: (JsonValue) -> T,
    ): ApiResult<T> {
        val requestBody = body?.toByteArray(StandardCharsets.UTF_8)
        return when (
            val result = transport.execute(
                HttpRequest(
                    method = method,
                    pathAndQuery = path,
                    headers = headers,
                    body = requestBody,
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
            return ApiResult.Failure(ApiError.Protocol(requestIdHeader, "Successful auth response has no body"))
        }
        return try {
            if (root.values["error"] !is JsonValue.NullValue) {
                throw JsonProtocolException("Successful auth response contains an error")
            }
            val metadata = mapMetadata(root.requiredObject("meta"), requestIdHeader)
            val data = root.values["data"] ?: throw JsonProtocolException("Missing response data")
            ApiResult.Success(mapData(data), metadata)
        } catch (error: Exception) {
            ApiResult.Failure(
                ApiError.Protocol(
                    requestId = requestIdHeader,
                    reason = error.message?.take(300) ?: "Auth response mapping failed",
                ),
            )
        }
    }

    private fun mapSession(data: JsonValue): AuthSessionCredentials {
        val value = data.asObject()
        val tokenType = value.requiredString("token_type")
        if (tokenType != "Bearer") throw JsonProtocolException("Unsupported token type")
        return AuthSessionCredentials(
            userId = value.requiredString("user_id").canonicalUuid("user_id"),
            deviceId = value.requiredString("device_id").canonicalUuid("device_id"),
            accessToken = AccessToken.from(value.requiredString("access_token")),
            accessTokenExpiresAt = value.requiredString("access_token_expires_at").utcTimestamp("access_token_expires_at"),
            refreshToken = RefreshToken.from(value.requiredString("refresh_token")),
            refreshTokenExpiresAt = value.requiredString("refresh_token_expires_at").utcTimestamp("refresh_token_expires_at"),
        )
    }

    private fun mapTelegramAuthorization(data: JsonValue): TelegramAuthorization {
        val value = data.asObject()
        return TelegramAuthorization(
            authorizationUrl = value.requiredString("authorization_url"),
            state = value.requiredString("state"),
            expiresAt = value.requiredString("expires_at").utcTimestamp("expires_at"),
        )
    }

    private fun mapTelegramBotAuthorization(data: JsonValue): TelegramBotAuthorization {
        val value = data.asObject()
        return TelegramBotAuthorization(
            approvalUrl = value.requiredString("approval_url"),
            state = value.requiredString("state"),
            expiresAt = value.requiredString("expires_at").utcTimestamp("expires_at"),
        )
    }

    private fun mapTelegramBotStatus(data: JsonValue): TelegramBotApprovalStatus {
        val value = data.asObject()
        val status = when (value.requiredString("status")) {
            "pending" -> TelegramBotApprovalState.PENDING
            "approved" -> TelegramBotApprovalState.APPROVED
            "cancelled" -> TelegramBotApprovalState.CANCELLED
            "expired" -> TelegramBotApprovalState.EXPIRED
            "consumed" -> TelegramBotApprovalState.CONSUMED
            else -> throw JsonProtocolException("Unknown Telegram Bot approval status")
        }
        val code = value.optionalString("code")
        return TelegramBotApprovalStatus(
            state = status,
            code = code,
            expiresAt = value.requiredString("expires_at").utcTimestamp("expires_at"),
        )
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
            401 -> ApiError.AuthenticationExpired(requestId, code)
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
            nextCursor = value.optionalString("next_cursor"),
            hasMore = value.optionalBoolean("has_more"),
        )
    }

    private fun String.canonicalUuid(field: String): String = also {
        try {
            if (length != 36 || UUID.fromString(this).toString() != lowercase()) throw IllegalArgumentException()
        } catch (_: IllegalArgumentException) {
            throw JsonProtocolException("$field must be a canonical UUID")
        }
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
        val BASE64URL_32_BYTES = Regex("^[A-Za-z0-9_-]{43}$")
    }
}
