package com.ganj.vpn

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.ganj.vpn.composition.GanjCompositionOwner
import com.ganj.vpn.presentation.ConnectionEffectResult
import com.ganj.vpn.presentation.UiFailure
import com.ganj.vpn.presentation.UiFailureKind
import com.ganj.vpn.ui.GanjTheme
import com.ganj.vpn.ui.GanjVpnApp
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private var vpnPermissionContinuation: CancellableContinuation<Boolean>? = null
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val continuation = vpnPermissionContinuation
        vpnPermissionContinuation = null
        if (continuation?.isActive == true) continuation.resume(result.resultCode == Activity.RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val composition = ViewModelProvider(
            this,
            GanjCompositionOwner.Factory(application, BuildConfig.CONTROL_API_BASE_URL),
        )[GanjCompositionOwner::class.java].composition
        setContent {
            GanjTheme {
                GanjVpnApp(
                    composition = composition,
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
                    onLaunchTelegram = { url ->
                        runCatching {
                            val uri = Uri.parse(url)
                            require(uri.scheme == "https" && uri.host == "t.me")
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    },
                )
            }
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
                if (vpnPermissionContinuation === continuation) vpnPermissionContinuation = null
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
