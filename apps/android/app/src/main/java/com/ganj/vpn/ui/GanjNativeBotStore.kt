package com.ganj.vpn.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

internal fun botMoney(value: Long): String = String.format(Locale.US, "%,d", value).toPersianDigits() + " تومان"

/** Same web storefront hierarchy, rendered entirely by Compose. No AndroidView/WebView here. */
@Composable
internal fun StitchNativeBotStoreDestination(
    onStoreBridgeAuthorization: () -> String?,
    onOpenSubscriptions: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
    suppliedApi: GanjBotStoreApi? = null,
    allowGuestCatalogCache: Boolean = false,
    vipEntryRequested: Boolean = false,
    onVipEntryHandled: () -> Unit = {},
) {
    val api = remember(suppliedApi, onStoreBridgeAuthorization) { suppliedApi ?: GanjBotNativeStoreApi(onStoreBridgeAuthorization) }
    var page by rememberSaveable { mutableStateOf("catalog") }
    LaunchedEffect(vipEntryRequested) { if (vipEntryRequested) page = "catalog" }
    var productCode by rememberSaveable { mutableStateOf("") }
    var panelCode by rememberSaveable { mutableStateOf("") }
    var shortfall by rememberSaveable { mutableLongStateOf(0L) }
    var walletReturn by rememberSaveable { mutableStateOf("catalog") }
    var order by rememberSaveable { mutableStateOf("") }
    var crypto by rememberSaveable { mutableStateOf(false) }
    var paymentReturn by rememberSaveable { mutableStateOf("wallet") }
    fun back() { page = if (page == "payment") "wallet" else "catalog" }
    BackHandler(page != "catalog") { back() }
    val effects = LocalGanjVisualEffectsPolicy.current
    val reveal = remember(page) { Animatable(if (effects.reduceMotion) 1f else 0f) }
    LaunchedEffect(page, effects.reduceMotion) {
        if (effects.reduceMotion) reveal.snapTo(1f) else reveal.animateTo(1f, tween(200))
    }
    Box(modifier.fillMaxSize().graphicsLayer { alpha = reveal.value; translationY = (1f-reveal.value)*8.dp.toPx() }) {
        when (page) {
            "product" -> StitchNativeBotCheckout(api, productCode, panelCode,
                onBack = { page = "catalog" }, onAccount = onOpenAccount,
                onTopUp = { needed -> shortfall = needed; walletReturn = "product"; page = "wallet" },
                onOpenSubscriptions = onOpenSubscriptions)
            "wallet" -> StitchNativeBotWallet(api, shortfall, onBack = { page = walletReturn },
                onPayment = { id, isCrypto -> order = id; crypto = isCrypto; paymentReturn = "wallet"; page = "payment" }, onAccount = onOpenAccount)
            "payment" -> StitchNativeBotPayment(api, order, crypto,
                onBack = { page = paymentReturn }, onContinue = { page = if (paymentReturn == "transactions") "transactions" else walletReturn })
            "transactions" -> StitchNativeBotTransactions(api, onBack = { page = "catalog" }, onAccount = onOpenAccount,
                onPayment = { id, isCrypto -> order = id; crypto = isCrypto; paymentReturn = "transactions"; page = "payment" })
            else -> StitchBotSyncedStoreScreen(
                onOpenProduct = { url ->
                    val uri = Uri.parse(url)
                    productCode = uri.getQueryParameter("id").orEmpty()
                    panelCode = uri.getQueryParameter("panel").orEmpty()
                    page = "product"
                },
                onOpenWallet = { shortfall = 0; walletReturn = "catalog"; page = "wallet" },
                onOpenTransactions = { page = "transactions" },
                loadCatalog = { botStoreReadCatalog(api.request("products")) },
                enablePublicCache = allowGuestCatalogCache,
                vipEntryRequested = vipEntryRequested,
                onVipEntryHandled = onVipEntryHandled,
            )
        }
    }
}

@Composable
internal fun GanjNativePlanArt(plan: GanjBotProduct, modifier: Modifier = Modifier) {
    val accent = LocalGanjContentPalette.current.premiumText
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.clip(shape).background(Brush.linearGradient(listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = .17f), accent.copy(alpha = .10f))))
        .border(1.dp, accent.copy(alpha = .26f), shape), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (plan.volumeGb > 0) String.format(Locale.US, "%.1f", plan.volumeGb).removeSuffix(".0").toPersianDigits() else "∞",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Text("GB", style = MaterialTheme.typography.labelSmall, color = accent)
        }
    }
}

