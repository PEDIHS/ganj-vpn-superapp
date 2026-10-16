package com.ganj.vpn.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.ganj.vpn.presentation.LatencyKey
import com.ganj.vpn.presentation.LatencyReading
import com.ganj.vpn.presentation.LatencySnapshot
import com.ganj.vpn.presentation.SessionLatencyManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Render-only projection. Unrelated ping results must never redraw the scrolling list. */
internal data class GanjProbeVisual(
    val reading: LatencyReading? = null,
    val measuring: Boolean = false,
)

internal fun LatencySnapshot.probeVisual(key: LatencyKey): GanjProbeVisual =
    GanjProbeVisual(readings[key], key in measuring)

internal fun LatencySnapshot.serviceProbing(serviceId: String): Boolean =
    measuring.any { it.serviceId == serviceId }

@Composable
internal fun rememberGanjProbeVisual(
    latency: SessionLatencyManager?,
    key: LatencyKey?,
): GanjProbeVisual {
    if (latency == null || key == null) return GanjProbeVisual()
    val updates = remember(latency, key) {
        latency.state.map { it.probeVisual(key) }.distinctUntilChanged()
    }
    val status by updates.collectAsState(initial = latency.state.value.probeVisual(key))
    return status
}

@Composable
internal fun rememberGanjServiceProbing(
    latency: SessionLatencyManager?,
    serviceId: String?,
): Boolean {
    if (latency == null || serviceId == null) return false
    val updates = remember(latency, serviceId) {
        latency.state.map { it.serviceProbing(serviceId) }.distinctUntilChanged()
    }
    val busy by updates.collectAsState(initial = latency.state.value.serviceProbing(serviceId))
    return busy
}
