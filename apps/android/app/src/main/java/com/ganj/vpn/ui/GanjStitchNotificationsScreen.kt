package com.ganj.vpn.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ganj.vpn.core.controlapi.NotificationActionType
import com.ganj.vpn.core.controlapi.NotificationKind
import com.ganj.vpn.core.controlapi.NotificationPreferences
import com.ganj.vpn.core.controlapi.UserNotification

internal sealed interface NotificationUiState {
    data object Loading : NotificationUiState
    data object AuthRequired : NotificationUiState
    data class Ready(
        val items: List<UserNotification>,
        val unreadCount: Int,
        val hasMore: Boolean,
        val loadingMore: Boolean,
        val errorMessage: String?,
    ) : NotificationUiState
    data class Error(val message: String, val retryable: Boolean) : NotificationUiState
}

@Composable
internal fun StitchNotificationsScreen(
    state: NotificationUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onMarkRead: (UserNotification) -> Unit,
    onMarkAllRead: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenPreferences: () -> Unit,
    onAction: (UserNotification) -> Unit,
    modifier: Modifier = Modifier,
) {
    Page(modifier = modifier.fillMaxSize()) {
        AppHeader(
            title = "اعلان‌ها",
            subtitle = "رویدادهای حساب، سرویس و امنیت گنج",
            onRefresh = onRefresh,
        )
        AccountBackAction(onBack)

        when (state) {
            NotificationUiState.Loading -> LoadingCard("در حال دریافت اعلان‌ها")
            NotificationUiState.AuthRequired -> AuthCard(onRefresh)
            is NotificationUiState.Error -> ContentCard(MaterialTheme.colorScheme.error) {
                Text(state.message, color = MaterialTheme.colorScheme.onSurface)
                if (state.retryable) {
                    GanjLiquidAction(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text("تلاش دوباره", modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.Bold)
                    }
                }
            }
            is NotificationUiState.Ready -> {
                NotificationToolbar(
                    unreadCount = state.unreadCount,
                    onMarkAllRead = onMarkAllRead,
                    onOpenPreferences = onOpenPreferences,
                )
                if (state.items.isEmpty()) {
                    EmptyCard(
                        title = "اعلان جدیدی ندارید",
                        subtitle = "رویدادهای مهم حساب و سرویس از همین بخش نمایش داده می‌شوند.",
                        onAction = onRefresh,
                    )
                } else {
                    state.items.forEach { item ->
                        NotificationCard(
                            item = item,
                            onClick = {
                                if (!item.read) onMarkRead(item)
                                if (item.action != null) onAction(item)
                            },
                        )
                    }
                    state.errorMessage?.let { message ->
                        ContentCard(MaterialTheme.colorScheme.error) {
                            Text(message, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (state.hasMore) {
                        GanjLiquidAction(
                            onClick = onLoadMore,
                            enabled = !state.loadingMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (state.loadingMore) "در حال دریافت…" else "نمایش اعلان‌های بیشتر",
                                modifier = Modifier.align(Alignment.Center),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationToolbar(
    unreadCount: Int,
    onMarkAllRead: () -> Unit,
    onOpenPreferences: () -> Unit,
) {
    ContentCard(MaterialTheme.colorScheme.primary) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("مرکز اعلان‌ها", fontWeight = FontWeight.Bold)
                Text(
                    if (unreadCount > 0) "${unreadCount.toPersianDigits()} اعلان خوانده‌نشده" else "همه اعلان‌ها خوانده شده‌اند",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GanjStatusPill(
                text = if (unreadCount > 0) unreadCount.toPersianDigits() else "✓",
                tone = if (unreadCount > 0) GanjStatusTone.Warning else GanjStatusTone.Positive,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NotificationSecondaryAction(
                text = "تنظیمات اعلان",
                onClick = onOpenPreferences,
                modifier = Modifier.weight(1f),
            )
            NotificationSecondaryAction(
                text = "خواندن همه",
                onClick = onMarkAllRead,
                enabled = unreadCount > 0,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NotificationCard(item: UserNotification, onClick: () -> Unit) {
    val tone = notificationTone(item.kind)
    val action = item.action
    val accent = when (tone) {
        GanjStatusTone.Positive -> MaterialTheme.colorScheme.primary
        GanjStatusTone.Warning -> GanjWarning
        GanjStatusTone.Danger -> MaterialTheme.colorScheme.error
        GanjStatusTone.Premium -> MaterialTheme.colorScheme.secondary
        GanjStatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    GanjGlassSurface(
        role = if (item.read) GanjGlassRole.Regular else GanjGlassRole.Prominent,
        accent = accent,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        shapeRadius = 22.dp,
        padding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, fontWeight = if (item.read) FontWeight.SemiBold else FontWeight.Bold)
                Text(item.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GanjStatusPill(
                text = if (item.read) "خوانده‌شده" else "جدید",
                tone = if (item.read) GanjStatusTone.Neutral else tone,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                notificationKindLabel(item.kind),
                style = MaterialTheme.typography.labelSmall,
                color = accent,
            )
            Text(
                isolateTechnicalLtr(item.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (action != null) {
            Text(
                text = notificationActionLabel(action.type),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun StitchNotificationPreferencesScreen(
    preferences: NotificationPreferences?,
    loading: Boolean,
    saving: Boolean,
    errorMessage: String?,
    systemPermissionRequired: Boolean,
    systemPermissionGranted: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSave: (NotificationPreferences) -> Unit,
    onRequestSystemPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPermissionPrePrompt by remember { mutableStateOf(false) }
    Page(modifier = modifier.fillMaxSize()) {
        AppHeader("تنظیمات اعلان", "کنترل اعلان‌های ضروری و اختیاری", onRefresh)
        AccountBackAction(onBack)

        ContentCard(MaterialTheme.colorScheme.primary) {
            Text("اجازه اعلان Android", fontWeight = FontWeight.Bold)
            Text(
                when {
                    !systemPermissionRequired -> "این نسخه Android به مجوز جداگانه اعلان نیاز ندارد."
                    systemPermissionGranted -> "مجوز اعلان برای این برنامه فعال است."
                    else -> "برای نمایش Push Notification باید اجازه Android را تأیید کنید."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (systemPermissionRequired && !systemPermissionGranted) {
                GanjLiquidAction(
                    onClick = { showPermissionPrePrompt = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("فعال‌کردن اعلان‌ها", modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.Bold)
                }
                NotificationSecondaryAction(
                    text = "باز کردن تنظیمات Android",
                    onClick = onOpenSystemSettings,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        when {
            loading -> LoadingCard("در حال دریافت تنظیمات اعلان")
            preferences == null -> ContentCard(MaterialTheme.colorScheme.error) {
                Text(errorMessage ?: "تنظیمات اعلان دریافت نشد.")
                GanjLiquidAction(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text("تلاش دوباره", modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.Bold)
                }
            }
            else -> {
                NotificationPreferenceRows(preferences, saving, onSave)
                errorMessage?.let { ContentCard(MaterialTheme.colorScheme.error) { Text(it) } }
            }
        }
    }

    if (showPermissionPrePrompt) {
        GanjLiquidConfirmDialog(
            title = "نمایش اعلان‌های گنج؟",
            body = "Android در مرحله بعد مجوز اعلان را می‌پرسد. گنج برای رویدادهای حساب، سرویس و امنیت از این مجوز استفاده می‌کند؛ اعلان بازاریابی به‌صورت پیش‌فرض خاموش است.",
            confirmText = "ادامه",
            dismissText = "فعلاً نه",
            onConfirm = {
                showPermissionPrePrompt = false
                onRequestSystemPermission()
            },
            onDismiss = { showPermissionPrePrompt = false },
        )
    }
}

@Composable
private fun NotificationPreferenceRows(
    value: NotificationPreferences,
    saving: Boolean,
    onSave: (NotificationPreferences) -> Unit,
) {
    ContentCard(MaterialTheme.colorScheme.primary) {
        GanjSectionHeader("دسته‌بندی‌ها", "تغییرات روی حساب شما ذخیره می‌شوند.")
        NotificationPreferenceSwitch("انقضا و تمدید سرویس", value.subscriptionExpiry, saving) {
            onSave(value.copy(subscriptionExpiry = it))
        }
        NotificationPreferenceSwitch("خرید موفق", value.purchaseSuccess, saving) {
            onSave(value.copy(purchaseSuccess = it))
        }
        NotificationPreferenceSwitch("خطای پرداخت", value.paymentFailure, saving) {
            onSave(value.copy(paymentFailure = it))
        }
        NotificationPreferenceSwitch("نگهداری سرویس", value.maintenance, saving) {
            onSave(value.copy(maintenance = it))
        }
        NotificationPreferenceSwitch("به‌روزرسانی امنیتی", value.securityUpdate, saving) {
            onSave(value.copy(securityUpdate = it))
        }
        NotificationPreferenceSwitch("پاسخ پشتیبانی", value.supportReply, saving) {
            onSave(value.copy(supportReply = it))
        }
        NotificationPreferenceSwitch("پیشنهادها و تخفیف‌ها", value.marketing, saving) {
            onSave(value.copy(marketing = it))
        }
        Text(
            "اعلان‌های بازاریابی به‌صورت پیش‌فرض خاموش هستند و فقط با انتخاب شما فعال می‌شوند.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NotificationPreferenceSwitch(
    title: String,
    checked: Boolean,
    disabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    GanjSettingsSwitchRow(
        title = title,
        subtitle = null,
        checked = checked,
        enabled = !disabled,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
internal fun StitchNotificationsEntry(unreadCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    QuickAction(
        title = "اعلان‌ها${if (unreadCount > 0) " · ${unreadCount.toPersianDigits()} جدید" else ""}",
        subtitle = "رویدادهای حساب، سرویس و امنیت",
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun NotificationSecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GanjGlassSurface(
        role = GanjGlassRole.Clear,
        modifier = modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        padding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
        shapeRadius = 999.dp,
    ) {
        Text(
            text,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun notificationKindLabel(kind: NotificationKind): String = when (kind) {
    NotificationKind.SUBSCRIPTION_EXPIRY -> "تمدید سرویس"
    NotificationKind.PURCHASE_SUCCESS -> "خرید"
    NotificationKind.PAYMENT_FAILURE -> "پرداخت"
    NotificationKind.MAINTENANCE -> "نگهداری"
    NotificationKind.SECURITY_UPDATE -> "امنیت"
    NotificationKind.SUPPORT_REPLY -> "پشتیبانی"
    NotificationKind.MARKETING -> "پیشنهاد"
}

internal fun notificationActionLabel(type: NotificationActionType): String = when (type) {
    NotificationActionType.OPEN_STORE -> "رفتن به فروشگاه"
    NotificationActionType.OPEN_SUBSCRIPTION -> "مشاهده سرویس"
    NotificationActionType.OPEN_SUPPORT -> "مشاهده پشتیبانی"
    NotificationActionType.OPEN_WALLET -> "مشاهده کیف پول"
    NotificationActionType.OPEN_SETTINGS -> "باز کردن تنظیمات"
}

internal fun notificationTone(kind: NotificationKind): GanjStatusTone = when (kind) {
    NotificationKind.SUBSCRIPTION_EXPIRY -> GanjStatusTone.Warning
    NotificationKind.PURCHASE_SUCCESS -> GanjStatusTone.Positive
    NotificationKind.PAYMENT_FAILURE -> GanjStatusTone.Danger
    NotificationKind.MAINTENANCE -> GanjStatusTone.Neutral
    NotificationKind.SECURITY_UPDATE -> GanjStatusTone.Danger
    NotificationKind.SUPPORT_REPLY -> GanjStatusTone.Positive
    NotificationKind.MARKETING -> GanjStatusTone.Premium
}
