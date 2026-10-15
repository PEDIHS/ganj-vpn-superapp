package com.ganj.vpn.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.filters.SdkSuppress
import android.provider.Settings
import java.util.concurrent.atomic.AtomicReference
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

/** Isolated UI state/semantics fixtures; performance has its own system-clock benchmark. */
@RunWith(AndroidJUnit4::class)
class GanjMotionReviewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun interruptedConnectionStatesRejectBusyTaps() {
        val state = mutableStateOf(GanjConnectionVisualState.Disconnected)
        val disconnecting = mutableStateOf(false)
        val preparing = mutableStateOf(false)
        var taps = 0
        try {
            compose.activityRule.scenario.onActivity { activity ->
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
            // This fixture checks interruption/semantics; real frame timings use the separate system-clock benchmark.
            compose.mainClock.advanceTimeBy(1800)
            compose.runOnIdle { state.value = GanjConnectionVisualState.Connected }
            compose.mainClock.advanceTimeBy(600)
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
        } finally {
            compose.mainClock.autoAdvance = true
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


    /** Compare actual rendered pixels, not only state labels or frame counters. */
    @Test @SdkSuppress(minSdkVersion = 26)
    fun lightweightTierReallyAnimatesPowerAndTabLensButHonorsExplicitStop() {
        val stopped = mutableStateOf(false)
        val selected = mutableStateOf(GanjDestination.Connect)
        try {
            compose.mainClock.autoAdvance = false
            compose.activityRule.scenario.onActivity { activity -> activity.setContent {
                GanjTheme(darkTheme = true, visualEffectsPolicy =
                    GanjVisualEffectsPolicy(GanjEffectsTier.Reduced, true, stopped.value, false)) {
                    Scaffold(containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = { GanjLiquidBottomNavigation(selected.value, { selected.value = it }) }) { padding ->
                        Column(Modifier.padding(padding).padding(20.dp)) {
                            GanjConnectionHero(GanjConnectionVisualState.Connecting, "اتصال", "در حال اتصال", false, false, {})
                        }
                    }
                }
            } }
            compose.mainClock.advanceTimeBy(100)
            val initialPower = pixels("connection-hero")
            compose.mainClock.advanceTimeBy(450)
            assertFalse("Low-memory mode must rotate the actual connection arcs",
                initialPower.contentEquals(pixels("connection-hero")))
            compose.onNodeWithTag("nav-Store").performClick()
            compose.mainClock.advanceTimeByFrame()
            val initialLens = pixels("liquid-bottom-navigation")
            compose.mainClock.advanceTimeBy(400)
            assertFalse("Low-memory mode must move the actual navigation lens",
                initialLens.contentEquals(pixels("liquid-bottom-navigation")))
            compose.runOnIdle { stopped.value = true }
            compose.mainClock.advanceTimeBy(1000)
            val stoppedPower = pixels("connection-hero")
            compose.mainClock.advanceTimeBy(500)
            assertArrayEquals("Explicit reduced motion must keep the control still",
                stoppedPower, pixels("connection-hero"))
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test @SdkSuppress(minSdkVersion = 29)
    fun deviceAnimationSettingChangesAreObservedWithoutRestartingActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val original = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val observed = AtomicReference<GanjVisualEffectsPolicy?>()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        try {
            compose.activityRule.scenario.onActivity { activity -> activity.setContent {
                val policy = currentGanjVisualEffectsPolicy()
                SideEffect { observed.set(policy) }
                GanjTheme(visualEffectsPolicy = policy) { Text("تنظیم حرکت") }
            } }
            assertTrue(Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f))
            compose.waitUntil(10_000) { observed.get()?.reduceMotion == true }
            assertTrue(Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
            compose.waitUntil(10_000) { observed.get()?.reduceMotion == false }
        } finally {
            try { Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, original) }
            finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
        }
    }

    @SdkSuppress(minSdkVersion = 26)
    private fun pixels(tag: String): IntArray {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        return IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        check(UiDevice.getInstance(instrumentation).takeScreenshot(File(instrumentation.targetContext.cacheDir, "$name.png")))
    }
}
