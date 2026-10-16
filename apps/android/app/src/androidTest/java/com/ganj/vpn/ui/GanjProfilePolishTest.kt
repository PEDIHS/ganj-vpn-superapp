package com.ganj.vpn.ui

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Base64
import java.io.ByteArrayOutputStream
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.ganj.vpn.MainActivity
import com.ganj.vpn.core.controlapi.CurrentAccount
import com.ganj.vpn.enterprise.EnterpriseUiState
import com.ganj.vpn.presentation.*
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** UI fixtures only. No test balance/account is reachable in the production application. */
@RunWith(AndroidJUnit4::class)
class GanjProfilePolishTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val account = CurrentAccount("10000000-0000-4000-8000-000000000001", "کاربر آزمایشی", "fa", true, "ganj_preview")
    private val service = ServiceUiModel("profile-preview", "اشتراک آزمایشی", ServiceUiStatus.ACTIVE, UiTier.PREMIUM,
        "DE", 60L * 1024 * 1024 * 1024, 18L * 1024 * 1024 * 1024, "2027-01-20T00:00:00Z", 3, setOf("vless"), "ganj_preview")
    private val current = GanjUiState(services = ContentState.Ready(listOf(service)), selectedEntitlementId = service.entitlementId)

    @Test fun actualBalanceRoutesAndScrollingDoesNotRefetch() {
        val api = WalletFixture()
        var wallet = 0; var history = 0; var family = 0; var settings = 0; var vip = 0
        render { padding -> Profile(api, padding, onWallet = { wallet++ }, onTransactions = { history++ },
            onFamily = { family++ }, onSettings = { settings++ }, onVip = { vip++ }) }
        compose.waitUntil(10000) { api.calls == 1 && compose.onAllNodesWithText(botMoney(100000)).fetchSemanticsNodes().isNotEmpty() }
        capture("profile-modern-dark")
        scroll("profile-premium").performClick()
        compose.runOnIdle { assertEquals(1, vip) }
        scroll("profile-to-wallet").performClick()
        scroll("profile-wallet-transactions").performClick()
        compose.runOnIdle { assertEquals(1, wallet); assertEquals(1, history) }
        scroll("profile-to-settings").performClick()
        capture("profile-modern-menu-dark")
        compose.onNodeWithTag("reference-profile").performScrollToNode(hasText("اعضا و ظرفیت اشتراک"))
        compose.onNodeWithText("اعضا و ظرفیت اشتراک").performClick()
        compose.onNodeWithTag("reference-profile").performScrollToIndex(0)
        compose.runOnIdle { assertEquals("Scrolling must not recreate the balance request", 1, api.calls); assertEquals(1, family); assertEquals(1, settings) }
        scroll("profile-wallet-refresh").performClick()
        compose.waitUntil(10000) { api.calls == 2 }
        scroll("profile-wallet-topup").performClick()
        compose.runOnIdle { assertEquals(2, wallet) }
    }

    @Test fun avatarFollowsLinkedAccountAndFallsBackWhenUnlinked() {
        val raw = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(AndroidColor.rgb(18, 135, 96))
        }
        val jpeg = ByteArrayOutputStream().also { raw.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        val encoded = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val linkedAccount = mutableStateOf(true)
        val avatarApi = object : GanjBotStoreApi {
            override suspend fun request(action: String, parameters: Map<String, String>): JSONObject {
                check(action == "profile_avatar")
                return JSONObject().put("data", JSONObject().put("mime", "image/jpeg").put("image_base64", encoded))
            }
            override suspend fun uploadReceipt(context: Context, uri: Uri, order: String, crypto: Boolean, txLink: String): JSONObject =
                error("No receipt upload in avatar test")
        }
        render { padding ->
            val photo = rememberGanjTelegramAvatar(account.takeIf { linkedAccount.value }, avatarApi)
            Column(Modifier.padding(padding).padding(12.dp)) {
                GanjProfileIdentity("حساب تلگرام", "@ganj_preview", linkedAccount.value, true,
                    onManage = {}, avatar = photo)
            }
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("profile-telegram-avatar").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("profile-telegram-avatar").assertExists()
        compose.runOnIdle { linkedAccount.value = false }
        compose.onNodeWithTag("profile-telegram-avatar").assertDoesNotExist()
        compose.onNodeWithTag("profile-identity-orbit").assertExists()
    }

    @Test fun largeLightProfileKeepsAmountAndActionLabelsComplete() {
        val api = WalletFixture()
        render(dark = false, scale = 2f) { padding -> Profile(api, padding) }
        scroll("profile-to-wallet")
        compose.waitUntil(10000) { compose.onAllNodesWithText(botMoney(100000)).fetchSemanticsNodes().isNotEmpty() }
        assertTextFits(botMoney(100000))
        capture("profile-modern-light-large")
        scroll("profile-wallet-topup")
        assertTextFits("افزایش موجودی")
        scroll("profile-wallet-transactions")
        assertTextFits("تراکنش‌ها")
        scroll("profile-to-settings")
        assertTextFits("ظاهر و دسترس‌پذیری")
        capture("profile-modern-menu-light-large")
    }

    @Test fun unknownStaleAndSwitchedAccountNeverBecomeAnInventedBalance() {
        val api = WalletFixture().apply { fail = true }
        val identity = mutableStateOf("first")
        render { padding ->
            val state = rememberGanjProfileWallet(api, identity.value)
            Column(Modifier.padding(padding).padding(16.dp)) {
                GanjProfileWalletCard(state.balance, state.busy, state.message, {}, {}, state::refresh)
            }
        }
        compose.onNodeWithText("موجودی در دسترس نیست").assertExists()
        compose.onNodeWithText(botMoney(0)).assertDoesNotExist()
        compose.runOnIdle { api.fail = false }
        compose.onNodeWithTag("profile-wallet-refresh").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText(botMoney(100000)).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { api.fail = true }
        compose.onNodeWithTag("profile-wallet-refresh").performClick()
        compose.onNodeWithText("آخرین موجودی دریافت‌شده").assertExists()
        compose.onNodeWithText(botMoney(100000)).assertExists()
        capture("profile-wallet-stale-dark")
        compose.runOnIdle { api.fail = false; api.gate = CompletableDeferred(); api.amount = 0; identity.value = "second" }
        compose.onNodeWithText(botMoney(100000)).assertDoesNotExist()
        compose.onNodeWithText("در حال دریافت موجودی…").assertExists()
        compose.runOnIdle { api.gate!!.complete(Unit) }
        compose.waitUntil(10000) { compose.onAllNodesWithText(botMoney(0)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun wavesMoveInLowMemoryAndReducedMotionIsStatic() {
        val stopped = mutableStateOf(false)
        val page = mutableStateOf("first")
        try {
            compose.mainClock.autoAdvance = false
            compose.activityRule.scenario.onActivity { activity -> activity.setContent {
                GanjTheme(darkTheme = true, visualEffectsPolicy = GanjVisualEffectsPolicy(GanjEffectsTier.Reduced, true, stopped.value, false)) {
                    Box(Modifier.size(300.dp, 180.dp).background(MaterialTheme.colorScheme.background).testTag("motion-capture")) {
                        Box(Modifier.padding(18.dp).then(profileRevealModifier(page.value))) {
                            GanjProfileIdentity("حساب آزمایشی", "ganj_preview", true, true, {})
                        }
                    }
                }
            } }
            compose.mainClock.advanceTimeBy(32)
            val first = pixels()
            compose.mainClock.advanceTimeBy(320)
            assertFalse("Finite profile reveal must visibly move in low-memory mode", first.contentEquals(pixels()))
            val wave = pixels()
            compose.mainClock.advanceTimeBy(600)
            assertFalse("Profile wave should move when motion is allowed", wave.contentEquals(pixels()))
            compose.runOnIdle { stopped.value = true; page.value = "second" }
            compose.mainClock.advanceTimeByFrame()
            val reduced = pixels()
            compose.mainClock.advanceTimeBy(500)
            assertArrayEquals("Reduce Motion must be immediately static", reduced, pixels())
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Composable private fun Profile(api: GanjBotStoreApi, padding: PaddingValues, onWallet: () -> Unit = {},
        onTransactions: () -> Unit = {}, onFamily: () -> Unit = {}, onSettings: () -> Unit = {}, onVip: () -> Unit = {}) {
        StitchProfileScreen(current, EnterpriseUiState(), {}, {}, {}, onSettings, {}, {}, {}, {}, {}, {}, {},
            modifier = Modifier.padding(padding), currentAccount = account, linked = true, botStoreApi = api,
            onOpenWallet = onWallet, onOpenTransactions = onTransactions, onOpenDevices = {}, onOpenFamily = onFamily,
            onOpenNotifications = {}, onOpenVip = onVip)
    }
    private fun render(dark: Boolean = true, scale: Float = 1f, content: @Composable (PaddingValues) -> Unit) {
        compose.activityRule.scenario.onActivity { activity -> activity.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                GanjTheme(darkTheme = dark, visualEffectsPolicy = GanjVisualEffectsPolicy(GanjEffectsTier.Reduced, true, true, false)) {
                    Scaffold(containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = { GanjLiquidBottomNavigation(GanjDestination.Account, {}) }, content = content)
                }
            }
        } }
    }
    private fun scroll(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("reference-profile").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).performScrollTo()
    }
    private fun assertTextFits(text: String) {
        val layout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        assertFalse("Text must be complete: $text", layout.single().hasVisualOverflow)
    }
    private fun pixels(): IntArray {
        val bitmap = compose.onNodeWithTag("motion-capture").captureToImage().asAndroidBitmap()
        return IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(UiDevice.getInstance(instrumentation).takeScreenshot(File(instrumentation.targetContext.cacheDir, "$name.png")))
    }
    private class WalletFixture : GanjBotStoreApi {
        var calls = 0
        var amount = 100000L
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun request(action: String, parameters: Map<String, String>): JSONObject {
            if (action == "profile_avatar") return JSONObject().put("data", JSONObject().put("image_base64", JSONObject.NULL))
            check(action == "wallet_methods"); calls++
            gate?.await()
            if (fail) throw java.io.IOException("fixture offline")
            return JSONObject().put("data", JSONObject().put("balance", amount))
        }
        override suspend fun uploadReceipt(context: Context, uri: Uri, order: String, crypto: Boolean, txLink: String): JSONObject = error("No payment in profile fixtures")
    }
}
