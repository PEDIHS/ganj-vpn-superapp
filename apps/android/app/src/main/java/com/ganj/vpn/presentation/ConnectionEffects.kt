package com.ganj.vpn.presentation

import com.ganj.vpn.core.controlapi.ConnectionProfileBroker
import com.ganj.vpn.core.controlapi.ConnectionProfileLease
import com.ganj.vpn.core.controlapi.ProfileProvisioningBinding
import com.ganj.vpn.core.controlapi.ProfileProvisioningError
import com.ganj.vpn.core.controlapi.ProfileProvisioningResult
import com.ganj.vpn.core.vpn.ConnectionRequest
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException

@JvmInline
value class ConnectionActionHandle(val value: String) {
    init {
        require(value.matches(Regex("^vpn_action_[a-f0-9]{32}$")))
    }

    override fun toString(): String = "ConnectionActionHandle([OPAQUE])"
}

sealed interface ConnectionSafeAction {
    data class StartTunnel(val handle: ConnectionActionHandle) : ConnectionSafeAction
}

class ConnectionProvisioningAction internal constructor(
    val lease: ConnectionProfileLease,
    val binding: ProfileProvisioningBinding,
) {
    override fun toString(): String = "ConnectionProvisioningAction([REDACTED])"
}

interface ConnectionActionVault {
    fun store(
        lease: ConnectionProfileLease,
        binding: ProfileProvisioningBinding,
    ): ConnectionActionHandle

    fun consume(handle: ConnectionActionHandle): ConnectionProvisioningAction?
    fun clear()
}

class InMemoryConnectionActionVault(
    private val maximumEntries: Int = 4,
    private val tokenFactory: () -> String = ::secureConnectionToken,
) : ConnectionActionVault {
    private val values = linkedMapOf<ConnectionActionHandle, ConnectionProvisioningAction>()

    init {
        require(maximumEntries in 1..16)
    }

    @Synchronized
    override fun store(
        lease: ConnectionProfileLease,
        binding: ProfileProvisioningBinding,
    ): ConnectionActionHandle {
        while (values.size >= maximumEntries) values.remove(values.keys.first())
        var handle: ConnectionActionHandle
        do {
            handle = ConnectionActionHandle("vpn_action_" + tokenFactory())
        } while (values.containsKey(handle))
        values[handle] = ConnectionProvisioningAction(lease, binding)
        return handle
    }

    @Synchronized
    override fun consume(handle: ConnectionActionHandle): ConnectionProvisioningAction? =
        values.remove(handle)

    @Synchronized
    override fun clear() {
        values.clear()
    }
}

interface TunnelConnector {
    suspend fun connect(request: ConnectionRequest): Result<Unit>
    suspend fun disconnect(): Result<Unit>
}

sealed interface ConnectionEffectResult {
    data class Connected(
        val profileId: String,
        val serverId: String,
    ) : ConnectionEffectResult

    data class Failed(val failure: UiFailure) : ConnectionEffectResult
}

class ConnectionEffectExecutor(
    private val actions: ConnectionActionVault,
    private val broker: ConnectionProfileBroker,
    private val tunnel: TunnelConnector,
) {
    suspend fun execute(handle: ConnectionActionHandle): ConnectionEffectResult {
        val action = actions.consume(handle)
            ?: return failed("connection.action_expired", UiFailureKind.CONFLICT, retryable = true)
        return when (val provisioned = broker.provision(action.lease, action.binding)) {
            is ProfileProvisioningResult.Failure -> provisioningFailure(provisioned.error)
            is ProfileProvisioningResult.Success -> {
                val profile = provisioned.profile
                val profileId = profile.profileId
                val serverId = profile.serverId
                val connected = try {
                    tunnel.connect(ConnectionRequest(profile))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: RuntimeException) {
                    Result.failure(IllegalStateException("vpn.tunnel_connector_failed"))
                } finally {
                    profile.close()
                }
                if (connected.isSuccess) {
                    ConnectionEffectResult.Connected(profileId, serverId)
                } else {
                    failed("connection.tunnel_start_failed", UiFailureKind.SERVER, retryable = true)
                }
            }
        }
    }

    suspend fun disconnect(): Result<Unit> = try {
        tunnel.disconnect()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: RuntimeException) {
        Result.failure(error)
    }

    private fun provisioningFailure(error: ProfileProvisioningError): ConnectionEffectResult = when (error) {
        ProfileProvisioningError.LEASE_CONSUMED ->
            failed("connection.profile_consumed", UiFailureKind.CONFLICT, retryable = true)
        ProfileProvisioningError.EXPIRED ->
            failed("connection.profile_expired", UiFailureKind.CONFLICT, retryable = true)
        ProfileProvisioningError.CRYPTO_UNAVAILABLE ->
            failed("connection.device_crypto_unavailable", UiFailureKind.CONFIGURATION, retryable = false)
        ProfileProvisioningError.AUTHENTICATION_FAILED ->
            failed("connection.profile_authentication_failed", UiFailureKind.ENTITLEMENT, retryable = true)
        ProfileProvisioningError.UNSUPPORTED_PROTOCOL ->
            failed("connection.protocol_unsupported", UiFailureKind.PROTOCOL, retryable = false)
        ProfileProvisioningError.BINDING_MISMATCH,
        ProfileProvisioningError.UNSUPPORTED_ALGORITHM,
        ProfileProvisioningError.MALFORMED_ENVELOPE,
        ProfileProvisioningError.CRYPTO_FAILURE,
        ProfileProvisioningError.PAYLOAD_INVALID,
        ProfileProvisioningError.PAYLOAD_MISMATCH,
        -> failed("connection.profile_rejected", UiFailureKind.PROTOCOL, retryable = true)
    }

    private fun failed(
        key: String,
        kind: UiFailureKind,
        retryable: Boolean,
    ): ConnectionEffectResult.Failed = ConnectionEffectResult.Failed(
        UiFailure(kind, key, retryable),
    )
}

private fun secureConnectionToken(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
}
