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
import androidx.compose.ui.graphics.Color
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
/** Display-only Persian wording. Canonical amount, fees and limits come from the bot. */
internal fun botTomanInWords(amount: Long): String {
    if (amount < 0) return "مبلغ نامعتبر"
    if (amount == 0L) return "صفر تومان"
    val ones = listOf("", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه")
    val teens = listOf("ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده")
    val tens = listOf("", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
    val hundreds = listOf("", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد")
    val groups = listOf("", "هزار", "میلیون", "میلیارد", "تریلیون", "بیلیارد")
    fun chunk(value: Int): String {
        val parts = mutableListOf<String>()
        if (value >= 100) parts += hundreds[value / 100]
        val last = value % 100
        when {
            last >= 20 -> { parts += tens[last / 10]; if (last % 10 != 0) parts += ones[last % 10] }
            last >= 10 -> parts += teens[last - 10]
            last > 0 -> parts += ones[last]
        }
        return parts.joinToString(" و ")
    }
    var rest = amount
    var index = 0
    val sections = mutableListOf<String>()
    while (rest > 0 && index < groups.size) {
        val part = (rest % 1000).toInt()
        if (part != 0) sections += listOf(chunk(part), groups[index]).filter(String::isNotBlank).joinToString(" ")
        rest /= 1000
        index++
    }
    return sections.asReversed().joinToString(" و ") + " تومان"
}

internal fun botTopUpSuggestions(minimum: Long, maximum: Long): List<Long> {
    val start = minimum.coerceAtLeast(0L)
    if (maximum > 0 && start > maximum) return emptyList()
    val base = if (start > 0) listOf(start) else emptyList()
    return (base + listOf(200_000L, 500_000L, 1_000_000L, 2_000_000L))
        .distinct().filter { it >= start && (maximum <= 0 || it <= maximum) }.take(5)
}

@Composable
private fun NativePaymentSpotlight(
    caption: String,
    amount: Long,
    context: String,
    modifier: Modifier = Modifier,
) {
    val palette = GanjFinancePalette
    GanjCommerceFocusSurface(modifier) {
        Text(caption, style = MaterialTheme.typography.labelMedium, color = palette.mutedInk)
        Text(botMoney(amount), style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold, color = palette.ink)
        Text(botTomanInWords(amount), style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold, color = palette.goldInk)
        Text(context, style = MaterialTheme.typography.bodySmall, color = palette.mutedInk)
    }
}


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
    activeVip: Boolean = false,
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
                activeVip = activeVip,
                onOpenSubscription = onOpenSubscriptions,
            )
        }
    }
}

@Composable
internal fun GanjNativePlanArt(plan: GanjBotProduct, modifier: Modifier = Modifier) {
    val tone = when {
        plan.isVip() -> GanjSignatureTone.Iris
        plan.featured -> GanjSignatureTone.Coral
        else -> GanjSignatureTone.Azure
    }
    val accent = ganjSignatureTone(tone)
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.clip(shape).background(Brush.linearGradient(listOf(
        accent.copy(alpha = .30f), accent.copy(alpha = .12f))))
        .border(1.dp, accent.copy(alpha = .55f), shape), contentAlignment = Alignment.Center) {
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
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text("GANJ", style = MaterialTheme.typography.labelSmall, color = LocalGanjContentPalette.current.premiumText)
    }
}

