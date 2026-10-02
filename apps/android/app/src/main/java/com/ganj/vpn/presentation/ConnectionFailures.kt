package com.ganj.vpn.presentation

import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.NetworkFailure
import com.ganj.vpn.core.controlapi.ProfileProvisioningError

/** Only fixed codes cross the native/API boundary; exception messages can contain credentials. */
object ConnectionFailures {
    fun runtime(code: String?): UiFailure {
        val key = when (code) {
            "vpn.profile_expired" -> "connection.profile_expired"
            "vpn.profile_compile_failed", "xray.config_invalid" -> "connection.config_invalid"
            "vpn.tun_establish_failed", "vpn.tun_missing", "xray.tun_unavailable" -> "connection.tun_failed"
            "vpn.socket_protection_failed", "xray.socket_protection_unavailable" -> "connection.socket_protection_failed"
            "vpn.profile_recovery_write_failed" -> "connection.recovery_write_failed"
            "vpn.profile_recovery_invalid" -> "connection.recovery_invalid"
            "vpn.service_bind_timeout" -> "connection.service_timeout"
            "vpn.service_start_failed", "vpn.service_bind_failed", "vpn.service_binding_rejected",
            "vpn.service_binding_died", "vpn.service_disconnected", "vpn.invalid_service_binder" -> "connection.service_failed"
            "xray.core_not_running" -> "connection.native_core_not_running"
            "xray.native_rejected", "xray.port_in_use" -> "connection.native_rejected"
            "xray.native_call_failed", "xray.invalid_native_response" -> "connection.native_unavailable"
            "vpn.reconnect_without_tunnel", "vpn.profile_recovery_missing" -> "connection.recovery_missing"
            "vpn.operation_cancelled", "vpn.connection_already_active" -> "connection.interrupted"
            "vpn.core_stop_failed" -> "connection.disconnect_failed"
            else -> "connection.tunnel_start_failed"
        }
        return UiFailure(UiFailureKind.SERVER, key, true, diagnosticCode =
            if (key == "connection.tunnel_start_failed") "vpn.unknown_failure" else code)
    }

    fun api(error: ApiError): UiFailure {
        val key = when (error) {
            is ApiError.Forbidden -> when (error.code) {
                "device_limit_reached" -> "connection.device_limit_reached"
                "invalid_device_proof" -> "connection.device_proof_invalid"
                "device_mismatch" -> "connection.device_mismatch"
                "server_unavailable" -> "connection.server_unavailable"
                "service_inactive", "entitlement_changed" -> "connection.service_inactive"
                else -> null
            }
            is ApiError.Network -> when (error.kind) {
                NetworkFailure.TIMEOUT -> "connection.api_timeout"
                NetworkFailure.TLS -> "connection.api_tls"
                NetworkFailure.OFFLINE_OR_DNS -> "connection.api_dns"
                NetworkFailure.IO -> "network.unavailable"
            }
            else -> null
        }
        return if (key == null) GanjPresentationMapper().apiFailure(error) else UiFailure(
            if (error is ApiError.Network) UiFailureKind.NETWORK else UiFailureKind.ENTITLEMENT,
            key, key != "connection.device_limit_reached", error.requestId, diagnosticCode = key,
        )
    }

    fun provisioning(error: ProfileProvisioningError): UiFailure {
        val key = when (error) {
            ProfileProvisioningError.LEASE_CONSUMED -> "connection.profile_consumed"
            ProfileProvisioningError.EXPIRED -> "connection.profile_expired"
            ProfileProvisioningError.CRYPTO_UNAVAILABLE -> "connection.device_crypto_unavailable"
            ProfileProvisioningError.AUTHENTICATION_FAILED -> "connection.profile_authentication_failed"
            ProfileProvisioningError.UNSUPPORTED_PROTOCOL -> "connection.protocol_unsupported"
            ProfileProvisioningError.BINDING_MISMATCH -> "connection.profile_binding_mismatch"
            ProfileProvisioningError.UNSUPPORTED_ALGORITHM -> "connection.profile_algorithm_unsupported"
            ProfileProvisioningError.MALFORMED_ENVELOPE -> "connection.profile_envelope_invalid"
            ProfileProvisioningError.CRYPTO_FAILURE -> "connection.profile_crypto_failed"
            ProfileProvisioningError.PAYLOAD_INVALID -> "connection.profile_payload_invalid"
            ProfileProvisioningError.PAYLOAD_MISMATCH -> "connection.profile_payload_mismatch"
        }
        val kind = when (error) {
            ProfileProvisioningError.LEASE_CONSUMED, ProfileProvisioningError.EXPIRED -> UiFailureKind.CONFLICT
            ProfileProvisioningError.CRYPTO_UNAVAILABLE -> UiFailureKind.CONFIGURATION
            ProfileProvisioningError.AUTHENTICATION_FAILED -> UiFailureKind.ENTITLEMENT
            else -> UiFailureKind.PROTOCOL
        }
        return UiFailure(kind, key,
            error != ProfileProvisioningError.CRYPTO_UNAVAILABLE && error != ProfileProvisioningError.UNSUPPORTED_PROTOCOL,
            diagnosticCode = key)
    }

    fun probe(code: String): UiFailure {
        val safe = when (code) {
            "probe.timeout", "probe.dns_failed", "probe.tls_failed", "probe.connection_refused",
            "probe.network_unreachable", "probe.http_failed", "probe.active_tunnel_busy",
            "probe.native_failed", "probe.unavailable", "probe.context_unavailable" -> code
            else -> "probe.native_failed"
        }
        return UiFailure(UiFailureKind.NETWORK, safe, true, diagnosticCode = safe)
    }
}

sealed interface LatencyProbeResult {
    data class Measured(val millis: Long) : LatencyProbeResult {
        init { require(millis >= 0) }
    }
    data class Failed(val failure: UiFailure) : LatencyProbeResult
}

val LatencyProbeResult.latencyMillis: Long?
    get() = (this as? LatencyProbeResult.Measured)?.millis
