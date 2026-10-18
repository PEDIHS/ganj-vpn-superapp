package com.ganj.vpn.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.ganj.vpn.BuildConfig
import com.ganj.vpn.composition.NotificationCompositionRegistry
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Register only after the active Telegram account has been verified.
 * Switching accounts rebinds the SAME phone token to the currently active
 * backend principal and revokes delivery to the previous account.
 * No background user-data polling is performed.
 */
@Composable
internal fun GanjPushDeviceBinding(verifiedAccountId: String?) {
    val context = LocalContext.current
    LaunchedEffect(verifiedAccountId) {
        if (!BuildConfig.FCM_ENABLED || verifiedAccountId.isNullOrBlank()) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@LaunchedEffect
        val api = NotificationCompositionRegistry.currentApi() ?: return@LaunchedEffect
        val token = suspendCancellableCoroutine<String?> { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (continuation.isActive) continuation.resume(
                    if (task.isSuccessful) task.result else null
                )
            }
        } ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { api.registerPushDevice(token) }
    }
}
