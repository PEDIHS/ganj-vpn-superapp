package com.ganj.vpn.ui

import android.app.ActivityManager
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

/** Observe device changes instead of freezing animation settings until a UI preference changes. */
@Composable
internal fun currentGanjVisualEffectsPolicy(): GanjVisualEffectsPolicy {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    var policy by remember(context) { mutableStateOf(readGanjVisualEffectsPolicy(context)) }
    DisposableEffect(context, owner) {
        val refresh = { policy = readGanjVisualEffectsPolicy(context) }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { refresh() }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { refresh() }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        ContextCompat.registerReceiver(context, receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        owner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            owner.lifecycle.removeObserver(lifecycleObserver)
            context.contentResolver.unregisterContentObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }
    return policy
}

private fun readGanjVisualEffectsPolicy(context: Context): GanjVisualEffectsPolicy {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val animationScale = Settings.Global.getFloat(context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return GanjVisualEffectsResolver.resolve(
        isLowRamDevice = activityManager?.isLowRamDevice == true,
        powerSaveMode = powerManager?.isPowerSaveMode == true,
        systemAnimationsEnabled = animationScale > 0f,
    )
}
