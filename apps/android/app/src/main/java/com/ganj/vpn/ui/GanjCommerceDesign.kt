package com.ganj.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A single high-contrast financial surface shared by cart, top-up, invoice
 * and volume confirmation. Explicit text/outline tokens avoid accidentally
 * inheriting unreadable text colors from the surrounding dark application.
 * Connect's approved visual identity is deliberately unchanged.
 */
@Immutable
internal data class GanjCommercePalette(
    val ink: Color,
    val mutedInk: Color,
    val goldInk: Color,
    val outline: Color,
    val start: Color,
    val end: Color,
)

internal val GanjFinancePalette = GanjCommercePalette(
    ink = Color(0xFF253A34),
    mutedInk = Color(0xFF52655D),
    goldInk = Color(0xFF795614),
    outline = Color(0xFFD4C28F),
    start = Color(0xFFFFFBF0),
    end = Color(0xFFF0F7F3),
)

@Composable
internal fun GanjCommerceFocusSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = GanjFinancePalette
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(palette.start, palette.end)))
            .border(1.dp, palette.outline, shape)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
