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
    private val permits = Semaphore(2)

    suspend fun probe(serviceId: String, serverId: String): Long? = permits.withPermit {
        withContext(Dispatchers.IO) {
            try {
                val user = currentUser.currentUserId()?.takeIf(String::isNotBlank) ?: return@withContext null
                val proof = context.forServer(serviceId, serverId) ?: return@withContext null
                val response = repository.prepareConnection(ConnectionProfileCommand(
                    serviceId = serviceId, deviceId = proof.deviceId, serverId = serverId,
                    clientNonce = proof.clientNonce, deviceProof = proof.deviceProof,
                )) as? ApiResult.Success ?: return@withContext null
                val lease = response.value
                if (lease.serverId != serverId) return@withContext null
                val binding = ProfileProvisioningBinding(
                    profileId = lease.profileId, userId = user, serviceId = serviceId,
                    deviceId = proof.deviceId, serverId = serverId, expiresAt = lease.expiresAt,
                )
                val provisioned = broker.provision(lease, binding) as? ProfileProvisioningResult.Success
                    ?: return@withContext null
                provisioned.profile.use { tunnel.probe(it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }
}
