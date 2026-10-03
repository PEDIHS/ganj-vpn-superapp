package com.ganj.vpn.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ganj.vpn.R
import com.ganj.vpn.core.vpn.ConnectionPhase
import com.ganj.vpn.core.vpn.ConnectionState
import com.ganj.vpn.presentation.ConnectionSessionTimer
import com.ganj.vpn.presentation.formatConnectionDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun GanjConnectionTelemetry(
    runtime: ConnectionState,
    sessionTimer: ConnectionSessionTimer?,
    ping: Long?,
    measuring: Boolean,
) {
    val timer = remember(sessionTimer) { sessionTimer ?: ConnectionSessionTimer(
        wallClock = System::currentTimeMillis, monotonicClock = SystemClock::elapsedRealtime) }
    val stacked = LocalDensity.current.fontScale >= 1.3f
    GanjGlassSurface(role = GanjGlassRole.Dense, modifier = Modifier.fillMaxWidth(),
        shapeRadius = 22.dp, padding = PaddingValues(16.dp)) {
        if (stacked) {
            GanjSessionDuration(runtime, timer)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            GanjSelectedProxyLatency(ping, measuring)
        } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) { GanjSessionDuration(runtime, timer) }
            Box(Modifier.weight(1f)) { GanjSelectedProxyLatency(ping, measuring) }
        }
    }
}

/** The ticking state lives here, so a one-second tick cannot recompose the page or power control. */
@Composable
private fun GanjSessionDuration(runtime: ConnectionState, timer: ConnectionSessionTimer) {
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(runtime)
    var elapsed by remember(timer, runtime.phase, runtime.connectedAtEpochMillis) {
        mutableStateOf(timer.elapsedMillis(runtime))
    }
    LaunchedEffect(owner, timer, runtime.phase, runtime.connectedAtEpochMillis) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            elapsed = timer.elapsedMillis(current)
            if (elapsed != null) while (isActive) {
                delay(1000)
                elapsed = timer.elapsedMillis(current)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.connection_session_duration), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(elapsed?.let { isolateTechnicalLtr(formatConnectionDuration(it).toPersianDigits()) } ?: "—",
            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("connection-duration"))
        Text(stringResource(if (elapsed != null) R.string.connection_duration_live
            else if (runtime.phase == ConnectionPhase.CONNECTED || runtime.phase == ConnectionPhase.RECONNECTING)
                R.string.connection_duration_unknown else R.string.connection_duration_unavailable), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GanjSelectedProxyLatency(ping: Long?, measuring: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.connection_selected_ping), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(persianTechnicalMetric(if (measuring) "…" else ping?.toString() ?: "—", "ms"),
            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(if (measuring) R.string.ping_measuring else if (ping == null)
            R.string.ping_not_measured else R.string.ping_last_result),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
