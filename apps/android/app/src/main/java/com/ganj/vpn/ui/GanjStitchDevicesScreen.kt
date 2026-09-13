package com.ganj.vpn.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ganj.vpn.core.controlapi.TrustedDevice
import com.ganj.vpn.core.controlapi.TrustedDeviceStatus

internal sealed interface DevicesUiState {
    data object Loading : DevicesUiState
    data object AuthRequired : DevicesUiState
    data class Ready(val devices: List<TrustedDevice>) : DevicesUiState
    data class Error(val message: String, val retryable: Boolean) : DevicesUiState
}

internal fun safeDeviceLabel(device: TrustedDevice): String {
    device.name?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    if (device.current) return "این دستگاه"
    val suffix = device.id.takeLast(8)
    return "دستگاه Android · ${isolateTechnicalLtr(suffix)}"
}

@Composable
internal fun StitchDevicesEntry(
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
            .border(1.dp, LocalGanjGlassPalette.current.borderSoft.copy(alpha = 0.62f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("▣", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("دستگاه‌ها", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "مدیریت دستگاه‌های متصل به حساب",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("‹", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun StitchDevicesScreen(
    state: DevicesUiState,
    revokingDeviceId: String?,
    revokeError: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRevoke: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmationDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    val devices = (state as? DevicesUiState.Ready)?.devices.orEmpty()
    val selected = selectedDeviceId?.let { id -> devices.firstOrNull { it.id == id } }
    val confirmation = confirmationDeviceId?.let { id -> devices.firstOrNull { it.id == id } }

    BackHandler(enabled = selected != null) { selectedDeviceId = null }

    if (selected != null) {
        StitchDeviceDetailScreen(
            device = selected,
            revoking = revokingDeviceId == selected.id,
            revokeError = revokeError,
            onBack = { selectedDeviceId = null },
            onRequestRevoke = { confirmationDeviceId = selected.id },
            modifier = modifier,
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = responsiveHorizontalPadding(),
                end = responsiveHorizontalPadding(),
                top = 18.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DevicesHeader(
                    title = "دستگاه‌های من",
                    subtitle = "دستگاه‌های ثبت‌شده روی حساب گنج",
                    onBack = onBack,
                )
            }
            when (state) {
                DevicesUiState.Loading -> item { DevicesLoadingCard() }
                DevicesUiState.AuthRequired -> item {
                    DevicesMessageCard(
                        title = "ورود لازم است",
                        body = "برای مدیریت دستگاه‌ها ابتدا حساب تلگرام را به گنج متصل کنید.",
                        action = null,
                        onAction = null,
                    )
                }
                is DevicesUiState.Error -> item {
                    DevicesMessageCard(
                        title = "دستگاه‌ها دریافت نشدند",
                        body = state.message,
                        action = if (state.retryable) "تلاش دوباره" else null,
                        onAction = if (state.retryable) onRefresh else null,
                    )
                }
                is DevicesUiState.Ready -> {
                    item {
                        GanjGlassSurface(
                            role = GanjGlassRole.Dense,
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth(),
                            shapeRadius = 22.dp,
                            padding = PaddingValues(15.dp),
                        ) {
                            val active = state.devices.count { it.status == TrustedDeviceStatus.ACTIVE }
                            Text(
                                "${active.toPersianDigits()} دستگاه فعال",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "دستگاه فعلی از داخل همین نشست قابل حذف نیست تا حساب ناخواسته از دسترس خارج نشود.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (state.devices.isEmpty()) {
                        item {
                            DevicesMessageCard(
                                title = "دستگاهی ثبت نشده",
                                body = "هیچ دستگاهی برای این حساب برنگشت.",
                                action = "به‌روزرسانی",
                                onAction = onRefresh,
                            )
                        }
                    } else {
                        items(state.devices, key = { it.id }) { device ->
                            DeviceRow(
                                device = device,
                                revoking = revokingDeviceId == device.id,
                                onClick = { selectedDeviceId = device.id },
                                onRequestRevoke = { confirmationDeviceId = device.id },
                            )
                        }
                    }
                    if (revokeError != null) {
                        item {
                            DevicesMessageCard(
                                title = "حذف دستگاه انجام نشد",
                                body = revokeError,
                                action = "به‌روزرسانی فهرست",
                                onAction = onRefresh,
                            )
                        }
                    }
                    item { DevicesTextAction("به‌روزرسانی دستگاه‌ها", onRefresh) }
                }
            }
        }
    }

    if (confirmation != null) {
        GanjLiquidConfirmDialog(
            title = "حذف ${safeDeviceLabel(confirmation)}؟",
            body = "نشست‌های این دستگاه لغو می‌شوند و اتصال آن به سرویس‌های حساب نیز حذف خواهد شد.",
            confirmText = "حذف دستگاه",
            dismissText = "انصراف",
            destructive = true,
            onConfirm = {
                confirmationDeviceId = null
                onRevoke(confirmation.id)
            },
            onDismiss = { confirmationDeviceId = null },
        )
    }
}

@Composable
private fun StitchDeviceDetailScreen(
    device: TrustedDevice,
    revoking: Boolean,
    revokeError: String?,
    onBack: () -> Unit,
    onRequestRevoke: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = responsiveHorizontalPadding(),
            end = responsiveHorizontalPadding(),
            top = 18.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DevicesHeader(
                title = "جزئیات دستگاه",
                subtitle = safeDeviceLabel(device),
                onBack = onBack,
            )
        }
        item {
            GanjGlassSurface(
                role = GanjGlassRole.Regular,
                accent = if (device.status == TrustedDeviceStatus.ACTIVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 26.dp,
                padding = PaddingValues(18.dp),
            ) {
                DeviceDetailValue("وضعیت", deviceStatusLabel(device))
                DeviceDetailValue("پلتفرم", device.platform)
                device.appVersion?.let { DeviceDetailValue("نسخه برنامه", it) }
                DeviceDetailValue("شناسه دستگاه", isolateTechnicalLtr(device.id))
                DeviceDetailValue(
                    "آخرین فعالیت",
                    device.lastSeenAt?.let(::formatDeviceDate) ?: "اطلاعاتی ثبت نشده",
                )
            }
        }
        if (device.current) {
            item {
                DevicesMessageCard(
                    title = "این دستگاه فعلی شماست",
                    body = "برای جلوگیری از قطع نشست جاری، حذف دستگاه فعلی از داخل همان دستگاه مجاز نیست.",
                    action = null,
                    onAction = null,
                )
            }
        } else if (device.status == TrustedDeviceStatus.ACTIVE) {
            item {
                if (revoking) DevicesLoadingCard("در حال لغو دسترسی دستگاه…")
                else DevicesDestructiveAction("حذف دسترسی این دستگاه", onRequestRevoke)
            }
        }
        if (revokeError != null) {
            item {
                DevicesMessageCard(
                    title = "عملیات ناموفق بود",
                    body = revokeError,
                    action = null,
                    onAction = null,
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: TrustedDevice,
    revoking: Boolean,
    onClick: () -> Unit,
    onRequestRevoke: () -> Unit,
) {
    val active = device.status == TrustedDeviceStatus.ACTIVE
    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        shapeRadius = 20.dp,
        padding = PaddingValues(15.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (device.current) "●" else "▣", color = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(safeDeviceLabel(device), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    deviceStatusLabel(device),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    device.lastSeenAt?.let(::formatDeviceDate) ?: "آخرین فعالیت ثبت نشده",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!device.current && active) {
            if (revoking) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("در حال حذف دسترسی…", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                DevicesDestructiveAction("حذف دسترسی", onRequestRevoke)
            }
        }
    }
}

private fun deviceStatusLabel(device: TrustedDevice): String = when {
    device.current && device.status == TrustedDeviceStatus.ACTIVE -> "فعال · دستگاه فعلی"
    device.status == TrustedDeviceStatus.ACTIVE -> "فعال"
    else -> "لغو شده"
}

private fun formatDeviceDate(value: String): String =
    value.replace('T', ' ').substringBefore('.').removeSuffix("Z").toPersianDigits()

@Composable
private fun DeviceDetailValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DevicesHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.58f))
                .border(1.dp, LocalGanjGlassPalette.current.borderSoft.copy(alpha = 0.62f), RoundedCornerShape(999.dp))
                .clickable(role = Role.Button, onClick = onBack)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("بازگشت", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DevicesLoadingCard(message: String = "در حال دریافت دستگاه‌های واقعی حساب…") {
    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 22.dp,
        padding = PaddingValues(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DevicesMessageCard(
    title: String,
    body: String,
    action: String?,
    onAction: (() -> Unit)?,
) {
    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 22.dp,
        padding = PaddingValues(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) DevicesTextAction(action, onAction)
    }
}

@Composable
private fun DevicesTextAction(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DevicesDestructiveAction(label: String, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.error
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontWeight = FontWeight.Bold)
    }
}
