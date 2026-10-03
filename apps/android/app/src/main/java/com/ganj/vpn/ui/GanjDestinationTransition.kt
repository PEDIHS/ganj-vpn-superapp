package com.ganj.vpn.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

internal object GanjDestinationMotionPolicy {
    fun shouldAnimate(
        tier: GanjEffectsTier,
        reduceMotion: Boolean,
    ): Boolean = !reduceMotion && tier != GanjEffectsTier.Reduced

    fun durationMillis(tier: GanjEffectsTier): Int = when (tier) {
        GanjEffectsTier.Full -> 220
        GanjEffectsTier.Balanced -> 150
        GanjEffectsTier.Reduced -> 0
    }
}

@Composable
internal fun GanjDestinationTransition(
    destination: GanjDestination,
    modifier: Modifier = Modifier,
    stateOwner: Any? = null,
    content: @Composable (GanjDestination) -> Unit,
) {
    val effects = LocalGanjVisualEffectsPolicy.current
    val animate = GanjDestinationMotionPolicy.shouldAnimate(
        tier = effects.tier,
        reduceMotion = effects.reduceMotion,
    )
    val savedDestinations = key(stateOwner) { rememberSaveableStateHolder() }
    val density = LocalDensity.current
    // Mount one screen, preserving saved scroll/search without keeping offscreen effects alive.
    val reveal = remember(destination, animate) { Animatable(if (animate) 0f else 1f) }
    val duration = GanjDestinationMotionPolicy.durationMillis(effects.tier)
    LaunchedEffect(reveal) { if (animate) reveal.animateTo(1f, tween(durationMillis = duration)) }
    Box(modifier.fillMaxSize().graphicsLayer {
        alpha = reveal.value
        translationY = with(density) { 6.dp.toPx() } * (1f - reveal.value)
    }) {
        key(stateOwner, destination) {
            savedDestinations.SaveableStateProvider(destination.name) { content(destination) }
        }
    }
}
