package com.ganj.vpn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

private const val BOT_PUBLIC_CATALOG = "https://bot.pedramhs.ir/site/api.php?action=products"
private const val BOT_STORE_HOME = "https://bot.pedramhs.ir/site/?page=products"
private val BotStoreGold = Color(0xFFD5A63A)

/**
 * Storefront data is the bot's public, retail catalog, not control_plans.
 * The bot site owns eligibility, user discounts, payment and service activation.
 * Never attempt to grant entitlements from this display model.
 */
internal data class GanjBotProduct(
    val code: String,
    val panelId: String,
    val panel: String,
    val category: String,
    val name: String,
    val description: String,
    val priceToman: Long,
    val volumeGb: Double,
    val durationDays: Int,
    val badge: String,
    val featured: Boolean,
    val oneBuy: Boolean,
) {
    val key: String get() = panelId + ":" + code
    val purchaseUrl: String get() =
        "https://bot.pedramhs.ir/site/?page=product&id=" +
            URLEncoder.encode(code, "UTF-8") + "&panel=" +
            URLEncoder.encode(panelId, "UTF-8")
}

internal fun botStorePanels(products: List<GanjBotProduct>): List<Pair<String, String>> =
    products.distinctBy { it.panelId }.map { it.panelId to it.panel }

internal fun botStoreCategories(products: List<GanjBotProduct>, panelId: String?): List<String> =
    products.asSequence().filter { panelId == null || it.panelId == panelId }
        .map { it.category }.filter { it.isNotBlank() }.distinct().toList()

internal fun botStoreFilter(
    products: List<GanjBotProduct>,
    panelId: String?,
    category: String?,
    query: String,
): List<GanjBotProduct> {
    val cleanQuery = query.trim()
    return products.filter { product ->
        (panelId == null || product.panelId == panelId) &&
            (category == null || product.category == category) &&
            (cleanQuery.isEmpty() || product.name.contains(cleanQuery, ignoreCase = true) ||
                product.description.contains(cleanQuery, ignoreCase = true))
    }
}

/** Small, bounded read-only HTTPS fetch; never send account credentials or bot API keys. */
internal object GanjBotCatalog {
    suspend fun fetch(): List<GanjBotProduct> = withContext(Dispatchers.IO) {
        val conn = (URL(BOT_PUBLIC_CATALOG).openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = 7000
            readTimeout = 9000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            check(conn.responseCode == 200) { "bot-catalog-http" }
            val raw = StringBuilder()
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(4096)
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    check(raw.length + read <= 512_000) { "bot-catalog-too-large" }
                    raw.append(buffer, 0, read)
                }
            }
            val envelope = JSONObject(raw.toString())
            check(envelope.optBoolean("status", false)) { "bot-catalog-unavailable" }
            val list = envelope.optJSONArray("data") ?: error("bot-catalog-invalid")
            check(list.length() <= 500) { "bot-catalog-too-many" }
            buildList {
                for (index in 0 until list.length()) {
                    val product = list.optJSONObject(index) ?: continue
                    val code = product.optString("id").trim()
                    val panelId = product.optString("panel_id").trim()
                    val name = product.optString("name").trim()
                    val panel = product.optString("panel").trim()
                    if (!code.matches(Regex("[a-zA-Z0-9_-]{1,64}")) ||
                        !panelId.matches(Regex("[a-zA-Z0-9_-]{1,64}")) ||
                        name.isBlank() || panel.isBlank()) continue
                    val price = product.optDouble("price", Double.NaN)
                    if (!price.isFinite() || price < 0 || price > 1_000_000_000_000) continue
                    add(GanjBotProduct(
                        code = code, panelId = panelId, panel = panel.take(120),
                        category = product.optString("category").trim().take(140),
                        name = name.take(180),
                        description = product.optString("description").trim().take(400),
                        priceToman = price.toLong(),
                        volumeGb = product.optDouble("volume", 0.0).takeIf { it.isFinite() && it >= 0 } ?: 0.0,
                        durationDays = product.optInt("days").coerceAtLeast(0),
                        badge = product.optString("badge").trim().take(60),
                        featured = product.optBoolean("featured", false),
                        oneBuy = product.optBoolean("one_buy", false),
                    ))
                }
            }.distinctBy { it.key }
        } finally {
            conn.disconnect()
        }
    }
}

