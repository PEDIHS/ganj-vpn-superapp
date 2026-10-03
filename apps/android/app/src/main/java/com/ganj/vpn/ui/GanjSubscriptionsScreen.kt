package com.ganj.vpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    var query by rememberSaveable { mutableStateOf("") }
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
            GanjSubscriptionsHeader(
                title = stringResource(R.string.subscriptions_title),
                subtitle = stringResource(R.string.subscriptions_subtitle),
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
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
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
                items(services, key = { it.entitlementId }, contentType = { "subscription" }) { service ->
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

// Royal Emerald is deliberately built from Compose primitives: no bitmaps, runtime blur,
// nested shadows or infinite animations. Static geometry stays in the draw phase.
@Composable
private fun GanjSubscriptionsHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(17.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF0C5033), Color(0xFF10261B))))
                .border(1.dp, GanjGold.copy(alpha = 0.65f), RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center,
        ) {
            GanjSecurityIcon(tint = GanjGoldBright, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun GanjSubscriptionsOverview(
    activeCount: Int,
    totalCount: Int,
    selectedUsername: String?,
) {
    val shape = remember { RoundedCornerShape(28.dp) }
    Box(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(
                Color(0xFF0F6440), Color(0xFF0A3627), Color(0xFF091A13),
            )))
            .border(1.dp, GanjGold.copy(alpha = 0.40f), shape),
    ) {
        // The crest is a static watermark. Canvas avoids per-frame layout or bitmap allocation.
        Canvas(
            Modifier.align(Alignment.TopStart).size(112.dp).padding(11.dp),
        ) {
            val gold = Color(0xFFF0CD70)
            drawCircle(gold.copy(alpha = 0.13f), radius = size.minDimension * 0.45f,
                style = Stroke(width = 1.dp.toPx()))
            drawCircle(gold.copy(alpha = 0.08f), radius = size.minDimension * 0.30f,
                style = Stroke(width = 1.dp.toPx()))
            drawArc(gold.copy(alpha = 0.19f), 205f, 135f, false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 19.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(GanjEmeraldBright))
                Text(stringResource(R.string.subscription_overview_title),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = Color.White)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                GanjOverviewMetric(activeCount.toPersianDigits(),
                    stringResource(R.string.subscription_overview_active),
                    Color(0xFF8BEAB6), Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(48.dp)
                    .background(Color.White.copy(alpha = 0.12f)))
                GanjOverviewMetric(totalCount.toPersianDigits(),
                    stringResource(R.string.subscription_overview_total),
                    GanjGoldBright, Modifier.weight(1f))
            }
            if (selectedUsername != null) {
                HorizontalDivider(color = GanjGold.copy(alpha = 0.20f))
                Text(stringResource(R.string.subscription_overview_selected, selectedUsername),
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFE5F5EB),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun GanjOverviewMetric(value: String, name: String, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = color)
        Text(name, style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFD2E5D9))
    }
}

