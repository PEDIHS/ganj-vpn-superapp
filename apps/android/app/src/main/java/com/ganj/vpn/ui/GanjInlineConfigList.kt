package com.ganj.vpn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ganj.vpn.composition.ConnectionServerCompositionRegistry
import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.ConnectionServer
import com.ganj.vpn.presentation.ConnectionFailures
import com.ganj.vpn.presentation.LatencyKey
import com.ganj.vpn.presentation.LatencySnapshot
import com.ganj.vpn.presentation.ServiceUiModel
import com.ganj.vpn.presentation.SessionLatencyManager
import com.ganj.vpn.presentation.ContentState
import kotlinx.coroutines.CancellationException

/**
 * Persistent, account-scoped configuration list on Connect. Never launches a picker/dialog.
 * Cached catalog is display-only; the authenticated API still validates connection profiles.
 */
@Composable
internal fun StitchInlineConfigList(
    service: ServiceUiModel?,
    selectedServer: ConnectionServer?,
    accountCacheKey: String?,
    cachedServers: List<ConnectionServer>,
    loadServers: suspend (String) -> ApiResult<List<ConnectionServer>>,
    latency: SessionLatencyManager?,
    onSelect: (String, ConnectionServer) -> Unit,
) {
    val serviceId = service?.takeIf { it.isActive }?.entitlementId
    var reload by remember(serviceId, accountCacheKey) { mutableIntStateOf(0) }
    var refreshing by remember(serviceId, accountCacheKey) { mutableStateOf(false) }
    var refreshMessage by remember(serviceId, accountCacheKey) { mutableStateOf<String?>(null) }
    var catalog by remember(serviceId, accountCacheKey) {
        mutableStateOf<ContentState<ConnectionServer>>(
            if (serviceId == null) ContentState.Empty
            else if (cachedServers.isEmpty()) ContentState.Loading
            else ContentState.Ready(cachedServers)
        )
    }
    LaunchedEffect(serviceId, accountCacheKey, reload) {
        if (serviceId == null) return@LaunchedEffect
        refreshing = true
        refreshMessage = null
        try {
            when (val result = loadServers(serviceId)) {
                is ApiResult.Success -> {
                    catalog = if (result.value.isEmpty()) ContentState.Empty
                    else ContentState.Ready(result.value)
                    // The manager de-duplicates in-flight probes and caches recent measurements.
                    latency?.measure(serviceId, result.value.map { it.id })
                }
                is ApiResult.Failure -> {
                    if (catalog is ContentState.Ready &&
                        result.error !is ApiError.AuthenticationRequired &&
                        result.error !is ApiError.AuthenticationExpired) {
                        refreshMessage = "فهرست ذخیره‌شده نمایش داده می‌شود؛ اتصال باید توسط سرور تأیید شود."
                    } else {
                        catalog = ContentState.Error(ConnectionFailures.api(result.error))
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (catalog is ContentState.Ready) {
                refreshMessage = "به‌روزرسانی انجام نشد؛ لیست قبلی نمایش داده می‌شود."
            } else {
                catalog = ContentState.Error(ConnectionFailures.probe("probe.unavailable"))
            }
        } finally {
            refreshing = false
        }
    }
    val readings = latency?.state?.collectAsState()?.value ?: LatencySnapshot()
    val servers = (catalog as? ContentState.Ready)?.items.orEmpty()
    val probing = readings.measuring.any { it.serviceId == serviceId }
    Column(Modifier.fillMaxWidth().testTag("inline-config-section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("کانفیگ‌های اشتراک · ${servers.size.toPersianDigits()}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        if (serviceId == null) {
            Text("ابتدا اشتراک خود را انتخاب کنید تا کانفیگ‌هایش همین‌جا نمایش داده شوند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GanjGlassButton(if (refreshing) "در حال دریافت…" else "به‌روزرسانی",
                onClick = { reload++ }, enabled = !refreshing,
                modifier = Modifier.weight(1f), filled = false)
            GanjGlassButton(if (probing) "در حال سنجش…" else "پینگ همه",
                onClick = { latency?.measure(serviceId, servers.map { it.id }, force = true) },
                enabled = !probing && latency != null && servers.isNotEmpty(),
                modifier = Modifier.weight(1f), filled = false)
        }
        refreshMessage?.let {
            Text(it, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (val data = catalog) {
            ContentState.Loading -> LoadingCard("در حال دریافت کانفیگ‌ها…")
            ContentState.Empty -> EmptyCard("کانفیگی موجود نیست", "اشتراک انتخاب‌شده سرور فعالی ندارد.") { reload++ }
            ContentState.AuthRequired -> AuthCard { reload++ }
            is ContentState.Error -> ErrorCard(data.failure) { reload++ }
            is ContentState.Ready -> {
                // A bounded inner LazyColumn keeps dozens of configurations responsive.
                // All rows remain on Connect, with stable identities and no navigation.
                val largeText = LocalDensity.current.fontScale >= 1.5f
                val rowDp = if (largeText) 120 else 80
                val height = minOf(
                    LocalConfiguration.current.screenHeightDp.dp * 0.60f,
                    (servers.size * rowDp + 8).dp
                )
                LazyColumn(Modifier.fillMaxWidth().height(height).testTag("inline-config-list"),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(servers, key = { it.id }, contentType = { "config" }) { server ->
                        val key = LatencyKey(serviceId, server.id)
                        GanjConfigOption(
                            server = server,
                            selected = server.id == selectedServer?.id,
                            reading = readings.readings[key],
                            measuring = key in readings.measuring,
                            onSelect = { onSelect(serviceId, server) },
                            onPing = { latency?.measure(serviceId, listOf(server.id), force = true) },
                            canPing = latency != null
                        )
                    }
                }
                if (selectedServer == null) {
                    Text("برای اتصال، یکی از کانفیگ‌های بالا را انتخاب کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
