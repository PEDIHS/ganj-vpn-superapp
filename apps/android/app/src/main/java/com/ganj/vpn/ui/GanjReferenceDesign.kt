package com.ganj.vpn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Product-approved 2026-10-15 reference. Geometry and colors are shared by every route. */
internal object GanjReference {
    val Canvas = Color(0xFF111718)
    val Surface = Color(0xFF1A2324)
    val Raised = Color(0xFF1C3026)
    val Border = Color(0xFF334B3D)
    val Emerald = Color(0xFF39E5A8)
    val Gold = Color(0xFFE0BE79)
    val PowerOffTop = Color(0xFF126C50)
    val PowerOffBottom = Color(0xFF073F30)
    val PowerOnTop = Color(0xFF16B67C)
    val PowerOnBottom = Color(0xFF05734F)
    val PowerErrorTop = Color(0xFFAA3B46)
    val PowerErrorBottom = Color(0xFF6A222F)
    val NavTop = Color(0xFF146C50)
    val NavBottom = Color(0xFF075039)
    val GoldTop = Color(0xFF665027)
    val GoldBottom = Color(0xFF43391F)
    val Ink = Color(0xFFEEF3F1)
    val Muted = Color(0xFFA2B3AE)
}

@Composable
internal fun referenceDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.3f

/** One compact menu grammar, rather than a new decorative card for each account route. */
@Composable
internal fun GanjReferenceMenuRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    icon: GanjDestination = GanjDestination.Account,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape)
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GanjNavigationIcon(icon, accent, Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.size(16.dp)) {
            val p = Path().apply { moveTo(size.width * .6f, size.height * .2f)
                lineTo(size.width * .3f, size.height * .5f); lineTo(size.width * .6f, size.height * .8f) }
            drawPath(p, accent, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        }
    }
}
