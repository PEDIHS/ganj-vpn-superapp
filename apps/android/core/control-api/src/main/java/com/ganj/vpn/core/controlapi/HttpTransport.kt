package com.ganj.vpn.core.controlapi

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

internal enum class HttpMethod { GET, POST, PUT, DELETE }

internal data class HttpRequest(
    val method: HttpMethod,
    val pathAndQuery: String,
    val headers: Map<String, String>,
    val body: ByteArray? = null,
) {
    override fun toString(): String =
        "HttpRequest(method=$method, pathAndQuery=$pathAndQuery, headers=[REDACTED], body=[REDACTED])"
}

internal data class HttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: ByteArray,
) {
    override fun toString(): String =
        "HttpResponse(statusCode=$statusCode, headers=${headers.keys}, body=[REDACTED])"
}

internal sealed interface TransportResult {
    data class Response(val value: HttpResponse) : TransportResult
    data class Failure(val kind: NetworkFailure) : TransportResult
}

internal fun interface HttpTransport {
    fun execute(request: HttpRequest): TransportResult
}

internal class UrlConnectionTransport(
    baseUrl: String,
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 20_000,
    private val maxResponseBytes: Int = 1_048_576,
) : HttpTransport {
    private val baseUri = URI(baseUrl.trimEnd('/') + "/").also { uri ->
        require(uri.scheme == "https") { "Control API requires HTTPS" }
        require(!uri.host.isNullOrBlank()) { "Control API host is required" }
        require(uri.userInfo == null) { "Credentials are forbidden in the base URL" }
        require(uri.fragment == null && uri.query == null) { "Base URL cannot contain query or fragment" }
    }

    override fun execute(request: HttpRequest): TransportResult {
        var connection: HttpURLConnection? = null
        return try {
            require(request.pathAndQuery.startsWith('/'))
            require(!request.pathAndQuery.startsWith("//"))
            val resolved = baseUri.resolve(request.pathAndQuery.removePrefix("/"))
            require(resolved.scheme == baseUri.scheme && resolved.host == baseUri.host && resolved.port == baseUri.port) {
                "Request cannot escape the configured API origin"
            }
            require(resolved.path.startsWith(baseUri.path)) { "Request cannot escape the configured API base path" }
            val activeConnection = ControlApiNetworkRouting.openConnection(resolved.toURL()) as HttpURLConnection
            connection = activeConnection
            require(activeConnection is HttpsURLConnection) { "Control API requires TLS" }
            activeConnection.instanceFollowRedirects = false
            activeConnection.connectTimeout = connectTimeoutMillis
            activeConnection.readTimeout = readTimeoutMillis
            activeConnection.requestMethod = request.method.name
            activeConnection.setRequestProperty("Accept", "application/json")
            activeConnection.setRequestProperty("Cache-Control", "no-store")
            request.headers.forEach { (name, value) ->
                require(name.matches(HEADER_NAME)) { "Invalid request header" }
                require(value.none { it == '\r' || it == '\n' }) { "Invalid request header value" }
                activeConnection.setRequestProperty(name, value)
            }
            request.body?.let { body ->
                require(body.size <= 262_144) { "Request body is too large" }
                activeConnection.doOutput = true
                activeConnection.setFixedLengthStreamingMode(body.size)
                activeConnection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                activeConnection.outputStream.use { it.write(body) }
            }
            val status = activeConnection.responseCode
            val stream = if (status in 200..399) activeConnection.inputStream else activeConnection.errorStream
            val responseBody = stream?.use { readLimited(it, maxResponseBytes) } ?: ByteArray(0)
            val headers = activeConnection.headerFields
                .filterKeys { it != null }
                .mapNotNull { (name, values) ->
                    name?.let { headerName ->
                        values?.firstOrNull()?.let { headerName.lowercase() to it }
                    }
                }
                .toMap()
            TransportResult.Response(HttpResponse(status, headers, responseBody))
        } catch (_: SocketTimeoutException) {
            TransportResult.Failure(NetworkFailure.TIMEOUT)
        } catch (_: SSLException) {
            TransportResult.Failure(NetworkFailure.TLS)
        } catch (_: UnknownHostException) {
            TransportResult.Failure(NetworkFailure.OFFLINE_OR_DNS)
        } catch (_: ConnectException) {
            TransportResult.Failure(NetworkFailure.OFFLINE_OR_DNS)
        } catch (_: IOException) {
            TransportResult.Failure(NetworkFailure.IO)
        } catch (_: IllegalArgumentException) {
            TransportResult.Failure(NetworkFailure.IO)
        } finally {
            request.body?.fill(0)
            connection?.disconnect()
        }
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IOException("Response body limit exceeded")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        val HEADER_NAME = Regex("^[A-Za-z0-9!#$%&'*+.^_`|~-]+$")
    }
}
