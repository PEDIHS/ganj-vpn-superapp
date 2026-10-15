package com.ganj.vpn.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Shared controls for the current GANJ visual language.
 * These only own presentation/interaction feedback; business and VPN state stay outside.
 */
@Composable
internal fun GanjGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary,
    filled: Boolean = true,
    contentColor: Color? = null,
    shapeRadius: Dp = 999.dp,
) {
    if (filled) {
        GanjLiquidAction(
            onClick = onClick,
            enabled = enabled,
            accent = accent,
            shapeRadius = shapeRadius,
            modifier = modifier,
        ) {
            Text(
                text = text,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor ?: MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }

    val effects = LocalGanjVisualEffectsPolicy.current
    val glass = LocalGanjGlassPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val animate = GanjDestinationMotionPolicy.shouldAnimate(effects.tier, effects.reduceMotion)
    val scale by animateFloatAsState(
        if (animate && pressed && enabled) 0.975f else 1f,
        if (animate) spring(dampingRatio = 0.9f, stiffness = 850f) else snap(),
        label = "ganjSecondaryButtonScale",
    )
    val shape = remember(shapeRadius) { RoundedCornerShape(shapeRadius) }
    val effectiveAccent = if (enabled) accent else MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .heightIn(min = GanjLiquidGlassPolicy.MinimumTouchTargetDp.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                if (effects.reduceTransparency) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
            )
            .background(
                Brush.linearGradient(
                    listOf(
                        glass.highlight.copy(alpha = if (enabled && !effects.reduceTransparency) 0.10f else 0.02f),
                        Color.Transparent,
                        effectiveAccent.copy(alpha = if (enabled) 0.055f else 0.02f),
                    ),
                ),
            )
            .border(
                1.dp,
                effectiveAccent.copy(alpha = if (enabled) 0.32f else 0.14f),
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) (contentColor ?: MaterialTheme.colorScheme.onSurface)
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun GanjChoicePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val effects = LocalGanjVisualEffectsPolicy.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val animate = GanjDestinationMotionPolicy.shouldAnimate(effects.tier, effects.reduceMotion)
    val scale by animateFloatAsState(
        if (animate && pressed && enabled) 0.96f else 1f,
        if (animate) spring(dampingRatio = 0.88f, stiffness = 900f) else snap(),
        label = "ganjChoiceScale",
    )
    val shape = remember { RoundedCornerShape(999.dp) }
    val activeAccent = if (enabled) accent else MaterialTheme.colorScheme.outline

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                if (selected) activeAccent.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surface.copy(alpha = if (effects.reduceTransparency) 1f else 0.52f),
            )
            .border(
                1.dp,
                activeAccent.copy(alpha = when {
                    !enabled -> 0.14f
                    selected -> 0.52f
                    else -> 0.20f
                }),
                shape,
            )
            .semantics { this.selected = selected }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(if (selected) 7.dp else 5.dp)
                .background(activeAccent.copy(alpha = if (selected) 1f else 0.52f), CircleShape),
        )
        Text(
            text = text,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                selected -> activeAccent
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun GanjLiquidTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.50f),
            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.30f),
            focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
            unfocusedIndicatorColor = LocalGanjGlassPalette.current.borderSoft.copy(alpha = 0.72f),
            disabledIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
internal fun GanjQuickActionTile(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary,
    marker: String = "›",
    leading: (@Composable BoxScope.() -> Unit)? = null,
) {
    val effects = LocalGanjVisualEffectsPolicy.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val animate = GanjDestinationMotionPolicy.shouldAnimate(effects.tier, effects.reduceMotion)
    val scale by animateFloatAsState(
        if (animate && pressed && enabled) 0.975f else 1f,
        if (animate) spring(dampingRatio = 0.9f, stiffness = 780f) else snap(),
        label = "ganjQuickActionScale",
    )
    val shape = remember { RoundedCornerShape(20.dp) }
    val glass = LocalGanjGlassPalette.current

    Row(
        modifier = modifier
            .heightIn(min = 76.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (effects.reduceTransparency) 1f else 0.66f))
            .background(
                Brush.linearGradient(
                    listOf(
                        glass.highlight.copy(alpha = if (effects.reduceTransparency) 0.02f else 0.10f),
                        Color.Transparent,
                        accent.copy(alpha = 0.07f),
                    ),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.24f), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(accent.copy(alpha = if (enabled) 0.13f else 0.05f), CircleShape)
                .border(1.dp, accent.copy(alpha = if (enabled) 0.28f else 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (leading != null) {
                leading()
            } else {
                Text(
                    marker,
                    color = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun GanjCompactSpinner(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 24.dp,
) {
    val effects = LocalGanjVisualEffectsPolicy.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val animate = lifecycle.isAtLeast(Lifecycle.State.STARTED) &&
        GanjDestinationMotionPolicy.shouldAnimate(effects.tier, effects.reduceMotion)
    val phase: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "ganjMiniSpinner").animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(1300, easing = LinearEasing)),
            label = "ganjMiniSpinnerPhase",
        )
    } else {
        remember { mutableFloatStateOf(0.18f) }
    }

    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier.size(size)) {
        val stroke = Stroke((size.value / 11f).dp.toPx().coerceAtLeast(1.6.dp.toPx()), cap = StrokeCap.Round)
        drawCircle(outline.copy(alpha = 0.18f), style = stroke)
        drawArc(
            color = accent,
            startAngle = phase.value * 360f,
            sweepAngle = 110f,
            useCenter = false,
            style = stroke,
        )
    }
}

@Composable
internal fun GanjSlimProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val effects = LocalGanjVisualEffectsPolicy.current
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        target,
        if (effects.reduceMotion) snap() else tween(GanjMotion.State),
        label = "ganjSlimProgress",
    )
    val shape = remember { RoundedCornerShape(999.dp) }
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated.coerceAtLeast(0.01f))
                .height(4.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.68f),
                            accent,
                            GanjGold.copy(alpha = 0.82f),
                        ),
                    ),
                    shape,
                ),
        )
    }
}

@Composable
internal fun GanjBackPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "بازگشت",
) {
    GanjGlassButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        accent = MaterialTheme.colorScheme.outline,
        filled = false,
        shapeRadius = 999.dp,
    )
}

@Composable
internal fun GanjInlineStatusPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    tone: GanjStatusTone = GanjStatusTone.Neutral,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    GanjGlassSurface(
        role = GanjGlassRole.Prominent,
        accent = accent,
        modifier = modifier.fillMaxWidth(),
        shapeRadius = 24.dp,
        padding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GanjStatusPill(title, tone)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionText != null && onAction != null) {
            GanjGlassButton(
                text = actionText,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                accent = accent,
                filled = true,
                contentColor = when (tone) {
                    GanjStatusTone.Warning, GanjStatusTone.Premium -> MaterialTheme.colorScheme.onSecondary
                    GanjStatusTone.Danger -> MaterialTheme.colorScheme.onError
                    else -> MaterialTheme.colorScheme.onPrimary
                },
            )
        }
        if (secondaryActionText != null && onSecondaryAction != null) {
            GanjGlassButton(
                text = secondaryActionText,
                onClick = onSecondaryAction,
                modifier = Modifier.fillMaxWidth(),
                accent = accent,
                filled = false,
            )
        }
    }
}