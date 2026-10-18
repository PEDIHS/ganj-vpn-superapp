package com.ganj.vpn.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal fun exactAppVariant(products: List<GanjBotProduct>, tier: String?, days: Int, devices: Int): GanjBotProduct? =
    products.firstOrNull { it.appTier == tier && it.durationDays == days && it.deviceLimit == devices }

/** Every period/device choice resolves to an existing product, never an interpolated price. */
@Composable
internal fun GanjTierCatalog(
    products: List<GanjBotProduct>, onOpenProduct: (String) -> Unit, onOpenWallet: () -> Unit,
    onOpenTransactions: () -> Unit, loading: Boolean, error: Boolean, onRefresh: () -> Unit,
    modifier: Modifier = Modifier, vipOnly: Boolean = false, onVipChanged: (Boolean) -> Unit = {},
    activeVip: Boolean = false, onOpenSubscription: () -> Unit = {},
) {
    var tier by rememberSaveable { mutableStateOf<String?>(null) }
    var days by rememberSaveable { mutableStateOf(30) }
    var devices by rememberSaveable { mutableStateOf(1) }
    var regularKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedVolumeKey by rememberSaveable { mutableStateOf<String?>(null) }
    val eligible = products.filter { !vipOnly || it.isVip() || it.appTier == "premium" }
    val tiers = eligible.filter { it.appTier != null }.groupBy { it.appTier!! }
    val order = listOf("economy", "standard", "plus", "professional", "premium")
    val matchingVariants = eligible.filter {
        it.appTier == tier && it.durationDays == days && it.deviceLimit == devices
    }
    val selected = matchingVariants.firstOrNull { it.key == selectedVolumeKey }
        ?: matchingVariants.firstOrNull()
        ?: eligible.firstOrNull { it.key == regularKey }
    fun choose(rows: List<GanjBotProduct>, key: String) {
        regularKey = null; tier = key; selectedVolumeKey = null
        days = rows.minOf { it.durationDays }
        devices = rows.filter { it.durationDays == days }.minOf { it.deviceLimit }
    }
    LazyColumn(modifier.fillMaxSize().then(profileRevealModifier(if (vipOnly) "tier-vip" else "tier-store"))
        .testTag("bot-synced-store"), contentPadding=PaddingValues(start=responsiveHorizontalPadding(),
            end=responsiveHorizontalPadding(), top=20.dp, bottom=GanjFloatingNavContentClearance),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if (vipOnly) AccountBackAction({ onVipChanged(false) })
                Column(Modifier.weight(1f)) {
                    Text(if (vipOnly) "GANJ · SIGNATURE COLLECTION" else "GANJ · FIND YOUR PLAN",
                        style=MaterialTheme.typography.labelSmall, color=if(vipOnly)signatureGold() else ganjSignatureTone(GanjSignatureTone.Azure))
                    Text(if (vipOnly) "گنج VIP" else "فروشگاه", style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.SemiBold)
                    Text("پلن، مدت و تعداد دستگاه را انتخاب کنید.", style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (vipOnly) item(key = "vip-hero") {
            GanjSignatureVipHero(Modifier.testTag("store-vip-hero"))
        }
        if (vipOnly && activeVip) item(key = "active-vip-status") {
            GanjSignatureVipMembership(onOpenSubscription)
        }
        else if (products.any { it.isVip() || it.appTier == "premium" }) item(key = "vip-banner") {
            GanjSignatureBanner("دنیای گنج VIP", "مشاهده و بررسی پلن‌های موجود", { onVipChanged(true) }, Modifier.testTag("vip-upgrade-entry"))
        }
        if (loading) item { GanjLoadingState("در حال دریافت پلن‌ها") }
        if (error) item {
            GanjInlineStatusPanel("به‌روزرسانی کامل نشد", "اطلاعات قبلی نمایش داده شده؛ مبلغ نهایی پیش از پرداخت از ربات بررسی می‌شود.",
                actionText="تلاش دوباره", onAction=onRefresh, modifier=Modifier.testTag("bot-store-stale"))
        }
        if (eligible.isEmpty() && !loading) item {
            GanjInlineStatusPanel("پلنی در دسترس نیست", "فروشگاه را دوباره بررسی کنید.",
                actionText = if (vipOnly) "همه پلن‌ها" else "به‌روزرسانی", onAction = { if (vipOnly) onVipChanged(false) else onRefresh() })
        }
        order.forEach { key ->
            val rows = tiers[key].orEmpty()
            val base = rows.minByOrNull { it.priceToman }
            if (base != null) item(key = "tier-$key") {
                Column(Modifier.testTag("tier-$key"), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    GanjReferenceBotPlan(if (tier == key) selected?.takeIf { it.appTier == key } ?: base else base,
                        tier == key, { choose(rows, key) }, startingPrice=tier != key)
                    if (tier == key) {
                        Text("مدت اشتراک", style=MaterialTheme.typography.titleSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            rows.map { it.durationDays }.distinct().sorted().forEach { period ->
                                GanjChoicePill("${period.toPersianDigits()} روز", days == period, {
                                    days = period
                                    selectedVolumeKey = null
                                    val limits = rows.filter { it.durationDays == period }.map { it.deviceLimit }
                                    if (devices !in limits) devices = limits.min()
                                }, Modifier.testTag("tier-period-$period"), accent=ganjSignatureTone(GanjSignatureTone.Azure))
                            }
                        }
                        Text("تعداد دستگاه", style=MaterialTheme.typography.titleSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            rows.filter { it.durationDays == days }.map { it.deviceLimit }.distinct().sorted().forEach { count ->
                                GanjChoicePill("${count.toPersianDigits()} دستگاه", devices == count, { devices = count; selectedVolumeKey = null },
                                    Modifier.testTag("tier-devices-$count"), accent=ganjSignatureTone(GanjSignatureTone.Iris))
                            }
                        }
                        val volumeVariants = rows.filter {
                            it.durationDays == days && it.deviceLimit == devices
                        }.distinctBy { it.volumeGb }
                        if (volumeVariants.isNotEmpty()) {
                            Text("انتخاب حجم سرویس", style=MaterialTheme.typography.titleSmall,
                                fontWeight=FontWeight.Bold, color=MaterialTheme.colorScheme.onSurface)
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),
                                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                volumeVariants.forEach { variant ->
                                    val volumeLabel = if (variant.volumeGb <= 0) "نامحدود"
                                        else String.format(java.util.Locale.US, "%.1f", variant.volumeGb)
                                            .removeSuffix(".0").toPersianDigits() + " گیگ"
                                    GanjChoicePill(volumeLabel, selected?.key == variant.key,
                                        { selectedVolumeKey=variant.key },
                                        Modifier.testTag("tier-volume-" + variant.key),
                                        accent=ganjSignatureTone(GanjSignatureTone.Gold))
                                }
                            }
                        }
                        val product = selected?.takeIf { it.appTier == key }
                        if (product != null) {
                            GanjBotVariantReviewCard(product)
                            GanjSignatureButton("بررسی فاکتور و ادامه خرید", { onOpenProduct(product.purchaseUrl) },
                                Modifier.fillMaxWidth().testTag("bot-buy-" + product.key))
                        } else Text("این ترکیب موجود نیست؛ گزینه‌های موجود را انتخاب کنید.", color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        // A mixed catalog must not hide original non-tier products.
        items(eligible.filter { it.appTier == null }, key = { it.key }) { product ->
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                GanjReferenceBotPlan(product, regularKey == product.key, { tier = null; regularKey = product.key })
                if (regularKey == product.key) {
                    GanjBotVariantReviewCard(product)
                    GanjSignatureButton("بررسی فاکتور و ادامه خرید", { onOpenProduct(product.purchaseUrl) },
                        Modifier.fillMaxWidth().testTag("bot-buy-" + product.key))
                }
            }
        }
        item(key = "finance") {
            GanjReferenceMenuRow("کیف پول", "موجودی، شارژ و ارسال رسید",
                accent=ganjSignatureTone(GanjSignatureTone.Azure),
                leading={ GanjProfileIconTile(GanjProfileSymbol.Wallet, GanjProfileAccent.Sky) },
                onClick=onOpenWallet)
            Spacer(Modifier.height(8.dp))
            GanjReferenceMenuRow("تراکنش‌ها", "سوابق پرداخت و وضعیت بررسی",
                accent=ganjSignatureTone(GanjSignatureTone.Coral),
                leading={ GanjProfileIcon(GanjProfileSymbol.History,
                    ganjSignatureTone(GanjSignatureTone.Coral), Modifier.size(25.dp)) },
                onClick=onOpenTransactions)
        }
    }
}

/** Real selected bot product; never synthesize a price or plan variant. */
@Composable
private fun GanjBotVariantReviewCard(product: GanjBotProduct) {
    val shape = RoundedCornerShape(20.dp)
    val ink = Color(0xFF243831)
    val volume = if (product.volumeGb > 0)
        String.format(java.util.Locale.US,"%.1f",product.volumeGb).removeSuffix(".0").toPersianDigits() + " گیگ"
        else "حجم طبق سرویس"
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFFFFAEF),Color(0xFFF1F7F3))))
            .border(1.dp,Color(0xFFD4C28F),shape)
            .padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(9.dp),
    ) {
        Text("بررسی انتخاب شما",style=MaterialTheme.typography.labelLarge,
            fontWeight=FontWeight.Bold,color=ink)
        Text(product.appDisplayName(),style=MaterialTheme.typography.titleMedium,
            fontWeight=FontWeight.ExtraBold,color=ink)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(volume,style=MaterialTheme.typography.labelMedium,color=ink)
            Text(if(product.durationDays>0) product.durationDays.toPersianDigits()+" روز" else "مدت طبق پلن",
                style=MaterialTheme.typography.labelMedium,color=ink)
            Text(product.deviceLimit.toPersianDigits()+" دستگاه",
                style=MaterialTheme.typography.labelMedium,color=ink)
        }
        Text(botPrice(product),style=MaterialTheme.typography.headlineSmall,
            fontWeight=FontWeight.ExtraBold,color=Color(0xFF765617))
        Text("مبلغ نهایی و تخفیف احتمالی در فاکتور رسمی ربات تأیید می‌شود.",
            style=MaterialTheme.typography.labelSmall,color=ink.copy(alpha=.75f))
    }
}
