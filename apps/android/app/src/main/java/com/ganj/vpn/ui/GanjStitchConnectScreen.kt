package com.ganj.vpn.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ganj.vpn.R
import com.ganj.vpn.presentation.ConnectionUiState
import com.ganj.vpn.presentation.GanjUiState
import com.ganj.vpn.presentation.ServiceUiModel
import com.ganj.vpn.presentation.UiTier

private val StitchGold = Color(0xFFD5A63A)
private val StitchGoldBright = Color(0xFFF0CD70)
private val StitchEmeraldGlow = Color(0xFF72FCB6)

@Composable
internal fun StitchConnectionScreen(
    state: GanjUiState,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenStore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connection = state.connection
    val service = state.selectedService
    val visualState = when (state.runtimeConnection.phase) {
        com.ganj.vpn.core.vpn.ConnectionPhase.RECONNECTING -> GanjConnectionVisualState.Reconnecting
        com.ganj.vpn.core.vpn.ConnectionPhase.DISCONNECTING -> GanjConnectionVisualState.Connecting
        else -> connection.toStitchVisualState(service)
    }
    val hasPremium = state.serviceItems.any {
        it.isActive && (it.tier == UiTier.PREMIUM || it.tier == UiTier.VIP)
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = responsiveHorizontalPadding(), vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StitchBrandHeader(premium = hasPremium, onOpenStore = onOpenStore)
        StitchProtectionBanner(state = visualState, onClick = onOpenServers)
        StitchConnectOrb(
            state = visualState,
            activeServiceAvailable = service?.isActive == true,
            onClick = {
                when {
                    connection is ConnectionUiState.Connected -> onDisconnect()
                    service?.isActive == true -> onConnect(service.entitlementId)
                    else -> onOpenServers()
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        StitchMetricsCard()
        StitchSelectedServerCard(service = service, onOpenServers = onOpenServers)
        StitchSmartConnectCard(
            enabled = service?.isActive == true,
            onClick = {
                service?.takeIf { it.isActive }?.let { onConnect(it.entitlementId) }
                    ?: onOpenServers()
            },
        )
        StitchPremiumCard(premium = hasPremium, onOpenStore = onOpenStore)

        if (connection is ConnectionUiState.Failed) {
            GanjGlassSurface(
                role = GanjGlassRole.Dense,
                accent = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 20.dp,
                padding = PaddingValues(16.dp),
            ) {
                Text(
                    text = "اتصال برقرار نشد",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = failureMessage(connection.failure),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                GanjLiquidAction(
                    onClick = { service?.entitlementId?.let(onConnect) ?: onRetry() },
                    accent = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "تلاش دوباره",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onError,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StitchBrandHeader(premium: Boolean, onOpenStore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    .border(1.dp, StitchGold.copy(alpha = 0.48f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ganj_logo_official),
                    contentDescription = "نشان گنج VPN",
                    modifier = Modifier.size(38.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "گنج VPN",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = StitchEmeraldGlow,
                    maxLines = 1,
                )
                Text(
                    text = "امن، سریع، نامحدود",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        GanjGlassSurface(
            role = GanjGlassRole.Clear,
            accent = StitchGold,
            shapeRadius = 999.dp,
            padding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
            modifier = Modifier.clickable(role = Role.Button, onClick = onOpenStore),
        ) {
            Text(
                text = if (premium) "پریمیوم" else "ارتقا",
                color = StitchGoldBright,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun StitchProtectionBanner(state: GanjConnectionVisualState, onClick: () -> Unit) {
    val protected = state == GanjConnectionVisualState.Connected
    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = if (protected) StitchEmeraldGlow else MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        shapeRadius = 22.dp,
        padding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (protected) StitchEmeraldGlow.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f),
                        )
                        .border(
                            1.dp,
                            if (protected) StitchEmeraldGlow.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (protected) "✓" else "×",
                        color = if (protected) StitchEmeraldGlow else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = connectionTitle(state),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (protected) "اتصال شما محافظت می‌شود" else "اتصال شما محافظت نشده است",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "‹",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StitchConnectOrb(
    state: GanjConnectionVisualState,
    activeServiceAvailable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effects = LocalGanjVisualEffectsPolicy.current
    val width = LocalConfiguration.current.screenWidthDp
    val orbSize = GanjResponsivePolicy.stitchConnectOrbSizeDp(width).dp
    val outerSize = orbSize + 46.dp
    val shouldPulse = !effects.reduceMotion && state in setOf(
        GanjConnectionVisualState.Connected,
        GanjConnectionVisualState.Connecting,
        GanjConnectionVisualState.Reconnecting,
    )
    val pulse = if (shouldPulse) {
        val transition = rememberInfiniteTransition(label = "stitchConnectPulse")
        val value by transition.animateFloat(
            initialValue = 0.985f,
            targetValue = 1.035f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (state == GanjConnectionVisualState.Connected) 1900 else 1100,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "stitchConnectPulseScale",
        )
        value
    } else {
        1f
    }
    val accent = when (state) {
        GanjConnectionVisualState.Connected -> StitchEmeraldGlow
        GanjConnectionVisualState.Connecting,
        GanjConnectionVisualState.Reconnecting,
        -> MaterialTheme.colorScheme.secondary
        GanjConnectionVisualState.Failed -> MaterialTheme.colorScheme.error
        GanjConnectionVisualState.Unavailable -> MaterialTheme.colorScheme.outline
        GanjConnectionVisualState.Disconnected -> StitchEmeraldGlow
    }

    Box(
        modifier = modifier
            .size(outerSize)
            .semantics { contentDescription = connectionTitle(state) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(outerSize - 8.dp)
                .scale(pulse)
                .clip(CircleShape)
                .border(1.dp, StitchGold.copy(alpha = 0.30f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(outerSize - 28.dp)
                .scale(pulse)
                .clip(CircleShape)
                .border(1.dp, accent.copy(alpha = 0.28f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(orbSize)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            accent.copy(alpha = if (activeServiceAvailable) 0.22f else 0.08f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.24f),
                            Color(0xFF07110D).copy(alpha = 0.94f),
                        ),
                    ),
                )
                .border(
                    3.dp,
                    accent.copy(alpha = if (activeServiceAvailable) 0.95f else 0.36f),
                    CircleShape,
                )
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(if (width <= 360) 72.dp else 82.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.08f))
                        .border(1.dp, StitchGold.copy(alpha = 0.20f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ganj_logo_official),
                        contentDescription = null,
                        modifier = Modifier.size(if (width <= 360) 58.dp else 66.dp),
                    )
                }
                Text(
                    text = connectionAction(state),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StitchMetricsCard() {
    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 22.dp,
        padding = PaddingValues(horizontal = 10.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StitchMetric(label = "پینگ", value = "—", unit = "ms", highlight = true)
            StitchMetricDivider()
            StitchMetric(label = "دانلود", value = "—", unit = "Mbps")
            StitchMetricDivider()
            StitchMetric(label = "آپلود", value = "—", unit = "Mbps")
        }
    }
}

@Composable
private fun StitchMetric(label: String, value: String, unit: String, highlight: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlight) StitchEmeraldGlow else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = persianTechnicalMetric(value, unit),
            style = MaterialTheme.typography.titleMedium,
            color = if (highlight) StitchEmeraldGlow else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        if (highlight) {
            Text(
                text = "در انتظار اندازه‌گیری",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StitchMetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(58.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    )
}

@Composable
private fun StitchSelectedServerCard(service: ServiceUiModel?, onOpenServers: () -> Unit) {
    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 22.dp,
        padding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = countryEmoji(service?.countryCode), style = MaterialTheme.typography.titleLarge)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "سرور انتخاب‌شده",
                        style = MaterialTheme.typography.labelSmall,
                        color = StitchEmeraldGlow,
                    )
                    Text(
                        text = service?.displayName ?: "هنوز سروری انتخاب نشده",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = service?.let { "${countryName(it.countryCode)} • ${tierPersian(it.tier)}" }
                            ?: "برای انتخاب سرور وارد فهرست سرورها شوید",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = persianTechnicalMetric("—", "ms"),
                    color = StitchEmeraldGlow,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                GanjGlassSurface(
                    role = GanjGlassRole.Clear,
                    accent = MaterialTheme.colorScheme.primary,
                    shapeRadius = 999.dp,
                    padding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenServers),
                ) {
                    Text(
                        text = "تغییر",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun StitchSmartConnectCard(enabled: Boolean, onClick: () -> Unit) {
    GanjGlassSurface(
        role = GanjGlassRole.Prominent,
        accent = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 22.dp,
        padding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f))
                        .border(1.dp, StitchEmeraldGlow.copy(alpha = 0.30f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    GanjNavigationIcon(
                        destination = GanjDestination.Connect,
                        tint = StitchEmeraldGlow,
                        modifier = Modifier.size(25.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "اتصال هوشمند",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "اتصال سریع با بهترین سرویس فعال حساب شما",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            GanjGlassSurface(
                role = GanjGlassRole.Clear,
                accent = StitchGold,
                shapeRadius = 999.dp,
                padding = PaddingValues(horizontal = 13.dp, vertical = 9.dp),
                modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
            ) {
                Text(
                    text = if (enabled) "اتصال سریع" else "انتخاب سرور",
                    color = StitchGoldBright,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StitchPremiumCard(premium: Boolean, onOpenStore: () -> Unit) {
    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = StitchGold,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 22.dp,
        padding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (premium) "پریمیوم فعال است" else "پریمیوم شوید؛ نامحدود بمانید",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StitchGoldBright,
                )
                Text(
                    text = if (premium) {
                        "به سرورهای پریمیوم و امکانات اشتراک خود دسترسی دارید."
                    } else {
                        "سرورهای بیشتر، اولویت بالاتر و تجربه سریع‌تر را فعال کنید."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(12.dp))
            GanjLiquidAction(
                onClick = onOpenStore,
                accent = StitchGold,
                shapeRadius = 999.dp,
                modifier = Modifier.width(104.dp),
            ) {
                Text(
                    text = if (premium) "مدیریت" else "ارتقا دهید",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF211600),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun ConnectionUiState.toStitchVisualState(service: ServiceUiModel?): GanjConnectionVisualState = when (this) {
    is ConnectionUiState.Connected -> GanjConnectionVisualState.Connected
    is ConnectionUiState.Requesting,
    is ConnectionUiState.ProfileReady,
    -> GanjConnectionVisualState.Connecting
    is ConnectionUiState.Failed -> GanjConnectionVisualState.Failed
    ConnectionUiState.AuthRequired -> GanjConnectionVisualState.Unavailable
    ConnectionUiState.Idle -> if (service?.isActive == true) {
        GanjConnectionVisualState.Disconnected
    } else {
        GanjConnectionVisualState.Unavailable
    }
}

private fun connectionTitle(state: GanjConnectionVisualState): String = when (state) {
    GanjConnectionVisualState.Disconnected -> "متصل نیستید"
    GanjConnectionVisualState.Connecting -> "در حال اتصال امن"
    GanjConnectionVisualState.Connected -> "اتصال امن برقرار است"
    GanjConnectionVisualState.Reconnecting -> "در حال اتصال مجدد"
    GanjConnectionVisualState.Failed -> "خطای اتصال"
    GanjConnectionVisualState.Unavailable -> "سرور انتخاب نشده"
}

private fun connectionAction(state: GanjConnectionVisualState): String = when (state) {
    GanjConnectionVisualState.Connected -> "قطع اتصال"
    GanjConnectionVisualState.Connecting -> "در حال اتصال…"
    GanjConnectionVisualState.Reconnecting -> "در حال بازیابی…"
    GanjConnectionVisualState.Failed -> "تلاش دوباره"
    GanjConnectionVisualState.Unavailable -> "انتخاب سرور"
    GanjConnectionVisualState.Disconnected -> "اتصال"
}

private fun tierPersian(tier: UiTier): String = when (tier) {
    UiTier.FREE -> "رایگان"
    UiTier.PREMIUM -> "پریمیوم"
    UiTier.VIP -> "وی‌آی‌پی"
}

private fun countryName(code: String?): String = when (code?.uppercase()) {
    "DE" -> "آلمان"
    "NL" -> "هلند"
    "US" -> "آمریکا"
    "GB", "UK" -> "بریتانیا"
    "TR" -> "ترکیه"
    "PL" -> "لهستان"
    "FR" -> "فرانسه"
    "CA" -> "کانادا"
    "SG" -> "سنگاپور"
    null -> "سرور جهانی"
    else -> isolateTechnicalLtr(code.uppercase())
}

private fun countryEmoji(code: String?): String = when (code?.uppercase()) {
    "DE" -> "🇩🇪"
    "NL" -> "🇳🇱"
    "US" -> "🇺🇸"
    "GB", "UK" -> "🇬🇧"
    "TR" -> "🇹🇷"
    "PL" -> "🇵🇱"
    "FR" -> "🇫🇷"
    "CA" -> "🇨🇦"
    "SG" -> "🇸🇬"
    else -> "🌐"
}

@Composable
internal fun responsiveHorizontalPadding(): Dp = GanjResponsivePolicy
    .stitchHorizontalPaddingDp(LocalConfiguration.current.screenWidthDp)
    .dp
