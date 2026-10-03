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
    // Recalculate search results only when the fetched service snapshot or query changes.
    // Connection-state and latency updates must not repeatedly filter the subscription list.
    val services = remember(state.services, query) {
        state.serviceItems.filter {
            query.isBlank() ||
                it.username?.contains(query, ignoreCase = true) == true ||
                it.displayName.contains(query, ignoreCase = true)
        }
    }
    val activeCount = remember(state.services) { state.serviceItems.count { it.isActive } }
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("subscriptions-screen").selectableGroup(),
        contentPadding = PaddingValues(horizontal = responsiveHorizontalPadding(), vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StitchSimpleHeader(
                title = stringResource(R.string.subscriptions_title),
                subtitle = stringResource(R.string.subscriptions_subtitle),
                badge = stringResource(R.string.subscriptions_active_count, activeCount.toPersianDigits()),
                goldBadge = state.serviceItems.any { it.isActive && it.tier != UiTier.FREE },
            )
        }
        item {
            GanjSubscriptionsOverview(
                activeCount = activeCount,
                totalCount = state.serviceItems.size,
                selectedUsername = state.selectedService?.let { subscriptionUsername(it) },
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
private fun GanjSubscriptionsOverview(
    activeCount: Int,
    totalCount: Int,
    selectedUsername: String?,
) {
    val shape = remember { RoundedCornerShape(26.dp) }
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color(0xFF075638), Color(0xFF0E281D), Color(0xFF111C16)),
                ),
            )
            .border(1.dp, GanjGold.copy(alpha = 0.25f), shape)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.subscription_overview_title),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(activeCount.toPersianDigits(), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = Color(0xFF88EBB1))
                Text(stringResource(R.string.subscription_overview_active),
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFD0E3D7))
            }
            Column(Modifier.weight(1f)) {
                Text(totalCount.toPersianDigits(), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = GanjGoldBright)
                Text(stringResource(R.string.subscription_overview_total),
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFD0E3D7))
            }
        }
        if (selectedUsername != null) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
            Text(
                stringResource(R.string.subscription_overview_selected, selectedUsername),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFE2F4E7),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun GanjSubscriptionCard(service: ServiceUiModel, selected: Boolean, onClick: () -> Unit) {
    val effects = LocalGanjVisualEffectsPolicy.current
    val reduceMotion = effects.reduceMotion || effects.tier == GanjEffectsTier.Reduced
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && !reduceMotion) 0.985f else 1f,
        if (reduceMotion) snap() else tween(120),
        label = "subscriptionPress",
    )
    val fraction = service.usageFraction
    val accent = when {
        !service.isActive -> MaterialTheme.colorScheme.onSurfaceVariant
        fraction != null && fraction >= 0.9f -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val borderColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
        if (reduceMotion) snap() else tween(170),
        label = "subscriptionSelection",
    )
    val progress by animateFloatAsState(
        fraction ?: 0f,
        if (reduceMotion) snap() else tween(340),
        label = "subscriptionUsage",
    )
    val description = stringResource(
        if (selected) R.string.subscription_selected else R.string.subscription_not_selected,
    )
    val shape = remember { RoundedCornerShape(24.dp) }
    val premium = service.tier == UiTier.VIP

    Column(
        modifier = Modifier.fillMaxWidth()
            .testTag("subscription-" + service.entitlementId)
            .scale(scale)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.055f)
                else MaterialTheme.colorScheme.surface,
            )
            .border(if (selected) 1.5.dp else 1.dp, borderColor, shape)
            .semantics(mergeDescendants = true) { stateDescription = description }
            .selectable(
                selected = selected,
                enabled = service.isActive,
                role = Role.RadioButton,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                    .background(
                        if (premium) GanjGold.copy(alpha = 0.13f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.11f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (premium) "◆" else "◈", style = MaterialTheme.typography.titleLarge,
                    color = if (premium) GanjGold else MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.subscription_username_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(subscriptionUsername(service), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(1.5.dp, borderColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Text("✓", color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GanjStatusPill(
                text = serviceStatusText(service.status),
                tone = if (service.isActive) GanjStatusTone.Positive else GanjStatusTone.Neutral,
            )
            if (premium) GanjStatusPill(
                text = stringResource(R.string.visual_signature),
                tone = GanjStatusTone.Premium,
            )
            if (selected) Text(stringResource(R.string.subscription_selected),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.13f))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.subscription_total_traffic),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(subscriptionTraffic(service.trafficLimitBytes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    fraction?.let { ((it * 100).roundToInt()).toPersianDigits() + "٪" }
                        ?: stringResource(
                            if (service.trafficUsageAvailable && service.trafficLimitBytes == null)
                                R.string.subscription_unlimited else R.string.subscription_usage_unavailable,
                        ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (fraction == null) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                )
                Text(stringResource(R.string.subscription_used_percent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (fraction != null) {
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) },
            ) {
                Box(
                    Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape)
                        .background(accent),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.subscription_used_traffic,
                    if (service.trafficUsageAvailable) subscriptionTraffic(service.trafficUsedBytes)
                    else stringResource(R.string.subscription_usage_unavailable)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (service.trafficLimitBytes != null && service.trafficUsageAvailable) {
                    Text(stringResource(R.string.subscription_remaining_traffic,
                        subscriptionTraffic(service.remainingBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                stringResource(
                    if (service.isActive) R.string.subscription_choose_config
                    else R.string.subscription_inactive,
                ) + if (service.isActive) " ←" else "",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (service.isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        subtitle = subscriptionUsername(service), onDismiss = onDismiss, skipPartiallyExpanded = true) {
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
