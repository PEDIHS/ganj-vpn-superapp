package com.ganj.vpn.ui

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

internal enum class GanjEffectsTier {
    Full,
    Balanced,
    Reduced,
}

@Immutable
internal data class GanjVisualEffectsPolicy(
    val tier: GanjEffectsTier,
    val reduceTransparency: Boolean,
    val reduceMotion: Boolean,
    val ambientBackgroundEffects: Boolean,
)

internal object GanjVisualEffectsResolver {
    fun resolve(
        isLowRamDevice: Boolean,
        powerSaveMode: Boolean,
        systemAnimationsEnabled: Boolean,
    ): GanjVisualEffectsPolicy {
        val tier = when {
            isLowRamDevice -> GanjEffectsTier.Reduced
            powerSaveMode -> GanjEffectsTier.Balanced
            else -> GanjEffectsTier.Full
        }
        return GanjVisualEffectsPolicy(
            tier = tier,
            reduceTransparency = isLowRamDevice,
            reduceMotion = !systemAnimationsEnabled,
            ambientBackgroundEffects = !isLowRamDevice && !powerSaveMode,
        )
    }
}

internal val LocalGanjVisualEffectsPolicy = staticCompositionLocalOf {
    GanjVisualEffectsPolicy(
        tier = GanjEffectsTier.Full,
        reduceTransparency = false,
        reduceMotion = false,
        ambientBackgroundEffects = true,
    )
}

@Composable
internal fun currentGanjVisualEffectsPolicy(): GanjVisualEffectsPolicy {
    val context = LocalContext.current
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val animationScale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    return GanjVisualEffectsResolver.resolve(
        isLowRamDevice = activityManager?.isLowRamDevice == true,
        powerSaveMode = powerManager?.isPowerSaveMode == true,
        systemAnimationsEnabled = animationScale > 0f,
    )
}
