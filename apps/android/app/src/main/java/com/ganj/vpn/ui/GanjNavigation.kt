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

private val GanjNavGold = Color(0xFFD5A63A)

internal enum class GanjDestination(@StringRes val labelRes: Int) {
    Home(R.string.nav_home), Servers(R.string.nav_servers), Connect(R.string.nav_connect),
    Store(R.string.nav_store), Account(R.string.nav_account), Settings(R.string.nav_settings),
}

// RTL: first item is on the right; Store / Connect / Profile are the only main tabs.
internal val GanjPrimaryDestinations = listOf(GanjDestination.Store, GanjDestination.Connect, GanjDestination.Account)

internal fun GanjDestination.primaryDestination(): GanjDestination = when (this) {
    GanjDestination.Home, GanjDestination.Settings -> GanjDestination.Account
    GanjDestination.Servers -> GanjDestination.Connect
    else -> this
}

/** Reference wave navigation: three real destinations; raised center remains a Tab. */
@Composable
internal fun GanjLiquidBottomNavigation(
    selectedDestination: GanjDestination,
    onDestinationSelected: (GanjDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val effects = LocalGanjVisualEffectsPolicy.current
    val colors = MaterialTheme.colorScheme
    val dark = referenceDark()
    val haptic = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val selected = selectedDestination.primaryDestination()
    val animate = GanjDestinationMotionPolicy.shouldAnimate(effects.tier, effects.reduceMotion)
    val index = GanjPrimaryDestinations.indexOf(selected).toFloat()
    val position = animateFloatAsState(index, if (animate) spring(.88f, 480f) else snap(), label = "waveNavLens")
    val tail = animateFloatAsState(index, if (animate) spring(1f, 300f) else snap(), label = "waveNavTail")
    val unreadCount by NotificationUnreadRegistry.count.collectAsState()
    val generation by NotificationUnreadRegistry.refreshGeneration.collectAsState()
    val notificationApi = NotificationCompositionRegistry.currentApi()
    LaunchedEffect(notificationApi, generation) {
        val active = notificationApi ?: return@LaunchedEffect
        when (val result = withContext(Dispatchers.IO) { active.notifications(limit = 1) }) {
            is ApiResult.Success -> NotificationUnreadRegistry.update(result.value.unreadCount)
            is ApiResult.Failure -> if (result.error is ApiError.AuthenticationRequired || result.error is ApiError.AuthenticationExpired) NotificationUnreadRegistry.clear()
        }
    }
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    Column(modifier.navigationBarsPadding().padding(horizontal = 13.dp, vertical = 4.dp)) {
        Box(Modifier.fillMaxWidth().height(if (largeFont) 122.dp else 104.dp).testTag("liquid-bottom-navigation")) {
            // Two separate floating islands, never a full-width panel behind Connect.
            // The scaffold reserves this height, so the islands never overlay the server list.
            Canvas(Modifier.matchParentSize()) {
                val slot = size.width / 3f
                val bottom = size.height - 5.dp.toPx()
                val islandHeight = (if (largeFont) 77 else 68).dp.toPx()
                val inset = 6.dp.toPx()
                val radius = 20.dp.toPx()
                val islandColor = if (dark) Color(0xFF102C27) else colors.surface
                listOf(0, 2).forEach { i ->
                    val topLeft = Offset(i * slot + inset, bottom - islandHeight)
                    val dimensions = Size(slot - inset * 2f, islandHeight)
                    drawRoundRect(islandColor.copy(alpha = .92f), topLeft, dimensions,
                        CornerRadius(radius))
                    drawRoundRect(GanjReference.Gold.copy(alpha = .23f),
                        topLeft, dimensions, CornerRadius(radius),
                        style = Stroke(.8.dp.toPx()))
                }
                val logical = position.value.coerceIn(0f, 2f)
                val physical = if (rtl) 2f - logical else logical
                val tailDelta = abs(position.value - tail.value).coerceAtMost(.3f)
                if (physical < .55f || physical > 1.45f) {
                    val i = if (physical < 1f) 0 else 2
                    val lensInset = inset + 6.dp.toPx()
                    val lensSize = Size(slot - lensInset * 2f, islandHeight - 12.dp.toPx())
                    val lensTopLeft = Offset(i * slot + lensInset, bottom - islandHeight + 6.dp.toPx())
                    drawRoundRect(colors.primary.copy(alpha = .12f + tailDelta * .12f),
                        lensTopLeft, lensSize, CornerRadius(15.dp.toPx()))
                    drawRoundRect(GanjReference.Gold.copy(alpha = .55f), lensTopLeft,
                        lensSize, CornerRadius(15.dp.toPx()), style = Stroke(.8.dp.toPx()))
                }
            }
            Row(Modifier.fillMaxSize().selectableGroup(), verticalAlignment = Alignment.Bottom) {
                GanjPrimaryDestinations.forEach { destination ->
                    val active = selected == destination
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale = animateFloatAsState(if (pressed && animate) .95f else 1f,
                        if (animate) spring(.88f,700f) else snap(), label = "waveTabPress")
                    val label = stringResource(destination.labelRes)
                    val tint = if (active) (if (dark) Color(0xFFE1FFEC) else colors.primary) else (if(dark) Color(0xFFB2E4D0) else colors.onSurfaceVariant)
                    Column(Modifier.weight(1f).fillMaxHeight().testTag("nav-"+destination.name)
                        .semantics { contentDescription = UiAccessibilityPolicy.destinationDescription(label,active,"انتخاب‌شده") }
                        .selectable(active,interactionSource=interaction,indication=null,role=Role.Tab,onClick={
                            if (destination != selectedDestination) {
                                if (!effects.reduceMotion) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDestinationSelected(destination)
                            }
                        }).graphicsLayer { scaleX=scale.value;scaleY=scale.value }.padding(bottom=15.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
                        if(destination == GanjDestination.Connect) {
                            Box(Modifier.padding(bottom=8.dp).size(62.dp),contentAlignment=Alignment.Center) {
                                Canvas(Modifier.matchParentSize()) {
                                    drawCircle(Brush.linearGradient(listOf(if(active) Color(0xFF13AD75) else Color(0xFF16865E),Color(0xFF07543C))))
                                    if(!effects.reduceTransparency) drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha=.25f),Color.Transparent), center=Offset(size.width*.2f,0f),radius=size.width))
                                    drawCircle(Color(0xFFB8C887),radius=size.minDimension/2-.7.dp.toPx(),style=Stroke(1.dp.toPx()))
                                    drawCircle(Color(0xFFC6DDB4).copy(alpha=.2f),radius=size.minDimension/2-5.dp.toPx(),style=Stroke(.7.dp.toPx()))
                                }
                                GanjNavigationIcon(destination,Color(0xFFE2F9CC),Modifier.size(27.dp))
                            }
                        } else {
                            Box(Modifier.height(31.dp),contentAlignment=Alignment.Center) {
                                GanjNavigationIcon(destination,tint,Modifier.size(22.dp))
                                if(destination == GanjDestination.Account && (unreadCount ?: 0)>0) Box(Modifier.align(Alignment.TopEnd).offset(x=9.dp,y=(-3).dp).size(5.dp).background(GanjReference.Gold,CircleShape))
                            }
                            Spacer(Modifier.height(5.dp))
                        }
                        Text(label,color=if(destination==GanjDestination.Connect && dark) GanjReference.Gold else tint,
                            style=MaterialTheme.typography.labelSmall,fontWeight=if(active)FontWeight.SemiBold else FontWeight.Normal,
                            textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}
