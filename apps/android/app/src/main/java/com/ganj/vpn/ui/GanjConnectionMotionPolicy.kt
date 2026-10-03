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
            GanjConnectionVisualState.Reconnecting,
        )

    fun pulseRange(state: GanjConnectionVisualState): ClosedFloatingPointRange<Float> = when (state) {
        GanjConnectionVisualState.Connecting,
        GanjConnectionVisualState.Reconnecting,
        -> 0.985f..1.015f
        else -> 1f..1f
    }
}
