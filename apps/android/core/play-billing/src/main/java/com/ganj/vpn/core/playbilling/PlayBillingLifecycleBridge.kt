package com.ganj.vpn.core.playbilling

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Application-scoped foreground bridge recommended by Google Play Billing.
 *
 * It intentionally stores no Activity reference. The host owns registration and must call [close]
 * from its application component shutdown/test teardown. Checkout launch still requires the caller
 * to pass its currently resumed Activity directly to `launchCheckout`.
 */
class PlayBillingLifecycleBridge(
    private val application: Application,
    private val adapter: GooglePlayBillingAdapter,
) : Application.ActivityLifecycleCallbacks, Closeable {
    private val registered = AtomicBoolean(false)

    fun start() {
        if (registered.compareAndSet(false, true)) {
            application.registerActivityLifecycleCallbacks(this)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        adapter.onAppResumed()
    }

    override fun close() {
        if (registered.compareAndSet(true, false)) {
            application.unregisterActivityLifecycleCallbacks(this)
        }
        adapter.close()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