@Composable
private fun NativeStorePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth(),
        shapeRadius = 14.dp,
        padding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
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
        contentPadding = PaddingValues(start = responsiveHorizontalPadding(), end = responsiveHorizontalPadding(), top = 18.dp,
        bottom = GanjFloatingNavContentClearance), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { NativeStoreHeader(if (completedInvoice == null) "بررسی و تأیید خرید" else "خرید ثبت شد", onBack, busy) }
        item { GanjInnerPageHeading(
            if (completedInvoice == null) "جزئیات خرید" else "سفارش شما",
            "خرید امن با موجودی واقعی کیف پول گنج",
            GanjProfileSymbol.Subscription, GanjSignatureTone.Gold,
        ) }
        if (loading) item { GanjLoadingState("در حال بررسی سرویس و موجودی") }
        product?.let { plan -> item {
            NativeStorePanel {
                GanjInnerSectionTitle("سرویس انتخابی",
                    "مشخصات دقیق سرویس از ربات گنج دریافت شده‌اند")
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
                    GanjInnerSectionTitle("خلاصه پرداخت",
                        "مبلغ قطعی با قیمت‌های فعلی ربات محاسبه شده است",
                        GanjSignatureTone.Gold)
                    NativePaymentSpotlight(
                        "مبلغ نهایی سفارش", q.price,
                        "حجم، مدت و مبلغ را پیش از ثبت سفارش بررسی کنید.",
                        Modifier.testTag("native-checkout-total-highlight"),
                    )
                    if (q.saved > 0) NativePriceRow("تخفیف شما", botMoney(q.saved))
                    NativePriceRow("موجودی کیف پول", botMoney(q.balance))
                    NativePriceRow(if (q.enough) "موجودی پس از خرید" else "مبلغ موردنیاز", botMoney(if (q.enough) (q.balance-q.price).coerceAtLeast(0) else (q.price-q.balance).coerceAtLeast(0)))
                }
            } }
            item {
                GanjInnerFeatureSection("کد تخفیف", "تخفیف معتبر را پیش از خرید بررسی کنید",
                    GanjProfileSymbol.Subscription, GanjProfileAccent.Gold) {
                    GanjLiquidTextField(discount, { discount = it.take(100); quote = null },
                        "کد تخفیف", Modifier.fillMaxWidth().testTag("native-purchase-discount"),
                        enabled = !busy && !attempted)
                    GanjGlassButton("بررسی قیمت", { if (!busy) retry++ }, Modifier.fillMaxWidth(),
                        enabled = !busy && !loading && !attempted, filled = false)
                }
            }
            item {
                GanjInnerFeatureSection("شخصی‌سازی سرویس",
                    "این موارد اختیاری‌اند و در قیمت‌گذاری دخالت ندارند",
                    GanjProfileSymbol.Appearance, GanjProfileAccent.Iris) {
                    GanjGlassButton(if (optional) "بستن گزینه‌های اختیاری" else "نمایش گزینه‌های اختیاری",
                        { optional = !optional }, Modifier.fillMaxWidth(), enabled = !busy && !attempted,
                        filled = false, accent = ganjSignatureTone(GanjSignatureTone.Iris))
                    if (optional) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GanjLiquidTextField(username, { username = it.take(64) },
                            "نام کاربری دلخواه", Modifier.fillMaxWidth(), enabled = !busy && !attempted)
                        GanjLiquidTextField(note, { note = it.take(250) },
                            "یادداشت سرویس", Modifier.fillMaxWidth(), enabled = !busy && !attempted)
                    }
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
                GanjInlineStatusBanner(
                    message = if (quote?.enough == true)
                        "با تأیید، مبلغ از کیف پول همین حساب کسر و سفارش در ربات گنج ثبت می‌شود."
                    else "موجودی کافی نیست؛ ابتدا کیف پول همین حساب را شارژ کنید.",
                    tone = AccountBannerTone.Info,
                )
                GanjInnerMetadataPill("قیمت و موجودی از سرور رسمی ربات گنج", GanjProfileAccent.Emerald)
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
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (prominent) FontWeight.Bold else FontWeight.Normal)
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
    val selectedMethod = methods.firstOrNull { it.optString("id") == selected }
    val parsedAmount = amount.toLongOrNull()
    val minAmount = selectedMethod?.optLong("min")?.coerceAtLeast(0L) ?: 0L
    val maxAmount = selectedMethod?.optLong("max")?.coerceAtLeast(0L) ?: 0L
    val suggestedAmounts = remember(minAmount, maxAmount) { botTopUpSuggestions(minAmount, maxAmount) }
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
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("native-store-wallet"), contentPadding = PaddingValues(start = responsiveHorizontalPadding(), end = responsiveHorizontalPadding(), top = 18.dp,
        bottom = GanjFloatingNavContentClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { NativeStoreHeader("کیف پول و افزایش موجودی", onBack, busy) }
        item { GanjInnerPageHeading(
            "مدیریت اعتبار",
            "موجودی و روش‌های پرداخت، متصل به ربات گنج",
            GanjProfileSymbol.Wallet, GanjSignatureTone.Azure,
        ) }
        if (loading) item { GanjLoadingState("در حال دریافت روش‌های پرداخت") }
        balance?.let { value -> item {
            GanjInnerFinanceHero("موجودی کیف پول", botMoney(value),
                "موجودی واقعی حساب از سرویس گنج دریافت شده است")
        } }
        openCard?.let { id -> item {
            GanjGlassButton("ادامه فاکتور کارت‌به‌کارت", { onPayment(id, false) },
                Modifier.fillMaxWidth(), filled = false,
                accent = ganjSignatureTone(GanjSignatureTone.Azure))
        } }
        openCrypto?.let { id -> item {
            GanjGlassButton("ادامه فاکتور TRX", { onPayment(id, true) },
                Modifier.fillMaxWidth(), filled = false,
                accent = ganjSignatureTone(GanjSignatureTone.Iris))
        } }
        item {
            NativeStorePanel {
                GanjInnerSectionTitle("افزایش موجودی",
                    "مبلغ را به تومان وارد کن؛ پیش از پرداخت فاکتور نمایش داده می‌شود",
                    GanjSignatureTone.Azure)
                Text("مبالغ پیشنهادی", style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestedAmounts.forEach { value ->
                        GanjChoicePill(
                            botMoney(value), selected = parsedAmount == value,
                            onClick = { amount = value.toString() }, enabled = !busy,
                            accent = ganjSignatureTone(GanjSignatureTone.Gold),
                            modifier = Modifier.testTag("native-wallet-suggested-" + value),
                        )
                    }
                }
                Text("یا مبلغ دلخواه (تومان)", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                GanjLiquidTextField(amount, { amount = botStoreAmountDigits(it).take(12) },
                    "مثلاً ۲۰۰۰۰۰ تومان",
                    Modifier.fillMaxWidth().testTag("native-wallet-amount"), enabled = !busy)
                if (parsedAmount != null && parsedAmount > 0) {
                    NativePaymentSpotlight(
                        "مبلغ انتخابی برای افزایش موجودی", parsedAmount,
                        "مبلغ نوشته‌شده با حروف را بخوانید تا صفر اضافی وارد نشود.",
                        Modifier.testTag("native-wallet-amount-words"),
                    )
                } else GanjInlineStatusBanner(
                    "مبلغ را انتخاب یا وارد کنید؛ پیش از پرداخت به حروف نمایش داده می‌شود.",
                    tone = AccountBannerTone.Info,
                )
                if (minAmount > 0 || maxAmount > 0)
                    Text(
                        (if (minAmount > 0) "حداقل: " + botMoney(minAmount) else "") +
                            (if (minAmount > 0 && maxAmount > 0) "  •  " else "") +
                            (if (maxAmount > 0) "حداکثر: " + botMoney(maxAmount) else ""),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                    )
                if (parsedAmount != null &&
                    ((minAmount > 0 && parsedAmount < minAmount) ||
                    (maxAmount > 0 && parsedAmount > maxAmount)))
                    GanjInlineStatusBanner("مبلغ خارج از محدودهٔ مجاز این روش پرداخت است.",
                        tone = AccountBannerTone.Error)
            }
        }
        if (methods.isNotEmpty()) item {
            GanjInnerSectionTitle("انتخاب روش پرداخت",
                "هر روش به همان سیستم صدور فاکتور ربات متصل است",
                GanjSignatureTone.Iris)
        }
        items(methods, key = { it.getString("id") }) { method ->
            val methodId = method.getString("id")
            val tone = if (methodId == "crypto_trx") GanjSignatureTone.Iris
                else GanjSignatureTone.Azure
            NativeStorePanel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GanjProfileIconTile(GanjProfileSymbol.Wallet,
                        if (methodId == "crypto_trx") GanjProfileAccent.Iris else GanjProfileAccent.Gold)
                    Column(Modifier.weight(1f)) {
                        Text(method.optString("title"), style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(if (methodId == "card") "کارت‌به‌کارت و ارسال رسید"
                             else if (methodId == "crypto_trx") "پرداخت رمزارزی و پیگیری تراکنش"
                             else "پرداخت از روش رسمی ربات گنج",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                GanjChoicePill(if (selected == methodId) "روش انتخاب‌شده" else "انتخاب این روش",
                    selected = selected == methodId, onClick = { selected = methodId },
                    modifier = Modifier.fillMaxWidth().testTag("native-wallet-method-" + methodId),
                    enabled = !busy, accent = ganjSignatureTone(tone))
                val minimum = method.optLong("min")
                val maximum = method.optLong("max")
                if (minimum > 0 || maximum > 0) Text(
                    (if (minimum > 0) "حداقل ${botMoney(minimum)}" else "") +
                        (if (minimum > 0 && maximum > 0) " · " else "") +
                        (if (maximum > 0) "حداکثر ${botMoney(maximum)}" else ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!loading && methods.isEmpty() && error == null) item {
            Text("در حال حاضر روش پرداختی برای حساب شما فعال نیست.", style = MaterialTheme.typography.bodySmall)
        }
        if (parsedAmount != null && parsedAmount > 0 && selectedMethod != null) item {
            NativePaymentSpotlight("خلاصه پیش از صدور فاکتور", parsedAmount,
                "روش انتخابی: " + selectedMethod.optString("title") +
                " — حداقل و سقف پرداخت توسط ربات کنترل می‌شود.",
                Modifier.testTag("native-wallet-payment-summary"))
        }
        item {
            GanjGlassButton(if (busy) "در حال آماده‌سازی فاکتور…" else "صدور فاکتور و ادامه پرداخت", {
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
            }, Modifier.fillMaxWidth().testTag("native-wallet-pay"),
                enabled = !busy && !loading && methods.isNotEmpty(),
                accent = MaterialTheme.colorScheme.primary)
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
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("native-store-payment"), contentPadding = PaddingValues(start = responsiveHorizontalPadding(), end = responsiveHorizontalPadding(), top = 18.dp,
        bottom = GanjFloatingNavContentClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { NativeStoreHeader("پرداخت امن گنج", onBack, busy) }
        item { GanjInnerPageHeading(
            "وضعیت فاکتور", "پرداخت و رسید از طریق سیستم رسمی گنج پیگیری می‌شوند",
            GanjProfileSymbol.Shield, GanjSignatureTone.Emerald,
        ) }
        item { NativeStorePanel {
            GanjSignatureMiniPill(
                if (status.isBlank()) "در حال بررسی فاکتور" else botFinanceStatusText(status),
                when(status) {
                    "paid" -> GanjSignatureTone.Emerald
                    "rejected", "reject", "expired", "expire" -> GanjSignatureTone.Coral
                    else -> GanjSignatureTone.Gold
                })
            GanjInnerSummaryLine("شناسه فاکتور", isolateTechnicalLtr(order))
            if (status == "unpaid" && hasDeadline) Text(
                "زمان باقی‌مانده: ${(seconds/60).toPersianDigits()}:${(seconds%60).toString().padStart(2,'0').toPersianDigits()}",
                style = MaterialTheme.typography.bodySmall,
                color = ganjSignatureTone(GanjSignatureTone.Gold))
        } }
        invoice?.let { data ->
            if (status == "unpaid" && (data.optString("card_number").isNotBlank() || data.optString("wallet").isNotBlank())) {
                item { NativeStorePanel {
                    NativePaymentSpotlight("مبلغ دقیق همین فاکتور",
                        data.getDouble("amount").toLong(),
                        if (crypto) "پیش از انتقال TRX، آدرس کیف پول و شبکه را بررسی کنید."
                        else "دقیقاً همین مبلغ را واریز کنید؛ سپس رسید را ارسال کنید.",
                        Modifier.testTag("native-payment-invoice-highlight"))
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
                    GanjInnerFeatureSection(
                        "ارسال رسید پرداخت",
                        "تصویر رسید یا فایل PDF را برای بررسی ربات ارسال کنید",
                        GanjProfileSymbol.Wallet, GanjProfileAccent.Sky,
                    ) {
                        GanjInnerMetadataPill("JPG، PNG، WebP یا PDF تا ۸ مگابایت",
                            GanjProfileAccent.Sky)
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
    var selectedKind by rememberSaveable { mutableStateOf("all") }
    LaunchedEffect(retry) {
        loading = true; error = null
        try {
            val list = api.request("transactions", mapOf("limit" to "30")).getJSONArray("data")
            rows = (0 until list.length()).map { list.getJSONObject(it) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) { error = ganjBotStoreError(e) }
        finally { loading = false }
    }
    val visibleRows = rows.filter { data ->
        selectedKind == "all" || data.optString("kind") == selectedKind
    }
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("native-store-transactions"),
        contentPadding = PaddingValues(
            start = responsiveHorizontalPadding(), end = responsiveHorizontalPadding(),
            top = 18.dp, bottom = GanjFloatingNavContentClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { NativeStoreHeader("تراکنش‌های گنج", onBack) }
        item { GanjInnerPageHeading(
            "سوابق مالی", "گردش واقعی پرداخت‌ها و کیف پول حساب شما",
            GanjProfileSymbol.History, GanjSignatureTone.Coral,
        ) }
        if (loading) item { GanjLoadingState("در حال دریافت سوابق") }
        if (!loading && rows.isEmpty() && error == null) item {
            GanjInlineStatusPanel("هنوز تراکنشی وجود ندارد",
                "پس از اولین خرید یا افزایش موجودی، سوابق از ربات گنج نمایش داده می‌شوند.",
                accent = MaterialTheme.colorScheme.primary)
        }
        if (rows.isNotEmpty()) {
            item {
                GanjInnerSectionTitle("فیلتر تراکنش‌ها", "مرتب‌سازی بر اساس نوع ثبت‌شده در ربات")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    GanjChoicePill("همه", selectedKind == "all",
                        { selectedKind = "all" }, Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.primary)
                    GanjChoicePill("پرداخت", selectedKind == "payment",
                        { selectedKind = "payment" }, Modifier.weight(1f),
                        accent = ganjSignatureTone(GanjSignatureTone.Coral))
                    GanjChoicePill("کیف پول", selectedKind == "wallet",
                        { selectedKind = "wallet" }, Modifier.weight(1f),
                        accent = ganjSignatureTone(GanjSignatureTone.Azure))
                }
            }
        }
        if (!loading && rows.isNotEmpty() && visibleRows.isEmpty()) item {
            GanjInlineStatusPanel("تراکنشی در این دسته وجود ندارد",
                "برای مشاهده سایر سوابق، فیلتر همه را انتخاب کن.",
                accent = ganjSignatureTone(GanjSignatureTone.Azure),
                actionText = "نمایش همه", onAction = { selectedKind = "all" })
        }
        items(visibleRows) { data ->
            val isWallet = data.optString("kind") == "wallet"
            val isCrypto = data.optString("Payment_Method") == "arze digital offline"
            val accent = ganjSignatureTone(
                if (isWallet) GanjSignatureTone.Azure else GanjSignatureTone.Coral)
            NativeStorePanel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GanjProfileIconTile(
                        if (isWallet) GanjProfileSymbol.Wallet else GanjProfileSymbol.History,
                        if (isWallet) GanjProfileAccent.Sky else GanjProfileAccent.Gold)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(data.optString("Payment_Method").ifBlank {
                            if (isWallet) "تراکنش کیف پول" else "پرداخت"
                        }, style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold)
                        Text(if (isWallet) "ثبت‌شده"
                            else botFinanceStatusText(data.optString("payment_Status")),
                            style = MaterialTheme.typography.labelSmall, color = accent)
                    }
                }
                GanjInnerSummaryLine("مبلغ تراکنش",
                    botMoney(data.optDouble("price").toLong()))
                val timestamp = data.optLong("created_at")
                if (timestamp > 0) Text(
                    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
                        java.text.DateFormat.SHORT, Locale("fa"))
                        .format(java.util.Date(timestamp * 1000)).toPersianDigits(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (data.optString("description").isNotBlank()) Text(
                    data.optString("description"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val id = data.optString("id_order")
                if (data.optString("kind") == "payment" &&
                    id.matches(Regex("[A-Za-z0-9_-]{6,64}"))) {
                    GanjGlassButton("مشاهده وضعیت فاکتور", { onPayment(id, isCrypto) },
                        Modifier.fillMaxWidth(), filled = false,
                        accent = ganjSignatureTone(GanjSignatureTone.Azure))
                }
            }
        }
        error?.let { message -> item { NativeStoreError(message, { retry++ }, onAccount) } }
    }
}
