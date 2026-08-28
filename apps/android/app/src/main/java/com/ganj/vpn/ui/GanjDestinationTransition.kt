package com.ganj.vpn.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
    content: @Composable (GanjDestination) -> Unit,
) {
    val effects = LocalGanjVisualEffectsPolicy.current
    val animate = GanjDestinationMotionPolicy.shouldAnimate(
        tier = effects.tier,
        reduceMotion = effects.reduceMotion,
    )
    if (!animate) {
        Box(modifier = modifier.fillMaxSize()) {
            content(destination)
        }
        return
    }

    val duration = GanjDestinationMotionPolicy.durationMillis(effects.tier)
    AnimatedContent(
        targetState = destination,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            (fadeIn(tween(durationMillis = duration)) +
                scaleIn(
                    initialScale = 0.992f,
                    animationSpec = tween(durationMillis = duration),
                )) togetherWith fadeOut(tween(durationMillis = (duration * 0.72f).toInt()))
        },
        label = "ganjLiquidDestinationTransition",
    ) { target ->
        content(target)
    }
}
