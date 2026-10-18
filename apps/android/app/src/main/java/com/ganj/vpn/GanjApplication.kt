package com.ganj.vpn

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/** Do not initialize Google's push SDK unless this build has explicit project options. */
class GanjApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!BuildConfig.FCM_ENABLED) return
        val options = FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.FCM_APP_ID)
            .setGcmSenderId(BuildConfig.FCM_SENDER_ID)
            .setProjectId(BuildConfig.FCM_PROJECT_ID)
            .setApiKey(BuildConfig.FCM_API_KEY)
            .build()
        if (FirebaseApp.getApps(this).isEmpty()) FirebaseApp.initializeApp(this, options)
    }
}
