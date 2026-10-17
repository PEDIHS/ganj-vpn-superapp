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
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onOpenStore: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val initialFocus = remember { FocusRequester() }
    // Older Android windows otherwise focus the first editable descendant and open the IME.
    LaunchedEffect(Unit) { initialFocus.requestFocus() }
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
        modifier = modifier.fillMaxSize().imePadding().testTag("subscriptions-screen")
            .focusRequester(initialFocus).focusable().selectableGroup(),
        contentPadding = PaddingValues(start = responsiveHorizontalPadding(), end = responsiveHorizontalPadding(), top = 12.dp,
            bottom = GanjFloatingNavContentClearance),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onBack?.let { AccountBackAction(it) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.subscriptions_title), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("${activeCount.toPersianDigits()} اشتراک فعال · انتخاب برای اتصال",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onOpenStore != null) TextButton(onClick = onOpenStore) { Text("فروشگاه") }
            }
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
                shape = RoundedCornerShape(12.dp),
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
        if (state.servicesStale) item(key = "services-stale") {
            GanjInlineStatusPanel(
                "اشتراک‌های ذخیره‌شده", "ارتباط با سرور موقتاً برقرار نشد. اطلاعات قبلی نمایش داده شده و وضعیت نهایی هنگام اتصال بررسی می‌شود.",
                accent = MaterialTheme.colorScheme.onSurfaceVariant, tone = GanjStatusTone.Neutral,
                actionText = "بررسی دوباره", onAction = onRetry,
                modifier = Modifier.testTag("subscriptions-stale"),
            )
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
                MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant,
            )))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), shape),
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
                    color = MaterialTheme.colorScheme.onSurface)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                GanjOverviewMetric(activeCount.toPersianDigits(),
                    stringResource(R.string.subscription_overview_active),
                    MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(48.dp)
                    .background(Color.White.copy(alpha = 0.12f)))
                GanjOverviewMetric(totalCount.toPersianDigits(),
                    stringResource(R.string.subscription_overview_total),
                    LocalGanjContentPalette.current.premiumText, Modifier.weight(1f))
            }
            if (selectedUsername != null) {
                HorizontalDivider(color = GanjGold.copy(alpha = 0.20f))
                Text(stringResource(R.string.subscription_overview_selected, selectedUsername),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun GanjSubscriptionCard(service: ServiceUiModel, selected: Boolean, onClick: () -> Unit) {
    val effects = LocalGanjVisualEffectsPolicy.current
    val palette = LocalGanjContentPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !effects.reduceMotion) .988f else 1f,
        if (effects.reduceMotion) snap() else tween(110), label = "subscriptionPress")
    val rim by animateColorAsState(if (selected) palette.premiumText else MaterialTheme.colorScheme.outlineVariant,
        if (effects.reduceMotion) snap() else tween(180), label = "subscriptionSelection")
    val fraction = service.usageFraction
    val progress by animateFloatAsState(fraction ?: 0f,
        if (effects.reduceMotion) snap() else tween(360), label = "subscriptionUsage")
    val accent = if (fraction != null && fraction >= .9f) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(13.dp)
    Column(Modifier.fillMaxWidth().testTag("subscription-" + service.entitlementId)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape).background(if (selected) palette.selectedSurface else MaterialTheme.colorScheme.surface)
        .border(1.dp, rim, shape)
        .semantics(mergeDescendants = true) {
            stateDescription = if (selected) "انتخاب شده" else if (!service.isActive) "غیرفعال" else "انتخاب نشده"
        }
        .selectable(selected, enabled = service.isActive, role = Role.RadioButton,
            interactionSource = interaction, indication = null, onClick = onClick)
        .padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(subscriptionUsername(service), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(serviceStatusText(service.status) + if (service.tier == UiTier.VIP) " · VIP" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (service.isActive) accent else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (selected) "✓" else "‹", color = if (selected) palette.premiumText else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(subscriptionTraffic(service.trafficLimitBytes), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(if (fraction != null) "${(fraction * 100).roundToInt().toPersianDigits()}٪ مصرف"
                else if (!service.trafficUsageAvailable) stringResource(R.string.subscription_usage_unavailable) else "حجم نامحدود",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (fraction != null) LinearProgressIndicator(progress = { progress },
            modifier = Modifier.fillMaxWidth().height(3.dp), color = accent,
            trackColor = accent.copy(alpha = .12f))
        Text(if (service.trafficUsageAvailable) stringResource(R.string.subscription_used_traffic, subscriptionTraffic(service.trafficUsedBytes))
            else stringResource(R.string.subscription_usage_unavailable), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    cachedServers: List<ConnectionServer> = emptyList(),
) {
    val haptic = LocalHapticFeedback.current
    val effects = LocalGanjVisualEffectsPolicy.current
    var query by rememberSaveable(service.entitlementId) { mutableStateOf("") }
    var sortByPing by rememberSaveable(service.entitlementId) { mutableStateOf(false) }
    // Display safe account-scoped metadata immediately; refresh never blanks existing rows.
    var catalog by remember(service.entitlementId) {
        mutableStateOf<ContentState<ConnectionServer>>(
            if (cachedServers.isEmpty()) ContentState.Loading else ContentState.Ready(cachedServers)
        )
    }
    var refresh by remember(service.entitlementId) { mutableIntStateOf(0) }
    var refreshing by remember(service.entitlementId) { mutableStateOf(false) }
    var refreshFailure by remember(service.entitlementId) { mutableStateOf<String?>(null) }
    LaunchedEffect(service.entitlementId, refresh) {
        if (catalog !is ContentState.Ready) catalog = ContentState.Loading
        refreshing = true
        refreshFailure = null
        try {
            val result = loadServers(service.entitlementId)
            if (!isActive) return@LaunchedEffect
            when (result) {
                is ApiResult.Success -> catalog = if (result.value.isEmpty()) ContentState.Empty else ContentState.Ready(result.value)
                is ApiResult.Failure -> {
                    if (catalog is ContentState.Ready && result.error !is com.ganj.vpn.core.controlapi.ApiError.AuthenticationRequired &&
                        result.error !is com.ganj.vpn.core.controlapi.ApiError.AuthenticationExpired) {
                        refreshFailure = "سرورها از آخرین دریافت نمایش داده شده‌اند؛ برای اتصال باید دوباره تأیید شوند."
                    } else catalog = ContentState.Error(ConnectionFailures.api(result.error))
                }
            }
        } finally { refreshing = false }
    }
    val readings = latency?.state?.collectAsState()?.value ?: LatencySnapshot()
    val servers = (catalog as? ContentState.Ready)?.items.orEmpty()
    val filteredServers = remember(servers, query, sortByPing, readings.readings, service.entitlementId) {
        val filtered = servers.filter { server ->
            query.isBlank() || server.name.contains(query, true) ||
                server.protocols.any { it.name.contains(query, true) } ||
                server.countryCode?.contains(query, true) == true ||
                server.countryCode?.let { java.util.Locale("", it).getDisplayCountry(java.util.Locale("fa")).contains(query, true) } == true
        }
        if (sortByPing) filtered.sortedBy { readings.readings[LatencyKey(service.entitlementId, it.id)]?.result?.latencyMillis ?: Long.MAX_VALUE }
        else filtered
    }
    val busy = readings.measuring.any { it.serviceId == service.entitlementId }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.65f
    GanjLiquidBottomSheet(title = stringResource(R.string.config_picker_title),
        subtitle = subscriptionUsername(service), onDismiss = onDismiss, skipPartiallyExpanded = true, compact = true) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxHeight).testTag("config-picker").selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(query, { query = it.take(120) }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.config_search), style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().testTag("config-search"), shape = RoundedCornerShape(11.dp),
                    trailingIcon = if (query.isNotEmpty()) {
                        { TextButton(onClick = { query = "" }) { Text(stringResource(R.string.common_clear)) } }
                    } else null)
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${filteredServers.size.toPersianDigits()} کانفیگ", Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { if (!refreshing) refresh++ }, enabled = !refreshing) {
                        Text(if (refreshing) "در حال به‌روزرسانی" else stringResource(R.string.common_refresh),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            refreshFailure?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall) }
            }
            when (val content = catalog) {
                ContentState.Loading -> item { LoadingCard(stringResource(R.string.config_picker_loading)) }
                ContentState.Empty -> item { EmptyCard(stringResource(R.string.config_picker_empty), stringResource(R.string.config_picker_empty_body)) { refresh++ } }
                ContentState.AuthRequired -> item { AuthCard { refresh++ } }
                is ContentState.Error -> item { ErrorCard(content.failure) { refresh++ } }
                is ContentState.Ready -> {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GanjGlassButton(if (busy) "سنجش…" else "پینگ همه",
                                { latency?.measure(service.entitlementId, servers.map { it.id }, force = true) },
                                Modifier.weight(1f), enabled = !busy && latency != null, filled = false)
                            GanjGlassButton(if (sortByPing) "ترتیب اصلی" else "ترتیب پینگ",
                                { sortByPing = !sortByPing }, Modifier.weight(1f), filled = false)
                        }
                        if (filteredServers.isEmpty()) Text(stringResource(R.string.config_no_match), style = MaterialTheme.typography.bodySmall)
                    }
                    items(filteredServers, key = { it.id }, contentType = { "config" }) { server ->
                        val key = LatencyKey(service.entitlementId, server.id)
                        val reading = readings.readings[key]
                        val measuring = key in readings.measuring
                        GanjConfigOption(server, server.id == selectedServer?.id, reading, measuring,
                            onSelect = {
                                if (!effects.reduceMotion) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(server)
                            }, onPing = { latency?.measure(service.entitlementId, listOf(server.id), force = true) },
                            canPing = latency != null)
                    }
                }
            }
        }
    }
}

