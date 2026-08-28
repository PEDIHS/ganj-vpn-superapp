package com.ganj.vpn.composition

import com.ganj.vpn.core.controlapi.ConnectionProfileProofContract
import com.ganj.vpn.core.deviceidentity.DeviceIdentity
import com.ganj.vpn.core.deviceidentity.DeviceProofRequest
import com.ganj.vpn.presentation.ConnectionProfileContext
import com.ganj.vpn.presentation.ConnectionProfileContextProvider
import java.security.SecureRandom

/** Produces a fresh, one-shot, device-bound proof for the exact entitlement/server pair. */
internal class AndroidConnectionProfileContextProvider(
    private val session: AndroidAuthSessionManager,
    private val identity: DeviceIdentity,
    private val random: SecureRandom = SecureRandom(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ConnectionProfileContextProvider {
    /** Legacy one-argument requests are deliberately fail-closed because a server must be explicit. */
    override fun forEntitlement(entitlementId: String): ConnectionProfileContext? = null

    override fun forConnection(entitlementId: String, serverId: String): ConnectionProfileContext? {
        val deviceId = session.currentDeviceId() ?: return null
        val clientNonce = randomHex(32)
        val proofNonce = randomHex(24)
        val unsignedBody = runCatching {
            ConnectionProfileProofContract.unsignedBody(
                clientNonce = clientNonce,
                deviceId = deviceId,
                serverId = serverId,
            )
        }.getOrNull() ?: return null
        val proof = try {
            val request = DeviceProofRequest.create(
                method = "POST",
                pathAndQuery = ConnectionProfileProofContract.path(entitlementId),
                body = unsignedBody,
                timestampEpochSeconds = nowMillis() / 1_000L,
                nonce = proofNonce,
            )
            identity.sign(request).getOrNull()?.compactValue()
        } finally {
            unsignedBody.fill(0)
        } ?: return null
        return ConnectionProfileContext(
            deviceId = deviceId,
            serverId = serverId,
            clientNonce = clientNonce,
            deviceProof = proof,
        )
    }

    private fun randomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return try {
            buildString(byteCount * 2) {
                bytes.forEach { byte ->
                    val value = byte.toInt() and 0xff
                    append(HEX[value ushr 4])
                    append(HEX[value and 0x0f])
                }
            }
        } finally {
            bytes.fill(0)
        }
    }

    override fun toString(): String = "AndroidConnectionProfileContextProvider([REDACTED])"

    private companion object {
        const val HEX = "0123456789abcdef"
    }
}
