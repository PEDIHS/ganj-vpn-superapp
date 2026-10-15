package com.ganj.vpn.ui

import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** Isolated native-commerce contract fixtures; never contacts a payment provider or real account. */
@RunWith(AndroidJUnit4::class)
class GanjNativeStoreTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val policy = GanjVisualEffectsPolicy(GanjEffectsTier.Reduced, true, true, false)
    private val product = JSONObject().put("id", "native-product").put("panel_id", "native-panel")
        .put("panel", "لوکیشن نمونه").put("name", "سرویس تست رابط بومی").put("category", "تک کاربر")
        .put("price", 240000).put("volume", 60).put("days", 30).put("description", "توضیح نمونه برای تست رابط")

    @Test fun catalogCheckoutTopUpAndCardInvoiceStayNativeAndUseExactBotProduct() {
        val api = FixtureApi(enough = false)
        render { StitchNativeBotStoreDestination({null}, {}, {}, suppliedApi = api) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("bot-product-native-panel:native-product").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("bot-synced-store").performScrollToNode(hasTestTag("bot-product-native-panel:native-product"))
        compose.onNodeWithTag("bot-product-native-panel:native-product").performClick()
        compose.onNodeWithTag("bot-buy-native-panel:native-product").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("native-purchase-quote").fetchSemanticsNodes().isNotEmpty() }
        capture("native-checkout-dark")
        compose.onNodeWithTag("native-purchase-topup").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("native-wallet-method-card").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("native-wallet-amount").assertTextContains("140000", substring = true)
        capture("native-wallet-dark")
        compose.onNodeWithTag("native-wallet-pay").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("native-receipt-select").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("native-receipt-select").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("native-receipt-upload").performScrollTo().assertIsNotEnabled()
        capture("native-card-invoice-dark")
        compose.runOnIdle {
            val quote = api.calls.first { it.first == "purchase_quote" }.second
            assertEquals("native-product", quote["product"]); assertEquals("native-panel", quote["panel"])
            assertEquals("140000", api.calls.first { it.first == "wallet_card" }.second["amount"])
            assertFalse(hasWebView(compose.activity.window.decorView))
            assertTrue(api.calls.none { it.first == "purchase" })
        }
    }

    @Test fun uncertainPurchaseRetriesSameIdempotencyAndNeverGrantsClientSuccess() {
        val api = FixtureApi(enough = true, firstPurchaseFails = true)
        render { StitchNativeBotCheckout(api, "native-product", "native-panel", {}, {}, {}, {}) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("native-purchase-quote").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("native-purchase-confirm").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("پیگیری همان خرید").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("native-purchase-services").assertDoesNotExist()
        compose.onNodeWithTag("native-purchase-discount").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("native-purchase-confirm").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("native-purchase-services").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle {
            val requests = api.calls.filter { it.first == "purchase" }.map { it.second }
            assertEquals(2, requests.size)
            assertEquals(requests[0], requests[1]); assertTrue(requests[0].getValue("idempotency_key").length >= 16)
        }
        capture("native-purchase-success-dark")
    }

    @Test fun editingDiscountInvalidatesQuoteAndLargeFontKeepsPurchaseReachable() {
        val api = FixtureApi(enough = true)
        render(2f) { StitchNativeBotCheckout(api, "native-product", "native-panel", {}, {}, {}, {}) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("native-purchase-quote").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("native-purchase-discount").performScrollTo().performTextInput("invalid")
        compose.onNodeWithTag("native-purchase-confirm").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertTrue(api.calls.none { it.first == "purchase" }); assertFalse(hasWebView(compose.activity.window.decorView)) }
        capture("native-checkout-large-light")
    }

    private fun render(fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.activityRule.scenario.onActivity { activity -> activity.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GanjTheme(darkTheme = fontScale == 1f, visualEffectsPolicy = policy) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        } }
    }
    private fun hasWebView(view: View): Boolean = view is WebView || (view is ViewGroup && (0 until view.childCount).any { hasWebView(view.getChildAt(it)) })
    private fun capture(name: String) {
        compose.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation(); i.waitForIdleSync()
        check(UiDevice.getInstance(i).takeScreenshot(File(i.targetContext.cacheDir, "$name.png")))
    }
    private inner class FixtureApi(val enough: Boolean, val firstPurchaseFails: Boolean = false) : GanjBotStoreApi {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        override suspend fun request(action: String, parameters: Map<String, String>): JSONObject {
            calls += action to parameters.toMap()
            delay(20)
            val data: Any = when (action) {
                "products" -> JSONArray().put(product)
                "product" -> product
                "purchase_quote" -> JSONObject().put("price",240000).put("balance",if(enough)300000 else 100000)
                    .put("saved",0).put("enough",enough).put("coupon_code",JSONObject.NULL)
                "purchase" -> {
                    if (firstPurchaseFails && calls.count { it.first == "purchase" } == 1) throw IOException("synthetic timeout")
                    JSONObject().put("invoice_id","NATIVE123456")
                }
                "wallet_methods" -> JSONObject().put("balance",100000).put("methods",JSONArray().put(
                    JSONObject().put("id","card").put("title","کارت‌به‌کارت").put("kind","receipt").put("min",1000).put("max",1000000)))
                "wallet_card" -> JSONObject().put("order_id","FIXTURE123456")
                "wallet_card_session" -> if (parameters["order"] == null) JSONObject().put("active",false) else
                    JSONObject().put("order_id","FIXTURE123456").put("status","unpaid").put("amount",140000)
                        .put("card_number","0000000000000000").put("card_holder","نمونه آزمایشی؛ غیرواقعی")
                "wallet_crypto_session" -> JSONObject().put("active",false)
                "payment_status" -> JSONObject().put("order_id","FIXTURE123456").put("status","unpaid").put("seconds_left",1200)
                else -> error("Unexpected fixture action: $action")
            }
            return JSONObject().put("status",true).put("data",data)
        }
        override suspend fun uploadReceipt(context: Context, uri: Uri, order: String, crypto: Boolean, txLink: String): JSONObject = error("No real receipt upload in fixture")
    }
}
