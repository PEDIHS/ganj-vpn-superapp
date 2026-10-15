package com.ganj.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
            botStoreReadCatalog(envelope)
        } finally {
            conn.disconnect()
        }
    }
}

private fun GanjBotProduct.isVip(): Boolean = listOf(name,category,badge,panel).any {
    it.contains("VIP",ignoreCase=true)||it.contains("وی آی پی")||it.contains("وی‌آی‌پی")
}

@Composable
internal fun StitchBotSyncedStoreScreen(
    onOpenProduct: (String) -> Unit,
    onOpenWallet: () -> Unit,
    onOpenTransactions: () -> Unit,
    modifier: Modifier = Modifier,
    loadCatalog: suspend () -> List<GanjBotProduct> = { GanjBotCatalog.fetch() },
) {
    var retryKey by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var products by remember { mutableStateOf(emptyList<GanjBotProduct>()) }
    var panelId by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf<Int?>(null) }
    var vipOnly by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(vipOnly) { vipOnly=false }
    LaunchedEffect(retryKey) {
        loading=true;error=false
        try { products=loadCatalog() }
        catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){products=emptyList();error=true}
        finally {loading=false}
    }
    val panels=remember(products){botStorePanels(products)}
    val selectedPanel=panelId?.takeIf { key->panels.any {it.first==key} }
    val categories=remember(products,selectedPanel){botStoreCategories(products,selectedPanel)}
    val selectedCategory=category?.takeIf {it in categories}
    val candidates=remember(products,selectedPanel,selectedCategory,query,vipOnly){
        botStoreFilter(products,selectedPanel,selectedCategory,query).filter{!vipOnly||it.isVip()}
    }
    val durations=remember(candidates){candidates.map{it.durationDays}.filter{it>0}.distinct().sorted()}
    val visible=remember(candidates,duration,sort){
        val rows=candidates.filter{duration==null||duration==it.durationDays}
        when(sort){1->rows.sortedBy{it.priceToman};2->rows.sortedByDescending{it.volumeGb};3->rows.sortedByDescending{it.durationDays}
            else->rows.sortedWith(compareByDescending<GanjBotProduct>{it.featured}.thenBy{it.priceToman})}
    }
    val chosen=visible.firstOrNull{it.key==selectedKey}
    val ink=MaterialTheme.colorScheme.onSurface
    val muted=MaterialTheme.colorScheme.onSurfaceVariant
    val gold=LocalGanjContentPalette.current.premiumText
    LazyColumn(modifier.fillMaxSize().testTag("bot-synced-store"),
        contentPadding=PaddingValues(horizontal=responsiveHorizontalPadding(),vertical=20.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="bot-header") {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(vipOnly) AccountBackAction({vipOnly=false})
                Column(Modifier.weight(1f)) {
                    Text(if(vipOnly)"ارتقا به VIP" else "فروشگاه",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold,color=ink)
                    Text(if(vipOnly)"اشتراک ویژه گنج" else "اشتراک متناسب با نیاز شما",style=MaterialTheme.typography.labelSmall,color=muted)
                }
                Text("GANJ",style=MaterialTheme.typography.labelMedium,color=gold)
            }
        }
        if(vipOnly || products.any{it.isVip()}) item(key="bot-vip-hero") {
            val shape=RoundedCornerShape(17.dp)
            Column(Modifier.fillMaxWidth().clip(shape)
                .background(if(referenceDark())Brush.linearGradient(listOf(Color(0xFF33513A),Color(0xFF233D2D),Color(0xFF5A4C2D)))
                    else Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer,MaterialTheme.colorScheme.secondaryContainer)))
                .border(1.dp,gold.copy(alpha=.6f),shape)
                .clickable(enabled=!vipOnly,role=Role.Button,onClick={vipOnly=true;selectedKey=null;duration=null;category=null})
                .padding(20.dp).testTag("vip-upgrade-entry"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("GANJ / VIP",style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Medium,color=gold)
                Text(if(vipOnly)"یک انتخاب ویژه" else "انتخاب ویژه شما",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,color=ink)
                Text(if(vipOnly)"مدت و مشخصات پلن VIP خود را بررسی کنید." else "مشاهده و بررسی پلن‌های VIP",style=MaterialTheme.typography.bodySmall,color=muted)
            }
        }
        when {
            loading -> item(key="loading"){GanjLoadingState("در حال دریافت پلن‌ها")}
            error -> item(key="error"){GanjInlineStatusPanel("دریافت فروشگاه ناموفق بود","ارتباط با فروشگاه را بررسی کنید و دوباره تلاش کنید.",accent=MaterialTheme.colorScheme.error,tone=GanjStatusTone.Danger,actionText="تلاش دوباره",onAction={retryKey++},modifier=Modifier.testTag("bot-store-retry"))}
            products.isEmpty() -> item(key="empty"){EmptyCard("پلنی موجود نیست","بعداً دوباره تلاش کنید.",{retryKey++})}
            else -> {
                item(key="filters") {
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        GanjLiquidTextField(query,{query=it.take(120)},"جستجوی سرویس",Modifier.fillMaxWidth().testTag("bot-store-search"))
                        Text("لوکیشن",style=MaterialTheme.typography.labelMedium,color=muted)
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.testTag("bot-store-panels")) {
                            item{GanjChoicePill("همه",selectedPanel==null,{panelId=null;category=null;duration=null;selectedKey=null})}
                            items(panels,key={it.first}){panel->GanjChoicePill(panel.second,selectedPanel==panel.first,{panelId=panel.first;category=null;duration=null;selectedKey=null})}
                        }
                        if(categories.isNotEmpty()) LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.testTag("bot-store-categories")) {
                            item{GanjChoicePill("همه محصولات",selectedCategory==null,{category=null;duration=null;selectedKey=null},accent=gold)}
                            items(categories,key={it}){option->GanjChoicePill(option,selectedCategory==option,{category=option;duration=null;selectedKey=null},accent=gold)}
                        }
                        if(durations.isNotEmpty()) {
                            Text("مدت اشتراک",style=MaterialTheme.typography.labelMedium,color=muted)
                            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                item{GanjChoicePill("همه دوره‌ها",duration==null,{duration=null;selectedKey=null})}
                                items(durations,key={it}){days->GanjChoicePill(days.toPersianDigits()+" روز",duration==days,{duration=days;selectedKey=null})}
                            }
                        }
                    }
                }
                item(key="catalog-sort") {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("${visible.size.toPersianDigits()} گزینه آماده انتخاب",Modifier.weight(1f),
                            style=MaterialTheme.typography.labelSmall,color=muted)
                        androidx.compose.material3.TextButton(onClick={sort=(sort+1)%4;selectedKey=null},modifier=Modifier.testTag("bot-store-sort")) {
                            Text(listOf("پیشنهادی","کمترین قیمت","بیشترین حجم","بیشترین مدت")[sort],style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if(visible.isEmpty()) item{EmptyCard("نتیجه‌ای پیدا نشد","فیلتر یا عبارت جستجو را تغییر دهید.",{query="";duration=null;category=null})}
                items(visible,key={it.key},contentType={"bot-plan"}){plan->
                    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        GanjReferenceBotPlan(plan,plan.key==selectedKey,{selectedKey=plan.key})
                        if(chosen!=null && chosen.key==plan.key) {
                    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("مبلغ کل دوره",style=MaterialTheme.typography.bodySmall,color=muted)
                            Text(botPrice(chosen),style=MaterialTheme.typography.titleMedium,color=ink,fontWeight=FontWeight.SemiBold)
                        }
                        GanjGlassButton(if(vipOnly)"ادامه با VIP" else "بررسی و ادامه خرید",{onOpenProduct(chosen.purchaseUrl)},
                            modifier=Modifier.fillMaxWidth().testTag("bot-buy-"+chosen.key),accent=if(vipOnly)GanjReference.Gold else MaterialTheme.colorScheme.primary,
                            contentColor=if(vipOnly)Color(0xFF292615) else MaterialTheme.colorScheme.onPrimary)
                        Text("بررسی مشخصات و تأیید خرید در همین برنامه",style=MaterialTheme.typography.labelSmall,color=muted)
                    }
                        }
                    }
                }
            }
        }
        item(key="finance-shortcuts") {
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("حساب و پرداخت",style=MaterialTheme.typography.labelMedium,color=muted)
                GanjReferenceMenuRow("کیف پول","موجودی، شارژ و ارسال رسید",icon=GanjDestination.Store,onClick=onOpenWallet)
                GanjReferenceMenuRow("تراکنش‌ها","سوابق پرداخت و وضعیت بررسی",icon=GanjDestination.Store,onClick=onOpenTransactions)
            }
        }
    }
}

