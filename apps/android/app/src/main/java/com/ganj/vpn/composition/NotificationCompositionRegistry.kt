package com.ganj.vpn.composition

import com.ganj.vpn.core.controlapi.NotificationApi
import java.util.WeakHashMap

internal object NotificationCompositionRegistry {
    private val values = WeakHashMap<GanjComposition, NotificationApi>()

    @Synchronized
    fun bind(composition: GanjComposition, api: NotificationApi) {
        values[composition] = api
    }

    @Synchronized
    fun api(composition: GanjComposition): NotificationApi? = values[composition]

    @Synchronized
    fun unbind(composition: GanjComposition) {
        values.remove(composition)
    }
}
