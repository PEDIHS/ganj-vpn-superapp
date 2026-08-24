package com.ganj.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.ganj.vpn.composition.GanjCompositionOwner
import com.ganj.vpn.ui.GanjVpnApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val composition = ViewModelProvider(
            this,
            GanjCompositionOwner.Factory(application, BuildConfig.CONTROL_API_BASE_URL),
        )[GanjCompositionOwner::class.java].composition
        setContent {
            GanjVpnApp(
                composition = composition,
                onLaunchGooglePlay = { handle ->
                    composition.launchGooglePlayCheckout(this@MainActivity, handle)
                },
            )
        }
    }
}
