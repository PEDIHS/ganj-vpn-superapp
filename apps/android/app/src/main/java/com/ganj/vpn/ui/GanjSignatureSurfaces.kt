package com.ganj.vpn.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlin.math.sin

/** Product-approved gold belongs to Store/Profile/VIP; Connect's palette is unchanged. */
@Composable
internal fun signatureGold(): Color = if (referenceDark()) Color(0xFFF2CE70) else Color(0xFF80540C)

internal val SignatureMetal = listOf(Color(0xFFB98022), Color(0xFFF6D77A), Color(0xFFFFF0AB), Color(0xFFDFB24E), Color(0xFFC5902C))
internal val SignatureInk = Color(0xFF352409)

/** Read only inside drawing lambdas. Offscreen lazy items and paused screens stop their work. */
@Composable
internal fun rememberSignaturePhase(): State<Float> {
    val effects = LocalGanjVisualEffectsPolicy.current
    val owner = LocalLifecycleOwner.current
    val phase = remember { Animatable(0f) }
    LaunchedEffect(effects.reduceMotion, owner) {
        if (effects.reduceMotion) phase.snapTo(0f)
        else owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                phase.snapTo(0f)
                phase.animateTo(1f, tween(7500, easing = LinearEasing))
            }
        }
    }
    return phase.asState()
}

@Composable
internal fun GanjSignatureButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val effects = LocalGanjVisualEffectsPolicy.current
    val scale = animateFloatAsState(if (pressed && !effects.reduceMotion) .975f else 1f,
        if (effects.reduceMotion) snap() else tween(GanjMotion.Press), label = "signaturePress")
    val phase = rememberSignaturePhase()
    val shape = RoundedCornerShape(10.dp)
    val disabled = MaterialTheme.colorScheme.surfaceVariant
    val disabledInk = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.heightIn(min = 48.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .clip(shape).background(if (enabled) Brush.linearGradient(SignatureMetal) else Brush.linearGradient(listOf(disabled, disabled)))
        .border(1.dp, if (enabled) Color(0xFFF2D887) else disabledInk.copy(alpha = .2f), shape)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        if (enabled && !effects.reduceMotion) Canvas(Modifier.matchParentSize()) {
            // One short reflection followed by a long resting interval; it never covers the label.
            val x = (phase.value * 4f - 1f) * size.width
            if (x < size.width * 1.5f) drawRect(Brush.linearGradient(
                listOf(Color.Transparent, Color(0xFFFFFBED).copy(alpha = .32f), Color.Transparent),
                start = Offset(x - size.width * .25f, 0f), end = Offset(x + size.width * .25f, size.height)))
        }
        Text(text, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            color = if (enabled) SignatureInk else disabledInk, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun GanjSignatureGem(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(SignatureMetal))
        .border(1.dp, Color(0xFFF2D887), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize(.55f)) {
            val w = size.width; val h = size.height
            val p = Path().apply { moveTo(w * .22f, h * .12f); lineTo(w * .78f, h * .12f)
                lineTo(w, h * .42f); lineTo(w * .5f, h); lineTo(0f, h * .42f); close()
                moveTo(0f, h * .42f); lineTo(w, h * .42f)
                moveTo(w * .22f, h * .12f); lineTo(w * .36f, h * .42f); lineTo(w * .5f, h)
                moveTo(w * .78f, h * .12f); lineTo(w * .64f, h * .42f); lineTo(w * .5f, h) }
            drawPath(p, SignatureInk, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
internal fun GanjSignatureBanner(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val gold = signatureGold()
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.fillMaxWidth().clip(shape)
        .background(gold.copy(alpha = .07f).compositeOver(MaterialTheme.colorScheme.surface))
        .border(1.dp, gold.copy(alpha = .5f), shape).clickable(role = Role.Button, onClick = onClick)
        .padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        GanjSignatureGem(Modifier.size(48.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = gold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("‹", style = MaterialTheme.typography.titleLarge, color = gold)
    }
}

@Composable
internal fun GanjSignatureVipHero(modifier: Modifier = Modifier) {
    val gold = signatureGold()
    val phase = rememberSignaturePhase()
    GanjGlassSurface(GanjGlassRole.Dense, modifier = modifier.fillMaxWidth(), accent = gold,
        shapeRadius = 18.dp, padding = PaddingValues(18.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(142.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(gold.copy(alpha = .25f), size.minDimension / 2f - 6.dp.toPx(), style = Stroke(1.dp.toPx()))
                    drawArc(gold, -45f + sin(phase.value * 6.283f) * 20f, 76f, false,
                        Offset(6.dp.toPx(), 6.dp.toPx()), Size(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()),
                        style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                }
                GanjSignatureGem(Modifier.size(92.dp).graphicsLayer { rotationZ = -8f })
            }
        }
        Text("GANJ VIP", Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold, color = gold, textAlign = TextAlign.Center)
        Text("گنج، یک سطح بالاتر", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Text("مشخصات و قیمت پلن‌های موجود را بررسی کنید.", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
