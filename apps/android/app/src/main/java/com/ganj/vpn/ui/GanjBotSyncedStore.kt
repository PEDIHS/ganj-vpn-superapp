package com.ganj.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
internal fun StitchBotSyncedStoreScreen(
    onOpenProduct: (String) -> Unit,
    onOpenWallet: () -> Unit,
    onOpenTransactions: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    val stackFinancialActions =
        androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.35f ||
            androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("bot-synced-store"),
        contentPadding = PaddingValues(
            horizontal = responsiveHorizontalPadding(),
            vertical = 18.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "bot-header") {
            StitchSimpleHeader(
                title = "فروشگاه گنج",
                subtitle = "خرید مستقیم از فروشگاه رسمی و همگام با ربات",
                badge = "فروشگاه رسمی",
                goldBadge = true,
            )
        }

        item(key = "bot-heading") {
            GanjGlassSurface(
                role = GanjGlassRole.Prominent,
                shapeRadius = 26.dp,
                modifier = Modifier.fillMaxWidth().testTag("bot-store-hero"),
                padding = PaddingValues(18.dp),
                accent = BotStoreGold,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GanjStatusPill(
                    text = "کاتالوگ زنده گنج",
                    tone = GanjStatusTone.Premium,
                )
                Text(
                    "پلن مناسب خودت را پیدا کن",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "لوکیشن، دسته‌بندی، قیمت پایه و مشخصات محصولات مستقیماً از فروشگاه گنج دریافت می‌شوند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!loading && !error) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GanjInfoChip(
                            text = products.size.toPersianDigits() + " محصول",
                            modifier = Modifier.weight(1f),
                            accent = MaterialTheme.colorScheme.primary,
                        )
                        GanjInfoChip(
                            text = panels.size.toPersianDigits() + " لوکیشن",
                            modifier = Modifier.weight(1f),
                            accent = BotStoreGold,
                        )
                    }
                }
            }
        }

        item(key = "bot-financial-quick-actions") {
            GanjGlassSurface(
                role = GanjGlassRole.Dense,
                modifier = Modifier.fillMaxWidth().testTag("bot-store-finance-actions"),
                shapeRadius = 22.dp,
                padding = PaddingValues(12.dp),
                accent = MaterialTheme.colorScheme.outline,
            ) {
                GanjSectionHeader(
                    title = "حساب و پرداخت",
                    supporting = "موجودی و سوابق همان حساب ربات گنج",
                )
                if (stackFinancialActions) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GanjQuickActionTile(
                            title = "شارژ کیف پول",
                            subtitle = "پرداخت، ارسال رسید و بررسی وضعیت",
                            onClick = onOpenWallet,
                            modifier = Modifier.fillMaxWidth().testTag("bot-store-wallet"),
                            accent = MaterialTheme.colorScheme.primary,
                            marker = "+",
                        )
                        GanjQuickActionTile(
                            title = "تراکنش‌ها",
                            subtitle = "مشاهده پرداخت‌ها و وضعیت بررسی",
                            onClick = onOpenTransactions,
                            modifier = Modifier.fillMaxWidth(),
                            accent = BotStoreGold,
                            marker = "↕",
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GanjQuickActionTile(
                            title = "شارژ کیف پول",
                            subtitle = "پرداخت و ارسال رسید",
                            onClick = onOpenWallet,
                            modifier = Modifier.weight(1f).testTag("bot-store-wallet"),
                            accent = MaterialTheme.colorScheme.primary,
                            marker = "+",
                        )
                        GanjQuickActionTile(
                            title = "تراکنش‌ها",
                            subtitle = "سوابق و وضعیت‌ها",
                            onClick = onOpenTransactions,
                            modifier = Modifier.weight(1f),
                            accent = BotStoreGold,
                            marker = "↕",
                        )
                    }
                }
            }
        }

        when {
            loading -> item(key = "bot-loading") {
                GanjLoadingState("در حال همگام‌سازی فروشگاه گنج")
            }

            error -> item(key = "bot-error") {
                GanjInlineStatusPanel(
                    title = "دریافت فروشگاه ناموفق بود",
                    body = "برای جلوگیری از نمایش قیمت یا پلن قدیمی، اطلاعات جایگزین نشان داده نمی‌شود.",
                    accent = MaterialTheme.colorScheme.error,
                    tone = GanjStatusTone.Danger,
                    actionText = "تلاش دوباره",
                    onAction = { retryKey++ },
                    secondaryActionText = "باز کردن فروشگاه گنج",
                    onSecondaryAction = { uriHandler.openUri(BOT_STORE_HOME) },
                    modifier = Modifier.testTag("bot-store-retry"),
                )
            }

            products.isEmpty() -> item(key = "bot-empty") {
                EmptyCard(
                    "محصول فعالی در ربات نیست",
                    "با افزودن محصول در فروشگاه گنج، این صفحه خودکار به‌روز می‌شود.",
                    onAction = { retryKey++ },
                )
            }

            else -> {
                item(key = "bot-discovery") {
                    GanjGlassSurface(
                        role = GanjGlassRole.Dense,
                        modifier = Modifier.fillMaxWidth(),
                        shapeRadius = 24.dp,
                        padding = PaddingValues(14.dp),
                        accent = MaterialTheme.colorScheme.primary,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GanjSectionHeader(
                            title = "پیدا کردن سرویس",
                            supporting = "ابتدا لوکیشن و سپس دسته محصول را محدود کنید.",
                        )
                        GanjLiquidTextField(
                            value = query,
                            onValueChange = { query = it.take(120) },
                            label = "جستجوی نام سرویس",
                            modifier = Modifier.fillMaxWidth().testTag("bot-store-search"),
                        )

                        Text(
                            "لوکیشن",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.testTag("bot-store-panels"),
                        ) {
                            item(key = "all-panels") {
                                GanjChoicePill(
                                    text = "همه",
                                    selected = selectedPanel == null,
                                    onClick = { panelId = null; category = null },
                                )
                            }
                            items(panels, key = { it.first }) { panel ->
                                GanjChoicePill(
                                    text = panel.second,
                                    selected = selectedPanel == panel.first,
                                    onClick = { panelId = panel.first; category = null },
                                )
                            }
                        }

                        if (categories.isNotEmpty()) {
                            Text(
                                "دسته‌بندی",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.testTag("bot-store-categories"),
                            ) {
                                item(key = "all-categories") {
                                    GanjChoicePill(
                                        text = "همه محصولات",
                                        selected = selectedCategory == null,
                                        onClick = { category = null },
                                        accent = BotStoreGold,
                                    )
                                }
                                items(categories, key = { it }) { option ->
                                    GanjChoicePill(
                                        text = option,
                                        selected = selectedCategory == option,
                                        onClick = { category = option },
                                        accent = BotStoreGold,
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "bot-list-header") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GanjSectionHeader(
                            title = "محصولات",
                            supporting = "قیمت نهایی هنگام خرید با حساب شما بررسی می‌شود.",
                            modifier = Modifier.weight(1f),
                        )
                        GanjStatusPill(
                            visible.size.toPersianDigits() + " مورد",
                            tone = GanjStatusTone.Neutral,
                        )
                    }
                }

                if (visible.isEmpty()) {
                    item(key = "bot-no-match") {
                        GanjInlineStatusPanel(
                            title = "نتیجه‌ای پیدا نشد",
                            body = "فیلتر یا عبارت جستجو را تغییر دهید.",
                            accent = MaterialTheme.colorScheme.outline,
                            tone = GanjStatusTone.Neutral,
                            actionText = if (query.isNotBlank()) "پاک کردن جستجو" else null,
                            onAction = if (query.isNotBlank()) ({ query = "" }) else null,
                        )
                    }
                }

                items(visible, key = { it.key }, contentType = { "bot-plan" }) { plan ->
                    GanjBotProductCard(
                        plan = plan,
                        onOpenProduct = onOpenProduct,
                    )
                }

                item(key = "bot-catalog-footnote") {
                    GanjGlassSurface(
                        role = GanjGlassRole.Clear,
                        accent = BotStoreGold,
                        modifier = Modifier.fillMaxWidth(),
                        shapeRadius = 18.dp,
                        padding = PaddingValues(14.dp),
                    ) {
                        Text(
                            "قیمت نهایی، تخفیف حساب، موجودی، محدودیت خرید و فعال‌سازی در صفحه رسمی گنج بررسی می‌شود.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun GanjBotProductCard(
    plan: GanjBotProduct,
    onOpenProduct: (String) -> Unit,
) {
    val premium = plan.featured ||
        plan.badge.contains("VIP", ignoreCase = true) ||
        plan.badge.contains("پریمیوم", ignoreCase = true)
    val accent = if (premium) BotStoreGold else MaterialTheme.colorScheme.primary
    val tone = if (premium) GanjStatusTone.Premium else GanjStatusTone.Positive
    val price = String.format(Locale.US, "%,d", plan.priceToman).toPersianDigits() + " تومان"

    GanjGlassSurface(
        role = if (plan.featured) GanjGlassRole.Prominent else GanjGlassRole.Dense,
        shapeRadius = 24.dp,
        modifier = Modifier.fillMaxWidth().testTag("bot-product-" + plan.key),
        padding = PaddingValues(16.dp),
        accent = accent,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(plan.panel, plan.category).filter { it.isNotBlank() }.joinToString(" / "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (plan.badge.isNotBlank()) {
                GanjStatusPill(
                    text = plan.badge,
                    tone = tone,
                    modifier = Modifier.padding(start = 8.dp),
                )
            } else if (plan.featured) {
                GanjStatusPill(
                    text = "پیشنهادی",
                    tone = GanjStatusTone.Premium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GanjInfoChip(
                text = if (plan.durationDays > 0) plan.durationDays.toPersianDigits() + " روز" else "مدت طبق پلن",
                modifier = Modifier.weight(1f),
                accent = accent,
            )
            GanjInfoChip(
                text = if (plan.volumeGb > 0) plan.volumeGb.toLong().toPersianDigits() + " گیگ" else "حجم طبق پلن",
                modifier = Modifier.weight(1f),
                accent = accent,
            )
        }

        if (plan.description.isNotBlank()) {
            Text(
                plan.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val priceShape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f), priceShape)
                .border(1.dp, accent.copy(alpha = 0.16f), priceShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "قیمت پایه ربات",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    price,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (plan.oneBuy) {
                GanjStatusPill(
                    "خرید یک‌باره",
                    tone = GanjStatusTone.Warning,
                )
            }
        }

        GanjGlassButton(
            text = "مشاهده جزئیات و خرید",
            onClick = { onOpenProduct(plan.purchaseUrl) },
            modifier = Modifier.fillMaxWidth().testTag("bot-buy-" + plan.key),
            accent = accent,
            filled = true,
            contentColor = if (premium) MaterialTheme.colorScheme.onSecondary
            else MaterialTheme.colorScheme.onPrimary,
        )
    }
}