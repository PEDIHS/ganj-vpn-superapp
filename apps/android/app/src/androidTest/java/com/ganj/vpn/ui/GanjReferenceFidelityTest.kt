package com.ganj.vpn.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.ganj.vpn.MainActivity
import com.ganj.vpn.enterprise.EnterpriseUiState
import com.ganj.vpn.presentation.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Rendering fixtures only: real product data/runtime are never replaced in production. */
@RunWith(AndroidJUnit4::class)
class GanjReferenceFidelityTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val policy=GanjVisualEffectsPolicy(GanjEffectsTier.Reduced,true,true,false)
    private val plans=listOf(
        GanjBotProduct("reference-premium","reference","نمونه","پریمیوم","گنج پریمیوم","",240000,60.0,30,"",false,false),
        GanjBotProduct("reference-vip","reference","نمونه","VIP","گنج VIP","",390000,100.0,30,"VIP",true,false),
    )
    @Test fun storeSelectionUsesExactProductAndVipIsDerivedFromCatalog() {
        var opened:String?=null
        compose.activityRule.scenario.onActivity { activity->activity.setContent {
            GanjTheme(darkTheme=true,visualEffectsPolicy=policy) {
                Scaffold(containerColor=MaterialTheme.colorScheme.background,bottomBar={GanjLiquidBottomNavigation(GanjDestination.Store,{})}) { padding->
                    StitchBotSyncedStoreScreen({opened=it},{},{},Modifier.padding(padding),loadCatalog={plans})
                }
            }
        } }
        capture("reference-store-top-dark")
        compose.onNodeWithTag("bot-product-reference:reference-premium").performScrollTo().performClick()
        compose.onNodeWithTag("bot-buy-reference:reference-premium").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(plans[0].purchaseUrl,opened) }
        capture("reference-store-dark")
        compose.onNodeWithTag("vip-upgrade-entry").performScrollTo().performClick()
        capture("reference-vip-top-dark")
        compose.onNodeWithTag("bot-product-reference:reference-vip").performScrollTo().performClick()
        compose.onNodeWithTag("bot-buy-reference:reference-vip").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(plans[1].purchaseUrl,opened) }
        capture("reference-vip-dark")
    }
    @Test fun profilePreservesRealCallbacksAndOpensAccountAndDiagnosticsAsSubpages() {
        var settings=0;var wallet=0;var subscriptions=0
        compose.activityRule.scenario.onActivity { activity->activity.setContent {
            GanjTheme(darkTheme=true,visualEffectsPolicy=policy) {
                Scaffold(containerColor=MaterialTheme.colorScheme.background,bottomBar={GanjLiquidBottomNavigation(GanjDestination.Account,{})}) { padding->
                    StitchProfileScreen(state=GanjUiState(),enterpriseState=EnterpriseUiState(),
                        onSelectService={},onConnect={},onBuy={},onOpenSettings={settings++},onOpenSubscriptions={subscriptions++},
                        onRetry={},onEnterpriseRefresh={},onSubmitBug={},onSubmitDiagnostics={},onClearBug={},onClearDiagnostic={},
                        modifier=Modifier.padding(padding),linked=true,onOpenWallet={wallet++},headerContent={
                            GanjReferenceMenuRow("حساب متصل",onClick={})
                        })
                }
            }
        } }
        capture("reference-profile-dark")
        compose.onNodeWithText("کیف پول").performScrollTo().performClick()
        compose.onNodeWithTag("profile-to-subscriptions").performScrollTo().performClick()
        compose.onNodeWithTag("profile-to-settings").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1,wallet);assertEquals(1,subscriptions);assertEquals(1,settings) }
        compose.onNodeWithText("مدیریت حساب").performScrollTo().performClick()
        compose.onNodeWithText("حساب متصل").assertExists()
        capture("reference-account-dark")
    }
    @Test fun circularControlKeepsReferenceGeometryAndFailureStatusInsideAction() {
        val state=mutableStateOf(GanjConnectionVisualState.Disconnected)
        compose.activityRule.scenario.onActivity { activity->activity.setContent {
            GanjTheme(darkTheme=true,visualEffectsPolicy=policy) {
                Scaffold(containerColor=MaterialTheme.colorScheme.background,bottomBar={GanjLiquidBottomNavigation(GanjDestination.Connect,{})}) { padding->
                    Column(Modifier.padding(padding).padding(20.dp)) {
                        GanjConnectionHero(state.value,"اتصال","در حال اتصال",false,false,{})
                    }
                }
            }
        } }
        val bounds=compose.onNodeWithTag("connect-action").fetchSemanticsNode().boundsInRoot
        assertEquals(bounds.width,bounds.height,.5f)
        assertTrue(bounds.width/compose.activity.resources.displayMetrics.density>=200f)
        compose.onNodeWithText("برای اتصال کلیک کنید",useUnmergedTree=true).assert(hasAnyAncestor(hasTestTag("connect-action")))
        capture("reference-connect-off-dark")
        compose.runOnIdle{state.value=GanjConnectionVisualState.Failed}
        compose.onNodeWithText("اتصال برقرار نشد",useUnmergedTree=true).assert(hasAnyAncestor(hasTestTag("connect-action")))
        capture("reference-connect-error-dark")
    }
    private fun capture(name:String) {
        compose.waitForIdle()
        val i=InstrumentationRegistry.getInstrumentation();i.waitForIdleSync()
        check(UiDevice.getInstance(i).takeScreenshot(File(i.targetContext.cacheDir,"$name.png")))
    }
}