internal enum class GanjPingTone { Measured, Timeout, Unknown }

internal fun ganjPingTone(result: LatencyProbeResult?): GanjPingTone = when (result) {
    is LatencyProbeResult.Measured -> GanjPingTone.Measured
    is LatencyProbeResult.Failed -> if (result.failure.diagnosticCode == "probe.timeout")
        GanjPingTone.Timeout else GanjPingTone.Unknown
    null -> GanjPingTone.Unknown
}

@Composable
internal fun GanjConfigOption(
    server: ConnectionServer,
    selected: Boolean,
    reading: LatencyReading?,
    measuring: Boolean,
    onSelect: () -> Unit,
    onPing: () -> Unit,
    canPing: Boolean,
    animateEntrance: Boolean = true,
    compact: Boolean = false,
) {
    val palette = LocalGanjContentPalette.current
    val effects = LocalGanjVisualEffectsPolicy.current
    val reduceMotion = effects.reduceMotion
    // Inline scrolling should not repeatedly animate rows entering the viewport:
    // only selection/press feedback and container fades move.
    // The embedded list displays rows immediately. Allocating an Animatable and
    // running LaunchedEffect for each flung row caused unnecessary main-thread work.
    val entry = if (animateEntrance && !reduceMotion) {
        remember(server.id) { androidx.compose.animation.core.Animatable(0f) }
    } else null
    if (entry != null) {
        LaunchedEffect(server.id, entry) { entry.animateTo(1f, tween(180)) }
    }
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
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        if (reduceMotion) snap() else tween(185), label = "configSelectedRim",
    )
    val shape = remember { RoundedCornerShape(12.dp) }
    // Fixed columns on every config row, including long names, timeouts and large text.
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val narrowScreen = LocalConfiguration.current.screenWidthDp < 380
    // Keep the previously approved larger flag at accessibility font sizes.
    val flagWidth = if (largeText) 76.dp else if (compact) 46.dp else if (narrowScreen) 44.dp else 52.dp
    val flagHeight = if (largeText) 50.dp else if (compact) 31.dp else if (narrowScreen) 30.dp else 35.dp
    val pingWidth = if (largeText && !narrowScreen) 106.dp else if (compact && narrowScreen) 76.dp
        else if (compact) 84.dp else if (narrowScreen) 90.dp else 98.dp
    val pingDescription = stringResource(if (measuring) R.string.ping_measuring else R.string.config_ping)
    val latencyMs = reading?.result?.latencyMillis
    // Numeric latency is always green, regardless of milliseconds.
    // Only an actual probe timeout is red; other probe errors stay neutral.
    val latencyTone = ganjPingTone(reading?.result)
    val isTimeout = latencyTone == GanjPingTone.Timeout
    val latencyColor = when (latencyTone) {
        GanjPingTone.Measured -> MaterialTheme.colorScheme.primary
        GanjPingTone.Timeout -> MaterialTheme.colorScheme.error
        GanjPingTone.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // The app remains RTL, but config rows use LTR for a LEFT flag and RIGHT latency.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier.fillMaxWidth().testTag("config-option-" + server.id)
                .graphicsLayer {
                    val entrance = entry?.value ?: 1f
                    scaleX = pressScale.value
                    scaleY = pressScale.value
                    alpha = entrance
                    translationX = (1f - entrance) * 8.dp.toPx()
                }
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
                modifier = Modifier.fillMaxWidth().heightIn(min = if (largeText) 90.dp else if (compact) 62.dp else 76.dp)
                    .padding(horizontal = if (compact) 7.dp else 9.dp,
                        vertical = if (compact) 6.dp else 9.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact || narrowScreen) 6.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GanjConfigFlagBadge(identity.countryCode, selected,
                    Modifier.size(flagWidth, flagHeight).testTag("config-option-flag-" + server.id))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 5.dp)) {
                    Text(identity.title, style = MaterialTheme.typography.titleSmall,
                        color = if (selected) palette.onSelected else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold,
                        maxLines = if (compact && !largeText) 1 else 2, overflow = TextOverflow.Ellipsis)
                    Text(isolateTechnicalLtr(server.protocols.joinToString("  •  ") { it.name }),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) palette.selectedMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Every badge has the same width, height, centerline and touch target.
                Box(
                        modifier = Modifier.width(pingWidth).height(if (compact && !largeText) 44.dp else 48.dp).testTag("ping-" + server.id)
                            .semantics { contentDescription = pingDescription }

                            .clip(RoundedCornerShape(if (compact) 11.dp else 14.dp))
                            .background(latencyColor.copy(alpha =
                                if (latencyMs == null) 0.10f else 0.15f))
                            .border(1.dp, latencyColor.copy(alpha = 0.58f),
                                RoundedCornerShape(if (compact) 11.dp else 14.dp))
                            .clickable(enabled = canPing && !measuring,
                                role = Role.Button, onClick = onPing)
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when {
                                measuring -> "…"
                                latencyMs != null ->
                                    persianTechnicalMetric(latencyMs.toString(), "ms")
                                isTimeout -> "تایم‌اوت"
                                reading?.result is LatencyProbeResult.Failed -> "خطا"
                                else -> "—"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = if (largeText) 10.sp else 12.sp,
                            color = latencyColor, fontWeight = FontWeight.Bold, maxLines = 1,
                            softWrap = false, overflow = TextOverflow.Ellipsis,
                        )
                    }
                Box(Modifier.size(if (compact) 20.dp else 22.dp)
                    .clip(CircleShape)
                    .background(if (selected) palette.premiumText.copy(alpha = .16f) else Color.Transparent)
                    .border(1.dp, if (selected) palette.premiumText else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center) {
                    if (selected) Text("✓", fontSize = 12.sp,
                        color = palette.premiumText, fontWeight = FontWeight.Bold)
                }
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
    val badgeShape = remember { RoundedCornerShape(8.dp) }
    val effects = LocalGanjVisualEffectsPolicy.current
    val painter = painterResource(imageRes)
    val ratio = if (isGlobal) 1.52f else painter.intrinsicSize.let {
        if (it.width > 0 && it.height > 0) it.width / it.height else 1.52f
    }
    val flagHeight = minOf(50f, 76f / ratio)
    val flagWidth = flagHeight * ratio
    val elevation = if (effects.tier == GanjEffectsTier.Reduced) 0.dp else if (selected) 5.dp else 3.dp
    Box(modifier.size(76.dp, 50.dp), contentAlignment = Alignment.Center) {
        if (isGlobal) {
            Box(Modifier.fillMaxSize().shadow(elevation, badgeShape, clip = false).clip(badgeShape)
                .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Image(painter, contentDescription = null, modifier = Modifier.size(31.dp),
                    colorFilter = ColorFilter.tint(GanjGoldBright))
            }
        } else {
            // Preserve the complete source image and its national aspect ratio; no cropped canton/symbol.
            Image(painter, contentDescription = null,
                modifier = Modifier.fillMaxSize().shadow(elevation, badgeShape, clip = false).clip(badgeShape),
                contentScale = ContentScale.Fit)
        }
    }
}
