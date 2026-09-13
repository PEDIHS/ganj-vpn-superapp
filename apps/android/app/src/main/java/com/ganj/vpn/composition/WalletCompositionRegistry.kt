package com.ganj.vpn.composition

import com.ganj.vpn.core.controlapi.WalletApi
import java.util.WeakHashMap

internal object WalletCompositionRegistry {
    private val values = WeakHashMap<GanjComposition, WalletApi>()

    @Synchronized
    fun bind(composition: GanjComposition, api: WalletApi) {
        values[composition] = api
    }

    @Synchronized
    fun api(composition: GanjComposition): WalletApi? = values[composition]

    @Synchronized
    fun unbind(composition: GanjComposition) {
        values.remove(composition)
    }
}
