package com.ganj.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ganj.vpn.presentation.CheckoutUiState
import com.ganj.vpn.presentation.ContentState
import com.ganj.vpn.presentation.GanjUiState
import com.ganj.vpn.presentation.PlanUiModel
import com.ganj.vpn.presentation.ServiceUiModel
import com.ganj.vpn.presentation.UiTier

private val StitchProductGold = Color(0xFFD5A63A)
private val StitchProductGoldBright = Color(0xFFF0CD70)
private val StitchProductEmerald = Color(0xFF72FCB6)

@Composable
internal fun StitchHomeScreen(
    state: GanjUiState,
    onOpenConnect: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeServices = state.serviceItems.filter { it.isActive }
    val premium = activeServices.any { it.tier != UiTier.FREE }
    val selected = state.selectedService

    StitchPage(modifier) {
        StitchSimpleHeader(
            title = "خانه",
            subtitle = "مدیریت سریع سرویس‌ها و وضعیت حساب",
            badge = if (premium) "پریمیوم" else "رایگان",
            goldBadge = premium,
        )

        GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 24.dp,
            padding = PaddingValues(18.dp),
        ) {
            Text(
                text = if (selected?.isActive == true) "آماده اتصال امن" else "یک سرویس فعال انتخاب کنید",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = selected?.displayName ?: "هنوز سرویس فعالی انتخاب نشده است",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StitchMiniAction(
                    text = "اتصال",
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = onOpenConnect,
                    modifier = Modifier.weight(1f),
                )
                StitchMiniAction(
                    text = "انتخاب سرور",
                    accent = MaterialTheme.colorScheme.secondary,
                    onClick = onOpenServers,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        StitchSectionLabel("دسترسی سریع")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StitchQuickCard(
                emoji = "🌐",
                title = "سرورها",
                subtitle = "${activeServices.size.toPersianDigits()} سرویس فعال",
                onClick = onOpenServers,
                modifier = Modifier.weight(1f),
            )
            StitchQuickCard(
                emoji = "🛍",
                title = "فروشگاه",
                subtitle = if (premium) "مدیریت اشتراک" else "ارتقای سرویس",
                onClick = onOpenStore,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StitchQuickCard(
                emoji = "👤",
                title = "پروفایل",
                subtitle = "حساب و دستگاه‌ها",
                onClick = onOpenProfile,
                modifier = Modifier.weight(1f),
            )
            StitchQuickCard(
                emoji = "🛡",
                title = "امنیت",
                subtitle = "دسترسی تأییدشده",
                onClick = onOpenProfile,
                modifier = Modifier.weight(1f),
            )
        }

        StitchSectionLabel("وضعیت سرویس")
        GanjGlassSurface(
            role = GanjGlassRole.Dense,
            accent = if (premium) StitchProductGold else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 22.dp,
            padding = PaddingValues(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (premium) "اشتراک پریمیوم فعال" else "حساب رایگان",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (premium) StitchProductGoldBright else StitchProductEmerald,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (premium) {
                            "به امکانات اشتراک و سرویس‌های فعال حساب خود دسترسی دارید."
                        } else {
                            "برای سرورهای بیشتر و اولویت بالاتر می‌توانید سرویس خود را ارتقا دهید."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                StitchPill(
                    text = if (premium) "مدیریت" else "ارتقا",
                    gold = true,
                    onClick = onOpenStore,
                )
            }
        }
    }
}

private enum class ServerFilter { ALL, FREE, PREMIUM }

@Composable
internal fun StitchServersScreen(
    state: GanjUiState,
    onSelectService: (String) -> Unit,
    onConnect: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ServerFilter.ALL) }

    val services = state.serviceItems.filter { service ->
        val matchesQuery = query.isBlank() ||
            service.displayName.contains(query, ignoreCase = true) ||
            service.countryCode.orEmpty().contains(query, ignoreCase = true) ||
            serverCountry(service.countryCode).contains(query, ignoreCase = true)
        val matchesFilter = when (filter) {
            ServerFilter.ALL -> true
            ServerFilter.FREE -> service.tier == UiTier.FREE
            ServerFilter.PREMIUM -> service.tier != UiTier.FREE
        }
        matchesQuery && matchesFilter
    }

    StitchPage(modifier) {
        StitchSimpleHeader(
            title = "سرورها",
            subtitle = "انتخاب بهترین سرویس فعال برای اتصال",
            badge = "${state.serviceItems.size.toPersianDigits()} سرور",
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text("جستجوی کشور یا سرور", color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.50f),
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StitchFilterChip("همه", filter == ServerFilter.ALL) { filter = ServerFilter.ALL }
            StitchFilterChip("رایگان", filter == ServerFilter.FREE) { filter = ServerFilter.FREE }
            StitchFilterChip("پریمیوم", filter == ServerFilter.PREMIUM, gold = true) {
                filter = ServerFilter.PREMIUM
            }
        }

        GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    enabled = state.selectedService?.isActive == true,
                    onClick = {
                        state.selectedService?.takeIf { it.isActive }?.let { onConnect(it.entitlementId) }
                    },
                ),
            shapeRadius = 22.dp,
            padding = PaddingValues(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "⚡ اتصال هوشمند",
                        style = MaterialTheme.typography.titleMedium,
                        color = StitchProductEmerald,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = state.selectedService?.displayName ?: "ابتدا یک سرویس را از لیست انتخاب کنید",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StitchPill(
                    text = "اتصال",
                    onClick = {
                        state.selectedService?.takeIf { it.isActive }?.let { onConnect(it.entitlementId) }
                    },
                )
            }
        }

        StitchSectionLabel("فهرست سرورها")
        when (val content = state.services) {
            ContentState.Loading -> LoadingCard("در حال دریافت سرورها")
            ContentState.Empty -> EmptyCard(
                "سروری پیدا نشد",
                "برای دریافت دوباره فهرست سرورها تلاش کنید.",
                onRetry,
            )
            ContentState.AuthRequired -> AuthCard(onRetry)
            is ContentState.Error -> ErrorCard(content.failure, onRetry)
            is ContentState.Ready -> {
                if (services.isEmpty()) {
                    GanjGlassSurface(
                        role = GanjGlassRole.Dense,
                        accent = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("نتیجه‌ای با این فیلتر پیدا نشد", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    services.forEach { service ->
                        StitchServerRow(
                            service = service,
                            selected = service.entitlementId == state.selectedEntitlementId,
                            onClick = {
                                onSelectService(service.entitlementId)
                                if (service.isActive) onConnect(service.entitlementId)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun StitchStoreScreen(
    state: GanjUiState,
    onSelect: (String) -> Unit,
    onPurchase: (PlanUiModel) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val premiumActive = state.serviceItems.any { it.isActive && it.tier != UiTier.FREE }

    StitchPage(modifier) {
        StitchSimpleHeader(
            title = "فروشگاه",
            subtitle = "انتخاب یا ارتقای اشتراک گنج VPN",
            badge = if (premiumActive) "فعال" else "پلن‌ها",
            goldBadge = premiumActive,
        )

        GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = StitchProductGold,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 24.dp,
            padding = PaddingValues(18.dp),
        ) {
            Text(
                text = if (premiumActive) "اشتراک شما فعال است" else "سرورهای بیشتر، تجربه سریع‌تر",
                style = MaterialTheme.typography.headlineSmall,
                color = StitchProductGoldBright,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (premiumActive) {
                    "از همین بخش می‌توانید سرویس دیگری بخرید یا اشتراک خود را مدیریت کنید."
                } else {
                    "پلن مناسب خود را انتخاب کنید و دسترسی پریمیوم را فعال کنید."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StitchSectionLabel("پلن‌های اشتراک")
        when (val catalog = state.catalog) {
            ContentState.Loading -> LoadingCard("در حال دریافت پلن‌ها")
            ContentState.Empty -> EmptyCard("پلنی موجود نیست", "بعداً دوباره تلاش کنید.", onRetry)
            ContentState.AuthRequired -> AuthCard(onRetry)
            is ContentState.Error -> ErrorCard(catalog.failure, onRetry)
            is ContentState.Ready -> catalog.items.forEach { plan ->
                StitchPlanCard(
                    plan = plan,
                    selected = plan.id == state.selectedPlanId,
                    onSelect = { onSelect(plan.id) },
                    onPurchase = { onPurchase(plan) },
                )
            }
        }

        when (state.checkout) {
            CheckoutUiState.Idle -> Unit
            else -> StitchCheckoutStatus(
                checkout = state.checkout,
                onRetry = { state.selectedPlan?.let(onPurchase) ?: onRetry() },
            )
        }
    }
}

@Composable
private fun StitchPage(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = responsiveHorizontalPadding(), vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        content()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StitchSimpleHeader(
    title: String,
    subtitle: String,
    badge: String,
    goldBadge: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        StitchStaticPill(text = badge, gold = goldBadge)
    }
}

@Composable
private fun StitchSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun StitchQuickCard(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = MaterialTheme.colorScheme.primary,
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        shapeRadius = 20.dp,
        padding = PaddingValues(14.dp),
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StitchMiniAction(
    text: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GanjLiquidAction(
        onClick = onClick,
        accent = accent,
        shapeRadius = 999.dp,
        modifier = modifier,
    ) {
        Text(
            text = text,
            modifier = Modifier.align(Alignment.Center),
            color = if (accent == StitchProductGold) Color(0xFF211600) else MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StitchPill(text: String, gold: Boolean = false, onClick: () -> Unit) {
    GanjGlassSurface(
        role = GanjGlassRole.Clear,
        accent = if (gold) StitchProductGold else MaterialTheme.colorScheme.primary,
        shapeRadius = 999.dp,
        padding = PaddingValues(horizontal = 13.dp, vertical = 8.dp),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = text,
            color = if (gold) StitchProductGoldBright else StitchProductEmerald,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StitchStaticPill(text: String, gold: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (gold) StitchProductGold.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            )
            .border(
                1.dp,
                if (gold) StitchProductGold.copy(alpha = 0.38f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = if (gold) StitchProductGoldBright else StitchProductEmerald,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StitchFilterChip(
    text: String,
    selected: Boolean,
    gold: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = if (gold) StitchProductGold else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) accent.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.52f))
            .border(1.dp, accent.copy(alpha = if (selected) 0.52f else 0.16f), RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = if (selected) {
                if (gold) StitchProductGoldBright else StitchProductEmerald
            } else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun StitchServerRow(
    service: ServiceUiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val premium = service.tier != UiTier.FREE
    GanjGlassSurface(
        role = if (selected) GanjGlassRole.Prominent else GanjGlassRole.Dense,
        accent = if (premium) StitchProductGold else MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        shapeRadius = 20.dp,
        padding = PaddingValues(14.dp),
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
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(serverFlag(service.countryCode), style = MaterialTheme.typography.titleMedium)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = service.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${serverCountry(service.countryCode)} • ${if (premium) "پریمیوم" else "رایگان"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = persianTechnicalMetric("—", "ms"),
                    color = StitchProductEmerald,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (selected) "انتخاب‌شده" else if (service.isActive) "آماده" else serviceStatusText(service.status),
                    color = if (selected) StitchProductEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun StitchPlanCard(
    plan: PlanUiModel,
    selected: Boolean,
    onSelect: () -> Unit,
    onPurchase: () -> Unit,
) {
    val premium = plan.tier != UiTier.FREE
    GanjGlassSurface(
        role = if (selected) GanjGlassRole.Prominent else GanjGlassRole.Dense,
        accent = if (premium) StitchProductGold else MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onSelect),
        shapeRadius = 22.dp,
        padding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (premium) StitchProductGoldBright else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (plan.durationDays == null) {
                        "بدون محدودیت زمانی مشخص"
                    } else {
                        "${plan.durationDays.toPersianDigits()} روز"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StitchStaticPill(
                text = when (plan.tier) {
                    UiTier.FREE -> "رایگان"
                    UiTier.PREMIUM -> "پریمیوم"
                    UiTier.VIP -> "وی‌آی‌پی"
                },
                gold = premium,
            )
        }
        Text(
            text = formatPrice(plan).toPersianDigits(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        plan.benefits.take(4).forEach { benefit ->
            Text(
                text = "• $benefit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StitchMiniAction(
            text = if (plan.amountMinor == 0L) "انتخاب پلن" else "خرید و فعال‌سازی",
            accent = if (premium) StitchProductGold else MaterialTheme.colorScheme.primary,
            onClick = onPurchase,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StitchCheckoutStatus(
    checkout: CheckoutUiState,
    onRetry: () -> Unit,
) {
    when (checkout) {
        CheckoutUiState.Idle -> Unit
        CheckoutUiState.AuthRequired -> AuthCard(onRetry)
        is CheckoutUiState.Pending -> GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = GanjWarning,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 22.dp,
            padding = PaddingValues(16.dp),
        ) {
            GanjStatusPill(text = "پرداخت در انتظار تأیید", tone = GanjStatusTone.Warning)
            Text(
                text = "در حال تکمیل خرید",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = checkoutActionText(checkout.action),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is CheckoutUiState.Verified -> GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 22.dp,
            padding = PaddingValues(16.dp),
        ) {
            GanjStatusPill(text = "پرداخت تأیید شد", tone = GanjStatusTone.Positive)
            Text(
                text = "در حال فعال‌سازی سرویس",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = StitchProductEmerald,
            )
            Text(
                text = "پرداخت تأیید شده و سرویس شما در حال همگام‌سازی با حساب است.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is CheckoutUiState.Active -> GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = StitchProductGold,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 22.dp,
            padding = PaddingValues(16.dp),
        ) {
            GanjStatusPill(text = "اشتراک فعال شد", tone = GanjStatusTone.Premium)
            Text(
                text = "سرویس شما آماده استفاده است",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = StitchProductGoldBright,
            )
            Text(
                text = "سرویس فعال‌شده در پروفایل و فهرست سرورها در دسترس است.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is CheckoutUiState.Failed -> ErrorCard(checkout.failure, onRetry)
    }
}

private fun serverCountry(code: String?): String = when (code?.uppercase()) {
    "DE" -> "آلمان"
    "NL" -> "هلند"
    "US" -> "آمریکا"
    "GB", "UK" -> "بریتانیا"
    "TR" -> "ترکیه"
    "PL" -> "لهستان"
    "FR" -> "فرانسه"
    "CA" -> "کانادا"
    "SG" -> "سنگاپور"
    else -> "جهانی"
}

private fun serverFlag(code: String?): String = when (code?.uppercase()) {
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
