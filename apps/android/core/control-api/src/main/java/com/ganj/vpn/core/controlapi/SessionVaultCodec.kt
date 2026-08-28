package com.ganj.vpn.core.controlapi

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

internal object SessionVaultCodec {
    private const val VERSION = 1
    private const val MAX_PAYLOAD_BYTES = 32_768

    fun encode(session: AuthSessionCredentials): ByteArray {
        val output = ByteArrayOutputStream(1_024)
        DataOutputStream(output).use { data ->
            data.writeInt(VERSION)
            data.writeUTF(session.userId)
            data.writeUTF(session.deviceId)
            data.writeUTF(session.accessToken.rawValue())
            data.writeUTF(session.accessTokenExpiresAt)
            data.writeUTF(session.refreshToken.rawValue())
            data.writeUTF(session.refreshTokenExpiresAt)
        }
        return output.toByteArray().also {
            require(it.size <= MAX_PAYLOAD_BYTES) { "Session payload is too large" }
        }
    }

    fun decode(payload: ByteArray): AuthSessionCredentials {
        require(payload.isNotEmpty() && payload.size <= MAX_PAYLOAD_BYTES) { "Session payload size is invalid" }
        return DataInputStream(ByteArrayInputStream(payload)).use { data ->
            require(data.readInt() == VERSION) { "Unsupported session vault version" }
            val result = AuthSessionCredentials(
                userId = data.readUTF(),
                deviceId = data.readUTF(),
                accessToken = AccessToken.from(data.readUTF()),
                accessTokenExpiresAt = data.readUTF(),
                refreshToken = RefreshToken.from(data.readUTF()),
                refreshTokenExpiresAt = data.readUTF(),
            )
            require(data.read() == -1) { "Trailing session vault bytes" }
            result
        }
    }
}
