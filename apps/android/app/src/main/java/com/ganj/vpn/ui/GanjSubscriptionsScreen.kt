package com.ganj.vpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ganj.vpn.R
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.ConnectionServer
import com.ganj.vpn.presentation.*
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
internal fun StitchSubscriptionsScreen(
    state: GanjUiState,
    onSelectService: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val services = state.serviceItems.filter {
        query.isBlank() || it.username?.contains(query, ignoreCase = true) == true
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("subscriptions-screen").selectableGroup(),
        contentPadding = PaddingValues(horizontal = responsiveHorizontalPadding(), vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            StitchSimpleHeader(
                title = stringResource(R.string.subscriptions_title),
                subtitle = stringResource(R.string.subscriptions_subtitle),
                badge = stringResource(R.string.subscriptions_active_count, state.serviceItems.count { it.isActive }.toPersianDigits()),
                goldBadge = state.serviceItems.any { it.isActive && it.tier != UiTier.FREE },
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.subscription_search)) },
                trailingIcon = if (query.isNotEmpty()) {
                    { TextButton(onClick = { query = "" }) { Text(stringResource(R.string.common_clear)) } }
                } else null,
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                ),
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.subscription_choose_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry, enabled = !state.refreshInProgress) {
                    Text(stringResource(R.string.common_refresh))
                }
            }
        }
        when (val content = state.services) {
            ContentState.Loading -> item { LoadingCard(stringResource(R.string.subscriptions_loading)) }
            ContentState.Empty -> item { EmptyCard(stringResource(R.string.subscriptions_empty), stringResource(R.string.subscriptions_empty_body), onRetry) }
            ContentState.AuthRequired -> item { AuthCard(onRetry) }
            is ContentState.Error -> item { ErrorCard(content.failure, onRetry) }
            is ContentState.Ready -> {
                if (services.isEmpty()) item { Text(stringResource(R.string.subscription_no_match), style = MaterialTheme.typography.bodyMedium) }
                items(services, key = { it.entitlementId }) { service ->
                    GanjSubscriptionCard(service, service.entitlementId == state.selectedEntitlementId) {
                        onSelectService(service.entitlementId)
                    }
                }
            }
        }
    }
}

@Composable
internal fun subscriptionUsername(service: ServiceUiModel): String = service.username?.let(::isolateTechnicalLtr)
    ?: stringResource(if (service.tier == UiTier.FREE) R.string.subscription_free else R.string.subscription_username_unavailable)

