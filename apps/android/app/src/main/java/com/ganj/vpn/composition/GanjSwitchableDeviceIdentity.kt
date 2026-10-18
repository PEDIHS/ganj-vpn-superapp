package com.ganj.vpn.composition

import android.app.Application
import com.ganj.vpn.core.controlapi.Gvp1CryptoResult
import com.ganj.vpn.core.controlapi.Gvp1DecryptRequest
import com.ganj.vpn.core.deviceidentity.AndroidDeviceIdentity
import com.ganj.vpn.core.deviceidentity.DeviceIdentity
import com.ganj.vpn.core.deviceidentity.DeviceProofRequest
import com.ganj.vpn.core.deviceidentity.DevicePublicIdentity
import com.ganj.vpn.core.deviceidentity.SignedDeviceProof

/**
 * Keep the device-bound signing and encryption keys on the same account slot
 * as the access and refresh tokens. The primary slot reuses all legacy keys.
 */
internal class GanjSwitchableDeviceIdentity(
    application: Application,
    private val slots: GanjMultiAccountVault,
    private val testNamespacePrefix: String = "",
) : DeviceIdentity {
    init { require(testNamespacePrefix.matches(Regex("[a-zA-Z0-9_-]{0,26}"))) }
    private val identities = listOf(
        AndroidDeviceIdentity.create(application, if (testNamespacePrefix.isEmpty()) "" else testNamespacePrefix + "_legacy"),
        AndroidDeviceIdentity.create(application, testNamespacePrefix + "_account_1"),
        AndroidDeviceIdentity.create(application, testNamespacePrefix + "_account_2"),
    )
    private fun current(): DeviceIdentity = identities[slots.activeIdentitySlot()]

    override fun publicIdentity(): Result<DevicePublicIdentity> = current().publicIdentity()
    override fun sign(request: DeviceProofRequest): Result<SignedDeviceProof> = current().sign(request)
    override fun rotate(): Result<DevicePublicIdentity> = current().rotate()
    override fun decrypt(request: Gvp1DecryptRequest): Gvp1CryptoResult = current().decrypt(request)
}
