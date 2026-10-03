package com.ganj.vpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ganj.vpn.R

/** Native layered power control; continuous motion is confined to actual transient work. */
@Composable
internal fun GanjConnectionHero(
    state: GanjConnectionVisualState,
    action: String,
    title: String,
    disconnecting: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val effects = LocalGanjVisualEffectsPolicy.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val motion = lifecycle.isAtLeast(Lifecycle.State.STARTED) &&
        GanjDestinationMotionPolicy.shouldAnimate(effects.tier, effects.reduceMotion)
    val connected = state == GanjConnectionVisualState.Connected
    val busy = selecting || disconnecting || state == GanjConnectionVisualState.Connecting ||
        state == GanjConnectionVisualState.Reconnecting
    val caption = when {
        selecting -> stringResource(R.string.connection_selecting_config)
        disconnecting -> stringResource(R.string.connection_disconnecting_label)
        else -> title
    }
    val subtitle = stringResource(when {
        selecting -> R.string.connection_selecting_hint
        disconnecting -> R.string.connection_disconnecting_hint
        state == GanjConnectionVisualState.Reconnecting -> R.string.connection_recovering_hint
        connected -> R.string.connection_active_hint
        state == GanjConnectionVisualState.Connecting -> R.string.connection_establishing_hint
        state == GanjConnectionVisualState.Failed -> R.string.connection_failed_hint
        state == GanjConnectionVisualState.Unavailable -> R.string.subscription_choose_hint
        else -> R.string.connection_idle_hint
    })
    val accent = when {
        busy -> colors.secondary
        connected -> colors.primary
        state == GanjConnectionVisualState.Failed -> colors.error
        else -> colors.onSurfaceVariant
    }
    val faceTarget = when {
        busy -> colors.secondaryContainer
        connected -> colors.primary
        else -> Color(0xFFF0F6F2)
    }
    val inkTarget = when {
        busy -> colors.onSecondaryContainer
        connected -> colors.onPrimary
        else -> Color(0xFF235641)
    }
    val face = animateColorAsState(faceTarget, tween(if (motion) 340 else 0), label = "powerFace")
    val ink = animateColorAsState(inkTarget, tween(if (motion) 340 else 0), label = "powerInk")
    val activation = animateFloatAsState(if (connected && !busy) 1f else 0f,
        tween(if (motion) 460 else 0, easing = FastOutSlowInEasing), label = "powerActivation")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = animateFloatAsState(if (pressed && motion && !busy) 1f else 0f,
        if (motion) spring(0.72f, 520f) else snap(), label = "powerPress")
    val spin: State<Float> = if (busy && motion && effects.tier == GanjEffectsTier.Full) {
        rememberInfiniteTransition(label = "powerBusy").animateFloat(0f, 360f,
            infiniteRepeatable(tween(1550, easing = LinearEasing)), label = "powerBusyArc")
    } else remember { mutableFloatStateOf(35f) }

    GanjGlassSurface(role = GanjGlassRole.Regular, accent = accent,
        modifier = Modifier.fillMaxWidth().testTag("connection-hero"), shapeRadius = 28.dp,
        padding = PaddingValues(horizontal = 18.dp, vertical = 20.dp)) {
        Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GanjSecurityIcon(accent, Modifier.size(20.dp))
            Text(caption, color = colors.onSurface, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
        }
        Box(Modifier.size(206.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val inset = 9.dp.toPx()
                val bounds = Size(size.width - inset * 2, size.height - inset * 2)
                val origin = Offset(inset, inset)
                val track = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
                drawArc(accent.copy(alpha = 0.12f), -90f, 360f, false, origin, bounds, style = track)
                if (busy) drawArc(accent.copy(alpha = 0.8f), spin.value - 90f, 95f, false,
                    origin, bounds, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                else if (activation.value > 0) drawArc(colors.primary.copy(alpha = 0.72f), -90f,
                    360f * activation.value, false, origin, bounds,
                    style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            }
            Box(Modifier.size(158.dp).graphicsLayer {
                scaleX = 1f - press.value * 0.035f
                scaleY = 1f - press.value * 0.025f
                translationY = press.value * 2.dp.toPx()
            }.shadow(if (effects.tier == GanjEffectsTier.Reduced) 0.dp else 12.dp, CircleShape, clip = false)
                .clip(CircleShape).testTag("connect-action")
                .semantics { contentDescription = if (busy) caption else action; stateDescription = caption }
                .clickable(enabled = !busy, interactionSource = interaction, indication = null,
                    role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    val base = face.value
                    drawCircle(Brush.radialGradient(listOf(base, base, base.copy(alpha = 0.90f)),
                        center = Offset(size.width * 0.34f, size.height * 0.25f), radius = size.width))
                    if (!effects.reduceTransparency) {
                        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = if (connected) 0.17f else 0.55f),
                            Color.Transparent), center = Offset(size.width * (0.28f + press.value * 0.10f),
                            size.height * 0.15f), radius = size.width * 0.75f))
                        drawCircle(Color.White.copy(alpha = 0.40f), radius = size.minDimension / 2 - 1.dp.toPx(),
                            style = Stroke(1.dp.toPx()))
                    }
                    val icon = ink.value
                    val line = 5.dp.toPx()
                    drawArc(icon, -42f, 264f, false,
                        topLeft = Offset(size.width * 0.30f, size.height * 0.30f),
                        size = Size(size.width * 0.40f, size.height * 0.40f),
                        style = Stroke(line, cap = StrokeCap.Round))
                    drawLine(icon, Offset(size.width * 0.5f, size.height * 0.245f),
                        Offset(size.width * 0.5f, size.height * 0.49f), line, cap = StrokeCap.Round)
                }
            }
        }
        Text(if (busy) caption else action, style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {})
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
