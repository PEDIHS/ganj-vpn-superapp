package com.ganj.vpn.composition

import com.ganj.vpn.core.controlapi.SupportApi
import java.util.WeakHashMap

internal object SupportCompositionRegistry {
    private val values = WeakHashMap<GanjComposition, SupportApi>()
    private var current: SupportApi? = null

    @Synchronized
    fun bind(composition: GanjComposition, api: SupportApi) {
        values[composition] = api
        current = api
    }

    @Synchronized
    fun api(composition: GanjComposition): SupportApi? = values[composition]

    @Synchronized
    fun currentApi(): SupportApi? = current

    @Synchronized
    fun unbind(composition: GanjComposition) {
        val removed = values.remove(composition)
        if (removed != null && current === removed) current = values.values.lastOrNull()
    }
}
