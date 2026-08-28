package com.ganj.vpn.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class GanjGlassRole {
    Clear,
    Regular,
    Dense,
    Prominent,
    OpaqueFallback,
}

internal object GanjLiquidGlassPolicy {
    const val RequiredMaturityLevel = 3
    const val MinimumTouchTargetDp = 48
    const val NestedGlassByDefault = false
    const val ContentCardsGlassByDefault = false
    const val ReduceTransparencyFallbackRequired = true
    const val PerformanceTieringRequired = true
}

@Composable
internal fun GanjLiquidCanvas(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.primary.copy(alpha = 0.14f),
                        colors.tertiary.copy(alpha = 0.035f),
                        colors.background,
                    ),
                    radius = 1150f,
                ),
            ),
        content = content,
    )
}

@Composable
internal fun GanjGlassSurface(
    role: GanjGlassRole,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    shapeRadius: Dp = 24.dp,
    padding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalGanjGlassPalette.current
    val shape = RoundedCornerShape(shapeRadius)
    val base = when (role) {
        GanjGlassRole.Clear -> MaterialTheme.colorScheme.surface.copy(alpha = 0.38f)
        GanjGlassRole.Regular -> MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
        GanjGlassRole.Dense -> MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
        GanjGlassRole.Prominent -> MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
        GanjGlassRole.OpaqueFallback -> glass.opaqueFallback.copy(alpha = 0.98f)
    }
    val accentStrength = when (role) {
        GanjGlassRole.Clear -> 0.035f
        GanjGlassRole.Regular -> 0.055f
        GanjGlassRole.Dense -> 0.065f
        GanjGlassRole.Prominent -> 0.11f
        GanjGlassRole.OpaqueFallback -> 0.02f
    }
    val border = when (role) {
        GanjGlassRole.Prominent -> glass.borderStrong
        else -> glass.borderSoft
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        glass.highlight.copy(alpha = if (role == GanjGlassRole.Clear) 0.10f else 0.07f),
                        base,
                        accent.copy(alpha = accentStrength),
                    ),
                ),
            )
            .border(1.dp, border.copy(alpha = 0.72f), shape)
            .padding(padding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@Composable
internal fun GanjLiquidAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary,
    shapeRadius: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
        label = "ganjLiquidPressScale",
    )
    val shape = RoundedCornerShape(shapeRadius)
    val glass = LocalGanjGlassPalette.current
    val effectiveAccent = if (enabled) accent else MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .heightIn(min = GanjLiquidGlassPolicy.MinimumTouchTargetDp.dp)
            .scale(scale)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        effectiveAccent.copy(alpha = if (enabled) 0.94f else 0.34f),
                        effectiveAccent.copy(alpha = if (enabled) 0.72f else 0.24f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = glass.highlight.copy(alpha = if (enabled) 0.22f else 0.08f),
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        content = content,
    )
}
