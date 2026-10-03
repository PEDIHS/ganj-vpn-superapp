package com.ganj.vpn.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.ganj.vpn.MainActivity
import com.ganj.vpn.core.controlapi.*
import com.ganj.vpn.core.vpn.ConnectionPhase
import com.ganj.vpn.core.vpn.ConnectionState
import com.ganj.vpn.presentation.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Isolated emulator fixtures, never production connection/account telemetry. */
@RunWith(AndroidJUnit4::class)
class ConnectionPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val service = ServiceUiModel("fixture-service", "fixture", ServiceUiStatus.ACTIVE,
        UiTier.PREMIUM, null, 100L * 1_073_741_824, 25L * 1_073_741_824, null, 2,
        setOf("VLESS"), username = "sample.alpha")
    private val server = ConnectionServer("fixture-config", "fixture-config", "کانفیگ آلمان", "DE", null,
        SubscriptionTier.PREMIUM, setOf(VpnProtocol.VLESS))
    private val reduced = GanjVisualEffectsPolicy(GanjEffectsTier.Reduced, true, true, false)
    private fun state(connection: ConnectionUiState = ConnectionUiState.Idle, runtime: ConnectionState = ConnectionState()) =
        GanjUiState(services = ContentState.Ready(listOf(service)), selectedEntitlementId = service.entitlementId,
            selectedConnectionServer = SelectedConnectionServer(service.entitlementId, server),
            connection = connection, runtimeConnection = runtime)

    @Test fun lightOffControlDispatchesChosenSubscriptionAndBusyControlRejectsDuplicateTap() {
        val requests = mutableListOf<String>()
        compose.activityRule.scenario.onActivity { activity -> activity.setContent {
            var current by remember { mutableStateOf(state()) }
            GanjTheme(darkTheme = false) { Surface(Modifier.fillMaxSize()) {
                screen(current, connect = {
                    requests += it
                    current = current.copy(runtimeConnection = ConnectionState(ConnectionPhase.CONNECTING),
                        connection = ConnectionUiState.Requesting(it))
                })
            } }
        } }
        compose.onNodeWithTag("connect-action").performScrollTo().assertIsEnabled().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("connection-duration").assertTextEquals("—")
        capture("connection-off-light")
        compose.onNodeWithTag("connect-action").performClick().assertIsNotEnabled()
        compose.onNodeWithTag("connect-action").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(service.entitlementId), requests) }
        capture("connection-busy-light")
    }

    @Test fun connectedClockSurvivesUnmountAndDisconnectUsesActualHandler() {
        var now = 50_000L
        val timer = ConnectionSessionTimer({ 10_000_000L }, { now })
        val runtime = ConnectionState(ConnectionPhase.CONNECTED, serverId = server.id,
            connectedAtEpochMillis = 6_275_000L, serviceId = service.entitlementId)
        val current = state(ConnectionUiState.Connected(service.entitlementId, "fixture-profile", server.id), runtime)
        var disconnects = 0
        compose.activityRule.scenario.onActivity { activity -> activity.setContent {
            var visible by remember { mutableStateOf(true) }
            GanjTheme(darkTheme = true, visualEffectsPolicy = reduced) { Surface(Modifier.fillMaxSize()) {
                Column {
                    TextButton(onClick = { visible = !visible }, modifier = Modifier.testTag("fixture-toggle")) { Text("تغییر صفحه") }
                    if (visible) screen(current, timer = timer, disconnect = { disconnects++ })
                }
            } }
        } }
        compose.onNodeWithTag("connection-duration").performScrollTo()
            .assertTextEquals(isolateTechnicalLtr("۰۱:۰۲:۰۵"))
        capture("connection-on-dark")
        compose.onNodeWithTag("fixture-toggle").performClick()
        compose.runOnIdle { now += 5_000 }
        compose.onNodeWithTag("fixture-toggle").performClick()
        compose.onNodeWithTag("connection-duration").performScrollTo()
            .assertTextEquals(isolateTechnicalLtr("۰۱:۰۲:۱۰"))
        compose.onNodeWithTag("connect-action").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, disconnects) }
    }

    @Test fun twoHundredPercentFontKeepsConfigFlagWideAndUnknownClockTruthful() {
        val current = state(ConnectionUiState.Connected(service.entitlementId, "fixture-profile", server.id),
            ConnectionState(ConnectionPhase.CONNECTED, serverId = server.id))
        compose.activityRule.scenario.onActivity { activity -> activity.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                GanjTheme(darkTheme = false, visualEffectsPolicy = reduced) { Surface(Modifier.fillMaxSize()) { screen(current) } }
            }
        } }
        compose.onNodeWithTag("connect-action").performScrollTo().assertIsEnabled().assertHeightIsAtLeast(48.dp)
        capture("connection-on-light-large")
        compose.onNodeWithTag("connection-duration").performScrollTo().assertTextEquals("—")
        compose.onNodeWithText("زمان شروع در دسترس نیست").assertExists()
        compose.onNodeWithTag("selected-config").performScrollTo().assertTextContains("کانفیگ آلمان", substring = true)
        compose.onNodeWithTag("selected-config-flag", useUnmergedTree = true).assertWidthIsEqualTo(76.dp).assertHeightIsEqualTo(50.dp)
        capture("connection-config-light-large")
    }

    @Composable private fun screen(current: GanjUiState, timer: ConnectionSessionTimer? = null,
        connect: (String) -> Unit = {}, disconnect: () -> Unit = {}) {
        StitchConnectionScreen(current, connect, disconnect, {}, {},
            loadServers = { ApiResult.Success(listOf(server), ResponseMetadata("ui-fixture", null)) },
            onSelectAndConnect = { _, _ -> }, onOpenStore = {}, onRetry = {}, sessionTimer = timer)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val device = UiDevice.getInstance(instrumentation)
        device.waitForIdle(1_000)
        check(device.takeScreenshot(File(instrumentation.targetContext.cacheDir, "$name.png")))
    }
}
