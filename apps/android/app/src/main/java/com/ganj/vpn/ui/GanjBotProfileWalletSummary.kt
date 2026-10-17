package com.ganj.vpn.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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

@Stable
internal class GanjProfileWalletState {
    var balance by mutableStateOf<Long?>(null)
    var busy by mutableStateOf(true)
    var message by mutableStateOf<String?>(null)
    var refreshGeneration by mutableIntStateOf(0)
    fun refresh() { if (!busy) refreshGeneration++ }
}

/** Owned by the screen, not a lazy list item: scrolling never restarts the balance request. */
@Composable
internal fun rememberGanjProfileWallet(api: GanjBotStoreApi, accountKey: String?): GanjProfileWalletState {
    val state = remember(api, accountKey) { GanjProfileWalletState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(api, accountKey, state.refreshGeneration, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Retain a verified in-session value while refreshing; don't flash a
            // loading placeholder on every screen resume.
            state.busy = state.balance == null
            state.message = null
            try {
                // One cheap owner-scoped DB snapshot, with existing bot API as
                // backward-compatible fallback until the PHP endpoint is deployed.
                val value = try {
                    val snapshot = api.request("app_snapshot").getJSONObject("data")
                    botReadVerifiedBalance(snapshot.getJSONObject("account"))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    botReadVerifiedBalance(api.request("wallet_methods").getJSONObject("data"))
                }
                state.balance = value
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                state.message = ganjBotStoreError(failure)
            } finally {
                state.busy = false
            }
        }
    }
    return state
}

@Composable
internal fun GanjBotProfileWalletSummary(
    api: GanjBotStoreApi,
    onOpenWallet: () -> Unit,
    onOpenTransactions: () -> Unit,
) {
    val state = rememberGanjProfileWallet(api, null)
    GanjProfileWalletCard(state.balance, state.busy, state.message, onOpenWallet, onOpenTransactions, state::refresh)
}
