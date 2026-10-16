package com.ganj.vpn.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.sin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/** Secondary account accents; emerald/gold and neutral surfaces remain the GANJ brand. */
internal enum class GanjProfileAccent { Emerald, Gold, Iris, Sky }

@Composable
internal fun profileAccent(tone: GanjProfileAccent): Color = when (tone) {
    GanjProfileAccent.Emerald -> MaterialTheme.colorScheme.primary
    GanjProfileAccent.Gold -> signatureGold()
    GanjProfileAccent.Iris -> if (referenceDark()) Color(0xFFB6B2F4) else Color(0xFF6155A3)
    GanjProfileAccent.Sky -> if (referenceDark()) Color(0xFF8FCDE1) else Color(0xFF256980)
}

internal enum class GanjProfileSymbol {
    Wallet, History, Subscription, Devices, Family, Appearance, Shield, Support, Diagnostics, Account, Refresh,
}

/** One 24-unit, rounded-stroke icon family. Icons supplement the visible semantic labels. */
@Composable
internal fun GanjProfileIcon(symbol: GanjProfileSymbol, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            val stroke = Stroke(1.65f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(vararg points: Float) {
                val path = Path().apply {
                    moveTo(points[0], points[1])
                    for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
                }
                drawPath(path, color, style = stroke)
            }
            fun rect(x: Float, y: Float, w: Float, h: Float, r: Float = 2f) =
                drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r), style = stroke)
            fun circle(x: Float, y: Float, r: Float) = drawCircle(color, r, Offset(x, y), style = stroke)
            when (symbol) {
                GanjProfileSymbol.Wallet -> {
                    rect(3f, 6f, 18f, 15f); line(3f, 7f, 3f, 5f, 17f, 2.5f, 17f, 6f)
                    rect(15f, 11f, 6f, 5f, 1f); drawCircle(color, .7f, Offset(17.5f, 13.5f))
                }
                GanjProfileSymbol.History -> {
                    drawArc(color, -135f, 300f, false, Offset(4f, 4f), Size(16f, 16f), style = stroke)
                    line(3f, 3f, 3f, 8f, 8f, 8f); line(12f, 7f, 12f, 12f, 15.5f, 14f)
                }
                GanjProfileSymbol.Subscription -> {
                    rect(4f, 3f, 16f, 7f); rect(4f, 14f, 16f, 7f)
                    line(8f, 6.5f, 12f, 6.5f); line(8f, 17.5f, 12f, 17.5f)
                    drawCircle(color, .8f, Offset(16.5f, 6.5f)); drawCircle(color, .8f, Offset(16.5f, 17.5f))
                }
                GanjProfileSymbol.Devices -> {
                    rect(2f, 4f, 14f, 11f); line(9f, 15f, 9f, 19f, 5f, 19f)
                    rect(15f, 9f, 7f, 12f, 1.5f); line(18f, 18f, 19f, 18f)
                }
                GanjProfileSymbol.Family -> {
                    circle(9f, 7f, 3f); drawArc(color, 180f, 180f, false, Offset(3f, 12f), Size(12f, 12f), style = stroke)
                    line(3f, 18f, 3f, 20f, 15f, 20f, 15f, 18f)
                    drawArc(color, -90f, 180f, false, Offset(14f, 4f), Size(6f, 6f), style = stroke)
                    drawArc(color, -90f, 90f, false, Offset(15f, 13f), Size(7f, 9f), style = stroke)
                }
                GanjProfileSymbol.Appearance -> {
                    circle(12f, 12f, 8f); drawArc(color, -90f, 180f, true, Offset(7f, 7f), Size(10f, 10f))
                    line(12f, 1f, 12f, 2f); line(12f, 22f, 12f, 23f)
                }
                GanjProfileSymbol.Shield -> {
                    val path = Path().apply {
                        moveTo(12f, 2f); lineTo(20f, 5f); lineTo(20f, 11f)
                        cubicTo(20f, 16f, 16f, 20f, 12f, 22f)
                        cubicTo(8f, 20f, 4f, 16f, 4f, 11f); lineTo(4f, 5f); close()
                    }
                    drawPath(path, color, style = stroke); line(8f, 11f, 11f, 14f, 16f, 9f)
                }
                GanjProfileSymbol.Support -> {
                    drawArc(color, 180f, 180f, false, Offset(4f, 3f), Size(16f, 17f), style = stroke)
                    rect(3f, 11f, 4f, 7f); rect(17f, 11f, 4f, 7f)
                    line(19f, 18f, 19f, 21f, 13f, 21f)
                }
                GanjProfileSymbol.Diagnostics -> {
                    line(2f, 12f, 6f, 12f, 9f, 5f, 14f, 20f, 17f, 12f, 22f, 12f)
                }
                GanjProfileSymbol.Account -> {
                    circle(12f, 7f, 3.5f)
                    val path = Path().apply { moveTo(4f, 21f); cubicTo(4f, 10f, 20f, 10f, 20f, 21f); close() }
                    drawPath(path, color, style = stroke)
                }
                GanjProfileSymbol.Refresh -> {
                    drawArc(color, -140f, 280f, false, Offset(4f, 4f), Size(16f, 16f), style = stroke)
                    line(3f, 3f, 3f, 8f, 8f, 8f)
                }
            }
        }
    }
}

