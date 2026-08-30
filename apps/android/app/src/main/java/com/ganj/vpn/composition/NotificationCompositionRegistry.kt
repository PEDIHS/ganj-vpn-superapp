package com.ganj.vpn.composition

import com.ganj.vpn.core.controlapi.NotificationApi
import com.ganj.vpn.ui.GanjNotificationUnreadRegistry
import java.util.WeakHashMap

internal object NotificationCompositionRegistry {
    private val values = WeakHashMap<GanjComposition, NotificationApi>()
    private var current: NotificationApi? = null

    @Synchronized
    fun bind(composition: GanjComposition, api: NotificationApi) {
        values[composition] = api
        current = api
        // Never carry presentation-only unread state across a newly bound account/composition.
        GanjNotificationUnreadRegistry.clear()
    }

    @Synchronized
    fun api(composition: GanjComposition): NotificationApi? = values[composition]

    @Synchronized
    fun currentApi(): NotificationApi? = current

    @Synchronized
    fun unbind(composition: GanjComposition) {
        val removed = values.remove(composition)
        if (removed != null && current === removed) {
            current = values.values.lastOrNull()
            if (current == null) GanjNotificationUnreadRegistry.clear()
        }
    }
}
