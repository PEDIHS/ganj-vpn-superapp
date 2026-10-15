package com.ganj.vpn.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Message
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val GANJ_WEB_ORIGIN = "https://bot.pedramhs.ir"
private const val GANJ_WEB_ROOT = "$GANJ_WEB_ORIGIN/site/"
private const val GANJ_WEB_PAYMENTS = "$GANJ_WEB_ROOT" + "api.php?action=payment_status&order="
internal const val GANJ_WEB_WALLET = GANJ_WEB_ROOT + "?page=wallet"
internal const val GANJ_WEB_TRANSACTIONS = GANJ_WEB_ROOT + "?page=transactions"

internal fun botFinanceIsTrusted(url: String): Boolean = runCatching {
    val uri = java.net.URI(url)
    uri.scheme == "https" && uri.host == "bot.pedramhs.ir" &&
        uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443) &&
        (uri.path == "/site" || uri.path?.startsWith("/site/") == true)
}.getOrDefault(false)

private fun botFinanceOrder(url: String): String? = runCatching {
    if (!botFinanceIsTrusted(url)) return@runCatching null
    val uri = Uri.parse(url)
    if (uri.getQueryParameter("page") !in setOf("payment", "crypto-payment")) return@runCatching null
    uri.getQueryParameter("order")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,64}")) }
}.getOrNull()

internal fun botFinanceStatusText(status: String): String = when (status.lowercase()) {
    "unpaid" -> "منتظر واریز و ارسال رسید"
    "waiting", "waiting_review" -> "رسید ارسال شد؛ در انتظار بررسی"
    "processing_smart", "processing" -> "در حال بررسی هوشمند رسید"
    "manual_review" -> "نیازمند تأیید مدیر گنج"
    "paid" -> "پرداخت تأیید شد و کیف پول شارژ شده است"
    "reject", "rejected" -> "رسید پرداخت تأیید نشد"
    "expire", "expired" -> "مهلت این فاکتور به پایان رسید"
    else -> "آخرین وضعیت فاکتور در حال بررسی است"
}

private fun showFinancePopup(status: String): Boolean =
    status.lowercase() in setOf("waiting", "waiting_review", "manual_review", "paid", "reject", "rejected", "expire", "expired")

/** Uses the same HttpOnly web session cookie; no separate payment/account authority. */
private suspend fun fetchBotPaymentStatus(orderId: String): String? = withContext(Dispatchers.IO) {
    val cookie = CookieManager.getInstance().getCookie(GANJ_WEB_ROOT) ?: return@withContext null
    if (cookie.isBlank()) return@withContext null
    val conn = (URL(GANJ_WEB_PAYMENTS + orderId).openConnection() as HttpsURLConnection).apply {
        requestMethod = "GET"
        instanceFollowRedirects = false
        connectTimeout = 6000
        readTimeout = 7000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Cookie", cookie)
        setRequestProperty("Cache-Control", "no-store")
    }
    try {
        if (conn.responseCode != 200) return@withContext null
        val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(16_384) }
        val response = JSONObject(raw)
        if (!response.optBoolean("status")) return@withContext null
        val data = response.optJSONObject("data") ?: return@withContext null
        if (data.optString("order_id") != orderId) return@withContext null
        data.optString("status").takeIf { it.isNotBlank() }
    } finally {
        conn.disconnect()
    }
}

