package com.ganj.vpn.composition

import com.ganj.vpn.core.controlapi.DeviceApi
import java.util.WeakHashMap

internal object DeviceCompositionRegistry {
    private val values = WeakHashMap<GanjComposition, DeviceApi>()

    @Synchronized
    fun bind(composition: GanjComposition, api: DeviceApi) {
        values[composition] = api
    }

    @Synchronized
    fun api(composition: GanjComposition): DeviceApi? = values[composition]

    @Synchronized
    fun unbind(composition: GanjComposition) {
        values.remove(composition)
    }
}
