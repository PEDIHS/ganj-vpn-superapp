package com.ganj.vpn.composition

import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.ConnectionServer
import com.ganj.vpn.core.controlapi.ServerApi
import com.ganj.vpn.core.deviceidentity.AndroidDeviceIdentity
import com.ganj.vpn.core.deviceidentity.DeviceProofRequest
import com.ganj.vpn.presentation.ConnectionProfileContext
import com.ganj.vpn.presentation.ConnectionProfileContextProvider
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.WeakHashMap

internal interface ConnectionServerController {
    fun servers(entitlementId: String? = null): ApiResult<List<ConnectionServer>>
    fun selectServer(serverId: String?)
    fun selectedServerId(): String?
}

internal fun connectionProfileProofPath(entitlementId: String): String =
    "/v1/services/$entitlementId/connection-profile"

internal class LiveConnectionProfileContextProvider(
    private val serverApi: ServerApi,
    private val identity: AndroidDeviceIdentity,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) : ConnectionProfileContextProvider, ConnectionServerController {
    @Volatile
    private var selectedServerId: String? = null

    override fun servers(entitlementId: String?): ApiResult<List<ConnectionServer>> = serverApi.servers(entitlementId)

    override fun selectServer(serverId: String?) {
        selectedServerId = serverId
    }

    override fun selectedServerId(): String? = selectedServerId

    override fun forEntitlement(entitlementId: String): ConnectionProfileContext? {
        val available = (serverApi.servers(entitlementId) as? ApiResult.Success)?.value.orEmpty()
        if (available.isEmpty()) return null
        val selected = selectedServerId?.let { id -> available.firstOrNull { it.id == id } }
            ?: available.first()
        selectedServerId = selected.id
        return signedContext(entitlementId, selected.id)
    }

    override fun forServer(entitlementId: String, serverId: String): ConnectionProfileContext? {
        val available = (serverApi.servers(entitlementId) as? ApiResult.Success)?.value.orEmpty()
        if (available.none { it.id == serverId }) return null
        return signedContext(entitlementId, serverId)
    }

    private fun signedContext(entitlementId: String, serverId: String): ConnectionProfileContext? {
        val publicIdentity = identity.publicIdentity().getOrNull() ?: return null
        val clientNonce = UUID.randomUUID().toString().replace("-", "")
        val proofNonce = UUID.randomUUID().toString().replace("-", "")
        val path = connectionProfileProofPath(entitlementId)
        val unsignedBody = buildString(160) {
            append("{\"client_nonce\":\"").append(clientNonce)
            append("\",\"device_id\":\"").append(publicIdentity.installationId)
            append("\",\"server_id\":\"").append(serverId).append("\"}")
        }.toByteArray(StandardCharsets.UTF_8)
        val request = DeviceProofRequest.create(
            method = "POST",
            pathAndQuery = path,
            body = unsignedBody,
            timestampEpochSeconds = nowEpochSeconds(),
            nonce = proofNonce,
        )
        val proof = identity.sign(request).getOrNull()?.compactValue() ?: return null
        return ConnectionProfileContext(
            deviceId = publicIdentity.installationId,
            serverId = serverId,
            clientNonce = clientNonce,
            deviceProof = proof,
        )
    }
}

internal object ConnectionServerCompositionRegistry {
    private val values = WeakHashMap<GanjComposition, ConnectionServerController>()

    @Synchronized
    fun bind(composition: GanjComposition, controller: ConnectionServerController) {
        values[composition] = controller
    }

    @Synchronized
    fun controller(composition: GanjComposition): ConnectionServerController? = values[composition]

    @Synchronized
    fun currentController(): ConnectionServerController? = values.values.firstOrNull()

    @Synchronized
    fun unbind(composition: GanjComposition) {
        values.remove(composition)
    }
}
