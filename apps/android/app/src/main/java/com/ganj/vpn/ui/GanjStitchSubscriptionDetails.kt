package com.ganj.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ganj.vpn.presentation.ServiceUiModel
import com.ganj.vpn.presentation.ServiceUiStatus
import com.ganj.vpn.presentation.UiTier

@Composable
internal fun StitchSubscriptionDetailsScreen(
    service: ServiceUiModel,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onOpenStore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusAccent = subscriptionStatusAccent(service.status)
    val premium = service.tier != UiTier.FREE

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = responsiveHorizontalPadding(), vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SubscriptionBackButton(onBack)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "جزئیات سرویس",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = service.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = if (premium) GanjGold else statusAccent,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 26.dp,
            padding = PaddingValues(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = service.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subscriptionTierLabel(service.tier),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SubscriptionStatusBadge(
                    text = subscriptionStatusLabel(service.status),
                    accent = statusAccent,
                )
            }

            SubscriptionStatusMessage(service.status)
        }

        SubscriptionSectionTitle("مصرف و اعتبار")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SubscriptionMetric(
                label = "باقی‌مانده",
                value = subscriptionBytes(service.remainingBytes, unlimitedWhenNull = true),
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.primary,
            )
            SubscriptionMetric(
                label = "مصرف‌شده",
                value = subscriptionBytes(service.trafficUsedBytes),
                modifier = Modifier.weight(1f),
                accent = if (premium) GanjGold else MaterialTheme.colorScheme.tertiary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SubscriptionMetric(
                label = "سقف ترافیک",
                value = subscriptionBytes(service.trafficLimitBytes, unlimitedWhenNull = true),
                modifier = Modifier.weight(1f),
            )
            SubscriptionMetric(
                label = "دستگاه مجاز",
                value = service.deviceLimit.toPersianDigits(),
                modifier = Modifier.weight(1f),
            )
        }

        GanjGlassSurface(
            role = GanjGlassRole.Regular,
            accent = statusAccent,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 22.dp,
            padding = PaddingValues(16.dp),
        ) {
            SubscriptionValueRow("انقضا", subscriptionExpiry(service.expiresAt))
            SubscriptionValueRow(
                "کشور",
                service.countryCode?.let(::isolateTechnicalLtr) ?: "انتخاب خودکار",
            )
            SubscriptionValueRow("شناسه سرویس", isolateTechnicalLtr(service.entitlementId))
        }

        SubscriptionSectionTitle("پروتکل‌های مجاز")
        GanjGlassSurface(
            role = GanjGlassRole.Dense,
            accent = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 22.dp,
            padding = PaddingValues(14.dp),
        ) {
            if (service.allowedProtocols.isEmpty()) {
                Text(
                    text = "پروتکل قابل نمایشی ثبت نشده است.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    service.allowedProtocols.sorted().take(4).forEach { protocol ->
                        SubscriptionProtocolChip(
                            text = isolateTechnicalLtr(protocol.uppercase()),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (service.isActive) {
            GanjLiquidAction(
                onClick = onConnect,
                accent = MaterialTheme.colorScheme.primary,
                shapeRadius = 999.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "اتصال با این سرویس",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        SubscriptionSecondaryAction(
            text = if (service.isActive) "خرید یا ارتقای سرویس" else "مشاهده پلن‌های قابل خرید",
            onClick = onOpenStore,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun StitchSubscriptionDetailsEntry(
    service: ServiceUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.56f))
            .border(
                1.dp,
                LocalGanjGlassPalette.current.borderSoft.copy(alpha = 0.62f),
                shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "جزئیات سرویس انتخاب‌شده",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = service.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SubscriptionStatusBadge(
            text = subscriptionStatusLabel(service.status),
            accent = subscriptionStatusAccent(service.status),
        )
    }
}

@Composable
private fun SubscriptionStatusMessage(status: ServiceUiStatus) {
    val text = when (status) {
        ServiceUiStatus.ACTIVE -> "این سرویس فعال است و در صورت وجود سرور واجد شرایط قابل اتصال است."
        ServiceUiStatus.PENDING -> "فعال‌سازی سرویس هنوز نهایی نشده است. وضعیت خرید را از فروشگاه پیگیری کنید."
        ServiceUiStatus.DISABLED -> "این سرویس در حال حاضر غیرفعال است و برای اتصال استفاده نمی‌شود."
        ServiceUiStatus.EXPIRED -> "اعتبار این سرویس تمام شده است. برای ادامه یک پلن معتبر انتخاب کنید."
        ServiceUiStatus.REVOKED -> "دسترسی این سرویس لغو شده است و امکان اتصال با آن وجود ندارد."
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SubscriptionMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.onSurface,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.58f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), shape)
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SubscriptionValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SubscriptionProtocolChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f),
                RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary,
            maxLines = 1,
        )
    }
}

@Composable
private fun SubscriptionSecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.64f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f), shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SubscriptionBackButton(onClick: () -> Unit) {
    SubscriptionSecondaryAction(
        text = "بازگشت",
        onClick = onClick,
    )
}

@Composable
private fun SubscriptionStatusBadge(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
}

@Composable
private fun SubscriptionSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun subscriptionStatusAccent(status: ServiceUiStatus): Color = when (status) {
    ServiceUiStatus.ACTIVE -> MaterialTheme.colorScheme.primary
    ServiceUiStatus.PENDING -> GanjGold
    ServiceUiStatus.DISABLED -> MaterialTheme.colorScheme.outline
    ServiceUiStatus.EXPIRED -> GanjWarning
    ServiceUiStatus.REVOKED -> MaterialTheme.colorScheme.error
}

private fun subscriptionStatusLabel(status: ServiceUiStatus): String = when (status) {
    ServiceUiStatus.ACTIVE -> "فعال"
    ServiceUiStatus.PENDING -> "در انتظار"
    ServiceUiStatus.DISABLED -> "غیرفعال"
    ServiceUiStatus.EXPIRED -> "منقضی"
    ServiceUiStatus.REVOKED -> "لغوشده"
}

private fun subscriptionTierLabel(tier: UiTier): String = when (tier) {
    UiTier.FREE -> "پلن رایگان"
    UiTier.PREMIUM -> "پلن پریمیوم"
    UiTier.VIP -> "پلن وی‌آی‌پی"
}

private fun subscriptionBytes(value: Long?, unlimitedWhenNull: Boolean = false): String = when {
    value == null && unlimitedWhenNull -> "نامحدود"
    value == null -> "—"
    value >= 1_000_000_000L -> "${(value / 1_000_000_000L).toPersianDigits()} گیگابایت"
    value >= 1_000_000L -> "${(value / 1_000_000L).toPersianDigits()} مگابایت"
    value >= 1_000L -> "${(value / 1_000L).toPersianDigits()} کیلوبایت"
    else -> "${value.toPersianDigits()} بایت"
}

private fun subscriptionExpiry(value: String?): String = when {
    value.isNullOrBlank() -> "بدون تاریخ مشخص"
    else -> isolateTechnicalLtr(value.take(10).toPersianDigits())
}
