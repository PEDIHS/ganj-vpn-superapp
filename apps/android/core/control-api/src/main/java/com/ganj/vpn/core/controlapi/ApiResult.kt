package com.ganj.vpn.core.controlapi

sealed interface ApiResult<out T> {
    data class Success<T>(
        val value: T,
        val metadata: ResponseMetadata,
    ) : ApiResult<T>

    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

data class ResponseMetadata(
    val requestId: String?,
    val serverTime: String?,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

sealed interface ApiError {
    val requestId: String?

    data class AuthenticationRequired(
        override val requestId: String? = null,
    ) : ApiError

    data class AuthenticationExpired(
        override val requestId: String?,
        val code: String?,
    ) : ApiError

    data class Forbidden(
        override val requestId: String?,
        val code: String?,
    ) : ApiError

    data class Validation(
        override val requestId: String? = null,
        val field: String,
        val reason: String,
    ) : ApiError

    data class Conflict(
        override val requestId: String?,
        val code: String?,
    ) : ApiError

    data class NotFound(
        override val requestId: String?,
        val code: String?,
    ) : ApiError

    data class RateLimited(
        override val requestId: String?,
        val retryAfterSeconds: Long?,
    ) : ApiError

    data class Server(
        override val requestId: String?,
        val statusCode: Int,
        val code: String?,
        val retryable: Boolean,
    ) : ApiError

    data class Network(
        override val requestId: String? = null,
        val kind: NetworkFailure,
    ) : ApiError

    data class Protocol(
        override val requestId: String?,
        val reason: String,
    ) : ApiError
}

enum class NetworkFailure {
    TIMEOUT,
    TLS,
    OFFLINE_OR_DNS,
    IO,
}
