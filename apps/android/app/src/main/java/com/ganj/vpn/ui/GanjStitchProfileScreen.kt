package com.ganj.vpn.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ganj.vpn.enterprise.BugReportInput
import com.ganj.vpn.enterprise.EnterpriseUiState
import com.ganj.vpn.presentation.ContentState
import com.ganj.vpn.presentation.GanjUiState
import com.ganj.vpn.presentation.ServiceUiModel
import com.ganj.vpn.presentation.UiTier

private val ProfileGold = Color(0xFFD5A63A)
private val ProfileGoldBright = Color(0xFFF0CD70)
private val ProfileEmerald = Color(0xFF72FCB6)

@Composable
internal fun StitchProfileScreen(
    state: GanjUiState,
    enterpriseState: EnterpriseUiState,
    onSelectService: (String) -> Unit,
    onConnect: () -> Unit,
    onBuy: () -> Unit,
    onRetry: () -> Unit,
    onEnterpriseRefresh: () -> Unit,
    onSubmitBug: (BugReportInput) -> Unit,
    onSubmitDiagnostics: (Boolean) -> Unit,
    onClearBug: () -> Unit,
    onClearDiagnostic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeServices = state.serviceItems.filter { it.isActive }
    val premium = activeServices.any { it.tier != UiTier.FREE }
    val selected = state.selectedService
    val horizontalPadding = when {
        LocalConfiguration.current.screenWidthDp <= 360 -> 18.dp
        LocalConfiguration.current.screenWidthDp >= 412 -> 22.dp
        else -> 20.dp
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "پروفایل",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "اشتراک‌ها، دستگاه و پشتیبانی",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ProfileBadge(text = if (premium) "Premium" else "رایگان", gold = premium)
        }

        GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = if (premium) ProfileGold else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 24.dp,
            padding = PaddingValues(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        .border(
                            1.dp,
                            if (premium) ProfileGold.copy(alpha = 0.52f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "گ",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (premium) ProfileGoldBright else ProfileEmerald,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "حساب گنج VPN",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = when {
                            premium -> "اشتراک پریمیوم فعال"
                            activeServices.isNotEmpty() -> "سرویس فعال رایگان"
                            else -> "بدون سرویس فعال"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProfileStat(
                    label = "سرویس فعال",
                    value = activeServices.size.toString(),
                    modifier = Modifier.weight(1f),
                )
                ProfileStat(
                    label = "دستگاه مجاز",
                    value = selected?.deviceLimit?.toString() ?: "—",
                    modifier = Modifier.weight(1f),
                    gold = premium,
                )
            }
        }

        ProfileSectionTitle("سرویس‌های من")
        when (val services = state.services) {
            ContentState.Loading -> LoadingCard("در حال دریافت سرویس‌ها")
            ContentState.Empty -> ProfileEmptyServices(onBuy)
            ContentState.AuthRequired -> AuthCard(onRetry)
            is ContentState.Error -> ErrorCard(services.failure, onRetry)
            is ContentState.Ready -> services.items.forEach { service ->
                ProfileServiceCard(
                    service = service,
                    selected = service.entitlementId == state.selectedEntitlementId,
                    onSelect = { onSelectService(service.entitlementId) },
                    onConnect = onConnect,
                )
            }
        }

        GanjLiquidAction(
            onClick = onBuy,
            accent = if (premium) ProfileGold else MaterialTheme.colorScheme.primary,
            shapeRadius = 999.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "خرید یا ارتقای سرویس",
                modifier = Modifier.align(Alignment.Center),
                color = if (premium) Color(0xFF211600) else MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        ProfileSectionTitle("امنیت و پشتیبانی")
        EnterpriseStatusCard(enterpriseState.availability, onEnterpriseRefresh)
        if (enterpriseState.features.bugReportsEnabled) {
            BugReportPanel(enterpriseState.bugReport, onSubmitBug, onClearBug)
        }
        if (enterpriseState.features.diagnosticsEnabled) {
            DiagnosticPanel(enterpriseState.diagnostic, onSubmitDiagnostics, onClearDiagnostic)
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ProfileServiceCard(
    service: ServiceUiModel,
    selected: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
) {
    val premium = service.tier != UiTier.FREE
    val accent = when {
        !service.isActive -> MaterialTheme.colorScheme.error
        premium -> ProfileGold
        else -> MaterialTheme.colorScheme.primary
    }

    GanjGlassSurface(
        role = if (selected) GanjGlassRole.Prominent else GanjGlassRole.Dense,
        accent = accent,
        modifier = Modifier.fillMaxWidth(),
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
                    text = service.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${profileTier(service.tier)} • ${profileServiceStatus(service)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                ProfileBadge(text = "انتخاب‌شده", gold = premium)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileInfoChip(
                label = "ترافیک",
                value = profileTraffic(service.remainingBytes),
                modifier = Modifier.weight(1f),
            )
            ProfileInfoChip(
                label = "دستگاه",
                value = service.deviceLimit.toString(),
                modifier = Modifier.weight(1f),
                gold = premium,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProfileSecondaryAction(
                text = if (selected) "انتخاب شده" else "انتخاب سرویس",
                enabled = service.isActive,
                onClick = onSelect,
                modifier = Modifier.weight(1f),
            )
            GanjLiquidAction(
                onClick = onConnect,
                enabled = service.isActive && selected,
                accent = MaterialTheme.colorScheme.primary,
                shapeRadius = 999.dp,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "اتصال",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ProfileStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    gold: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.46f))
            .border(
                1.dp,
                if (gold) ProfileGold.copy(alpha = 0.24f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                RoundedCornerShape(18.dp),
            )
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (gold) ProfileGoldBright else ProfileEmerald,
            )
        }
    }
}

@Composable
private fun ProfileInfoChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    gold: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f))
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (gold) ProfileGoldBright else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProfileSecondaryAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 0.68f else 0.30f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ProfileBadge(text: String, gold: Boolean = false) {
    val accent = if (gold) ProfileGold else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = if (gold) ProfileGoldBright else ProfileEmerald,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ProfileSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun ProfileEmptyServices(onBuy: () -> Unit) {
    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = MaterialTheme.colorScheme.outline,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 22.dp,
        padding = PaddingValues(16.dp),
    ) {
        Text(
            text = "سرویس فعالی ندارید",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "از فروشگاه یک سرویس انتخاب کنید تا در این بخش نمایش داده شود.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProfileSecondaryAction(
            text = "رفتن به فروشگاه",
            enabled = true,
            onClick = onBuy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun profileTier(tier: UiTier): String = when (tier) {
    UiTier.FREE -> "رایگان"
    UiTier.PREMIUM -> "پریمیوم"
    UiTier.VIP -> "VIP"
}

private fun profileServiceStatus(service: ServiceUiModel): String = when {
    service.isActive -> "فعال"
    else -> when (service.status.name) {
        "PENDING" -> "در انتظار"
        "DISABLED" -> "غیرفعال"
        "EXPIRED" -> "منقضی"
        "REVOKED" -> "لغوشده"
        else -> "نامشخص"
    }
}

private fun profileTraffic(bytes: Long?): String = when {
    bytes == null -> "نامحدود"
    bytes >= 1_000_000_000L -> "${bytes / 1_000_000_000L} GB"
    else -> "${bytes / 1_000_000L} MB"
}
