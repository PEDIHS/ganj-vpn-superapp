package com.ganj.vpn.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.ganj.vpn.MainActivity
import com.ganj.vpn.core.controlapi.*
import com.ganj.vpn.presentation.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Synthetic, isolated emulator fixtures. Never used for production account telemetry. */
@RunWith(AndroidJUnit4::class)
class SubscriptionSelectionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val first = service("service-a", "sample.alpha", 25L * 1_073_741_824)
    private val second = service("service-b", "sample.beta", 40L * 1_073_741_824)
    private val configs = listOf(server("config-one", "کانفیگ اول"), server("config-two", "کانفیگ دوم"))
    private val reducer = GanjUiReducer()
    private val reduced = GanjVisualEffectsPolicy(GanjEffectsTier.Reduced, true, true, false)

    @Test fun subscriptionListsEveryConfigInlineWithoutPickerAndSelectsSecond() {
        val requested = mutableListOf<String>()
        var connectedChoice: SelectedConnectionServer? = null
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                var state by remember { mutableStateOf(GanjUiState(services = ContentState.Ready(listOf(first, second)))) }
                var subscriptions by remember { mutableStateOf(false) }
                GanjTheme(darkTheme = true, visualEffectsPolicy = reduced) {
                    Surface(Modifier.fillMaxSize()) {
                        if (subscriptions) StitchSubscriptionsScreen(state,
                            onSelectService = {
                                state = reducer.reduce(state, GanjUiEvent.SelectService(it))
                                subscriptions = false // No automatic configuration sheet.
                            }, onRetry = {})
                        else StitchConnectionScreen(state,
                            onConnect = { connectedChoice = state.selectedConnectionServer },
                            onDisconnect = { error("Selecting another config must not disconnect an old connection") },
                            onOpenServers = { subscriptions = true },
                            onOpenSubscriptions = { subscriptions = true },
                            loadServers = { requested += it; ApiResult.Success(configs, ResponseMetadata("ui-fixture", null)) },
                            onSelectAndConnect = { _, _ -> },
                            onSelectServer = { serviceId, server ->
                                state = reducer.reduce(state, GanjUiEvent.SelectServer(serviceId, server))
                                state = state.copy(
                                    connection = ConnectionUiState.Connected(first.entitlementId, "fixture-profile", configs[0].id),
                                    runtimeConnection = com.ganj.vpn.core.vpn.ConnectionState(
                                        com.ganj.vpn.core.vpn.ConnectionPhase.CONNECTED,
                                        serverId = configs[0].id, serviceId = first.entitlementId)
                                )
                            },
                            onOpenStore = {}, onRetry = {})
                    }
                }
            }
        }
        compose.onNodeWithTag("selected-subscription").performScrollTo().performClick()
        compose.onNodeWithTag("subscriptions-screen").assertExists()
        compose.onNodeWithTag("subscription-service-b").performScrollTo().performClick()
        compose.onNodeWithTag("connection-screen").assertExists()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("config-option-config-two").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("config-picker").assertDoesNotExist()
        compose.onNodeWithTag("inline-config-list").performScrollToNode(hasTestTag("config-option-config-two"))
        compose.onNodeWithTag("config-option-config-two").assertExists()
        capture("config-inline-dark")
        compose.onNodeWithTag("config-option-config-two").performClick()
        compose.onNodeWithTag("config-option-config-two").assertIsSelected()
        compose.onNodeWithTag("connection-screen").assertExists()
        compose.onNodeWithTag("config-picker").assertDoesNotExist()
        compose.onNodeWithTag("connect-action").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(second.entitlementId), requested)
            assertEquals(SelectedConnectionServer(second.entitlementId, configs[1]), connectedChoice)
        }
        capture("connection-selected")
    }

    @Test fun lightLargeTextShowsRealQuotaAndUnknownUsageWithoutFabricatedPercent() {
        val unknown = second.copy(trafficUsageAvailable = false)
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                    GanjTheme(darkTheme = false, visualEffectsPolicy = reduced) {
                        Surface(Modifier.fillMaxSize()) {
                            StitchSubscriptionsScreen(GanjUiState(services = ContentState.Ready(listOf(first, unknown))), {}, {})
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("subscriptions-screen").performScrollToNode(hasTestTag("subscription-service-a"))
        compose.onNodeWithTag("subscription-service-a").assertTextContains("۲۵٪", substring = true)
        capture("subscriptions-light-large")
        compose.onNodeWithTag("subscriptions-screen").performScrollToNode(hasTestTag("subscription-service-b"))
        compose.onNodeWithTag("subscription-service-b")
            .assertTextContains("مصرف در دسترس نیست", substring = true)
        compose.onNodeWithTag("subscription-service-b").assertTextContains("۱۰۰ گیگابایت", substring = true)
        capture("subscriptions-unknown-large")
    }

    @Test fun lightConfigPickerPreservesSelectionAtLargeFontScale() {
        val longConfig = configs[1].copy(name = "🇩🇪 کانفیگ آلمان با عنوان فارسی بلند")
        var chosen: ConnectionServer? = null
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    GanjTheme(darkTheme = false, visualEffectsPolicy = reduced) {
                        Surface(Modifier.fillMaxSize()) {
                            StitchConfigSelectionSheet(second, longConfig,
                                loadServers = { ApiResult.Success(listOf(configs[0], longConfig), ResponseMetadata("ui-fixture", null)) },
                                latency = null, onSelect = { chosen = it }, onDismiss = {})
                        }
                    }
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("config-option-config-one").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("config-search").assertIsNotFocused()
        compose.onNodeWithTag("config-picker").performScrollToNode(hasTestTag("config-option-config-two"))
        compose.onNodeWithTag("config-option-config-two").performScrollTo().assertIsSelected()
        capture("config-picker-light-large")
        compose.onNodeWithTag("config-picker").performScrollToNode(hasTestTag("config-option-config-two"))
        compose.onNodeWithTag("config-option-config-two").performClick()
        compose.runOnIdle { assertEquals(longConfig, chosen) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val device = UiDevice.getInstance(instrumentation)
        device.waitForIdle(1_000)
        check(device.takeScreenshot(File(instrumentation.targetContext.cacheDir, "$name.png")))
    }

    private fun service(id: String, username: String, used: Long) = ServiceUiModel(
        entitlementId = id, displayName = "Plan title should not replace username", status = ServiceUiStatus.ACTIVE,
        tier = UiTier.PREMIUM, countryCode = null, trafficLimitBytes = 100L * 1_073_741_824,
        trafficUsedBytes = used, expiresAt = null, deviceLimit = 2, allowedProtocols = setOf("VLESS"), username = username)

    private fun server(id: String, name: String) = ConnectionServer(id, id, name, "DE", null,
        SubscriptionTier.PREMIUM, setOf(VpnProtocol.VLESS))
}