@Composable
internal fun GanjProfileIconTile(symbol: GanjProfileSymbol, tone: GanjProfileAccent) {
    val accent = profileAccent(tone)
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
        .background(accent.copy(alpha = .12f))
        .border(1.dp, accent.copy(alpha = .16f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
        GanjProfileIcon(symbol, accent)
    }
}

/** Finite, draw-phase reveal: no looping animation, no requests or work delayed for decoration. */
@Composable
internal fun profileRevealModifier(key: String): Modifier {
    val effects = LocalGanjVisualEffectsPolicy.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val reveal = remember(key) { Animatable(if (effects.reduceMotion) 1f else 0f) }
    LaunchedEffect(key, effects.reduceMotion, lifecycle) {
        if (effects.reduceMotion || !lifecycle.isAtLeast(Lifecycle.State.RESUMED)) reveal.snapTo(1f)
        else reveal.animateTo(1f, tween(GanjMotion.Page))
    }
    return Modifier.graphicsLayer {
        alpha = if (effects.reduceMotion) 1f else .7f + .3f * reveal.value
        translationY = if (effects.reduceMotion) 0f else (1f - reveal.value) * 10.dp.toPx()
    }
}

private object ProfileWaterShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val corner = with(density) { 18.dp.toPx() }
        val radius = with(density) { 77.dp.toPx() }
        val shift = with(density) { 4.dp.toPx() }
        val base = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(corner))) }
        val notch = Path().apply { addOval(Rect(size.width / 2 - radius, -radius - shift, size.width / 2 + radius, radius - shift)) }
        return Outline.Generic(Path.combine(PathOperation.Difference, base, notch))
    }
}