@Composable
internal fun StitchBotSyncedStoreScreen(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    var retryKey by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var products by remember { mutableStateOf(emptyList<GanjBotProduct>()) }
    var panelId by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(retryKey) {
        loading = true
        error = false
        try {
            products = GanjBotCatalog.fetch()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Never fall back to an unrelated price catalog on a network/API error.
            products = emptyList()
            error = true
        } finally {
            loading = false
        }
    }

    val panels = remember(products) { botStorePanels(products) }
    val selectedPanel = panelId?.takeIf { key -> panels.any { it.first == key } }
    val categories = remember(products, selectedPanel) { botStoreCategories(products, selectedPanel) }
    val selectedCategory = category?.takeIf { it in categories }
    val visible = remember(products, selectedPanel, selectedCategory, query) {
        botStoreFilter(products, selectedPanel, selectedCategory, query)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("bot-synced-store"),
        contentPadding = PaddingValues(horizontal = responsiveHorizontalPadding(), vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item(key = "bot-header") {
            StitchSimpleHeader(
                title = "فروشگاه گنج",
                subtitle = "محصولات و دسته‌بندی‌های واقعی ربات",
                badge = "همگام با ربات",
                goldBadge = false,
            )
        }
        item(key = "bot-heading") {
            GanjGlassSurface(role = GanjGlassRole.Dense, shapeRadius = 22.dp,
                modifier = Modifier.fillMaxWidth(), padding = PaddingValues(18.dp),
                accent = BotStoreGold) {
                Text("اشتراک مناسب خودت را پیدا کن",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("لوکیشن، دسته‌بندی و محصولات مستقیماً از فروشگاه گنج دریافت می‌شوند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!loading && !error) {
                    Text(products.size.toPersianDigits() + " محصول قابل نمایش",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (loading) {
            item(key = "bot-loading") {
                Row(Modifier.fillMaxWidth().height(120.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                }
            }
        } else if (error) {
            item(key = "bot-error") {
                GanjGlassSurface(role = GanjGlassRole.OpaqueFallback,
                    modifier = Modifier.fillMaxWidth(), shapeRadius = 18.dp) {
                    Text("دریافت محصولات ربات انجام نشد",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium)
                    Text("برای جلوگیری از نمایش قیمت اشتباه، محصولات قدیمی جایگزین نمی‌شوند.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { retryKey++ },
                        modifier = Modifier.fillMaxWidth().testTag("bot-store-retry")) {
                        Text("تلاش دوباره")
                    }
                    TextButton(onClick = { uriHandler.openUri(BOT_STORE_HOME) },
                        modifier = Modifier.fillMaxWidth()) {
                        Text("ورود به فروشگاه گنج")
                    }
                }
            }
        } else if (products.isEmpty()) {
            item(key = "bot-empty") {
                EmptyCard("محصول فعالی در ربات نیست",
                    "با افزودن محصولات در ربات، این فهرست خودکار به‌روزرسانی می‌شود.",
                    onRetry = { retryKey++ })
            }
        } else {
            item(key = "bot-search") {
                OutlinedTextField(
                    value = query, onValueChange = { query = it.take(120) },
                    modifier = Modifier.fillMaxWidth().testTag("bot-store-search"),
                    label = { Text("جستجوی سرویس") },
                    singleLine = true,
                )
            }
            item(key = "bot-panel-heading") {
                Text("نوع اشتراک و لوکیشن", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            item(key = "bot-panels") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("bot-store-panels")) {
                    item(key = "all-panels") {
                        FilterChip(selected = selectedPanel == null,
                            onClick = { panelId = null; category = null },
                            label = { Text("همه") }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                    items(panels, key = { it.first }) { panel ->
                        FilterChip(selected = selectedPanel == panel.first,
                            onClick = { panelId = panel.first; category = null },
                            label = { Text(panel.second, maxLines = 1) },
                            modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
            }
            if (categories.isNotEmpty()) {
                item(key = "bot-category-heading") {
                    Text("دسته‌بندی محصولات", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                item(key = "bot-categories") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("bot-store-categories")) {
                        item(key = "all-categories") {
                            FilterChip(selected = selectedCategory == null,
                                onClick = { category = null },
                                label = { Text("همه محصولات") }, modifier = Modifier.heightIn(min = 48.dp))
                        }
                        items(categories, key = { it }) { option ->
                            FilterChip(selected = selectedCategory == option,
                                onClick = { category = option },
                                label = { Text(option, maxLines = 1) },
                                modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                }
            }
            item(key = "bot-list-header") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("لیست محصولات", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    Text(visible.size.toPersianDigits() + " مورد",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            if (visible.isEmpty()) item(key = "bot-no-match") {
                Text("محصولی با این فیلتر پیدا نشد.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(visible, key = { it.key }, contentType = { "bot-plan" }) { plan ->
                GanjGlassSurface(
                    role = GanjGlassRole.Dense, shapeRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth().testTag("bot-product-" + plan.key),
                    padding = PaddingValues(16.dp),
                    accent = if (plan.featured) BotStoreGold else MaterialTheme.colorScheme.outline) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(plan.name, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(plan.panel + " / " + plan.category,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (plan.badge.isNotBlank()) {
                            Text(plan.badge, modifier = Modifier.padding(start = 8.dp),
                                color = BotStoreGold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("مدت: " + (if (plan.durationDays > 0) plan.durationDays.toPersianDigits() + " روز" else "طبق پلن"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                        Text("حجم: " + (if (plan.volumeGb > 0) plan.volumeGb.toLong().toPersianDigits() + " گیگ" else "طبق پلن"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (plan.description.isNotBlank()) {
                        Text(plan.description, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("قیمت پایه ربات", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                            Text(String.format(Locale.US, "%,d", plan.priceToman).toPersianDigits() + " تومان",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                        }
                        Button(onClick = { uriHandler.openUri(plan.purchaseUrl) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("bot-buy-" + plan.key)) {
                            Text("مشاهده و خرید")
                        }
                    }
                }
            }
            item(key = "bot-catalog-footnote") {
                Text("قیمت نهایی، تخفیف حساب، موجودی و امکان خرید در صفحه رسمی گنج بررسی می‌شود.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}
