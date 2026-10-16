package com.ganj.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ganj.vpn.presentation.ServiceUiModel

/**
 * Live service numbers remain in the ViewModel/API model. The surface only owns
 * layout, hierarchy, colour and routing. Empty states show no invented amounts.
 */
@Composable
internal fun GanjSignatureSubscriptionCard(
    service: ServiceUiModel?,
    onOpen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val cyan = ganjSignatureTone(GanjSignatureTone.Aqua)
    val coral = ganjSignatureTone(GanjSignatureTone.Coral)
    val iris = ganjSignatureTone(GanjSignatureTone.Iris)
    val dark = referenceDark()
    val shape = RoundedCornerShape(19.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(
                Brush.linearGradient(
                    if (dark) listOf(Color(0xFF183D3D), Color(0xFF202B41), Color(0xFF31263C))
                    else listOf(Color(0xFFDDF9F4), Color(0xFFE5EEFF), Color(0xFFF4E8FF)),
                ),
            )
            .border(1.dp, cyan.copy(alpha = .62f), shape)
            .padding(17.dp).testTag("profile-subscription-signature"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            GanjSignatureEyebrow("YOUR SUBSCRIPTION", GanjSignatureTone.Aqua)
            GanjSignatureMiniPill(
                if (service == null) "بدون انتخاب" else profileServiceStatus(service),
                if (service?.isActive == true) GanjSignatureTone.Aqua else GanjSignatureTone.Coral,
            )
        }
        Text(service?.let { subscriptionUsername(it) } ?: "اشتراکی انتخاب نشده است",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = colors.onSurface)
        if (service != null) {
            if (service.trafficUsageAvailable && service.usageFraction != null) {
                Text(profileTraffic(service.remainingBytes) + " باقی‌مانده",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text(profileTraffic(service.trafficUsedBytes) + " از " +
                        profileTraffic(service.trafficLimitBytes) + " مصرف شده",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                GanjSlimProgress(service.usageFraction!!)
            } else {
                Text("میزان مصرف این اشتراک در دسترس نیست",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                GanjProfileIcon(GanjProfileSymbol.Subscription, iris, Modifier.size(23.dp))
                Text(profileExpiry(service.expiresAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant)
            }
        } else {
            Text("برای شروع، یکی از اشتراک‌های حساب خود را انتخاب کنید.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant)
        }
        GanjGlassButton(if (service == null) "انتخاب اشتراک" else "جزئیات اشتراک",
            onOpen, filled = false, accent = cyan, shapeRadius = 12.dp,
            modifier = Modifier.fillMaxWidth())
    }
}
