package com.ganj.vpn.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ganj.vpn.R

internal enum class StitchProductGateKind {
    SecurityUpdate,
    Maintenance,
}

@Composable
internal fun StitchProductGateScreen(
    kind: StitchProductGateKind,
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = when (kind) {
        StitchProductGateKind.SecurityUpdate -> GanjGold
        StitchProductGateKind.Maintenance -> MaterialTheme.colorScheme.secondary
    }
    val status = when (kind) {
        StitchProductGateKind.SecurityUpdate -> "بروزرسانی لازم است"
        StitchProductGateKind.Maintenance -> "سرویس موقتاً متوقف است"
    }
    val glyph = when (kind) {
        StitchProductGateKind.SecurityUpdate -> "↑"
        StitchProductGateKind.Maintenance -> "…"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = responsiveHorizontalPadding(), vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = accent,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 28.dp,
            padding = PaddingValues(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = 0.38f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ganj_logo_official),
                    contentDescription = "نشان گنج VPN",
                    modifier = Modifier.size(58.dp),
                )
            }

            GanjStatusPill(
                text = status,
                tone = when (kind) {
                    StitchProductGateKind.SecurityUpdate -> GanjStatusTone.Premium
                    StitchProductGateKind.Maintenance -> GanjStatusTone.Warning
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = glyph,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }

            Text(
                text = stringResource(R.string.gate_policy_paused),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(2.dp))
            GanjLiquidAction(
                onClick = onRetry,
                accent = accent,
                shapeRadius = 999.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.gate_check_again),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (kind == StitchProductGateKind.SecurityUpdate) {
                        Color(0xFF211600)
                    } else {
                        MaterialTheme.colorScheme.onSecondary
                    },
                )
            }
        }
    }
}
