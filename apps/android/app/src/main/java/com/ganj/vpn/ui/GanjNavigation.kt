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
    Column(modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(if (largeFont) 132.dp else 112.dp).testTag("liquid-bottom-navigation")) {
            Canvas(Modifier.matchParentSize()) {
                val w = size.width; val h = size.height - 4.dp.toPx(); val c = w / 2
                val top = 27.dp.toPx(); val r = 22.dp.toPx(); val mouth = 58.dp.toPx(); val neck = 38.dp.toPx()
                val wave = androidx.compose.ui.graphics.Path().apply {
                    moveTo(r, top); lineTo(c - mouth, top)
                    cubicTo(c - neck, top, c - neck, 75.dp.toPx(), c, 75.dp.toPx())
                    cubicTo(c + neck, 75.dp.toPx(), c + neck, top, c + mouth, top)
                    lineTo(w - r, top); quadraticTo(w, top, w, top + r)
                    lineTo(w, h - r); quadraticTo(w, h, w - r, h)
                    lineTo(r, h); quadraticTo(0f, h, 0f, h-r)
                    lineTo(0f, top+r); quadraticTo(0f, top, r, top); close()
                }
                val baseTop = if (dark) GanjReference.NavTop else colors.primaryContainer
                val baseBottom = if (dark) GanjReference.NavBottom else colors.surface
                drawPath(wave, Brush.linearGradient(listOf(baseTop, baseBottom)))
                if (!effects.reduceTransparency) {
                    drawPath(wave, Brush.linearGradient(listOf(Color.White.copy(alpha = .13f), Color.Transparent, GanjReference.Gold.copy(alpha = .08f))))
                }
                drawPath(wave, Brush.linearGradient(listOf(GanjReference.Gold.copy(alpha = .65f), Color(0xFFBCFFE2).copy(alpha = .5f), colors.primary.copy(alpha = .2f))), style = Stroke(.85.dp.toPx()))
                val logical = position.value.coerceIn(0f, 2f)
                val physical = if (rtl) 2f-logical else logical
                val slot = w/3
                val visibility = abs(logical-1f).coerceIn(0f,1f)
                val stretch = (abs(position.value-tail.value)*.13f).coerceAtMost(.18f)
                val lensW = (slot-22.dp.toPx())*(1f+stretch)
                val origin = Offset((physical+.5f)*slot-lensW/2, 36.dp.toPx())
                val bounds = Size(lensW, h-43.dp.toPx())
                drawRoundRect(Color(0xFFBEFFDF).copy(alpha = .14f*visibility), origin,bounds,CornerRadius(15.dp.toPx()))
                drawRoundRect(Color(0xFFBED4AA).copy(alpha = .4f*visibility), origin,bounds,CornerRadius(15.dp.toPx()), style = Stroke(.7.dp.toPx()))
                drawRoundRect(GanjReference.Gold.copy(alpha = visibility),Offset(origin.x + lensW/2 - 9.dp.toPx(), h - 9.dp.toPx()),Size(18.dp.toPx(),2.dp.toPx()),CornerRadius(1.dp.toPx()))
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
