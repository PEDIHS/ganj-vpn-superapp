package com.ganj.vpn

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.ganj.vpn.composition.GanjCompositionOwner
import com.ganj.vpn.composition.TelegramAuthResult
import com.ganj.vpn.presentation.ConnectionEffectResult
import com.ganj.vpn.presentation.UiFailure
import com.ganj.vpn.presentation.UiFailureKind
import com.ganj.vpn.ui.GanjTheme
import com.ganj.vpn.ui.GanjVpnApp
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private var vpnPermissionContinuation: CancellableContinuation<Boolean>? = null
    private lateinit var owner: GanjCompositionOwner

    private val telegramLinked = mutableStateOf(false)
    private val telegramBusy = mutableStateOf(false)
    private val telegramError = mutableStateOf(false)
    private val accountRefreshGeneration = mutableStateOf(0)

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val continuation = vpnPermissionContinuation
        vpnPermissionContinuation = null
        if (continuation?.isActive == true) {
            continuation.resume(result.resultCode == Activity.RESULT_OK)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        owner = ViewModelProvider(
            this,
            GanjCompositionOwner.Factory(
                application = application,
                endpoint = BuildConfig.CONTROL_API_BASE_URL,
                telegramRedirectUri = BuildConfig.TELEGRAM_REDIRECT_URI,
            ),
        )[GanjCompositionOwner::class.java]

        telegramLinked.value = owner.telegramAuth?.isLinked() == true
        handleTelegramIntent(intent)

        val composition = owner.composition
        setContent {
            GanjTheme {
                GanjVpnApp(
                    composition = composition,
                    telegramLinked = telegramLinked.value,
                    telegramBusy = telegramBusy.value,
                    telegramError = telegramError.value,
                    accountRefreshGeneration = accountRefreshGeneration.value,
                    onTelegramLogin = ::beginTelegramLogin,
                    onTelegramLogout = ::logoutTelegram,
                    onLaunchGooglePlay = { handle ->
                        composition.launchGooglePlayCheckout(this@MainActivity, handle)
                    },
                    onLaunchVpn = { handle ->
                        if (ensureVpnPermission()) {
                            composition.launchVpnConnection(handle)
                        } else {
                            ConnectionEffectResult.Failed(
                                UiFailure(
                                    UiFailureKind.CONFIGURATION,
                                    "connection.permission_denied",
                                    retryable = true,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTelegramIntent(intent)
    }

    private fun beginTelegramLogin() {
        if (telegramBusy.value) return
        val auth = owner.telegramAuth ?: run {
            telegramError.value = true
            return
        }

        telegramBusy.value = true
        telegramError.value = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { auth.begin() }
            telegramBusy.value = false
            when (result) {
                is TelegramAuthResult.Launch -> {
                    val launched = runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.authorizationUrl)))
                    }.isSuccess
                    if (!launched) telegramError.value = true
                }
                is TelegramAuthResult.Failed -> telegramError.value = true
                TelegramAuthResult.Linked,
                TelegramAuthResult.LoggedOut,
                -> Unit
            }
        }
    }

    private fun handleTelegramIntent(intent: Intent?) {
        val callback = intent
            ?.takeIf { it.action == Intent.ACTION_VIEW }
            ?.dataString
            ?: return
        if (!::owner.isInitialized || telegramBusy.value) return

        val auth = owner.telegramAuth ?: return
        telegramBusy.value = true
        telegramError.value = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { auth.complete(callback) }
            telegramBusy.value = false
            when (result) {
                TelegramAuthResult.Linked -> {
                    telegramLinked.value = true
                    telegramError.value = false
                    accountRefreshGeneration.value += 1
                }
                is TelegramAuthResult.Failed -> telegramError.value = true
                is TelegramAuthResult.Launch,
                TelegramAuthResult.LoggedOut,
                -> Unit
            }
        }
    }

    private fun logoutTelegram() {
        if (telegramBusy.value) return
        val auth = owner.telegramAuth ?: run {
            telegramError.value = true
            return
        }

        telegramBusy.value = true
        telegramError.value = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { auth.logout() }
            telegramBusy.value = false
            telegramLinked.value = auth.isLinked()
            accountRefreshGeneration.value += 1
            telegramError.value = result is TelegramAuthResult.Failed
        }
    }

    private suspend fun ensureVpnPermission(): Boolean {
        val request: Intent = VpnService.prepare(this) ?: return true
        return suspendCancellableCoroutine { continuation ->
            if (vpnPermissionContinuation != null) {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            vpnPermissionContinuation = continuation
            continuation.invokeOnCancellation {
                if (vpnPermissionContinuation === continuation) {
                    vpnPermissionContinuation = null
                }
            }
            vpnPermissionLauncher.launch(request)
        }
    }

    override fun onDestroy() {
        val continuation = vpnPermissionContinuation
        vpnPermissionContinuation = null
        if (continuation?.isActive == true) continuation.resume(false)
        super.onDestroy()
    }
}
