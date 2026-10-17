package com.ganj.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Brand-adjacent colors: conspicuous but meaningful. Used only in the profile,
 * wallet, subscription and VIP surfaces, never the existing connection/nav UI.
 */
internal enum class GanjSignatureTone { Aqua, Azure, Iris, Coral, Gold, Emerald }

@Composable
internal fun ganjSignatureTone(tone: GanjSignatureTone): Color {
    val dark = referenceDark()
    return when (tone) {
        GanjSignatureTone.Aqua -> if (dark) Color(0xFF4FEAD8) else Color(0xFF007A72)
        GanjSignatureTone.Azure -> if (dark) Color(0xFF69BFFF) else Color(0xFF1261BF)
        GanjSignatureTone.Iris -> if (dark) Color(0xFFC2A0FF) else Color(0xFF6743C1)
        GanjSignatureTone.Coral -> if (dark) Color(0xFFFFA388) else Color(0xFFA83340)
        GanjSignatureTone.Gold -> if (dark) Color(0xFFF7CF75) else Color(0xFF845A0D)
        GanjSignatureTone.Emerald -> if (dark) Color(0xFF55ECA4) else Color(0xFF06795B)
    }
}

@Composable
internal fun ganjSignatureSurface(tone: GanjSignatureTone): Brush {
    val gold = ganjSignatureTone(GanjSignatureTone.Gold)
    val emerald = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.surface
    return Brush.linearGradient(
        listOf(
            gold.copy(alpha = .075f).compositeOver(background),
            background,
            emerald.copy(alpha = .055f).compositeOver(background),
        ),
    )
}

@Composable
internal fun GanjSignatureEyebrow(text: String, tone: GanjSignatureTone, modifier: Modifier = Modifier) {
    val tint = ganjSignatureTone(tone)
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = tint,
    )
}

@Composable
internal fun GanjSignatureMiniPill(text: String, tone: GanjSignatureTone, modifier: Modifier = Modifier) {
    val tint = ganjSignatureTone(tone)
    val shape = RoundedCornerShape(100.dp)
    Text(
        text,
        modifier = modifier
            .background(tint.copy(alpha = if (referenceDark()) .17f else .11f), shape)
            .border(1.dp, tint.copy(alpha = .55f), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = tint,
    )
}
