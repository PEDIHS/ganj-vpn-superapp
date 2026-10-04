package com.ganj.vpn.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ganj.vpn.R
import com.ganj.vpn.composition.NotificationCompositionRegistry
import com.ganj.vpn.composition.NotificationUnreadRegistry
import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

internal enum class GanjDestination(@StringRes val labelRes: Int) {
    Home(R.string.nav_home), Servers(R.string.nav_servers), Connect(R.string.nav_connect),
    Store(R.string.nav_store), Account(R.string.nav_account), Settings(R.string.nav_settings),
}

internal val GanjPrimaryDestinations = listOf(GanjDestination.Connect, GanjDestination.Servers, GanjDestination.Settings)

internal fun GanjDestination.primaryDestination(): GanjDestination = when (this) {
    GanjDestination.Home, GanjDestination.Account -> GanjDestination.Settings
    GanjDestination.Store -> GanjDestination.Servers
    else -> this
}

/** One shared, interruptible lens. Animation values are read only by draw/layer modifiers. */
@Composable
internal fun GanjLiquidBottomNavigation(
    selectedDestination: GanjDestination,
    onDestinationSelected: (GanjDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val showAllLabels = true
    val effects = LocalGanjVisualEffectsPolicy.current
    val glass = LocalGanjGlassPalette.current
    val colors = MaterialTheme.colorScheme
    val shape = remember { RoundedCornerShape(28.dp) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val motion = GanjDestinationMotionPolicy.shouldAnimate(effects.tier, effects.reduceMotion)
    val selectedPrimary = selectedDestination.primaryDestination()
    val targetIndex = GanjPrimaryDestinations.indexOf(selectedPrimary).toFloat()
    val position = animateFloatAsState(targetIndex,
        if (motion) spring(dampingRatio = 0.88f, stiffness = 650f) else snap(), label = "navLensPosition")
    val tail = animateFloatAsState(targetIndex,
        if (motion) spring(dampingRatio = 1f, stiffness = 390f) else snap(), label = "navLensTail")
    val unreadCount by NotificationUnreadRegistry.count.collectAsState()
    val generation by NotificationUnreadRegistry.refreshGeneration.collectAsState()
    val notificationApi = NotificationCompositionRegistry.currentApi()
    LaunchedEffect(notificationApi, generation) {
        val active = notificationApi ?: return@LaunchedEffect
        when (val result = withContext(Dispatchers.IO) { active.notifications(limit = 1) }) {
            is ApiResult.Success -> NotificationUnreadRegistry.update(result.value.unreadCount)
            is ApiResult.Failure -> if (result.error is ApiError.AuthenticationRequired ||
                result.error is ApiError.AuthenticationExpired) NotificationUnreadRegistry.clear()
        }
    }
    Box(modifier.navigationBarsPadding().padding(
        horizontal = GanjResponsivePolicy.stitchNavigationHorizontalPaddingDp(configuration.screenWidthDp).dp,
        vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth()
            .shadow(if (effects.tier == GanjEffectsTier.Reduced) 0.dp else 10.dp, shape, clip = false)
            .clip(shape)
            .background(if (effects.reduceTransparency) glass.opaqueFallback else colors.surface.copy(alpha = 0.94f))
            .background(Brush.verticalGradient(listOf(
                glass.highlight.copy(alpha = if (effects.reduceTransparency) 0f else 0.09f),
                Color.Transparent, glass.emeraldTint.copy(alpha = 0.035f))))
            .border(0.75.dp, glass.borderSoft, shape)
            .padding(5.dp).testTag("liquid-bottom-navigation")) {
            if (largeText) {
                Text(stringResource(selectedPrimary.labelRes), color = colors.primary,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp)
                        .clearAndSetSemantics {})
            }
            Box(Modifier.fillMaxWidth()) {
                Canvas(Modifier.matchParentSize()) {
                    val slot = size.width / GanjPrimaryDestinations.size
                    val leading = position.value.coerceIn(0f, 2f)
                    val trailing = tail.value.coerceIn(0f, 2f)
                    val stretch = (abs(leading - trailing) * slot * 0.24f).coerceAtMost(slot * 0.32f)
                    val centerIndex = if (rtl) 2f - leading else leading
                    val lensWidth = slot - 4.dp.toPx() + stretch
                    val lensHeight = size.height - 4.dp.toPx()
                    val origin = Offset((centerIndex + 0.5f) * slot - lensWidth / 2, 2.dp.toPx())
                    val lensSize = Size(lensWidth, lensHeight)
                    val radius = CornerRadius(23.dp.toPx())
                    drawRoundRect(colors.primary.copy(alpha = if (effects.reduceTransparency) 0.17f else 0.12f),
                        origin, lensSize, radius)
                    if (!effects.reduceTransparency) {
                        drawRoundRect(Brush.verticalGradient(listOf(glass.highlight.copy(alpha = 0.19f),
                            Color.Transparent, glass.emeraldTint.copy(alpha = 0.08f))), origin, lensSize, radius)
                        drawRoundRect(glass.highlight.copy(alpha = 0.20f), origin, lensSize, radius,
                            style = Stroke(0.65.dp.toPx()))
                    }
                    drawRoundRect(colors.primary.copy(alpha = 0.70f),
                        Offset(origin.x + lensWidth * 0.32f, origin.y + lensHeight - 3.dp.toPx()),
                        Size(lensWidth * 0.36f, 1.5.dp.toPx()), CornerRadius(2.dp.toPx()))
                }
                Row(Modifier.fillMaxWidth().selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                    GanjPrimaryDestinations.forEach { destination ->
                        GanjNavigationItem(destination, destination == selectedPrimary,
                            !largeText && (showAllLabels || destination == selectedPrimary), !largeText,
                            if (destination == GanjDestination.Settings) unreadCount ?: 0 else 0,
                            onClick = { if (destination != selectedDestination) {
                                if (!effects.reduceMotion) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDestinationSelected(destination)
                            } },
                            modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GanjNavigationItem(
    destination: GanjDestination, selected: Boolean, showLabel: Boolean, reserveLabelSpace: Boolean, unreadCount: Int,
    onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val label = stringResource(destination.labelRes)
    val effects = LocalGanjVisualEffectsPolicy.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val animate = GanjDestinationMotionPolicy.shouldAnimate(effects.tier, effects.reduceMotion)
    val scale = animateFloatAsState(if (animate && pressed) 0.94f else 1f,
        if (animate) spring(0.9f, 850f) else snap(), label = "navPress")
    val lift = animateFloatAsState(if (animate && selected) -1.5f else 0f,
        if (animate) spring(0.9f, 650f) else snap(), label = "navIconLift")
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val description = UiAccessibilityPolicy.destinationDescription(label, selected, stringResource(R.string.a11y_selected)) +
        if (unreadCount > 0) "، ${unreadCount.toPersianDigits()} اعلان خوانده‌نشده" else ""
    Column(modifier.heightIn(min = 66.dp).testTag("nav-${destination.name}")
        .clip(RoundedCornerShape(23.dp)).semantics { contentDescription = description }
        .selectable(selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
        .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(30.dp).graphicsLayer {
            translationY = lift.value * density; scaleX = scale.value; scaleY = scale.value
        }, contentAlignment = Alignment.Center) {
            if (destination == GanjDestination.Connect) {
                Box(Modifier.size(30.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.16f else 0.06f)))
            }
            GanjNavigationIcon(destination, tint, Modifier.size(if (destination == GanjDestination.Connect) 25.dp else 23.dp))
            notificationBadgeText(unreadCount)?.let { badge ->
                Box(Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-3).dp)
                    .clip(CircleShape).background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text(badge, color = MaterialTheme.colorScheme.onError,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        modifier = Modifier.clearAndSetSemantics {})
                }
            }
        }
        if (reserveLabelSpace) {
            Spacer(Modifier.height(3.dp))
            Text(if (showLabel) label else "", color = tint, style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}
