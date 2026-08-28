package com.ganj.vpn.core.controlapi

import java.nio.charset.StandardCharsets

object AuthSessionProofPayload {
    const val GUEST_PATH = "/v1/auth/guest"
    const val REFRESH_PATH = "/v1/auth/refresh"

    fun guest(
        deviceId: String,
        keyVersion: String,
        signingPublicKeySpki: String,
        encryptionPublicKeyRaw: String,
    ): ByteArray = JsonEncoder.objectValue(
        // Keep lexicographic order in lock-step with server canonicalUnsignedBody().
        "device_id" to deviceId,
        "encryption_public_key_raw" to encryptionPublicKeyRaw,
        "key_version" to keyVersion,
        "signing_public_key_spki" to signingPublicKeySpki,
    ).toByteArray(StandardCharsets.UTF_8)

    fun refresh(
        deviceId: String,
        refreshToken: RefreshToken,
    ): ByteArray = JsonEncoder.objectValue(
        "device_id" to deviceId,
        "refresh_token" to refreshToken.rawValue(),
    ).toByteArray(StandardCharsets.UTF_8)
}