@Composable
internal fun GanjProfileIdentity(
    name: String, username: String?, linked: Boolean, premium: Boolean?, onManage: () -> Unit,
    onOpenVip: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val gold = signatureGold()
    val phase = rememberSignaturePhase()
    Box(Modifier.fillMaxWidth().testTag("profile-identity")) {
        // The card starts under the avatar; a true concave notch keeps the reference wave geometry.
        Box(Modifier.fillMaxWidth().padding(top = 77.dp).clip(ProfileWaterShape)
            .background(colors.surface).border(1.dp, gold.copy(alpha = .35f), ProfileWaterShape)) {
            Canvas(Modifier.matchParentSize().testTag("profile-water")) {
                val offset = sin(phase.value * 6.283f)
                fun wave(y: Float, bend: Float, color: Color) {
                    val p = Path().apply {
                        moveTo(-20.dp.toPx(), y)
                        cubicTo(size.width * .27f, y + bend, size.width * .67f, y - bend, size.width + 20.dp.toPx(), y)
                        lineTo(size.width + 20.dp.toPx(), y + 100.dp.toPx()); lineTo(-20.dp.toPx(), y + 100.dp.toPx()); close()
                    }
                    drawPath(p, Brush.verticalGradient(listOf(color.copy(alpha = .16f), Color.Transparent), y, y + 100.dp.toPx()))
                    val edge = Path().apply { moveTo(-20.dp.toPx(), y)
                        cubicTo(size.width * .27f, y + bend, size.width * .67f, y - bend, size.width + 20.dp.toPx(), y) }
                    drawPath(edge, color.copy(alpha = .48f), style = Stroke(1.dp.toPx()))
                }
                wave(35.dp.toPx(), (14f + offset * 13f).dp.toPx(), colors.primary)
                wave(48.dp.toPx(), (-14f - offset * 10f).dp.toPx(), gold)
            }
            Column(Modifier.fillMaxWidth().padding(top = 84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name, Modifier.fillMaxWidth().padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, color = colors.onSurface, textAlign = TextAlign.Center)
                Text(username?.let(::isolateTechnicalLtr) ?: if (linked) "حساب تلگرام متصل" else "ورود با حساب تلگرام",
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                if (premium == true) {
                    GanjSignatureButton("عضو پریمیوم", onOpenVip ?: onManage,
                        Modifier.widthIn(max = 210.dp).padding(vertical = 8.dp).testTag("profile-premium"))
                } else if (premium == false) {
                    GanjStatusPill("استاندارد", GanjStatusTone.Neutral)
                    Spacer(Modifier.height(10.dp))
                } else Spacer(Modifier.height(8.dp))
                FlowRow(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 15.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(if (linked) "تلگرام متصل" else "حساب مهمان", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    GanjGlassButton("مدیریت حساب", onManage, filled = false, shapeRadius = 10.dp)
                }
            }
        }
        Box(Modifier.size(146.dp).align(Alignment.TopCenter).testTag("profile-identity-orbit"), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val r = size.minDimension / 2f - 2.dp.toPx()
                drawCircle(gold.copy(alpha = .40f), r, style = Stroke(1.dp.toPx()))
                drawCircle(colors.primary.copy(alpha = .20f), r - 6.dp.toPx(), style = Stroke(1.dp.toPx()))
                drawArc(gold, -35f + sin(phase.value * 6.283f) * 23f, 80f, false,
                    Offset(2.dp.toPx(), 2.dp.toPx()), Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                val sparkle = (sin(phase.value * 6.283f) * .25f + .65f)
                val x = size.width * .16f; val y = size.height * .16f; val d = 5.dp.toPx()
                drawLine(gold.copy(alpha = sparkle), Offset(x-d, y), Offset(x+d, y), 1.dp.toPx(), StrokeCap.Round)
                drawLine(gold.copy(alpha = sparkle), Offset(x, y-d), Offset(x, y+d), 1.dp.toPx(), StrokeCap.Round)
            }
            val shape = CircleShape
            Column(Modifier.size(116.dp).clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF17845D), Color(0xFF07503A))))
                .background(Brush.radialGradient(listOf(Color(0xFFADFFE0).copy(alpha = .22f), Color.Transparent), radius = 150f))
                .border(1.dp, Color(0xFFB6D79A), shape).clickable(role = Role.Button, onClick = onManage)
                .semantics { contentDescription = "مدیریت حساب" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                GanjProfileIcon(GanjProfileSymbol.Account, Color(0xFFEAFDD5), Modifier.size(35.dp))
            }
        }
    }
}

/** Finance state has no invented intermediate numbers; the full amount changes atomically. */
@Composable
internal fun GanjProfileWalletCard(
    balance: Long?, busy: Boolean, message: String?, onOpenWallet: () -> Unit,
    onOpenTransactions: () -> Unit, onRefresh: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val gold = profileAccent(GanjProfileAccent.Gold)
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().testTag("profile-wallet-live").clip(shape)
        .background(gold.copy(alpha = if (referenceDark()) .09f else .07f).compositeOver(colors.surface))
        .border(1.dp, gold.copy(alpha = .35f), shape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GanjProfileIconTile(GanjProfileSymbol.Wallet, GanjProfileAccent.Gold)
            Column(Modifier.weight(1f)) {
                Text("کیف پول گنج", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(when { message != null && balance != null -> "آخرین موجودی دریافت‌شده"
                    busy -> "در حال به‌روزرسانی"; else -> "اعتبار حساب شما" },
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(enabled = !busy, role = Role.Button, onClick = onRefresh)
                .semantics { contentDescription = "به‌روزرسانی موجودی" }.testTag("profile-wallet-refresh"), contentAlignment = Alignment.Center) {
                GanjProfileIcon(GanjProfileSymbol.Refresh, if (busy) colors.onSurfaceVariant else gold, Modifier.size(20.dp))
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onOpenWallet).testTag("profile-to-wallet")
            .padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(balance?.let(::botMoney) ?: if (busy) "در حال دریافت موجودی…" else "موجودی در دسترس نیست",
                style = if (balance != null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = gold, modifier = Modifier.testTag("profile-wallet-balance"))
            if (message != null) Text(message, style = MaterialTheme.typography.bodySmall, color = colors.error)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GanjSignatureButton("افزایش موجودی", onOpenWallet,
                modifier = Modifier.weight(1f).testTag("profile-wallet-topup"))
            GanjGlassButton("تراکنش‌ها", onOpenTransactions, filled = false, accent = gold, shapeRadius = 10.dp,
                modifier = Modifier.weight(1f).testTag("profile-wallet-transactions"))
        }
    }
}
