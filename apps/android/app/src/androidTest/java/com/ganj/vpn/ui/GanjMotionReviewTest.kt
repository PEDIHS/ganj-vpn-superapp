package com.ganj.vpn.ui

import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.ganj.vpn.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

/** Isolated UI state fixtures plus actual Window frame timings; no synthetic VPN success claim. */
@androidx.test.filters.SdkSuppress(minSdkVersion = 31)
@RunWith(AndroidJUnit4::class)
class GanjMotionReviewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun interruptedConnectionStatesRejectBusyTapsAndRecordRenderedFrameTimings() {
        val state = mutableStateOf(GanjConnectionVisualState.Disconnected)
        val disconnecting = mutableStateOf(false)
        val preparing = mutableStateOf(false)
        var taps = 0
        val frames = Collections.synchronizedList(mutableListOf<Long>())
        val deadlines = Collections.synchronizedList(mutableListOf<Long>())
        var droppedCallbacks = 0
        val thread = HandlerThread("ganj-ui-frame-review").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, drops ->
            frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
            deadlines.add(metrics.getMetric(FrameMetrics.DEADLINE))
            droppedCallbacks += drops
        }
        var listenerAttached = false
        try {
            compose.activityRule.scenario.onActivity { activity ->
                activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
                listenerAttached = true
                activity.setContent {
                    GanjTheme(darkTheme = false, visualEffectsPolicy = GanjVisualEffectsPolicy(GanjEffectsTier.Full, false, false, false)) {
                        Surface(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                                GanjConnectionHero(state.value, "اتصال", "وضعیت واقعی تست", disconnecting.value, false, { taps++ }, preparing.value)
                            }
                        }
                    }
                }
            }
            compose.onNodeWithTag("connect-action").performClick()
            compose.runOnIdle { assertEquals(1, taps); state.value = GanjConnectionVisualState.Connecting; preparing.value = true }
            compose.onNodeWithTag("connect-action").assertIsNotEnabled().performTouchInput { click() }
            capture("connection-preparing-light")
            compose.runOnIdle { preparing.value = false }
            // Wall-clock observation records actual rendered frames, rather than advancing only the test clock.
            Thread.sleep(1800)
            compose.runOnIdle { state.value = GanjConnectionVisualState.Connected }
            Thread.sleep(600)
            capture("connection-confirmed-light")
            compose.runOnIdle { state.value = GanjConnectionVisualState.Reconnecting }
            capture("connection-recovering-light")
            compose.onNodeWithTag("connect-action").assertIsNotEnabled()
            compose.runOnIdle { state.value = GanjConnectionVisualState.Connected; disconnecting.value = true }
            capture("connection-disconnecting-light")
            compose.onNodeWithTag("connect-action").assertIsNotEnabled().performTouchInput { click() }
            compose.runOnIdle { state.value = GanjConnectionVisualState.Failed; disconnecting.value = false }
            capture("connection-failed-light")
            compose.onNodeWithTag("connect-action").assertIsEnabled()
            compose.runOnIdle { assertEquals(1, taps) }
            compose.activityRule.scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener); listenerAttached = false }
            thread.quitSafely(); thread.join(2000)
            val observed = synchronized(frames) { frames.toList() }
            assertTrue("Actual rendered frames must be recorded", observed.size > 10)
            val sorted = observed.sorted()
            val limits = synchronized(deadlines) { deadlines.toList() }
            val missed = observed.indices.count { it < limits.size && limits[it] > 0 && observed[it] > limits[it] }
            val report = "{\"scope\":\"API35 emulator UI fixture; physical performance unverified\",\"frames\":${sorted.size},\"medianMs\":${sorted[sorted.size / 2] / 1e6},\"p95Ms\":${sorted[((sorted.size - 1) * .95).toInt()] / 1e6},\"overDeadline\":$missed,\"droppedCallbacks\":$droppedCallbacks}"
            File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "motion-frame-metrics.json").writeText(report)
        } finally {
            if (listenerAttached) compose.activityRule.scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            thread.quitSafely()
        }
    }

    @Test fun primarySettingsKeepsRealAccountAndAppearanceActionsAtLargeFont() {
        var accountOpened = false
        var chosen: GanjThemePreference? = null
        compose.activityRule.scenario.onActivity { activity -> activity.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                GanjTheme(darkTheme = false, visualEffectsPolicy = GanjVisualEffectsPolicy(GanjEffectsTier.Reduced, true, true, false)) {
                    Surface(Modifier.fillMaxSize()) {
                        StitchSettingsScreen(GanjUserPreferences(), { chosen = it }, {}, {}, {}, null,
                            onOpenAccount = { accountOpened = true }, onOpenHome = {})
                    }
                }
            }
        } }
        capture("settings-light-large")
        compose.onNodeWithText("حساب و دستگاه‌ها").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(accountOpened) }
        compose.onNodeWithText("تیره").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(GanjThemePreference.DARK, chosen) }
        capture("settings-appearance-light-large")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        check(UiDevice.getInstance(instrumentation).takeScreenshot(File(instrumentation.targetContext.cacheDir, "$name.png")))
    }
}
