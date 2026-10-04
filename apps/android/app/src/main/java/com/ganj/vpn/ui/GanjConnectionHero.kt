package com.ganj.vpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ganj.vpn.R
import kotlin.math.PI
import kotlin.math.sin

/** Tunnel core: interrupted motion follows runtime truth, with no delayed state/action writes. */
@Composable
internal fun GanjConnectionHero(
    state: GanjConnectionVisualState,
    action: String,
    title: String,
    disconnecting: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    preparing: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val effects = LocalGanjVisualEffectsPolicy.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val motion = lifecycle.isAtLeast(Lifecycle.State.STARTED) &&
        GanjDestinationMotionPolicy.shouldAnimate(effects.tier, effects.reduceMotion)
    val connected = state == GanjConnectionVisualState.Connected && !disconnecting && !selecting && !preparing
    val recovering = state == GanjConnectionVisualState.Reconnecting
    val busy = selecting || preparing || disconnecting || recovering || state == GanjConnectionVisualState.Connecting
    val failed = state == GanjConnectionVisualState.Failed
    val caption = when {
        selecting -> stringResource(R.string.connection_selecting_config)
        disconnecting -> stringResource(R.string.connection_disconnecting_label)
        preparing -> stringResource(R.string.connection_preparing_label)
        else -> title
    }
    val subtitle = stringResource(when {
        selecting -> R.string.connection_selecting_hint
        disconnecting -> R.string.connection_disconnecting_hint
        recovering -> R.string.connection_recovering_hint
        connected -> R.string.connection_active_hint
        busy -> R.string.connection_establishing_hint
        failed -> R.string.connection_failed_hint
        state == GanjConnectionVisualState.Unavailable -> R.string.subscription_choose_hint
        else -> R.string.connection_idle_hint
    })
    val targetAccent = when {
        failed -> colors.error
        recovering -> colors.secondary
        else -> colors.primary
    }
    val accent = animateColorAsState(targetAccent, tween(if (motion) GanjMotion.State else 0), label = "coreAccent")
    val face = animateColorAsState(if (connected) colors.primary else colors.surface,
        tween(if (motion) GanjMotion.Confirmation else 0), label = "coreFace")
    val ink = animateColorAsState(if (connected) colors.onPrimary else targetAccent,
        tween(if (motion) GanjMotion.State else 0), label = "coreInk")
    val activation = animateFloatAsState(if (connected) 1f else 0f,
        tween(if (motion) GanjMotion.Confirmation else 0, easing = FastOutSlowInEasing), label = "coreSeal")
    val contraction = animateFloatAsState(if (disconnecting) 1f else 0f,
        tween(if (motion) GanjMotion.State else 0), label = "coreRelease")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = animateFloatAsState(if (pressed && motion && !busy) 1f else 0f,
        if (motion) spring(0.82f, 650f) else snap(), label = "corePress")
    // A nonlinear pair of arcs conveys ongoing work; sweep is not a completion percentage.
    val cycle: State<Float> = if (busy && motion && effects.tier != GanjEffectsTier.Reduced) {
        rememberInfiniteTransition(label = "tunnelWork").animateFloat(0f, 1f,
            infiniteRepeatable(tween(GanjMotion.WorkCycle, easing = LinearEasing)), label = "tunnelArcPhase")
    } else remember { mutableFloatStateOf(0.18f) }
    val ripple = remember { Animatable(1f) }
    var previousConnected by remember { mutableStateOf(connected) }
    LaunchedEffect(connected, motion) {
        val confirm = connected && !previousConnected && motion
        previousConnected = connected
        if (confirm) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(GanjMotion.Confirmation, easing = LinearOutSlowInEasing))
        } else ripple.snapTo(1f)
    }
    val haptic = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().testTag("connection-hero"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GanjSecurityIcon(targetAccent, Modifier.size(20.dp))
            Text(caption, color = colors.onSurface, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false).semantics { liveRegion = LiveRegionMode.Polite })
        }
        Box(Modifier.size(244.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val c = accent.value
                val active = activation.value
                val shrink = 1f - contraction.value * 0.10f
                val inset = 10.dp.toPx() + contraction.value * 10.dp.toPx()
                val origin = Offset(inset, inset)
                val bounds = Size(size.width - 2 * inset, size.height - 2 * inset)
                if (!effects.reduceTransparency) drawCircle(
                    Brush.radialGradient(listOf(c.copy(alpha = 0.09f + active * 0.04f), Color.Transparent)),
                    radius = size.minDimension / 2)
                // Static broken perimeter makes the control recognisable even without motion.
                repeat(4) { quadrant ->
                    drawArc(c.copy(alpha = 0.18f), quadrant * 90f + 12f, 66f, false, origin, bounds,
                        style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                }
                if (busy) {
                    val phase = cycle.value
                    val wave = (sin(phase * 2 * PI).toFloat() + 1f) / 2f
                    val direction = if (disconnecting) -1f else 1f
                    val angle = direction * (phase * 360f + wave * 24f) - 90f
                    val sweep = if (preparing) 30f + wave * 35f else 40f + wave * 85f
                    drawArc(c, angle, sweep * shrink, false, origin, bounds,
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(c.copy(alpha = 0.35f), angle + 180f, 35f + (1f - wave) * 45f,
                        false, origin, bounds, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                } else if (active > 0f) {
                    repeat(4) { quadrant -> drawArc(c.copy(alpha = active * 0.75f),
                        quadrant * 90f + 12f, 66f * active, false, origin, bounds,
                        style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)) }
                }
                if (connected && ripple.value < 1f) drawCircle(c.copy(alpha = (1f - ripple.value) * 0.28f),
                    radius = size.minDimension * (0.34f + ripple.value * 0.15f),
                    style = Stroke(2.dp.toPx()))
            }
            val shape = remember { RoundedCornerShape(40.dp) }
            Box(Modifier.size(164.dp).graphicsLayer {
                scaleX = 1f - press.value * 0.04f - contraction.value * 0.025f
                scaleY = 1f - press.value * 0.035f - contraction.value * 0.025f
                rotationZ = -2f * press.value
                translationY = press.value * 2.dp.toPx()
            }.shadow(if (effects.tier == GanjEffectsTier.Reduced) 0.dp else 10.dp, shape, clip = false)
                .clip(shape).testTag("connect-action")
                .semantics { contentDescription = if (busy) caption else action; stateDescription = caption }
                .clickable(enabled = !busy, interactionSource = interaction, indication = null, role = Role.Button,
                    onClick = {
                        if (!effects.reduceMotion) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    })) {
                Canvas(Modifier.matchParentSize()) {
                    val base = face.value
                    val seal = activation.value
                    drawRoundRect(base, cornerRadius = CornerRadius(40.dp.toPx()))
                    if (!effects.reduceTransparency) {
                        drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.20f),
                            Color.Transparent, accent.value.copy(alpha = 0.09f))), cornerRadius = CornerRadius(40.dp.toPx()))
                        drawRoundRect(colors.outline.copy(alpha = 0.18f), Offset(1.dp.toPx(), 1.dp.toPx()),
                            Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()), CornerRadius(39.dp.toPx()),
                            style = Stroke(1.dp.toPx()))
                    }
                    val w = size.width
                    val h = size.height
                    val line = 4.dp.toPx()
                    val icon = ink.value
                    if (seal < 1f) {
                        drawArc(icon.copy(alpha = 1f - seal), -42f, 264f, false,
                            Offset(w * 0.32f, h * 0.32f), Size(w * 0.36f, h * 0.36f),
                            style = Stroke(line, cap = StrokeCap.Round))
                        drawLine(icon.copy(alpha = 1f - seal), Offset(w * 0.5f, h * 0.26f),
                            Offset(w * 0.5f, h * 0.48f), line, cap = StrokeCap.Round)
                    }
                    if (seal > 0f) {
                        val shield = Path().apply {
                            moveTo(w * 0.5f, h * 0.23f)
                            lineTo(w * 0.73f, h * 0.33f)
                            cubicTo(w * 0.73f, h * 0.59f, w * 0.68f, h * 0.70f, w * 0.5f, h * 0.79f)
                            cubicTo(w * 0.32f, h * 0.70f, w * 0.27f, h * 0.59f, w * 0.27f, h * 0.33f)
                            close()
                        }
                        drawPath(shield, icon.copy(alpha = seal), style = Stroke(2.5.dp.toPx()))
                        val p1 = Offset(w * 0.39f, h * 0.51f)
                        val p2 = Offset(w * 0.47f, h * 0.59f)
                        val p3 = Offset(w * 0.63f, h * 0.42f)
                        drawLine(icon.copy(alpha = seal), p1, Offset(p1.x + (p2.x - p1.x) * seal,
                            p1.y + (p2.y - p1.y) * seal), line, cap = StrokeCap.Round)
                        drawLine(icon.copy(alpha = seal), p2, Offset(p2.x + (p3.x - p2.x) * seal,
                            p2.y + (p3.y - p2.y) * seal), line, cap = StrokeCap.Round)
                    }
                }
            }
        }
        Text(if (busy) caption else action, style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurface, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {})
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
    }
}
