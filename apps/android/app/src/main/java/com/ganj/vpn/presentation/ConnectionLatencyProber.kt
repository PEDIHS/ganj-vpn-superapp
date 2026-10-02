package com.ganj.vpn.presentation

import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.ConnectionProfileBroker
import com.ganj.vpn.core.controlapi.ConnectionProfileCommand
import com.ganj.vpn.core.controlapi.ControlApiRepository
import com.ganj.vpn.core.controlapi.ProfileProvisioningBinding
import com.ganj.vpn.core.controlapi.ProfileProvisioningResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Only a server-authorized, device-bound lease can reach the native latency probe. */
class ConnectionLatencyProber(
    private val repository: ControlApiRepository,
    private val broker: ConnectionProfileBroker,
    private val currentUser: CurrentUserIdProvider,
    private val context: ConnectionProfileContextProvider,
    private val tunnel: TunnelConnector,
) {
    // The pinned libXray temporary-core probe is process-global. Serialize inactive probes so
    // two server tests cannot replace each other's native Xray state.
    private val permits = Semaphore(6)

    suspend fun probe(serviceId: String, serverId: String): Long? = withContext(Dispatchers.IO) {
        try {
            // An active tunnel is measured through that tunnel by GanjVpnService and never starts
            // a temporary Xray instance.
            when (val active = tunnel.probeActive(serviceId, serverId)) {
                is ActiveTunnelProbe.Measured -> return@withContext active.latencyMillis
                ActiveTunnelProbe.NotActive -> Unit
            }
            permits.withPermit {
                withTimeoutOrNull(INACTIVE_PROBE_TIMEOUT_MILLIS) {
                    val user = currentUser.currentUserId()?.takeIf(String::isNotBlank)
                        ?: return@withTimeoutOrNull null
                    val proof = context.forServer(serviceId, serverId)
                        ?: return@withTimeoutOrNull null
                    val response = repository.prepareConnection(ConnectionProfileCommand(
                        serviceId = serviceId, deviceId = proof.deviceId, serverId = serverId,
                        clientNonce = proof.clientNonce, deviceProof = proof.deviceProof,
                    )) as? ApiResult.Success ?: return@withTimeoutOrNull null
                    val lease = response.value
                    if (lease.serverId != serverId) return@withTimeoutOrNull null
                    val binding = ProfileProvisioningBinding(
                        profileId = lease.profileId, userId = user, serviceId = serviceId,
                        deviceId = proof.deviceId, serverId = serverId, expiresAt = lease.expiresAt,
                    )
                    val provisioned = broker.provision(lease, binding) as? ProfileProvisioningResult.Success
                        ?: return@withTimeoutOrNull null
                    provisioned.profile.use { tunnel.probe(it) }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val INACTIVE_PROBE_TIMEOUT_MILLIS = 6_000L
    }
}
