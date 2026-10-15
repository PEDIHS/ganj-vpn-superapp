package com.ganj.vpn.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Read-only balance from the canonical retail-bot account; never a fake local ledger. */
internal fun botReadVerifiedBalance(raw: Double): Long {
    require(raw.isFinite() && raw >= -1_000_000_000_000.0 &&
        raw <= 1_000_000_000_000.0 && raw % 1.0 == 0.0) {
        "Invalid wallet balance response"
    }
    return raw.toLong()
}

internal fun botReadVerifiedBalance(data: JSONObject): Long =
    botReadVerifiedBalance(data.getDouble("balance"))

@Composable
internal fun GanjBotProfileWalletSummary(
    api: GanjBotStoreApi,
    onOpenWallet: () -> Unit,
    onOpenTransactions: () -> Unit,
) {
    var balance by remember(api) { mutableStateOf<Long?>(null) }
    var busy by remember(api) { mutableStateOf(true) }
    var message by remember(api) { mutableStateOf<String?>(null) }
    var refresh by remember(api) { mutableIntStateOf(0) }
    LaunchedEffect(api, refresh) {
        busy = true
        message = null
        try {
            val payload = api.request("wallet_methods").getJSONObject("data")
            balance = botReadVerifiedBalance(payload)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            message = ganjBotStoreError(failure)
        } finally {
            busy = false
        }
    }
    GanjGlassSurface(
        role = GanjGlassRole.OpaqueFallback,
        modifier = Modifier.fillMaxWidth().testTag("profile-wallet-live"),
        shapeRadius = 14.dp,
        padding = PaddingValues(15.dp),
    ) {
        Text("کیف پول گنج", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold)
        Text(
            balance?.let(::botMoney) ?: if (busy) "در حال دریافت موجودی…" else "موجودی قابل دریافت نیست",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("profile-wallet-balance"),
        )
        if (busy && balance != null) Text("در حال همگام‌سازی با ربات",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (message != null) Text(message.orEmpty(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GanjGlassButton("افزایش موجودی", onOpenWallet, Modifier.weight(1f),
                shapeRadius = 10.dp)
            GanjGlassButton("تراکنش‌ها", onOpenTransactions, Modifier.weight(1f),
                filled = false, shapeRadius = 10.dp)
        }
        GanjGlassButton("به‌روزرسانی موجودی", { if (!busy) refresh++ },
            Modifier.fillMaxWidth(), enabled = !busy, filled = false, shapeRadius = 10.dp)
    }
}