/**
 * Financial operations run on the canonical GANJ website inside an Android surface.
 * Purchases, card invoice creation, image/PDF receipts, smart/manual approval,
 * wallet balance and transactions all remain bot-owned; no duplicate ledger.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun StitchBotFinanceScreen(
    startUrl: String,
    title: String,
    onClose: () -> Unit,
    onTransactions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var pageFailed by remember { mutableStateOf(false) }
    var order by remember(startUrl) { mutableStateOf<String?>(botFinanceOrder(startUrl)) }
    var invoiceStatus by remember(startUrl) { mutableStateOf<String?>(null) }
    var statusPopup by remember(startUrl) { mutableStateOf<String?>(null) }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
        fileCallback?.onReceiveValue(selected?.let { arrayOf(it) })
        fileCallback = null
    }

    fun openExternal(url: String) {
        val uri = Uri.parse(url)
        if (uri.scheme !in setOf("https", "tg", "mailto")) return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
            pageFailed = true
        }
    }

    BackHandler(enabled = true) {
        val view = web
        if (view != null && view.canGoBack()) view.goBack() else onClose()
    }

    LaunchedEffect(order) {
        val target = order ?: return@LaunchedEffect
        var previous: String? = null
        while (isActive) {
            try {
                val next = fetchBotPaymentStatus(target)
                if (next != null) {
                    invoiceStatus = next
                    if (next != previous && showFinancePopup(next)) {
                        statusPopup = next
                    }
                    previous = next
                    if (next.lowercase() in setOf("paid", "reject", "rejected", "expire", "expired")) break
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // This observer cannot mutate the payment; the website retains source of truth.
            }
            delay(8_000L)
        }
    }

    Column(modifier.fillMaxSize().testTag("bot-finance-screen"),
        verticalArrangement = Arrangement.spacedBy(0.dp)) {
        GanjGlassSurface(
            role = GanjGlassRole.Dense,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 16.dp,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("پرداخت و موجودی مشترک با ربات گنج",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onClose, modifier = Modifier.testTag("bot-finance-close")) {
                    Text("بازگشت")
                }
            }
            if (invoiceStatus != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(botFinanceStatusText(invoiceStatus!!),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (invoiceStatus in setOf("reject", "rejected", "expire", "expired"))
                            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    TextButton(onClick = onTransactions) { Text("تراکنش‌ها") }
                }
            }
        }
        if (progress in 1..99) LinearProgressIndicator(
            progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        Box(Modifier.weight(1f)) {
            if (!botFinanceIsTrusted(startUrl)) {
                Text("آدرس فروشگاه گنج معتبر نیست.", modifier = Modifier.padding(18.dp),
                    color = MaterialTheme.colorScheme.error)
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize().testTag("bot-finance-webview"),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = true
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.setSupportMultipleWindows(true)
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                settings.safeBrowsingEnabled = true
                            }
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    if (!request.isForMainFrame) return false
                                    val destination = request.url.toString()
                                    if (botFinanceIsTrusted(destination)) return false
                                    openExternal(destination)
                                    return true
                                }
                                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                    pageFailed = false
                                    botFinanceOrder(url)?.let { order = it }
                                }
                                override fun onReceivedError(
                                    view: WebView, request: WebResourceRequest,
                                    error: android.webkit.WebResourceError,
                                ) {
                                    if (request.isForMainFrame) pageFailed = true
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) {
                                    progress = newProgress.coerceIn(0, 100)
                                }
                                override fun onShowFileChooser(
                                    view: WebView,
                                    callback: ValueCallback<Array<Uri>>,
                                    parameters: FileChooserParams,
                                ): Boolean {
                                    fileCallback?.onReceiveValue(null)
                                    fileCallback = callback
                                    openDocument.launch(arrayOf("image/jpeg", "image/png", "image/webp", "application/pdf"))
                                    return true
                                }
                                override fun onPermissionRequest(request: PermissionRequest) {
                                    request.deny()
                                }
                                override fun onGeolocationPermissionsShowPrompt(
                                    origin: String?, callback: GeolocationPermissions.Callback?,
                                ) { callback?.invoke(origin, false, false) }
                                override fun onCreateWindow(
                                    view: WebView, dialog: Boolean, userGesture: Boolean, resultMsg: Message,
                                ): Boolean {
                                    if (!userGesture) return false
                                    val popup = WebView(ctx)
                                    popup.webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView, request: WebResourceRequest,
                                        ): Boolean {
                                            if (request.isForMainFrame) {
                                                if (request.url.toString() == "about:blank") return false
                                                openExternal(request.url.toString())
                                                view.post { view.destroy() }
                                            }
                                            return true
                                        }
                                    }
                                    (resultMsg.obj as WebView.WebViewTransport).webView = popup
                                    resultMsg.sendToTarget()
                                    return true
                                }
                            }
                            loadUrl(startUrl)
                            web = this
                        }
                    },
                    onRelease = { view ->
                        fileCallback?.onReceiveValue(null)
                        fileCallback = null
                        CookieManager.getInstance().flush()
                        view.stopLoading()
                        view.loadUrl("about:blank")
                        view.destroy()
                        web = null
                    },
                )
            }
            if (pageFailed) {
                Column(Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("بارگذاری صفحه پرداخت انجام نشد",
                        color = MaterialTheme.colorScheme.error)
                    Button(onClick = { pageFailed = false; web?.reload() }) {
                        Text("تلاش دوباره")
                    }
                }
            }
        }
    }

    statusPopup?.let { status ->
        AlertDialog(
            onDismissRequest = { statusPopup = null },
            title = { Text("وضعیت پرداخت گنج") },
            text = { Text(botFinanceStatusText(status)) },
            confirmButton = {
                TextButton(onClick = { statusPopup = null; onTransactions() }) {
                    Text("مشاهده تراکنش‌ها")
                }
            },
            dismissButton = {
                TextButton(onClick = { statusPopup = null }) { Text("متوجه شدم") }
            },
        )
    }
}
