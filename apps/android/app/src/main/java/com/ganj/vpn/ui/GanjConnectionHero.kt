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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ganj.vpn.R

/** Circular reference control. Labels/time are inside the hit area; motion cannot create VPN state. */
@Composable
internal fun GanjConnectionHero(
    state: GanjConnectionVisualState,
    action: String,
    title: String,
    disconnecting: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    preparing: Boolean = false,
    duration: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val dark = referenceDark()
    val effects = LocalGanjVisualEffectsPolicy.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val motion = lifecycle.isAtLeast(Lifecycle.State.STARTED) &&
        GanjDestinationMotionPolicy.shouldAnimate(effects.tier, effects.reduceMotion)
    val connected = state == GanjConnectionVisualState.Connected && !disconnecting && !selecting && !preparing
    val failed = state == GanjConnectionVisualState.Failed
    val busy = selecting || preparing || disconnecting || state == GanjConnectionVisualState.Reconnecting || state == GanjConnectionVisualState.Connecting
    val caption = when {
        selecting -> stringResource(R.string.connection_selecting_config)
        disconnecting -> stringResource(R.string.connection_disconnecting_label)
        preparing -> stringResource(R.string.connection_preparing_label)
        connected -> stringResource(R.string.reference_connected)
        failed -> stringResource(R.string.reference_failed)
        busy -> title
        else -> stringResource(R.string.reference_tap_connect)
    }
    val hint = when {
        connected -> ""
        selecting -> stringResource(R.string.connection_selecting_hint)
        disconnecting -> stringResource(R.string.connection_disconnecting_hint)
        busy -> stringResource(R.string.connection_establishing_hint)
        failed -> stringResource(R.string.reference_tap_retry)
        state == GanjConnectionVisualState.Unavailable -> stringResource(R.string.subscription_choose_hint)
        else -> stringResource(R.string.reference_ready)
    }
    val topTarget = when {
        failed -> if (dark) GanjReference.PowerErrorTop else colors.errorContainer
        connected -> GanjReference.PowerOnTop
        else -> GanjReference.PowerOffTop
    }
    val bottomTarget = when {
        failed -> if (dark) GanjReference.PowerErrorBottom else colors.errorContainer
        connected -> GanjReference.PowerOnBottom
        else -> GanjReference.PowerOffBottom
    }
    val top = animateColorAsState(topTarget, tween(if (motion) 520 else 0), label = "powerFaceTop")
    val bottom = animateColorAsState(bottomTarget, tween(if (motion) 520 else 0), label = "powerFaceBottom")
    val accentTarget = when { failed -> colors.error; busy -> GanjReference.Gold; else -> GanjReference.Emerald }
    val accent = animateColorAsState(accentTarget, tween(if (motion) 300 else 0), label = "powerRim")
    val cycle: State<Float> = if (busy && motion) {
        rememberInfiniteTransition(label = "powerWork").animateFloat(0f, 360f,
            infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "powerArc")
    } else remember { mutableFloatStateOf(0f) }
    val breath: State<Float> = if (motion && !busy && !failed) {
        rememberInfiniteTransition(label = "powerAmbient").animateFloat(.45f, .85f,
            infiniteRepeatable(tween(if(connected)2250 else 2500), androidx.compose.animation.core.RepeatMode.Reverse), label = "powerBreath")
    } else remember { mutableFloatStateOf(.65f) }
    val shake = remember { Animatable(0f) }
    val confirmation = remember { Animatable(1f) }
    var previousConnected by remember { mutableStateOf(connected) }
    LaunchedEffect(failed, motion) {
        shake.snapTo(0f)
        if (failed && motion) shake.animateTo(0f, keyframes {
            durationMillis = 440; -5f at 70; 5f at 145; -3f at 230; 2f at 310; 0f at 440
        })
    }
    LaunchedEffect(connected, motion) {
        val run = connected && !previousConnected && motion
        previousConnected = connected
        if (run) { confirmation.snapTo(0f); confirmation.animateTo(1f, tween(850)) }
        else confirmation.snapTo(1f)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = animateFloatAsState(if (pressed && motion) .97f else 1f,
        if (motion) spring(.88f, 700f) else snap(), label = "powerPress")
    val haptic = LocalHapticFeedback.current
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("connection-hero"), contentAlignment = Alignment.Center) {
        val size = minOf(maxWidth, if (fontScale >= 1.5f) 302.dp else 250.dp)
        val face = size - 38.dp
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = Stroke(1.dp.toPx())
                val c = accent.value
                if (failed) repeat(3) { i -> drawArc(c.copy(alpha = .75f), i * 120f + 10f, 102f, false,
                    topLeft = androidx.compose.ui.geometry.Offset(6.dp.toPx(), 6.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(this.size.width - 12.dp.toPx(), this.size.height - 12.dp.toPx()), style = stroke) }
                else drawCircle(c.copy(alpha = if (connected) .48f else .20f), radius = this.size.minDimension / 2 - 6.dp.toPx(), style = stroke)
                drawCircle(c.copy(alpha = .20f), radius = this.size.minDimension / 2 - 14.dp.toPx(), style = stroke)
                if (!busy && !failed) drawArc(GanjReference.Gold.copy(alpha=breath.value), -125f, 80f, false,
                    androidx.compose.ui.geometry.Offset(6.dp.toPx(),6.dp.toPx()),
                    androidx.compose.ui.geometry.Size(this.size.width-12.dp.toPx(),this.size.height-12.dp.toPx()),
                    style=Stroke(1.2.dp.toPx(),cap=StrokeCap.Round))
                if (busy) {
                    drawArc(GanjReference.Gold, cycle.value - 35f, 105f, false,
                        androidx.compose.ui.geometry.Offset(6.dp.toPx(), 6.dp.toPx()),
                        androidx.compose.ui.geometry.Size(this.size.width - 12.dp.toPx(), this.size.height - 12.dp.toPx()),
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(GanjReference.Emerald, -cycle.value + 120f, 80f, false,
                        androidx.compose.ui.geometry.Offset(14.dp.toPx(), 14.dp.toPx()),
                        androidx.compose.ui.geometry.Size(this.size.width - 28.dp.toPx(), this.size.height - 28.dp.toPx()),
                        style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                }
                if (connected && confirmation.value < 1f) drawCircle(c.copy(alpha = (1f - confirmation.value) * .5f),
                    radius = this.size.minDimension * (.40f + confirmation.value * .1f), style = Stroke(1.5.dp.toPx()))
                drawCircle(if (failed) colors.error else GanjReference.Gold, 2.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(center.x, this.size.height - 2.dp.toPx()))
            }
            Box(Modifier.size(face).graphicsLayer {
                scaleX = press.value; scaleY = press.value; translationX = shake.value.dp.toPx()
            }.clip(CircleShape).testTag("connect-action")
                .semantics { contentDescription = if (busy) caption else action; stateDescription = caption }
                .clickable(enabled = !busy, interactionSource = interaction, indication = null, role = Role.Button,
                    onClick = { if (!effects.reduceMotion) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }),
                contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(Brush.linearGradient(listOf(top.value, bottom.value)))
                    if (!effects.reduceTransparency) {
                        drawCircle(Brush.radialGradient(listOf(Color(0xFFB2FFD8).copy(alpha = .22f), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(this.size.width * .2f, 0f), radius = this.size.width * .9f))
                        drawOval(Color.White.copy(alpha = .055f), topLeft = androidx.compose.ui.geometry.Offset(-this.size.width * .15f, -this.size.height * .32f),
                            size = androidx.compose.ui.geometry.Size(this.size.width * 1.3f, this.size.height * .65f))
                    }
                    drawCircle(if (failed) Color(0xFFF3908C) else Color(0xFFB8C887), radius = this.size.minDimension/2 - .7.dp.toPx(), style = Stroke(1.dp.toPx()))
                    drawCircle(Color.White.copy(alpha = .10f), radius = this.size.minDimension/2 - 6.dp.toPx(), style = Stroke(.7.dp.toPx()))
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Canvas(Modifier.size(42.dp).graphicsLayer { alpha=if(!busy && !failed && !connected) .7f+breath.value*.3f else 1f }) {
                        val ink = if (failed && !dark) colors.onErrorContainer else Color(0xFFE2F9EC)
                        val line = 2.4.dp.toPx()
                        if (failed) {
                            drawCircle(ink, this.size.minDimension * .35f, style = Stroke(line))
                            drawLine(ink, androidx.compose.ui.geometry.Offset(this.size.width * .38f, this.size.height * .38f), androidx.compose.ui.geometry.Offset(this.size.width * .62f, this.size.height * .62f), line, StrokeCap.Round)
                            drawLine(ink, androidx.compose.ui.geometry.Offset(this.size.width * .62f, this.size.height * .38f), androidx.compose.ui.geometry.Offset(this.size.width * .38f, this.size.height * .62f), line, StrokeCap.Round)
                        } else {
                            drawArc(ink, -42f, 264f, false, androidx.compose.ui.geometry.Offset(this.size.width * .16f, this.size.height * .16f),
                                androidx.compose.ui.geometry.Size(this.size.width * .68f, this.size.height * .68f), style = Stroke(line, cap = StrokeCap.Round))
                            drawLine(ink, androidx.compose.ui.geometry.Offset(this.size.width * .5f, this.size.height * .08f), androidx.compose.ui.geometry.Offset(this.size.width * .5f, this.size.height * .46f), line, StrokeCap.Round)
                        }
                    }
                    Text(caption, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
                        color = if (failed && !dark) colors.onErrorContainer else Color(0xFFF0FFF4), fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center)
                    if (connected) duration() else Text(hint, style = MaterialTheme.typography.labelSmall,
                        color = if (failed && !dark) colors.onErrorContainer else Color(0xFFC0E8D2), textAlign = TextAlign.Center)
                }
            }
        }
    }
}