@Composable
internal fun GanjSubscriptionCard(service: ServiceUiModel, selected: Boolean, onClick: () -> Unit) {
    val effects = LocalGanjVisualEffectsPolicy.current
    val reduceMotion = effects.reduceMotion || effects.tier == GanjEffectsTier.Reduced
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !reduceMotion) 0.99f else 1f,
        if (reduceMotion) snap() else spring(dampingRatio = 1f, stiffness = 520f), label = "subscriptionPress")
    val accent = when {
        !service.isActive -> MaterialTheme.colorScheme.onSurfaceVariant
        (service.usageFraction ?: 0f) >= 0.85f -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val edge by animateColorAsState(if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
        if (reduceMotion) snap() else tween(170), label = "subscriptionSelection")
    val fraction = service.usageFraction
    val progress by animateFloatAsState(fraction ?: 0f,
        if (reduceMotion) snap() else tween(360), label = "subscriptionUsage")
    val description = stringResource(if (selected) R.string.subscription_selected else R.string.subscription_not_selected)
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier.fillMaxWidth().testTag("subscription-${service.entitlementId}")
            .scale(scale).clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (selected) 1.5.dp else 1.dp, edge, shape)
            .semantics(mergeDescendants = true) { stateDescription = description }
            .selectable(selected = selected, enabled = service.isActive, role = Role.RadioButton,
                interactionSource = interaction, indication = null, onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.subscription_username_label), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(subscriptionUsername(service), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(28.dp).clip(CircleShape).background(if (selected) accent else Color.Transparent)
                .border(1.dp, edge, CircleShape), contentAlignment = Alignment.Center) {
                if (selected) Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.subscription_total_traffic), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(subscriptionTraffic(service.trafficLimitBytes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(fraction?.let { "${(it * 100).roundToInt().toPersianDigits()}٪" }
                    ?: stringResource(if (service.trafficUsageAvailable && service.trafficLimitBytes == null)
                        R.string.subscription_unlimited else R.string.subscription_usage_unavailable),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
                if (fraction != null) Text(stringResource(R.string.subscription_used_percent),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (fraction != null) {
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(accent))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (service.trafficUsageAvailable) Text(stringResource(R.string.subscription_used_traffic,
                    subscriptionTraffic(service.trafficUsedBytes)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(serviceStatusText(service.status), style = MaterialTheme.typography.labelMedium, color = accent)
            }
            Text(stringResource(if (service.isActive) R.string.subscription_choose_config else R.string.subscription_inactive),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = accent)
        }
    }
}

internal fun subscriptionTraffic(bytes: Long?): String = when {
    bytes == null -> "نامحدود"
    bytes >= 1_073_741_824L -> "${trafficDecimal(bytes.toDouble() / 1_073_741_824.0)} گیگابایت"
    bytes >= 1_048_576L -> "${trafficDecimal(bytes.toDouble() / 1_048_576.0)} مگابایت"
    bytes >= 1024L -> "${trafficDecimal(bytes.toDouble() / 1024.0)} کیلوبایت"
    else -> "${bytes.toPersianDigits()} بایت"
}

private fun trafficDecimal(value: Double): String = String.format(java.util.Locale.ROOT, "%.1f", value).removeSuffix(".0").toPersianDigits()

@Composable
internal fun StitchConfigSelectionSheet(
    service: ServiceUiModel,
    selectedServer: ConnectionServer?,
    loadServers: suspend (String) -> ApiResult<List<ConnectionServer>>,
    latency: SessionLatencyManager?,
    onSelect: (ConnectionServer) -> Unit,
    onDismiss: () -> Unit,
) {
    var catalog by remember(service.entitlementId) { mutableStateOf<ContentState<ConnectionServer>>(ContentState.Loading) }
    var refresh by remember(service.entitlementId) { mutableIntStateOf(0) }
    LaunchedEffect(service.entitlementId, refresh) {
        catalog = ContentState.Loading
        val result = loadServers(service.entitlementId)
        if (!isActive) return@LaunchedEffect
        catalog = when (result) {
            is ApiResult.Success -> if (result.value.isEmpty()) ContentState.Empty else ContentState.Ready(result.value)
            is ApiResult.Failure -> ContentState.Error(ConnectionFailures.api(result.error))
        }
    }
    val readings = latency?.state?.collectAsState()?.value ?: LatencySnapshot()
    val servers = (catalog as? ContentState.Ready)?.items.orEmpty()
    val busy = readings.measuring.any { it.serviceId == service.entitlementId }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.65f
    GanjLiquidBottomSheet(title = stringResource(R.string.config_picker_title),
        subtitle = subscriptionUsername(service), onDismiss = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxHeight).testTag("config-picker").selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.config_picker_scope), modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { refresh++ }) { Text(stringResource(R.string.common_refresh)) }
                }
            }
            when (val content = catalog) {
                ContentState.Loading -> item { LoadingCard(stringResource(R.string.config_picker_loading)) }
                ContentState.Empty -> item { EmptyCard(stringResource(R.string.config_picker_empty), stringResource(R.string.config_picker_empty_body)) { refresh++ } }
                ContentState.AuthRequired -> item { AuthCard { refresh++ } }
                is ContentState.Error -> item { ErrorCard(content.failure) { refresh++ } }
                is ContentState.Ready -> {
                    item {
                        TextButton(enabled = !busy && latency != null,
                            onClick = { latency?.measure(service.entitlementId, servers.map { it.id }) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (busy) R.string.ping_measuring else R.string.ping_all))
                        }
                    }
                    items(content.items, key = { it.id }) { server ->
                        val key = LatencyKey(service.entitlementId, server.id)
                        val reading = readings.readings[key]
                        val measuring = key in readings.measuring
                        GanjConfigOption(server, server.id == selectedServer?.id, reading, measuring,
                            onSelect = { onSelect(server) }, onPing = { latency?.measure(service.entitlementId, listOf(server.id)) },
                            canPing = latency != null)
                    }
                }
            }
        }
    }
}

@Composable
private fun GanjConfigOption(server: ConnectionServer, selected: Boolean, reading: LatencyReading?, measuring: Boolean,
    onSelect: () -> Unit, onPing: () -> Unit, canPing: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("config-option-${server.id}")
        .clip(RoundedCornerShape(16.dp))
        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(server.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            Text(isolateTechnicalLtr(server.protocols.joinToString(" / ") { it.name }), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(when {
                measuring -> stringResource(R.string.ping_measuring)
                reading?.result?.latencyMillis != null -> persianTechnicalMetric(reading.result.latencyMillis.toString(), "ms")
                reading?.result is LatencyProbeResult.Failed -> latencyFailureLabel(reading.result.failure)
                else -> stringResource(R.string.ping_not_measured)
            }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            reading?.let { Text(latencyReadingTime(it.measuredAtMillis), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        TextButton(onClick = onPing, enabled = canPing && !measuring, modifier = Modifier.testTag("ping-${server.id}")) {
            Text(stringResource(R.string.config_ping))
        }
        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}
