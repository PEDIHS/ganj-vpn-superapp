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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
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

/** Keep the gold glint on capable devices, but never run a hidden shimmer.
 * Low-memory devices retain the same static gold finish without per-frame work.
 */
internal fun shouldRunSignaturePhase(
    active: Boolean,
    reduceMotion: Boolean,
    tier: GanjEffectsTier,
): Boolean = active && !reduceMotion && tier != GanjEffectsTier.Reduced

/** Read only inside drawing lambdas. Offscreen lazy items and paused screens stop their work. */
@Composable
internal fun rememberSignaturePhase(active: Boolean = true): State<Float> {
    val effects = LocalGanjVisualEffectsPolicy.current
    val owner = LocalLifecycleOwner.current
    val phase = remember { Animatable(0f) }
    val animate = shouldRunSignaturePhase(active, effects.reduceMotion, effects.tier)
    LaunchedEffect(animate, owner) {
        if (!animate) phase.snapTo(0f)
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
    // A button needs a press response, not its own forever-running animator.
    // The animated VIP hero retains its single phase loop.
    val shape = RoundedCornerShape(10.dp)
    val disabled = MaterialTheme.colorScheme.surfaceVariant
    val disabledInk = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.heightIn(min = 48.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .clip(shape).background(if (enabled) Brush.linearGradient(SignatureMetal) else Brush.linearGradient(listOf(disabled, disabled)))
        .border(1.dp, if (enabled) Color(0xFFF2D887) else disabledInk.copy(alpha = .2f), shape)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
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
    val gold = ganjSignatureTone(GanjSignatureTone.Gold)
    val emerald = ganjSignatureTone(GanjSignatureTone.Emerald)
    val shape = RoundedCornerShape(17.dp)
    Row(modifier.fillMaxWidth().clip(shape)
        .background(Brush.linearGradient(listOf(
            gold.copy(alpha = .10f).compositeOver(MaterialTheme.colorScheme.surface),
            MaterialTheme.colorScheme.surface,
            emerald.copy(alpha = .07f).compositeOver(MaterialTheme.colorScheme.surface))))
        .border(1.dp, gold.copy(alpha = .45f), shape).clickable(role = Role.Button, onClick = onClick)
        .padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        GanjSignatureGem(Modifier.size(48.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("‹", style = MaterialTheme.typography.titleLarge, color = gold)
    }
}

@Composable
internal fun GanjSignatureVipHero(modifier: Modifier = Modifier) {
    val dark = referenceDark()
    val gold = ganjSignatureTone(GanjSignatureTone.Gold)
    val emerald = ganjSignatureTone(GanjSignatureTone.Emerald)
    val phase = rememberSignaturePhase()
    val rim = RoundedCornerShape(22.dp)
    val background = if (dark)
        listOf(Color(0xFF1C3228), Color(0xFF162A23), Color(0xFF202824))
        else listOf(Color(0xFFF4F9F0), Color(0xFFFFFFFF), Color(0xFFEDF5E9))

    Box(modifier.fillMaxWidth().heightIn(min = 244.dp).clip(rim)
        .background(Brush.linearGradient(background))
        .border(1.dp, gold.copy(alpha = .65f), rim)
        .testTag("signature-vip-sculpted-hero")) {
        Canvas(Modifier.matchParentSize()) {
            val orbital = size.width * .28f
            drawCircle(Brush.radialGradient(listOf(gold.copy(alpha = .18f), Color.Transparent)),
                radius = orbital * 2.25f, center = Offset(size.width * .86f, size.height * .24f))
            drawCircle(gold.copy(alpha = .24f), radius = orbital,
                center = Offset(size.width * .80f, size.height * .27f),
                style = Stroke(1.dp.toPx()))
            drawArc(gold.copy(alpha = .94f),
                -65f + sin(phase.value * 6.283f) * 11f, 96f, false,
                Offset(size.width * .8f - orbital, size.height * .27f - orbital),
                Size(orbital * 2, orbital * 2),
                style = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round))
            val wave = Path().apply {
                moveTo(-8f, size.height * .83f)
                cubicTo(size.width * .27f, size.height * .65f,
                    size.width * .61f, size.height * .99f,
                    size.width + 8f, size.height * .77f)
            }
            drawPath(wave, Brush.linearGradient(listOf(emerald, gold, emerald)),
                style = Stroke(1.65.dp.toPx(), cap = StrokeCap.Round))
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GanjSignatureEyebrow("GANJ / SIGNATURE COLLECTION", GanjSignatureTone.Gold)
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("VIP", color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text("تجربه‌ای متفاوت", color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("اشتراک‌های ویژه گنج، با جزئیات و قیمت شفاف",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
                Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.matchParentSize()) {
                        drawCircle(gold.copy(alpha = .22f), style = Stroke(1.dp.toPx()))
                        drawCircle(emerald.copy(alpha = .50f), size.minDimension * .39f,
                            style = Stroke(1.dp.toPx()))
                    }
                    GanjSignatureGem(Modifier.size(56.dp).graphicsLayer { rotationZ = -8f })
                }
            }
            Spacer(Modifier.height(10.dp))
            GanjSignatureMiniPill("GANJ · PRIVATE ACCESS", GanjSignatureTone.Gold)
        }
    }
}


/** Live VIP membership marker sourced only from an active VIP entitlement. */
@Composable
internal fun GanjSignatureVipMembership(onOpenSubscription: () -> Unit) {
    val emerald = ganjSignatureTone(GanjSignatureTone.Emerald)
    val gold = ganjSignatureTone(GanjSignatureTone.Gold)
    val shape = RoundedCornerShape(17.dp)
    Row(Modifier.fillMaxWidth().clip(shape)
        .background(Brush.linearGradient(listOf(
            emerald.copy(alpha = .12f).compositeOver(MaterialTheme.colorScheme.surface),
            gold.copy(alpha = .07f).compositeOver(MaterialTheme.colorScheme.surface))))
        .border(1.dp, emerald.copy(alpha = .48f), shape)
        .padding(15.dp).testTag("vip-active-membership"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GanjSignatureGem(Modifier.size(42.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("عضویت VIP فعال", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("اطلاعات مدت، حجم و دستگاه‌ها در اشتراک‌های شماست",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GanjGlassButton("جزئیات", onOpenSubscription, accent = emerald, filled = false,
            shapeRadius = 12.dp)
    }
}
