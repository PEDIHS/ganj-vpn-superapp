package com.ganj.vpn.core.xray

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Classify locally and discard raw native errors, which may embed the entire private config. */
object NativeFailureClassifier {
    fun startup(message: String): String {
        val value = message.lowercase()
        return when {
            "address already in use" in value -> "xray.port_in_use"
            "tun" in value && ("permission denied" in value || "bad file descriptor" in value || "operation not permitted" in value) -> "xray.tun_unavailable"
            "failed to build" in value || "failed to parse" in value || "invalid config" in value || "invalid uuid" in value -> "xray.config_invalid"
            else -> "xray.native_rejected"
        }
    }

    fun probe(message: String): String {
        val value = message.lowercase()
        return when {
            "timeout" in value || "deadline exceeded" in value || "timed out" in value -> "probe.timeout"
            "no such host" in value || "name resolution" in value || "dns" in value && "failed" in value -> "probe.dns_failed"
            "certificate" in value || "tls" in value || "handshake" in value -> "probe.tls_failed"
            "connection refused" in value -> "probe.connection_refused"
            "network is unreachable" in value || "no route to host" in value -> "probe.network_unreachable"
            else -> "probe.native_failed"
        }
    }

    fun probe(error: Exception): String = when (error) {
        is SocketTimeoutException -> "probe.timeout"
        is UnknownHostException -> "probe.dns_failed"
        is SSLException -> "probe.tls_failed"
        is NoRouteToHostException -> "probe.network_unreachable"
        is ConnectException -> probe(error.message.orEmpty())
        else -> "probe.native_failed"
    }
}

sealed interface NativeProbeResult {
    data class Measured(val millis: Long) : NativeProbeResult
    data class Failed(val code: String) : NativeProbeResult
}
