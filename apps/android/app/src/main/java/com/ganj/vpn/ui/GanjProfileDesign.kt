package com.ganj.vpn.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.ui.platform.LocalDensity
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
    GanjProfileAccent.Emerald -> ganjSignatureTone(GanjSignatureTone.Aqua)
    GanjProfileAccent.Gold -> ganjSignatureTone(GanjSignatureTone.Gold)
    GanjProfileAccent.Iris -> ganjSignatureTone(GanjSignatureTone.Iris)
    GanjProfileAccent.Sky -> ganjSignatureTone(GanjSignatureTone.Azure)
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
        .background(accent.copy(alpha = .20f))
        .border(1.dp, accent.copy(alpha = .53f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
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
    avatar: ImageBitmap? = null,
    onConnectTelegram: () -> Unit = onManage,
) {
    val colors = MaterialTheme.colorScheme
    val dark = referenceDark()
    val aqua = ganjSignatureTone(GanjSignatureTone.Aqua)
    val iris = ganjSignatureTone(GanjSignatureTone.Iris)
    val azure = ganjSignatureTone(GanjSignatureTone.Azure)
    val coral = ganjSignatureTone(GanjSignatureTone.Coral)
    val gold = ganjSignatureTone(GanjSignatureTone.Gold)
    val phase = rememberSignaturePhase()
    val shape = RoundedCornerShape(22.dp)

    Box(Modifier.fillMaxWidth().testTag("profile-identity")) {
        Column(Modifier.fillMaxWidth().padding(top = 53.dp)
            .clip(shape)
            .background(Brush.linearGradient(
                if (dark) listOf(Color(0xFF172E38), Color(0xFF261F3B), Color(0xFF17232A))
                else listOf(Color(0xFFE3F4FF), Color(0xFFF0E8FF), Color(0xFFE5F8F4))))
            .border(1.dp, iris.copy(alpha = .46f), shape),
            horizontalAlignment = Alignment.CenterHorizontally) {
            // Reserve physical space for the real Telegram avatar overlapping this surface.
            Spacer(Modifier.height(102.dp))
            Text(name, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = colors.onSurface, textAlign = TextAlign.Center)
            Text(username?.let(::isolateTechnicalLtr)
                    ?: if (linked) "حساب تلگرام متصل" else "برای ورود و مشاهده سرویس‌ها، تلگرام را متصل کنید",
                Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 5.dp),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(7.dp))
            if (linked) {
                if (premium == true) {
                    GanjSignatureMiniPill("GANJ VIP  ·  عضویت ویژه", GanjSignatureTone.Gold)
                } else {
                    GanjSignatureMiniPill("حساب متصل", GanjSignatureTone.Aqua)
                }
            }
            Canvas(Modifier.fillMaxWidth().height(38.dp).testTag("profile-water")) {
                val ripple = sin(phase.value * 6.283f) * size.height * .11f
                val curve = Path().apply {
                    moveTo(-5f, size.height * .55f)
                    cubicTo(size.width * .28f, size.height * .10f + ripple,
                        size.width * .72f, size.height * .95f - ripple,
                        size.width + 5f, size.height * .49f)
                }
                drawPath(curve, Brush.linearGradient(listOf(azure, iris, coral)),
                    style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round))
            }
            if (linked) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    GanjSignatureMiniPill("تلگرام متصل", GanjSignatureTone.Aqua)
                    GanjGlassButton("مدیریت حساب", onManage, filled = false,
                        accent = azure, shapeRadius = 12.dp)
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    GanjLiquidAction(onClick = onConnectTelegram, accent = Color(0xFF0B6DAF),
                        shapeRadius = 14.dp,
                        modifier = Modifier.fillMaxWidth().testTag("profile-connect-telegram")) {
                        Text("اتصال حساب به تلگرام", Modifier.align(Alignment.Center),
                            color = Color.White, style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold)
                    }
                    Text("اشتراک‌ها، کیف پول و دستگاه‌ها بعد از ورود همگام می‌شوند.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Box(Modifier.size(146.dp).align(Alignment.TopCenter)
            .testTag("profile-identity-orbit"), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val radius = size.minDimension / 2f - 2.dp.toPx()
                drawCircle(Brush.sweepGradient(listOf(aqua, iris, coral, aqua)),
                    radius = radius, style = Stroke(1.6.dp.toPx()))
                drawCircle(iris.copy(alpha = .45f), radius = radius - 7.dp.toPx(),
                    style = Stroke(1.dp.toPx()))
                drawArc(coral, -50f + sin(phase.value * 6.283f) * 18f, 64f, false,
                    Offset(2.dp.toPx(), 2.dp.toPx()),
                    Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()),
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(Brush.radialGradient(listOf(azure.copy(alpha = .16f), Color.Transparent)),
                    radius = radius)
            }
            if (linked && avatar != null) {
                Image(bitmap = avatar, contentDescription = "عکس پروفایل تلگرام",
                    modifier = Modifier.size(114.dp).clip(CircleShape)
                        .border(2.dp, aqua, CircleShape)
                        .clickable(role = Role.Button, onClick = onManage)
                        .testTag("profile-telegram-avatar"))
            } else {
                Box(Modifier.size(114.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF2369B0), Color(0xFF693F9D))))
                    .border(2.dp, aqua.copy(alpha = .8f), CircleShape)
                    .clickable(role = Role.Button,
                        onClick = if (linked) onManage else onConnectTelegram)
                    .semantics { contentDescription = if (linked) "مدیریت حساب" else "اتصال حساب به تلگرام" },
                    contentAlignment = Alignment.Center) {
                    GanjProfileIcon(GanjProfileSymbol.Account, Color.White, Modifier.size(48.dp))
                }
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
    val dark = referenceDark()
    val sky = ganjSignatureTone(GanjSignatureTone.Azure)
    val aqua = ganjSignatureTone(GanjSignatureTone.Aqua)
    val iris = ganjSignatureTone(GanjSignatureTone.Iris)
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().testTag("profile-wallet-live").clip(shape)
            .background(Brush.linearGradient(
                if (dark) listOf(Color(0xFF163955), Color(0xFF222647), Color(0xFF153A3A))
                else listOf(Color(0xFFD8F0FF), Color(0xFFE9E2FF), Color(0xFFD9F8EE)),
            ))
            .border(1.dp, sky.copy(alpha = .68f), shape).padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                .background(sky.copy(alpha = .16f))
                .border(1.dp, sky.copy(alpha = .46f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center) {
                GanjProfileIcon(GanjProfileSymbol.Wallet, sky, Modifier.size(27.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("کیف پول گنج", style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface, fontWeight = FontWeight.Bold)
                Text(when {
                    message != null && balance != null -> "آخرین موجودی دریافت‌شده"
                    busy -> "در حال دریافت موجودی"
                    else -> "اعتبار حساب"
                }, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            Box(Modifier.size(48.dp).clip(CircleShape)
                .background(iris.copy(alpha = .12f))
                .clickable(enabled = !busy, role = Role.Button, onClick = onRefresh)
                .semantics { contentDescription = "به‌روزرسانی موجودی" }
                .testTag("profile-wallet-refresh"), contentAlignment = Alignment.Center) {
                GanjProfileIcon(GanjProfileSymbol.Refresh,
                    if (busy) colors.onSurfaceVariant else iris, Modifier.size(21.dp))
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onOpenWallet)
            .testTag("profile-to-wallet"), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(balance?.let(::botMoney) ?: if (busy) "در حال دریافت موجودی…" else "موجودی در دسترس نیست",
                modifier = Modifier.fillMaxWidth().testTag("profile-wallet-balance"),
                style = if (balance != null && !largeText) MaterialTheme.typography.headlineLarge
                    else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = colors.onSurface,
                maxLines = 2, softWrap = true)
            if (message != null) Text(message, style = MaterialTheme.typography.bodySmall,
                color = colors.error)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            GanjLiquidAction(onClick = onOpenWallet, accent = Color(0xFF0B69AA),
                shapeRadius = 12.dp,
                modifier = Modifier.weight(1f).testTag("profile-wallet-topup")) {
                Text("افزایش موجودی", Modifier.align(Alignment.Center),
                    color = Color.White, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            GanjGlassButton("تراکنش‌ها", onOpenTransactions, filled = false,
                accent = aqua, shapeRadius = 12.dp,
                modifier = Modifier.weight(1f).testTag("profile-wallet-transactions"))
        }
    }
}
