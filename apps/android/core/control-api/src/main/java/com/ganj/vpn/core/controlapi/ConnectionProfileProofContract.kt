package com.ganj.vpn.core.controlapi

import java.nio.charset.StandardCharsets

/**
 * Canonical device-proof input for one-time connection-profile issuance.
 *
 * The proof deliberately excludes device_proof itself and binds the signature to the exact
 * service path, selected server, device and fresh client nonce. Keep this contract byte-for-byte
 * aligned with services/control-api/src/application.js.
 */
object ConnectionProfileProofContract {
    fun path(serviceId: String): String {
        requireUuid("serviceId", serviceId)
        return "/v1/services/$serviceId/connection-profile"
    }

    fun unsignedBody(
        clientNonce: String,
        deviceId: String,
        serverId: String,
    ): ByteArray {
        requireUuid("deviceId", deviceId)
        requireUuid("serverId", serverId)
        require(clientNonce.length in 32..256)
        return JsonEncoder.objectValue(
            // Alphabetical order intentionally matches the backend canonical object serializer.
            "client_nonce" to clientNonce,
            "device_id" to deviceId,
            "server_id" to serverId,
        ).toByteArray(StandardCharsets.UTF_8)
    }
}
