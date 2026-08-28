package com.ganj.vpn.ui

internal object GanjConnectionMotionPolicy {
    fun shouldPulse(
        state: GanjConnectionVisualState,
        effectsTier: GanjEffectsTier,
        reduceMotion: Boolean,
    ): Boolean = !reduceMotion &&
        effectsTier == GanjEffectsTier.Full &&
        state in setOf(
            GanjConnectionVisualState.Connecting,
            GanjConnectionVisualState.Connected,
            GanjConnectionVisualState.Reconnecting,
        )

    fun pulseRange(state: GanjConnectionVisualState): ClosedFloatingPointRange<Float> = when (state) {
        GanjConnectionVisualState.Connecting,
        GanjConnectionVisualState.Reconnecting,
        -> 0.94f..1.04f
        GanjConnectionVisualState.Connected -> 0.98f..1.025f
        else -> 1f..1f
    }
}
