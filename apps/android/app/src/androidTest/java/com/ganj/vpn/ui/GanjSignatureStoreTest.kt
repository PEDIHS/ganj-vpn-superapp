package com.ganj.vpn.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class GanjSignatureStoreTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val plans = listOf(
        product("vip-30-1", 30, 1, 390000), product("vip-90-1", 90, 1, 1170000), product("vip-90-3", 90, 3, 1500000),
        GanjBotProduct("original", "retail", "آلمان", "عادی", "محصول اصلی", "", 100000, 30.0, 30, "", false, false),
    )
    @Test fun tierChangesResolveTheExactExistingPriceAndPurchaseUrl() {
        var opened: String? = null
        compose.activityRule.scenario.onActivity { activity -> activity.setContent {
            GanjTheme(darkTheme = true, visualEffectsPolicy = GanjVisualEffectsPolicy(GanjEffectsTier.Reduced, true, true, false)) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    StitchBotSyncedStoreScreen({ opened = it }, {}, {}, loadCatalog = { plans })
                }
            }
        } }
        scroll("vip-upgrade-entry").performClick()
        compose.onNodeWithTag("store-vip-hero").assertExists()
        capture("signature-vip-dark")
        scroll("bot-product-tier:vip-30-1").performClick()
        scroll("tier-period-90").performClick()
        scroll("tier-devices-3").performClick()
        scroll("bot-buy-tier:vip-90-3").performClick()
        compose.runOnIdle { assertEquals(plans[2].purchaseUrl, opened) }
        compose.onNodeWithText(botMoney(1500000)).assertExists()
        capture("signature-tier-selection-dark")
    }
    @Test fun vipEntryAndMixedCatalogKeepRealProductsReachable() {
        var handled = 0; var opened: String? = null
        val vipRequest = mutableStateOf(true)
        compose.activityRule.scenario.onActivity { activity -> activity.setContent {
            GanjTheme(darkTheme = true, visualEffectsPolicy = GanjVisualEffectsPolicy(GanjEffectsTier.Reduced, true, true, false)) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    StitchBotSyncedStoreScreen({ opened = it }, {}, {}, loadCatalog = { plans },
                        vipEntryRequested = vipRequest.value, onVipEntryHandled = { handled++; vipRequest.value = false })
                }
            }
        } }
        compose.onNodeWithTag("store-vip-hero").assertExists()
        compose.runOnIdle { assertEquals(1, handled) }
        compose.onNodeWithContentDescription("بازگشت به صفحه قبل").performClick()
        scroll("bot-product-retail:original").performClick()
        scroll("bot-buy-retail:original").performClick()
        compose.runOnIdle { assertEquals(plans.last().purchaseUrl, opened) }
        capture("signature-store-dark")
    }
    private fun scroll(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("bot-synced-store").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).performScrollTo()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation()
        check(UiDevice.getInstance(i).takeScreenshot(File(i.targetContext.cacheDir, "$name.png")))
    }
    private fun product(code: String, days: Int, devices: Int, price: Long) =
        GanjBotProduct(code, "tier", "گنج VIP", "پریمیوم", "پلن نمونه", "", price, if (days == 30) 100.0 else 300.0,
            days, "VIP", false, false, devices, "premium", "پریمیوم")
}
