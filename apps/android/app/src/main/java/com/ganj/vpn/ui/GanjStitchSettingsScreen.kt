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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ganj.vpn.BuildConfig

@Composable
internal fun StitchSettingsScreen(
    preferences: GanjUserPreferences,
    onThemeChanged: (GanjThemePreference) -> Unit,
    onReduceMotionChanged: (Boolean) -> Unit,
    onReduceTransparencyChanged: (Boolean) -> Unit,
    onRestartOnboarding: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNotificationHub by remember { mutableStateOf(false) }
    if (showNotificationHub) {
        StitchNotificationHub(
            onBack = { showNotificationHub = false },
            modifier = modifier,
        )
        return
    }

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
            SettingsBackButton(onBack)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "تنظیمات",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "ظاهر، دسترس‌پذیری و اعلان‌های برنامه",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsSectionTitle("اعلان‌ها")
        GanjGlassSurface(
            role = GanjGlassRole.Regular,
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 24.dp,
            padding = PaddingValues(16.dp),
        ) {
            SettingsActionRow(
                title = "مرکز اعلان و تنظیمات",
                description = "اعلان‌های حساب، سرویس، امنیت و انتخاب دسته‌بندی‌ها",
                onClick = { showNotificationHub = true },
            )
        }

        SettingsSectionTitle("ظاهر")
        GanjGlassSurface(
            role = GanjGlassRole.Regular,
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 24.dp,
            padding = PaddingValues(16.dp),
        ) {
            Text(
                text = "حالت نمایش",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "می‌توانید ظاهر برنامه را با سیستم هماهنگ کنید یا روشن و تیره را دستی انتخاب کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeChoice(
                    label = "سیستم",
                    selected = preferences.theme == GanjThemePreference.SYSTEM,
                    onClick = { onThemeChanged(GanjThemePreference.SYSTEM) },
                    modifier = Modifier.weight(1f),
                )
                ThemeChoice(
                    label = "روشن",
                    selected = preferences.theme == GanjThemePreference.LIGHT,
                    onClick = { onThemeChanged(GanjThemePreference.LIGHT) },
                    modifier = Modifier.weight(1f),
                )
                ThemeChoice(
                    label = "تیره",
                    selected = preferences.theme == GanjThemePreference.DARK,
                    onClick = { onThemeChanged(GanjThemePreference.DARK) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SettingsSectionTitle("دسترس‌پذیری")
        GanjGlassSurface(
            role = GanjGlassRole.Regular,
            accent = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 24.dp,
            padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            SettingsToggleRow(
                title = "کاهش حرکت",
                description = "انیمیشن‌ها و حرکت‌های تزئینی برنامه کمتر می‌شوند.",
                checked = preferences.reduceMotion,
                onCheckedChange = onReduceMotionChanged,
            )
            SettingsDivider()
            SettingsToggleRow(
                title = "کاهش شفافیت",
                description = "سطوح شیشه‌ای خواناتر و نزدیک‌تر به حالت مات نمایش داده می‌شوند.",
                checked = preferences.reduceTransparency,
                onCheckedChange = onReduceTransparencyChanged,
            )
        }

        SettingsSectionTitle("درباره برنامه")
        GanjGlassSurface(
            role = GanjGlassRole.Dense,
            accent = GanjGold,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 24.dp,
            padding = PaddingValues(16.dp),
        ) {
            SettingsValueRow("نسخه", BuildConfig.VERSION_NAME)
            SettingsValueRow("کد ساخت", BuildConfig.VERSION_CODE.toString().toPersianDigits())
            SettingsDivider()
            SettingsActionRow(
                title = "نمایش دوباره راهنمای شروع",
                description = "راهنمای شروع رایگان و اولین اتصال دوباره نمایش داده می‌شود.",
                onClick = onRestartOnboarding,
            )
            Text(
                text = "گنج VPN",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun StitchSettingsEntry(
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
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "⚙",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "تنظیمات",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "ظاهر، اعلان‌ها و دسترس‌پذیری برنامه",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "‹",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsBackButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.58f))
            .border(
                1.dp,
                LocalGanjGlassPalette.current.borderSoft.copy(alpha = 0.62f),
                RoundedCornerShape(999.dp),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "بازگشت",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ThemeChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(accent.copy(alpha = if (selected) 0.16f else 0.06f))
            .border(1.dp, accent.copy(alpha = if (selected) 0.58f else 0.24f), shape)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics {
                stateDescription = if (checked) "روشن" else "خاموش"
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LiquidSwitch(checked)
    }
}

@Composable
private fun LiquidSwitch(checked: Boolean) {
    val track = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(track.copy(alpha = if (checked) 0.86f else 0.76f))
            .border(
                1.dp,
                if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                RoundedCornerShape(999.dp),
            )
            .padding(3.dp),
    ) {
        Box(
            modifier = Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface),
        )
    }
}

@Composable
private fun SettingsValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "‹",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
    )
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}
