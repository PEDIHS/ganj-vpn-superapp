package com.ganj.vpn.core.controlapi

/**
 * Retries one protected Control API request after a 401 when the token provider supports secure
 * rotation. The original request body is copied because UrlConnectionTransport zeroizes each body
 * after transmission. Never retries more than once.
 */
internal class RefreshingHttpTransport(
    private val delegate: HttpTransport,
    private val tokenProvider: AuthTokenProvider,
    private val authenticationEvents: AuthenticationEventSink,
) : HttpTransport {
    override fun execute(request: HttpRequest): TransportResult {
        val replayBody = request.body?.copyOf()
        return try {
            val first = delegate.execute(request.copy(body = replayBody?.copyOf()))
            if (first !is TransportResult.Response || first.value.statusCode != 401) return first
            val refreshing = tokenProvider as? RefreshingAuthTokenProvider ?: return first
            runCatching {
                authenticationEvents.onAuthenticationExpired(first.value.headers["x-request-id"])
            }
            val refreshed = refreshing.refreshAccessToken() ?: return first
            delegate.execute(
                request.copy(
                    headers = request.headers + ("Authorization" to refreshed.authorizationValue()),
                    body = replayBody?.copyOf(),
                ),
            )
        } finally {
            replayBody?.fill(0)
            request.body?.fill(0)
        }
    }
}