internal fun botPrice(plan:GanjBotProduct):String = String.format(Locale.US,"%,d",plan.priceToman).toPersianDigits()+" تومان"

@Composable
private fun GanjReferenceBotPlan(plan:GanjBotProduct,selected:Boolean,onSelect:()->Unit) {
    val gold=LocalGanjContentPalette.current.premiumText
    val premium=plan.isVip()||plan.featured
    val accent=if(premium)gold else MaterialTheme.colorScheme.primary
    val shape=RoundedCornerShape(13.dp)
    val effects=LocalGanjVisualEffectsPolicy.current
    val rim by androidx.compose.animation.animateColorAsState(if(selected)accent else MaterialTheme.colorScheme.outlineVariant,
        if(effects.reduceMotion)androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(180),label="storePlanSelection")
    Column(Modifier.fillMaxWidth().clip(shape).background(if(selected)LocalGanjContentPalette.current.selectedSurface else MaterialTheme.colorScheme.surface)
        .border(1.dp,rim,shape)
        .clickable(role=Role.RadioButton,onClick=onSelect)
        .semantics{this.selected=selected}
        .padding(15.dp).testTag("bot-product-"+plan.key),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(17.dp).border(1.dp,if(selected)accent else MaterialTheme.colorScheme.onSurfaceVariant,androidx.compose.foundation.shape.CircleShape),contentAlignment=Alignment.Center) {
                if(selected)Box(Modifier.size(7.dp).background(accent,androidx.compose.foundation.shape.CircleShape))
            }
            Text(plan.name,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.onSurface)
            if(plan.badge.isNotBlank()) Text(plan.badge,style=MaterialTheme.typography.labelSmall,color=gold)
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            GanjNativePlanArt(plan,Modifier.size(58.dp))
            Column(Modifier.weight(1f)) {
                Text("قیمت سرویس",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(botPrice(plan),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(if(plan.durationDays>0)plan.durationDays.toPersianDigits()+" روز" else "مدت طبق پلن",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if(plan.volumeGb>0)plan.volumeGb.toLong().toPersianDigits()+" گیگابایت" else "حجم طبق پلن",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(plan.description.isNotBlank()) Text(plan.description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=3,overflow=TextOverflow.Ellipsis)
        if(selected) Text("انتخاب‌شده",style=MaterialTheme.typography.labelSmall,color=accent)
    }
}

internal fun botStoreReadCatalog(envelope: JSONObject): List<GanjBotProduct> {
    val list = envelope.optJSONArray("data") ?: error("bot-catalog-invalid")
            check(list.length() <= 500) { "bot-catalog-too-many" }
    return buildList {
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
}