@Composable
internal fun GanjSubscriptionCard(service: ServiceUiModel, selected: Boolean, onClick: () -> Unit) {
    val effects = LocalGanjVisualEffectsPolicy.current
    val reduceMotion = effects.reduceMotion || effects.tier == GanjEffectsTier.Reduced
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(
        if (pressed && !reduceMotion) 0.988f else 1f,
        if (reduceMotion) snap() else tween(110),
        label = "royalSubscriptionPress",
    )
    val palette = LocalGanjContentPalette.current
    val fraction = service.usageFraction
    val premium = service.tier == UiTier.VIP
    val accent = when {
        !service.isActive -> MaterialTheme.colorScheme.onSurfaceVariant
        fraction != null && fraction >= 0.90f -> MaterialTheme.colorScheme.error
        premium -> palette.premiumText
        else -> MaterialTheme.colorScheme.primary
    }
    val borderColor by animateColorAsState(
        if (selected) GanjGold.copy(alpha = 0.78f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.26f),
        if (reduceMotion) snap() else tween(180),
        label = "royalSubscriptionBorder",
    )
    val progress = animateFloatAsState(
        fraction ?: 0f,
        if (reduceMotion) snap() else tween(420),
        label = "royalTrafficArc",
    )
    val labelColor = if (selected) palette.selectedMuted
        else MaterialTheme.colorScheme.onSurfaceVariant
    val mainColor = if (selected) palette.onSelected else MaterialTheme.colorScheme.onSurface
    val shape = remember { RoundedCornerShape(26.dp) }
    val description = stringResource(
        if (selected) R.string.subscription_selected else R.string.subscription_not_selected,
    )

    Column(
        modifier = Modifier.fillMaxWidth()
            .testTag("subscription-" + service.entitlementId)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(shape)
            .background(if (selected) palette.selectedSurface else MaterialTheme.colorScheme.surface)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, shape)
            .semantics(mergeDescendants = true) { stateDescription = description }
            .selectable(
                selected = selected,
                enabled = service.isActive,
                role = Role.RadioButton,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // A single gold foil edge conveys the selected tier without a costly shadow/blur.
        Box(Modifier.fillMaxWidth().height(if (selected) 3.dp else 1.dp)
            .background(if (selected) Brush.horizontalGradient(
                listOf(GanjGold.copy(alpha = 0.14f), GanjGoldBright, GanjGold.copy(alpha = 0.14f)),
            ) else Brush.horizontalGradient(
                listOf(borderColor.copy(alpha = 0.26f), borderColor.copy(alpha = 0.26f)),
            )))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (premium) GanjGold.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .border(1.dp, if (premium) GanjGold.copy(alpha = 0.34f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    GanjSecurityIcon(tint = if (premium) palette.premiumText
                        else MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.subscription_username_label),
                        style = MaterialTheme.typography.bodySmall, color = labelColor)
                    Text(subscriptionUsername(service), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = mainColor,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(28.dp).clip(CircleShape)
                    .background(if (selected) GanjGold else Color.Transparent)
                    .border(1.5.dp, if (selected) GanjGoldBright
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.43f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    if (selected) Text("✓", style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF142216), fontWeight = FontWeight.Bold)
                }
            }

            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                GanjStatusPill(text = serviceStatusText(service.status),
                    tone = if (service.isActive) GanjStatusTone.Positive else GanjStatusTone.Neutral)
                if (premium) GanjStatusPill(
                    text = stringResource(R.string.visual_signature),
                    tone = GanjStatusTone.Premium)
                if (selected) Text(stringResource(R.string.subscription_selected),
                    style = MaterialTheme.typography.labelMedium, color = palette.premiumText,
                    fontWeight = FontWeight.SemiBold)
            }

            HorizontalDivider(color = if (selected) GanjGold.copy(alpha = 0.20f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.subscription_total_traffic),
                        style = MaterialTheme.typography.bodySmall, color = labelColor)
                    Text(subscriptionTraffic(service.trafficLimitBytes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = mainColor)
                    if (fraction != null) {
                        Text(stringResource(R.string.subscription_used_percent),
                            style = MaterialTheme.typography.labelSmall, color = labelColor)
                    }
                }
                GanjTrafficRing(fraction, { progress.value }, accent, labelColor,
                    service.trafficLimitBytes == null && service.trafficUsageAvailable)
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(stringResource(R.string.subscription_used_traffic,
                    if (service.trafficUsageAvailable) subscriptionTraffic(service.trafficUsedBytes)
                    else stringResource(R.string.subscription_usage_unavailable)),
                    style = MaterialTheme.typography.bodySmall, color = labelColor)
                if (service.trafficLimitBytes != null && service.trafficUsageAvailable) {
                    Text(stringResource(R.string.subscription_remaining_traffic,
                        subscriptionTraffic(service.remainingBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                .background(if (selected) GanjGold.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.075f))
                .padding(horizontal = 13.dp, vertical = 11.dp)) {
                Text(stringResource(
                    if (service.isActive) R.string.subscription_choose_config
                    else R.string.subscription_inactive,
                ) + if (service.isActive) "  ←" else "",
                    modifier = Modifier.align(Alignment.CenterEnd),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) palette.premiumText
                        else if (service.isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GanjTrafficRing(
    fraction: Float?,
    progress: () -> Float,
    accent: Color,
    muted: Color,
    unlimited: Boolean,
) {
    Box(Modifier.size(102.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().padding(9.dp)
                .semantics {
                    if (fraction != null) {
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
                    }
                },
        ) {
            val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = muted.copy(alpha = 0.17f),
                startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke,
            )
            if (fraction != null && progress() > 0f) {
                drawArc(color = accent, startAngle = -90f,
                    sweepAngle = 360f * progress().coerceIn(0f, 1f),
                    useCenter = false, style = stroke)
            }
        }
        Text(fraction?.let { ((it * 100).roundToInt()).toPersianDigits() + "٪" }
            ?: if (unlimited) "∞" else "—",
            style = if (fraction == null) MaterialTheme.typography.labelSmall
                else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = if (fraction == null) muted else accent,
            maxLines = 1)
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
                    items(content.items, key = { it.id }, contentType = { "config" }) { server ->
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
private fun GanjConfigOption(
    server: ConnectionServer,
    selected: Boolean,
    reading: LatencyReading?,
    measuring: Boolean,
    onSelect: () -> Unit,
    onPing: () -> Unit,
    canPing: Boolean,
) {
    val palette = LocalGanjContentPalette.current
    val effects = LocalGanjVisualEffectsPolicy.current
    val reduceMotion = effects.reduceMotion || effects.tier == GanjEffectsTier.Reduced
    val identity = remember(server.name, server.countryCode) {
        ganjConfigIdentity(server.name, server.countryCode)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        if (!reduceMotion && pressed) 0.987f else 1f,
        if (reduceMotion) snap() else tween(115), label = "configPress",
    )
    val rim by animateColorAsState(
        if (selected) palette.premiumText else MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
        if (reduceMotion) snap() else tween(185), label = "configSelectedRim",
    )
    val shape = remember { RoundedCornerShape(22.dp) }
    val latencyMs = reading?.result?.latencyMillis
    val latencyColor = when {
        latencyMs == null -> MaterialTheme.colorScheme.onSurfaceVariant
        latencyMs <= 80L -> MaterialTheme.colorScheme.primary
        latencyMs <= 180L -> palette.premiumText
        else -> MaterialTheme.colorScheme.error
    }
    // The app remains RTL, but config rows use LTR for a LEFT flag and RIGHT latency.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier.fillMaxWidth().testTag("config-option-" + server.id)
                .graphicsLayer { scaleX = pressScale.value; scaleY = pressScale.value }
                .clip(shape)
                .background(if (selected) palette.selectedSurface else MaterialTheme.colorScheme.surface)
                .border(if (selected) 1.5.dp else 1.dp, rim, shape)
                .selectable(
                    selected = selected, role = Role.RadioButton,
                    interactionSource = interaction, indication = null, onClick = onSelect,
                ),
        ) {
            if (selected) {
                Box(Modifier.fillMaxWidth().height(2.dp)
                    .background(Brush.horizontalGradient(listOf(
                        GanjGold.copy(alpha = 0.10f), palette.premiumText,
                        GanjGold.copy(alpha = 0.10f),
                    ))))
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 92.dp)
                    .padding(horizontal = 11.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GanjConfigFlagBadge(identity.countryCode, selected)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(identity.title, style = MaterialTheme.typography.titleMedium,
                        color = if (selected) palette.onSelected else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(isolateTechnicalLtr(server.protocols.joinToString("  •  ") { it.name }),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) palette.selectedMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (selected) Text("✓  انتخاب‌شده",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.premiumText, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier.testTag("ping-" + server.id)
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(latencyColor.copy(alpha =
                                if (latencyMs == null) 0.12f else 0.17f))
                            .border(1.dp, latencyColor.copy(alpha = 0.68f),
                                RoundedCornerShape(14.dp))
                            .clickable(enabled = canPing && !measuring,
                                role = Role.Button, onClick = onPing)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when {
                                measuring -> "…"
                                latencyMs != null ->
                                    persianTechnicalMetric(latencyMs.toString(), "ms")
                                reading?.result is LatencyProbeResult.Failed -> "!"
                                else -> "—"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = latencyColor, fontWeight = FontWeight.Bold, maxLines = 1,
                        )
                    }
                    Text(
                        when {
                            measuring -> stringResource(R.string.ping_measuring)
                            reading?.result is LatencyProbeResult.Failed ->
                                latencyFailureLabel(reading.result.failure)
                            else -> stringResource(R.string.config_ping)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) palette.selectedMuted else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(if (selected) "✓" else "›",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (selected) palette.premiumText else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Locally bundled, sharp WebP flag image. No network request, emoji rendering or decode loop. */
@Composable
internal fun GanjConfigFlagBadge(
    countryCode: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val normalized = remember(countryCode) { ganjNormalizedCountry(countryCode) }
    val imageRes = remember(normalized) { ganjFlagDrawable(normalized) }
    val isGlobal = imageRes == R.drawable.ic_ganj_world
    val flagShape = remember { RoundedCornerShape(7.dp) }
    val badgeShape = remember { RoundedCornerShape(17.dp) }
    Box(
        modifier = modifier.size(55.dp, 51.dp)
            .clip(badgeShape)
            .background(Brush.linearGradient(
                listOf(Color(0xFF4C926B), Color(0xFF15462F)),
            ))
            .border(if (selected) 1.5.dp else 1.dp,
                if (selected) GanjGoldBright
                else Color(0xFF9BD8B0).copy(alpha = 0.70f), badgeShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null, // Country is described in the adjacent server details.
            modifier = if (isGlobal) Modifier.size(31.dp)
                else Modifier.size(43.dp, 31.dp).clip(flagShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.24f), flagShape),
            contentScale = ContentScale.Fit,
            colorFilter = if (isGlobal) ColorFilter.tint(GanjGoldBright) else null,
        )
    }
}
