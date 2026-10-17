package com.ganj.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The inner screens share one semantic hierarchy but retain their distinct accent icons. */
@Composable
internal fun GanjInnerPageHeading(
    title: String,
    subtitle: String,
    symbol: GanjProfileSymbol,
    tone: GanjSignatureTone,
    modifier: Modifier = Modifier,
) {
    val accent = ganjSignatureTone(tone)
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        GanjProfileIconTile(symbol, when (tone) {
            GanjSignatureTone.Azure -> GanjProfileAccent.Sky
            GanjSignatureTone.Iris -> GanjProfileAccent.Iris
            GanjSignatureTone.Gold -> GanjProfileAccent.Gold
            else -> GanjProfileAccent.Emerald
        })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
        GanjSignatureMiniPill("GANJ", tone)
    }
}

@Composable
internal fun GanjInnerSectionTitle(title: String, subtitle: String? = null,
                                   tone: GanjSignatureTone = GanjSignatureTone.Emerald) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun GanjInnerFinanceHero(
    label: String,
    balance: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accent = ganjSignatureTone(GanjSignatureTone.Gold)
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(
                colors.primary.copy(alpha = .11f).compositeOver(colors.surface),
                colors.surface,
                accent.copy(alpha = .10f).compositeOver(colors.surface),
            )))
            .border(1.dp, accent.copy(alpha = .50f), shape)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant)
            Text(balance, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = colors.onSurface)
            Text(detail, style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant)
        }
        GanjProfileIconTile(GanjProfileSymbol.Wallet, GanjProfileAccent.Sky)
    }
}

@Composable
internal fun GanjInnerSummaryLine(label: String, value: String, tint: Color? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = tint ?: MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End)
    }
}

/** A balanced editorial section: emerald and gold surfaces, feature-only accent. */
@Composable
internal fun GanjInnerFeatureSection(
    title: String,
    subtitle: String,
    symbol: GanjProfileSymbol,
    accent: GanjProfileAccent = GanjProfileAccent.Emerald,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = profileAccent(accent),
        shapeRadius = 20.dp,
        modifier = modifier.fillMaxWidth(),
        padding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GanjProfileIconTile(symbol, accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

@Composable
internal fun GanjInnerMetadataPill(
    text: String,
    accent: GanjProfileAccent = GanjProfileAccent.Emerald,
) {
    val tint = profileAccent(accent)
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.clip(shape).background(tint.copy(alpha = .12f))
            .border(1.dp,tint.copy(alpha = .35f),shape)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
    }
}
