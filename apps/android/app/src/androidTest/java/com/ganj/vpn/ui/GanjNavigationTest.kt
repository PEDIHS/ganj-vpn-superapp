package com.ganj.vpn.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.ganj.vpn.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GanjNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val mounted = mutableSetOf<GanjDestination>()

    @Test fun interruptedTabAnimationMountsOnlyLatestScreenAndRestoresSavedInput() {
        show(dark = true, large = false, motion = true)
        compose.onNodeWithTag("edit-state").performClick()
        compose.mainClock.autoAdvance = false
        listOf(GanjDestination.Store, GanjDestination.Account, GanjDestination.Servers,
            GanjDestination.Connect, GanjDestination.Home).forEach { destination ->
            compose.onNodeWithTag("nav-${destination.name}").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("screen-${destination.name}").assertExists()
            compose.runOnIdle { assertEquals(setOf(destination), mounted) }
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("saved-1").assertExists()
        compose.onNodeWithTag("nav-Store").performClick()
        compose.onNodeWithTag("loading-state").assertExists()
        compose.onNodeWithTag("nav-Account").performClick()
        compose.onNodeWithTag("screen-Account").assertExists()
        capture("navigation-dark")
    }

    @Test fun largeTextRtlNavigationRemainsClickableWhileLoading() {
        show(dark = false, large = true, motion = false)
        compose.onNodeWithTag("nav-Store").performClick()
        compose.onNodeWithTag("loading-state").assertExists()
        GanjDestination.entries.forEach { destination ->
            val bounds = compose.onNodeWithTag("nav-${destination.name}").fetchSemanticsNode().boundsInRoot
            compose.runOnIdle {
                val density = compose.activity.resources.displayMetrics.density
                assertTrue("48dp tab width", bounds.width / density >= 48f)
                assertTrue("48dp tab height", bounds.height / density >= 48f)
            }
        }
        capture("navigation-light-loading-large")
        compose.onNodeWithTag("nav-Connect").performClick()
        compose.onNodeWithTag("screen-Connect").assertExists()
        compose.onNodeWithTag("loading-state").assertDoesNotExist()
    }

    private fun show(dark: Boolean, large: Boolean, motion: Boolean) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 2f else 1f),
                    LocalLayoutDirection provides LayoutDirection.Rtl) {
                    GanjTheme(darkTheme = dark, visualEffectsPolicy = GanjVisualEffectsPolicy(
                        if (motion) GanjEffectsTier.Full else GanjEffectsTier.Reduced, false, !motion, false)) {
                        var destination by remember { mutableStateOf(GanjDestination.Home) }
                        Scaffold(bottomBar = { GanjLiquidBottomNavigation(destination, { destination = it }) }) { padding ->
                            GanjDestinationTransition(destination, Modifier.padding(padding)) { current ->
                                DisposableEffect(current) {
                                    mounted.add(current)
                                    onDispose { mounted.remove(current) }
                                }
                                Column(Modifier.fillMaxSize().testTag("screen-${current.name}").padding(16.dp)) {
                                    var saved by rememberSaveable { mutableIntStateOf(0) }
                                    Text("saved-$saved")
                                    Button(onClick = { saved++ }, Modifier.testTag("edit-state")) { Text("Edit") }
                                    if (current == GanjDestination.Store) LoadingCard("در حال دریافت پلن‌ها")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        check(UiDevice.getInstance(instrumentation).takeScreenshot(File(instrumentation.targetContext.cacheDir, "$name.png")))
    }
}