@Composable
private fun NativeStoreHeader(title: String, onBack: () -> Unit, busy: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AccountBackAction({ if (!busy) onBack() })
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("GANJ", style = MaterialTheme.typography.labelSmall, color = LocalGanjContentPalette.current.premiumText)
    }
}

@Composable
private fun NativeStorePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun NativeStoreError(text: String, onRetry: () -> Unit, onAccount: (() -> Unit)? = null) {
    GanjInlineStatusPanel("درخواست کامل نشد", text, accent = MaterialTheme.colorScheme.error,
        tone = GanjStatusTone.Danger, actionText = "تلاش دوباره", onAction = onRetry)
    onAccount?.let { GanjGlassButton("بررسی حساب تلگرام", it, Modifier.fillMaxWidth(), filled = false) }
}

@Composable
internal fun StitchNativeBotCheckout(
    api: GanjBotStoreApi, code: String, panel: String, onBack: () -> Unit,
    onAccount: () -> Unit, onTopUp: (Long) -> Unit, onOpenSubscriptions: () -> Unit,
) {
    var product by remember(code, panel) { mutableStateOf<GanjBotProduct?>(null) }
    var quote by remember(code, panel) { mutableStateOf<GanjBotQuote?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var discount by rememberSaveable(code, panel) { mutableStateOf("") }
    var username by rememberSaveable(code, panel) { mutableStateOf("") }
    var note by rememberSaveable(code, panel) { mutableStateOf("") }
    var optional by rememberSaveable { mutableStateOf(false) }
    var key by rememberSaveable(code, panel) { mutableStateOf(UUID.randomUUID().toString()) }
    var attempted by rememberSaveable(code, panel) { mutableStateOf(false) }
    var completedInvoice by rememberSaveable(code, panel) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler(busy) { /* a native financial request must not be submitted twice */ }
    LaunchedEffect(code, panel, retry) {
        loading = true; error = null; quote = null
        try {
            val p = api.request("product", mapOf("id" to code, "panel" to panel)).getJSONObject("data")
            product = botStoreReadCatalog(JSONObject().put("data", org.json.JSONArray().put(p))).singleOrNull()
                ?: throw GanjBotStoreException("مشخصات این سرویس قابل دریافت نیست.")
            quote = GanjBotQuote.parse(api.request("purchase_quote", mapOf("product" to code, "panel" to panel, "discount_code" to discount)).getJSONObject("data"))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) { error = ganjBotStoreError(e) }
        finally { loading = false }
    }
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("native-store-checkout"),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { NativeStoreHeader(if (completedInvoice == null) "بررسی و تأیید خرید" else "خرید ثبت شد", onBack, busy) }
        if (loading) item { GanjLoadingState("در حال بررسی سرویس و موجودی") }
        product?.let { plan -> item {
            NativeStorePanel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GanjNativePlanArt(plan, Modifier.size(64.dp))
                    Column(Modifier.weight(1f)) {
                        Text(plan.appDisplayName(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(plan.panel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (plan.durationDays > 0) "${plan.durationDays.toPersianDigits()} روز اعتبار" else "اعتبار نامحدود", style = MaterialTheme.typography.labelMedium)
                    Text(plan.category, style = MaterialTheme.typography.labelSmall, color = LocalGanjContentPalette.current.premiumText)
                }
                if (plan.description.isNotBlank()) Text(plan.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
        if (completedInvoice != null) item {
            NativeStorePanel {
                Text("سرویس شما با موفقیت آماده شد", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text("شماره سفارش: ${isolateTechnicalLtr(completedInvoice!!)}", style = MaterialTheme.typography.bodySmall)
                GanjGlassButton("مشاهده اشتراک‌ها", onOpenSubscriptions, Modifier.fillMaxWidth().testTag("native-purchase-services"))
            }
        } else {
            quote?.let { q -> item {
                NativeStorePanel(Modifier.testTag("native-purchase-quote")) {
                    NativePriceRow("مبلغ نهایی", botMoney(q.price), true)
                    if (q.saved > 0) NativePriceRow("تخفیف", botMoney(q.saved))
                    NativePriceRow("موجودی کیف پول", botMoney(q.balance))
                    NativePriceRow(if (q.enough) "موجودی پس از خرید" else "مبلغ موردنیاز", botMoney(if (q.enough) (q.balance-q.price).coerceAtLeast(0) else (q.price-q.balance).coerceAtLeast(0)))
                }
            } }
            item {
                GanjLiquidTextField(discount, { discount = it.take(100); quote = null }, "کد تخفیف",
                    Modifier.fillMaxWidth().testTag("native-purchase-discount"), enabled = !busy && !attempted)
                GanjGlassButton("بررسی قیمت", { if (!busy) retry++ }, Modifier.fillMaxWidth(),
                    enabled = !busy && !loading && !attempted, filled = false)
            }
            item {
                GanjGlassButton("تنظیمات اختیاری", { optional = !optional }, Modifier.fillMaxWidth(), enabled = !busy && !attempted, filled = false)
                if (optional) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GanjLiquidTextField(username, { username = it.take(64) }, "نام کاربری دلخواه", Modifier.fillMaxWidth(), enabled = !busy && !attempted)
                    GanjLiquidTextField(note, { note = it.take(250) }, "یادداشت سرویس", Modifier.fillMaxWidth(), enabled = !busy && !attempted)
                }
            }
            if (quote?.enough == false) item {
                GanjGlassButton("افزایش موجودی", { quote?.let { onTopUp((it.price-it.balance).coerceAtLeast(1000)) } },
                    Modifier.fillMaxWidth().testTag("native-purchase-topup"), enabled = !busy, filled = false,
                    accent = LocalGanjContentPalette.current.premiumText)
            }
            item {
                GanjSignatureButton(if (busy) "در حال خرید…" else if (attempted) "پیگیری خرید" else "تأیید خرید", {
                    if (!busy && quote?.enough == true) {
                        busy = true; attempted = true; error = null
                        scope.launch {
                            try {
                                val result = api.request("purchase", mapOf("product" to code, "panel" to panel,
                                    "discount_code" to discount, "custom_username" to username, "note" to note, "idempotency_key" to key)).getJSONObject("data")
                                completedInvoice = result.getString("invoice_id").takeIf { it.isNotBlank() }
                                    ?: throw GanjBotStoreException("پاسخ خرید کامل نیست. همان درخواست را دوباره پیگیری کنید.")
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (e: Exception) { error = ganjBotStoreError(e) }
                            finally { busy = false }
                        }
                    }
                }, Modifier.fillMaxWidth().testTag("native-purchase-confirm"), enabled = !busy && !loading && quote?.enough == true)
                Text("مبلغ و دسترسی سرویس فقط پس از اعتبارسنجی سرور ثبت می‌شود.", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        error?.let { message -> item { NativeStoreError(message, { if (!busy) retry++ }, onAccount) } }
    }
}

@Composable
private fun NativePriceRow(label: String, value: String, prominent: Boolean = false) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = if (prominent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall,
            fontWeight = if (prominent) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
internal fun StitchNativeBotWallet(
    api: GanjBotStoreApi, initialAmount: Long, onBack: () -> Unit,
    onPayment: (String, Boolean) -> Unit, onAccount: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var methods by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var balance by remember { mutableStateOf<Long?>(null) }
    var selected by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf(if (initialAmount > 0) initialAmount.toString() else "") }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var openCard by remember { mutableStateOf<String?>(null) }
    var openCrypto by remember { mutableStateOf<String?>(null) }
    var attemptKey by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var attemptFingerprint by rememberSaveable { mutableStateOf("") }
    BackHandler(busy) {}
    LaunchedEffect(retry) {
        loading = methods.isEmpty(); error = null
        try {
            val data = api.request("wallet_methods").getJSONObject("data")
            balance = botReadVerifiedBalance(data)
            val list = data.getJSONArray("methods")
            methods = (0 until list.length()).map { list.getJSONObject(it) }
            if (selected.isBlank() || methods.none { it.optString("id") == selected }) selected = methods.firstOrNull()?.optString("id").orEmpty()
            // The primary wallet UI is usable without waiting for invoice recovery.
            loading = false
            // Each recovery read is optional, and must never mask valid wallet data.
            for (isCrypto in listOf(false, true)) {
                val session = try {
                    api.request(if (isCrypto) "wallet_crypto_session" else "wallet_card_session").getJSONObject("data")
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { continue }
                val orderId = session.optString("order_id")
                if (orderId.matches(Regex("[A-Za-z0-9_-]{6,64}")) &&
                    session.optString("status") in setOf("unpaid", "waiting", "waiting_review", "manual_review", "processing_smart")) {
                    if (isCrypto) openCrypto = orderId else openCard = orderId
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) { error = ganjBotStoreError(e) }
        finally { loading = false }
    }
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("native-store-wallet"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { NativeStoreHeader("کیف پول و پرداخت", onBack, busy) }
        if (loading) item { GanjLoadingState("در حال دریافت روش‌های پرداخت") }
        balance?.let { value -> item { NativeStorePanel { NativePriceRow("موجودی فعلی", botMoney(value), true) } } }
        openCard?.let { id -> item { GanjGlassButton("ادامه فاکتور کارت‌به‌کارت", { onPayment(id, false) }, Modifier.fillMaxWidth(), filled = false) } }
        openCrypto?.let { id -> item { GanjGlassButton("ادامه فاکتور TRX", { onPayment(id, true) }, Modifier.fillMaxWidth(), filled = false) } }
        item { GanjLiquidTextField(amount, { amount = botStoreAmountDigits(it).take(12) }, "مبلغ افزایش موجودی (تومان)",
            Modifier.fillMaxWidth().testTag("native-wallet-amount"), enabled = !busy) }
        items(methods, key = { it.getString("id") }) { method ->
            GanjGlassButton(method.optString("title"), { selected = method.getString("id") },
                Modifier.fillMaxWidth().testTag("native-wallet-method-" + method.getString("id")), enabled = !busy,
                filled = selected == method.getString("id"))
            val minimum = method.optLong("min"); val maximum = method.optLong("max")
            if (minimum > 0 || maximum > 0) Text(
                (if (minimum > 0) "حداقل ${botMoney(minimum)}" else "") +
                    (if (minimum > 0 && maximum > 0) " · " else "") + (if (maximum > 0) "حداکثر ${botMoney(maximum)}" else ""),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!loading && methods.isEmpty() && error == null) item {
            Text("در حال حاضر روش پرداختی برای حساب شما فعال نیست.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            GanjSignatureButton(if (busy) "در حال آماده‌سازی فاکتور…" else "ادامه پرداخت", {
                val method = methods.firstOrNull { it.optString("id") == selected }
                val value = amount.toLongOrNull()
                when {
                    busy -> Unit
                    method == null -> error = "یک روش پرداخت انتخاب کنید."
                    value == null || value < 1 -> error = "مبلغ معتبر وارد کنید."
                    method.optLong("min") > 0 && value < method.optLong("min") -> error = "مبلغ از حداقل این روش کمتر است."
                    method.optLong("max") > 0 && value > method.optLong("max") -> error = "مبلغ از سقف این روش بیشتر است."
                    else -> {
                        busy = true; error = null
                        scope.launch {
                            try {
                                if (method.optString("kind") == "telegram") {
                                    botStoreOpenExternal(context, method.getString("url"))
                                } else {
                                    val fingerprint = "$selected:$value"
                                    if (attemptFingerprint != fingerprint) { attemptKey = UUID.randomUUID().toString(); attemptFingerprint = fingerprint }
                                    val action = when (selected) { "card" -> "wallet_card"; "crypto_trx" -> "wallet_crypto"; else -> "wallet_gateway" }
                                    val out = api.request(action, mapOf("amount" to value.toString(), "method" to selected,
                                        "idempotency_key" to attemptKey)).getJSONObject("data")
                                    val id = out.getString("order_id")
                                    require(id.matches(Regex("[A-Za-z0-9_-]{6,64}")))
                                    if (out.optString("redirect").isNotBlank()) botStoreOpenExternal(context, out.getString("redirect"))
                                    onPayment(id, selected == "crypto_trx")
                                }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (e: Exception) { error = ganjBotStoreError(e) }
                            finally { busy = false }
                        }
                    }
                }
            }, Modifier.fillMaxWidth().testTag("native-wallet-pay"), enabled = !busy && !loading && methods.isNotEmpty())
        }
        error?.let { message -> item { NativeStoreError(message, { if (!busy) retry++ }, onAccount) } }
    }
}

internal fun botStoreAmountDigits(value: String): String = buildString {
    value.forEach { c -> c.digitToIntOrNull()?.let { append(it) } }
}

private fun botStoreOpenExternal(context: Context, value: String) {
    val uri = Uri.parse(value)
    if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null ||
        (uri.port != -1 && uri.port != 443) || botFinanceIsTrusted(value)) {
        throw GanjBotStoreException("آدرس پرداخت معتبر نیست.")
    }
    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)) }
    catch (_: ActivityNotFoundException) { throw GanjBotStoreException("برنامه‌ای برای بازکردن درگاه یا تلگرام پیدا نشد.") }
}

@Composable
internal fun StitchNativeBotPayment(
    api: GanjBotStoreApi, order: String, crypto: Boolean,
    onBack: () -> Unit, onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var invoice by remember(order) { mutableStateOf<JSONObject?>(null) }
    var status by remember(order) { mutableStateOf("") }
    var error by remember(order) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<Uri?>(null) }
    var txLink by rememberSaveable(order) { mutableStateOf("") }
    var seconds by remember { mutableLongStateOf(0) }
    var deadline by remember { mutableLongStateOf(0) }
    var hasDeadline by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { receipt = it }
    BackHandler(busy) {}
    LaunchedEffect(order, crypto, retry, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                val data = api.request(if (crypto) "wallet_crypto_session" else "wallet_card_session", mapOf("order" to order)).getJSONObject("data")
                // Gateways have no receipt/card session; their status is still native and bot-owned.
                if (data.optString("order_id") == order) invoice = data
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* payment_status below is authoritative for gateways */ }
            while (isActive) {
                try {
                    val data = api.request("payment_status", mapOf("order" to order)).getJSONObject("data")
                    require(data.getString("order_id") == order)
                    status = data.getString("status").lowercase(); error = null
                    hasDeadline = !data.isNull("seconds_left") && data.has("seconds_left")
                    if (hasDeadline) deadline = SystemClock.elapsedRealtime() + data.getLong("seconds_left").coerceAtLeast(0) * 1000
                    if (status in setOf("paid", "reject", "rejected", "expire", "expired")) break
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (e: Exception) { error = ganjBotStoreError(e) }
                delay(8000)
            }
        }
    }
    LaunchedEffect(deadline, hasDeadline, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive && hasDeadline) { seconds = ((deadline-SystemClock.elapsedRealtime()).coerceAtLeast(0)+999)/1000; delay(1000) }
        }
    }
    fun copy(value: String) { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("GANJ", value)) }
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("native-store-payment"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { NativeStoreHeader("پرداخت امن گنج", onBack, busy) }
        item { NativeStorePanel {
            Text(if (status.isBlank()) "در حال دریافت وضعیت فاکتور" else botFinanceStatusText(status),
                style = MaterialTheme.typography.titleSmall,
                color = if (status == "paid") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text("فاکتور ${isolateTechnicalLtr(order)}", style = MaterialTheme.typography.labelSmall)
            if (status == "unpaid" && hasDeadline) Text("زمان باقی‌مانده: ${(seconds/60).toPersianDigits()}:${(seconds%60).toString().padStart(2,'0').toPersianDigits()}",
                style = MaterialTheme.typography.bodySmall, color = LocalGanjContentPalette.current.premiumText)
        } }
        invoice?.let { data ->
            if (status == "unpaid" && (data.optString("card_number").isNotBlank() || data.optString("wallet").isNotBlank())) {
                item { NativeStorePanel {
                    NativePriceRow("مبلغ فاکتور", botMoney(data.getDouble("amount").toLong()), true)
                    if (data.optBoolean("resumed")) Text("فاکتور ناتمام قبلی بازیابی شد؛ مبلغ همین فاکتور را پرداخت کنید.", style = MaterialTheme.typography.labelSmall)
                    if (crypto) {
                        Text("${data.optString("trx_amount")} TRX", style = MaterialTheme.typography.titleMedium)
                        Text(isolateTechnicalLtr(data.getString("wallet")), style = MaterialTheme.typography.bodySmall)
                        GanjGlassButton("کپی آدرس TRX", { copy(data.getString("wallet")) }, Modifier.fillMaxWidth(), filled = false)
                    } else {
                        Text(isolateTechnicalLtr(data.getString("card_number").chunked(4).joinToString("  ")),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(data.optString("card_holder"), style = MaterialTheme.typography.bodySmall)
                        GanjGlassButton("کپی شماره کارت", { copy(data.getString("card_number")) }, Modifier.fillMaxWidth(), filled = false)
                        GanjGlassButton("کپی مبلغ", { copy(data.getLong("amount").toString()) }, Modifier.fillMaxWidth(), filled = false)
                    }
                } }
                item {
                    NativeStorePanel {
                        Text("ارسال رسید پرداخت", style = MaterialTheme.typography.titleSmall)
                        Text("JPG، PNG، WebP یا PDF تا ۸ مگابایت", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (crypto) GanjLiquidTextField(txLink, { txLink = it.take(500) }, "لینک تراکنش TRX (اختیاری)", Modifier.fillMaxWidth(), enabled = !busy)
                        GanjGlassButton(if (receipt == null) "انتخاب فایل رسید" else "رسید انتخاب شد؛ تغییر فایل", {
                            picker.launch(arrayOf("image/jpeg", "image/png", "image/webp", "application/pdf"))
                        }, Modifier.fillMaxWidth().testTag("native-receipt-select"), enabled = !busy, filled = false)
                        GanjGlassButton(if (busy) "در حال ارسال رسید…" else "ارسال رسید", {
                            val file = receipt
                            if (!busy && file != null) {
                                busy = true; error = null
                                scope.launch {
                                    try {
                                        val result = api.uploadReceipt(context, file, order, crypto, txLink).getJSONObject("data")
                                        require(result.getString("order_id") == order)
                                        status = result.getString("status").lowercase(); receipt = null; retry++
                                    } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (e: Exception) { error = ganjBotStoreError(e) }
                                    finally { busy = false }
                                }
                            }
                        }, Modifier.fillMaxWidth().testTag("native-receipt-upload"), enabled = !busy && receipt != null && (!hasDeadline || seconds > 0))
                    }
                }
            }
        }
        if (status == "paid") item { GanjGlassButton("ادامه و بررسی موجودی", onContinue,
            Modifier.fillMaxWidth().testTag("native-payment-continue")) }
        item { GanjGlassButton("به‌روزرسانی وضعیت", { retry++ }, Modifier.fillMaxWidth(), enabled = !busy, filled = false) }
        error?.let { message -> item { NativeStoreError(message, { retry++ }) } }
    }
}

@Composable
internal fun StitchNativeBotTransactions(api: GanjBotStoreApi, onBack: () -> Unit, onAccount: () -> Unit, onPayment: (String, Boolean) -> Unit) {
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(retry) {
        loading = true; error = null
        try {
            val list = api.request("transactions", mapOf("limit" to "30")).getJSONArray("data")
            rows = (0 until list.length()).map { list.getJSONObject(it) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) { error = ganjBotStoreError(e) }
        finally { loading = false }
    }
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("native-store-transactions"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { NativeStoreHeader("تراکنش‌های گنج", onBack) }
        if (loading) item { GanjLoadingState("در حال دریافت سوابق") }
        if (!loading && rows.isEmpty() && error == null) item { Text("تراکنشی ثبت نشده است.", style = MaterialTheme.typography.bodySmall) }
        items(rows) { data -> NativeStorePanel {
            Text(data.optString("Payment_Method"), style = MaterialTheme.typography.titleSmall)
            NativePriceRow("مبلغ", botMoney(data.getDouble("price").toLong()))
            Text(if (data.optString("kind") == "wallet") "ثبت‌شده" else botFinanceStatusText(data.optString("payment_Status")), style = MaterialTheme.typography.labelSmall)
            val timestamp = data.optLong("created_at")
            if (timestamp > 0) Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
                java.text.DateFormat.SHORT, Locale("fa")).format(java.util.Date(timestamp * 1000)).toPersianDigits(),
                style = MaterialTheme.typography.labelSmall)
            if (data.optString("description").isNotBlank()) Text(data.optString("description"), style = MaterialTheme.typography.bodySmall)
            val id = data.optString("id_order")
            if (data.optString("kind") == "payment" && id.matches(Regex("[A-Za-z0-9_-]{6,64}"))) {
                GanjGlassButton("مشاهده وضعیت فاکتور", { onPayment(id, data.optString("Payment_Method") == "arze digital offline") },
                    Modifier.fillMaxWidth(), filled = false)
            }
        } }
        error?.let { message -> item { NativeStoreError(message, { retry++ }, onAccount) } }
    }
}
