package com.ganj.vpn.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal fun exactAppVariant(products: List<GanjBotProduct>, tier: String?, days: Int, devices: Int): GanjBotProduct? =
    products.firstOrNull {it.appTier==tier&&it.durationDays==days&&it.deviceLimit==devices}

/** Four tier families, backed by exact server products rather than app-calculated prices. */
@Composable
internal fun GanjTierCatalog(products: List<GanjBotProduct>,onOpenProduct:(String)->Unit,onOpenWallet:()->Unit,
    onOpenTransactions:()->Unit,loading:Boolean,error:Boolean,onRefresh:()->Unit,modifier:Modifier=Modifier) {
    var tier by rememberSaveable {mutableStateOf<String?>(null)}
    var days by rememberSaveable {mutableStateOf(30)}
    var devices by rememberSaveable {mutableStateOf(1)}
    val tiers=products.filter {it.appTier!=null}.groupBy {it.appTier!!}
    val chosen=exactAppVariant(products,tier,days,devices)
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=responsiveHorizontalPadding(),vertical=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("اشتراک VIP گنج",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text("پلن، مدت و تعداد دستگاه را انتخاب کنید.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(loading)item {Text("در حال دریافت…")}
        if(error)item {GanjInlineStatusPanel("به‌روزرسانی کامل نشد","مبلغ نهایی پیش از پرداخت از ربات بررسی می‌شود.",actionText="تلاش دوباره",onAction=onRefresh)}
        listOf("economy","standard","professional","premium").forEach {key ->
            val options=tiers[key].orEmpty()
            val base=options.minByOrNull {it.priceToman}
            if(base!=null)item(key=key) {
                GanjGlassButton((base.appTierName?:key)+" · "+if(key=="premium")"دوره‌های ۳۰، ۶۰ و ۹۰ روزه" else "${base.volumeGb.toInt().toPersianDigits()} گیگ",
                    {tier=key;days=options.minOf{it.durationDays};devices=options.filter{it.durationDays==days}.minOf{it.deviceLimit}},
                    Modifier.fillMaxWidth(),filled=tier==key)
                Text("از ${botMoney(base.priceToman)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(tier!=null)item {
            val rows=tiers[tier].orEmpty()
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("مدت اشتراک",style=MaterialTheme.typography.titleMedium)
                rows.map{it.durationDays}.distinct().sorted().forEach {period -> GanjChoicePill("${period.toPersianDigits()} روز",days==period,{days=period;devices=rows.filter{it.durationDays==period}.minOf{it.deviceLimit}},Modifier.fillMaxWidth())}
                Text("تعداد دستگاه",style=MaterialTheme.typography.titleMedium)
                rows.filter{it.durationDays==days}.map{it.deviceLimit}.distinct().sorted().forEach {limit -> GanjChoicePill("${limit.toPersianDigits()} دستگاه",devices==limit,{devices=limit},Modifier.fillMaxWidth())}
                chosen?.let {p ->
                    Text(p.appTierName.orEmpty()+" · ${p.volumeGb.toInt().toPersianDigits()} گیگ · ${p.durationDays.toPersianDigits()} روز · ${p.deviceLimit.toPersianDigits()} دستگاه",style=MaterialTheme.typography.titleMedium)
                    Text(p.description,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(botMoney(p.priceToman),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    GanjGlassButton("بررسی و ادامه خرید",{onOpenProduct(p.purchaseUrl)},Modifier.fillMaxWidth())
                } ?: Text("این ترکیب موجود نیست؛ گزینه‌های موجود را انتخاب کنید.")
            }
        }
        item {GanjGlassButton("کیف پول",onOpenWallet,Modifier.fillMaxWidth(),filled=false);GanjGlassButton("تراکنش‌ها",onOpenTransactions,Modifier.fillMaxWidth(),filled=false)}
    }
}
