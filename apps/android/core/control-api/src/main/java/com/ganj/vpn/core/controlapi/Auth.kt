package com.ganj.vpn.core.controlapi

class AccessToken private constructor(private val secret: String) {
    internal fun authorizationValue(): String = "Bearer $secret"
    internal fun rawValue(): String = secret

    override fun toString(): String = "AccessToken([REDACTED])"

    companion object {
        fun from(value: String): AccessToken {
            require(value.length in 16..8192) { "Access token length is invalid" }
            require(value.matches(Regex("^[A-Za-z0-9._~+/=-]+$"))) { "Access token format is invalid" }
            return AccessToken(value)
        }
    }
}

fun interface AuthTokenProvider {
    fun currentAccessToken(): AccessToken?
}

fun interface AuthenticationEventSink {
    fun onAuthenticationExpired(requestId: String?)

    companion object {
        val NONE = AuthenticationEventSink { }
    }
}
