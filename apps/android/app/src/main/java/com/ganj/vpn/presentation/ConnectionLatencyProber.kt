package com.ganj.vpn.presentation

import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.ConnectionProfileBroker
import com.ganj.vpn.core.controlapi.ConnectionProfileCommand
import com.ganj.vpn.core.controlapi.ControlApiRepository
import com.ganj.vpn.core.controlapi.ProfileProvisioningBinding
import com.ganj.vpn.core.controlapi.ProfileProvisioningResult
import com.ganj.vpn.core.xray.VpnRuntimeException
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
    private val permits = Semaphore(1)

    suspend fun probe(serviceId: String, serverId: String): Long? = probeDetailed(serviceId, serverId).latencyMillis

    suspend fun probeDetailed(serviceId: String, serverId: String): LatencyProbeResult = permits.withPermit {
        withContext(Dispatchers.IO) {
            try {
                when (val active = tunnel.probeActive(serviceId, serverId)) {
                    is ActiveTunnelProbe.Measured -> return@withContext active.latencyMillis?.let {
                        LatencyProbeResult.Measured(it)
                    } ?: failed(active.failureCode ?: "probe.unavailable")
                    ActiveTunnelProbe.OtherTunnelActive -> return@withContext failed("probe.active_tunnel_busy")
                    ActiveTunnelProbe.NotActive -> Unit
                }
                val user = currentUser.currentUserId()?.takeIf(String::isNotBlank)
                    ?: return@withContext LatencyProbeResult.Failed(UiFailure(UiFailureKind.AUTHENTICATION, "auth.required", true))
                val proof = context.forServer(serviceId, serverId)
                    ?: return@withContext failed("probe.context_unavailable")
                val response = repository.prepareConnection(ConnectionProfileCommand(
                    serviceId = serviceId, deviceId = proof.deviceId, serverId = serverId,
                    clientNonce = proof.clientNonce, deviceProof = proof.deviceProof,
                ))
                val lease = when (response) {
                    is ApiResult.Success -> response.value
                    is ApiResult.Failure -> return@withContext LatencyProbeResult.Failed(ConnectionFailures.api(response.error))
                }
                if (lease.serverId != serverId) return@withContext LatencyProbeResult.Failed(
                    ConnectionFailures.provisioning(com.ganj.vpn.core.controlapi.ProfileProvisioningError.BINDING_MISMATCH),
                )
                val binding = ProfileProvisioningBinding(
                    profileId = lease.profileId, userId = user, serviceId = serviceId,
                    deviceId = proof.deviceId, serverId = serverId, expiresAt = lease.expiresAt,
                )
                when (val provisioned = broker.provision(lease, binding)) {
                    is ProfileProvisioningResult.Failure -> LatencyProbeResult.Failed(ConnectionFailures.provisioning(provisioned.error))
                    is ProfileProvisioningResult.Success -> provisioned.profile.use { tunnel.probeDetailed(it) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: VpnRuntimeException) {
                LatencyProbeResult.Failed(ConnectionFailures.runtime(error.code))
            } catch (_: Exception) {
                failed("probe.unavailable")
            }
        }
    }

    private fun failed(code: String) = LatencyProbeResult.Failed(ConnectionFailures.probe(code))
}
