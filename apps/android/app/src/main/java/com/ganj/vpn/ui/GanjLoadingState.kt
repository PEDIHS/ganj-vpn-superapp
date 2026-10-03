package com.ganj.vpn.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ganj.vpn.R

/** Local to the loading section: leaves navigation interactive and reports no invented progress. */
@Composable
internal fun GanjLoadingState(title: String) {
    val effects = LocalGanjVisualEffectsPolicy.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val animate = lifecycle.isAtLeast(Lifecycle.State.STARTED) &&
        GanjDestinationMotionPolicy.shouldAnimate(effects.tier, effects.reduceMotion)
    val phase: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "loading").animateFloat(0f, 1f,
            infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "loadingSweep")
    } else remember { mutableFloatStateOf(0.2f) }
    val colors = MaterialTheme.colorScheme
    val wait = stringResource(R.string.common_wait)
    GanjGlassSurface(role = GanjGlassRole.Dense, accent = colors.outline,
        modifier = Modifier.fillMaxWidth().testTag("loading-state").clearAndSetSemantics {
            contentDescription = "$title، $wait"
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            liveRegion = LiveRegionMode.Polite
        }, shapeRadius = 22.dp, padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(Modifier.size(30.dp)) {
                val stroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(colors.outline.copy(alpha = 0.18f), style = stroke)
                drawArc(colors.primary, phase.value * 360f, 105f, false,
                    topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                    size = Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()), style = stroke)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Text(wait, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(48.dp)) {
            val radius = CornerRadius(5.dp.toPx())
            val height = 8.dp.toPx()
            val sweep = size.width * (phase.value * 1.8f - 0.4f)
            val band = size.width * 0.28f
            repeat(3) { line ->
                val barSize = Size(size.width * (1f - line * 0.17f), height)
                val origin = Offset(0f, line * 16.dp.toPx())
                drawRoundRect(colors.onSurface.copy(alpha = 0.055f), origin, barSize, radius)
                if (animate) drawRoundRect(Brush.horizontalGradient(listOf(
                    Color.Transparent, colors.onSurface.copy(alpha = 0.055f), Color.Transparent),
                    startX = sweep - band, endX = sweep + band), origin, barSize, radius)
            }
        }
    }
}
