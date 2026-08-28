package com.ganj.vpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ganj.vpn.R

internal enum class GanjConnectionVisualState {
    Disconnected,
    Connecting,
    Connected,
    Reconnecting,
    Failed,
    Unavailable,
}

@Composable
internal fun GanjLiquidConnectControl(
    state: GanjConnectionVisualState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effects = LocalGanjVisualEffectsPolicy.current
    val accentTarget = when (state) {
        GanjConnectionVisualState.Disconnected -> MaterialTheme.colorScheme.primary
        GanjConnectionVisualState.Connecting -> MaterialTheme.colorScheme.secondary
        GanjConnectionVisualState.Connected -> GanjEmeraldBright
        GanjConnectionVisualState.Reconnecting -> GanjJade
        GanjConnectionVisualState.Failed -> MaterialTheme.colorScheme.error
        GanjConnectionVisualState.Unavailable -> MaterialTheme.colorScheme.outline
    }
    val animatedAccent by animateColorAsState(accentTarget, label = "ganjConnectionAccent")
    val accent = if (effects.reduceMotion) accentTarget else animatedAccent
    val scaleTarget = when (state) {
        GanjConnectionVisualState.Connecting, GanjConnectionVisualState.Reconnecting -> 0.985f
        else -> 1f
    }
    val animatedScale by animateFloatAsState(
        targetValue = if (effects.reduceMotion) 1f else scaleTarget,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 320f),
        label = "ganjConnectionStateScale",
    )
    val shouldPulse = GanjConnectionMotionPolicy.shouldPulse(
        state = state,
        effectsTier = effects.tier,
        reduceMotion = effects.reduceMotion,
    )
    val pulseRange = GanjConnectionMotionPolicy.pulseRange(state)
    val pulseScale = if (shouldPulse) {
        val transition = rememberInfiniteTransition(label = "ganjConnectionLiquidPulse")
        val value by transition.animateFloat(
            initialValue = pulseRange.start,
            targetValue = pulseRange.endInclusive,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = if (state == GanjConnectionVisualState.Connected) 1900 else 1050),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ganjConnectionHaloPulse",
        )
        value
    } else {
        1f
    }

    val status = when (state) {
        GanjConnectionVisualState.Disconnected -> stringResource(R.string.connection_disconnected)
        GanjConnectionVisualState.Connecting -> stringResource(R.string.connection_connecting)
        GanjConnectionVisualState.Connected -> stringResource(R.string.connection_connected)
        GanjConnectionVisualState.Reconnecting -> stringResource(R.string.connection_reconnecting)
        GanjConnectionVisualState.Failed -> stringResource(R.string.connection_failed)
        GanjConnectionVisualState.Unavailable -> stringResource(R.string.connection_unavailable)
    }
    val action = when (state) {
        GanjConnectionVisualState.Connected -> stringResource(R.string.connection_action_disconnect)
        GanjConnectionVisualState.Failed -> stringResource(R.string.connection_action_retry)
        else -> stringResource(R.string.connection_action_connect)
    }
    val a11y = UiAccessibilityPolicy.connectionStateDescription(
        connected = state == GanjConnectionVisualState.Connected,
        busy = state == GanjConnectionVisualState.Connecting || state == GanjConnectionVisualState.Reconnecting,
        hasActiveService = state != GanjConnectionVisualState.Unavailable,
        connectedText = stringResource(R.string.connection_a11y_connected),
        busyText = stringResource(R.string.connection_a11y_busy),
        readyText = stringResource(R.string.connection_a11y_ready),
        unavailableText = stringResource(R.string.connection_a11y_unavailable),
    )
    val contentColor = when (state) {
        GanjConnectionVisualState.Failed -> MaterialTheme.colorScheme.onError
        GanjConnectionVisualState.Unavailable -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onPrimary
    }
    val haloAlpha = when (effects.tier) {
        GanjEffectsTier.Full -> 0.18f
        GanjEffectsTier.Balanced -> 0.11f
        GanjEffectsTier.Reduced -> 0.055f
    }

    Box(
        modifier = modifier
            .size(226.dp)
            .scale(if (effects.reduceMotion) 1f else animatedScale)
            .semantics {
                stateDescription = status
                contentDescription = a11y
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(222.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            accent.copy(alpha = haloAlpha),
                            accent.copy(alpha = haloAlpha * 0.35f),
                            Color.Transparent,
                        ),
                    ),
                )
                .border(
                    1.dp,
                    accent.copy(alpha = if (effects.reduceTransparency) 0.12f else 0.22f),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(194.dp)
                .clip(CircleShape)
                .border(
                    1.dp,
                    LocalGanjGlassPalette.current.highlight.copy(
                        alpha = if (effects.reduceTransparency) 0.05f else 0.12f,
                    ),
                    CircleShape,
                ),
        )
        GanjLiquidAction(
            onClick = onClick,
            enabled = state != GanjConnectionVisualState.Unavailable,
            accent = accent,
            shapeRadius = 999.dp,
            modifier = Modifier.size(176.dp),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}
