package com.ganj.vpn.presentation

import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.ConnectionProfileBroker
import com.ganj.vpn.core.controlapi.ConnectionProfileCommand
import com.ganj.vpn.core.controlapi.ControlApiRepository
import com.ganj.vpn.core.controlapi.ProfileProvisioningBinding
import com.ganj.vpn.core.controlapi.ProfileProvisioningResult
import com.ganj.vpn.core.vpn.ProvisionedProfile
import com.ganj.vpn.core.xray.VpnRuntimeException
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Every probe must be backed by a server-authorized, device-bound, one-use lease. */
class ConnectionLatencyProber(
    private val repository: ControlApiRepository,
    private val broker: ConnectionProfileBroker,
    private val currentUser: CurrentUserIdProvider,
    private val context: ConnectionProfileContextProvider,
    private val tunnel: TunnelConnector,
) {
    // Keep native single and bulk operations mutually exclusive, especially beside the TUN.
    private val permits = Semaphore(1)
    // HTTP preparation runs concurrently; avoid flooding the control API.
    private val leasePermits = Semaphore(6)

    private data class Prepared(
        val profile: ProvisionedProfile? = null,
        val result: LatencyProbeResult? = null,
    )

    suspend fun probe(serviceId: String, serverId: String): Long? = probeDetailed(serviceId, serverId).latencyMillis

    suspend fun probeDetailed(serviceId: String, serverId: String): LatencyProbeResult = permits.withPermit {
        withContext(Dispatchers.IO) {
            val prepared = prepare(serviceId, serverId)
            prepared.result ?: prepared.profile!!.use { tunnel.probeDetailed(it) }
        }
    }

    /**
     * Prepare independent, authenticated leases concurrently, then pass all validated
     * configs to a single protected native pingBatch. Results retain their server IDs.
     * Bad/missing leases never reach the native library.
     */
    suspend fun probeBatchDetailed(
        serviceId: String,
        serverIds: List<String>,
    ): Map<String, LatencyProbeResult> = permits.withPermit {
        withContext(Dispatchers.IO) {
            val ids = serverIds.distinct()
            if (ids.isEmpty()) return@withContext emptyMap()
            val owned = ConcurrentLinkedQueue<ProvisionedProfile>()
            try {
                val prepared = coroutineScope {
                    ids.map { id ->
                        async {
                            leasePermits.withPermit {
                                prepare(serviceId, id) { owned.add(it) }
                            }
                        }
                    }.awaitAll()
                }
                val available = prepared.mapIndexedNotNull { index, item ->
                    item.profile?.let { index to it }
                }
                val native = if (available.isEmpty()) emptyList() else
                    tunnel.probeDetailedBatch(available.map { it.second })
                val resolved = mutableMapOf<Int, LatencyProbeResult>()
                available.forEachIndexed { index, (originalIndex, _) ->
                    resolved[originalIndex] = native.getOrElse(index) { failed("probe.native_failed") }
                }
                ids.mapIndexed { index, id ->
                    id to (prepared[index].result ?: resolved[index] ?: failed("probe.native_failed"))
                }.toMap()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ids.associateWith { failed("probe.unavailable") }
            } finally {
                owned.forEach { it.close() }
            }
        }
    }

    private suspend fun prepare(
        serviceId: String,
        serverId: String,
        takeOwnership: (ProvisionedProfile) -> Unit = {},
    ): Prepared {
        return try {
        when (val active = tunnel.probeActive(serviceId, serverId)) {
            is ActiveTunnelProbe.Measured ->
                return Prepared(result = active.latencyMillis?.let { LatencyProbeResult.Measured(it) }
                    ?: failed(active.failureCode ?: "probe.unavailable"))
            // A separate, authorized, protected outbound may be tested beside the managed TUN.
            ActiveTunnelProbe.OtherTunnelActive, ActiveTunnelProbe.NotActive -> Unit
        }
        val user = currentUser.currentUserId()?.takeIf(String::isNotBlank)
            ?: return Prepared(result = LatencyProbeResult.Failed(
                UiFailure(UiFailureKind.AUTHENTICATION, "auth.required", true),
            ))
        val proof = context.forServer(serviceId, serverId)
            ?: return Prepared(result = failed("probe.context_unavailable"))
        val response = repository.prepareConnection(ConnectionProfileCommand(
            serviceId = serviceId, deviceId = proof.deviceId, serverId = serverId,
            clientNonce = proof.clientNonce, deviceProof = proof.deviceProof,
        ))
        val lease = when (response) {
            is ApiResult.Success -> response.value
            is ApiResult.Failure -> return Prepared(result = LatencyProbeResult.Failed(
                ConnectionFailures.api(response.error),
            ))
        }
        if (lease.serverId != serverId) return Prepared(result = LatencyProbeResult.Failed(
            ConnectionFailures.provisioning(com.ganj.vpn.core.controlapi.ProfileProvisioningError.BINDING_MISMATCH),
        ))
        val binding = ProfileProvisioningBinding(
            profileId = lease.profileId, userId = user, serviceId = serviceId,
            deviceId = proof.deviceId, serverId = serverId, expiresAt = lease.expiresAt,
        )
        when (val provisioned = broker.provision(lease, binding)) {
            is ProfileProvisioningResult.Failure -> Prepared(result = LatencyProbeResult.Failed(
                ConnectionFailures.provisioning(provisioned.error),
            ))
            is ProfileProvisioningResult.Success -> {
                takeOwnership(provisioned.profile)
                Prepared(profile = provisioned.profile)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: VpnRuntimeException) {
        Prepared(result = LatencyProbeResult.Failed(ConnectionFailures.runtime(error.code)))
        } catch (_: Exception) {
            Prepared(result = failed("probe.unavailable"))
        }
    }

    private fun failed(code: String) = LatencyProbeResult.Failed(ConnectionFailures.probe(code))
}
