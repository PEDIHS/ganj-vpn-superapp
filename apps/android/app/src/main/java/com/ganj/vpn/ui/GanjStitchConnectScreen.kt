package com.ganj.vpn.ui

import com.ganj.vpn.R

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ganj.vpn.presentation.LatencyProbeResult
import com.ganj.vpn.presentation.latencyMillis
import com.ganj.vpn.presentation.ConnectionFailures
import com.ganj.vpn.presentation.UiFailure
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import com.ganj.vpn.presentation.SessionLatencyManager
import com.ganj.vpn.presentation.LatencyKey
import com.ganj.vpn.presentation.LatencySnapshot
import androidx.compose.ui.res.stringResource
import com.ganj.vpn.composition.ConnectionServerCompositionRegistry
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.ConnectionServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ganj.vpn.presentation.ConnectionSessionTimer
import com.ganj.vpn.core.vpn.ConnectionPhase
import com.ganj.vpn.presentation.ConnectionUiState
import com.ganj.vpn.presentation.GanjUiState
import com.ganj.vpn.presentation.ServiceUiModel
import com.ganj.vpn.presentation.UiTier

private val StitchGold = GanjGold
private val StitchGoldBright: Color
    @Composable get() = LocalGanjContentPalette.current.premiumText
private val StitchEmeraldGlow: Color
    @Composable get() = MaterialTheme.colorScheme.primary

@Composable
internal fun StitchConnectionScreen(
    state: GanjUiState,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    loadServers: suspend (String) -> ApiResult<List<ConnectionServer>>,
    onSelectAndConnect: (String, ConnectionServer) -> Unit,
    onOpenStore: () -> Unit,
    onRetry: () -> Unit,
    onProbe: suspend (String, String) -> LatencyProbeResult = { _, _ -> LatencyProbeResult.Failed(ConnectionFailures.probe("probe.unavailable")) },
    latency: SessionLatencyManager? = null,
    modifier: Modifier = Modifier,
    sessionTimer: ConnectionSessionTimer? = null,
    onOpenNotifications: (() -> Unit)? = null,
    disconnectRequested: Boolean = false,
) {
    val connection = state.connection
    val service = state.selectedService
    val scope = rememberCoroutineScope()
    val latencyState = latency?.state?.collectAsState()?.value ?: LatencySnapshot()
    var smartBusy by remember { mutableStateOf(false) }
    var smartFailure by remember { mutableStateOf<UiFailure?>(null) }
    val displayedServer = state.selectedServer
    val displayServerId = displayedServer?.id
    val displayServiceId = service?.entitlementId
    val connectedSelection = connection is ConnectionUiState.Connected &&
        connection.entitlementId == displayServiceId && connection.serverId == displayServerId
    val switchingSelection = connection is ConnectionUiState.Connected && service?.isActive == true && !connectedSelection
    val pingKey = if (displayServiceId != null && displayServerId != null) LatencyKey(displayServiceId, displayServerId) else null
    val reading = latencyState.readings[pingKey]
    val ping = reading?.result?.latencyMillis
    val pingBusy = pingKey in latencyState.measuring
    val pingFailure = (reading?.result as? LatencyProbeResult.Failed)?.failure
    val visualState = ganjConnectionVisualState(state.runtimeConnection.phase, connection, service?.isActive == true)
    val disconnecting = disconnectRequested || state.runtimeConnection.phase == ConnectionPhase.DISCONNECTING
    val preparing = state.runtimeConnection.phase == ConnectionPhase.PREPARING ||
        (state.runtimeConnection.phase in setOf(ConnectionPhase.DISCONNECTED, ConnectionPhase.CONNECTED) &&
            (connection is ConnectionUiState.Requesting || connection is ConnectionUiState.ProfileReady))
    val connectionBusy = preparing || visualState == GanjConnectionVisualState.Connecting ||
        visualState == GanjConnectionVisualState.Reconnecting || disconnecting
    val hasPremium = remember(state.services) { state.serviceItems.any {
        it.isActive && (it.tier == UiTier.PREMIUM || it.tier == UiTier.VIP)
    } }

    Column(
        modifier = modifier
            .testTag("connection-screen")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = responsiveHorizontalPadding(), vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StitchBrandHeader(premium = hasPremium, onOpenStore = onOpenStore, onOpenNotifications = onOpenNotifications)
        val statusColor = when (visualState) {
            GanjConnectionVisualState.Connected -> MaterialTheme.colorScheme.primary
            GanjConnectionVisualState.Failed -> MaterialTheme.colorScheme.error
            GanjConnectionVisualState.Connecting, GanjConnectionVisualState.Reconnecting -> StitchGoldBright
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.Center,
            verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(statusColor,CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(if(disconnecting) stringResource(R.string.connection_disconnecting_label) else connectionTitle(visualState),
                style=MaterialTheme.typography.bodySmall,color=statusColor)
        }
        GanjConnectionHero(
            state = visualState,
            action = if (switchingSelection) stringResource(R.string.connection_switch_selection) else connectionAction(visualState),
            title = connectionTitle(visualState),
            disconnecting = disconnecting,
            selecting = smartBusy,
            preparing = preparing,
            duration = { GanjSessionDurationInline(state.runtimeConnection, sessionTimer) },
            onClick = {
                when {
                    connection is ConnectionUiState.Connected && !switchingSelection -> onDisconnect()
                    service?.isActive == true -> onConnect(service.entitlementId)
                    else -> onOpenServers()
                }
            },
        )
        if (visualState == GanjConnectionVisualState.Connected && state.runtimeConnection.connectedAtEpochMillis == null) {
            Text(stringResource(R.string.connection_duration_unknown),modifier=Modifier.fillMaxWidth(),
                style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
        }
        if (visualState == GanjConnectionVisualState.Failed) {
            GanjGlassSurface(
                role = GanjGlassRole.Dense,
                accent = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 20.dp,
                padding = PaddingValues(16.dp),
            ) {
                Text(
                    text = "اتصال برقرار نشد",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = (connection as? ConnectionUiState.Failed)?.let { failureMessage(it.failure) }
                        ?: "لایه VPN اتصال را برقرار نکرد. برای تلاش دوباره، وضعیت شبکه و سرور را بررسی کنید.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                (connection as? ConnectionUiState.Failed)?.let { ConnectionFailureDetails(it.failure) }
                GanjLiquidAction(
                    onClick = { service?.entitlementId?.let(onConnect) ?: onRetry() },
                    accent = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "تلاش دوباره",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onError,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (smartBusy) LoadingCard(stringResource(R.string.connection_selecting_config))
        smartFailure?.let { failure ->
            Text(failureMessage(failure), style = MaterialTheme.typography.bodySmall)
            ConnectionFailureDetails(failure)
        }
        StitchSmartConnectCard(
            activeSubscription = service?.isActive == true,
            busy = smartBusy || connectionBusy,
            onClick = {
                val selected = service?.takeIf { it.isActive }
                if (selected == null) onOpenSubscriptions()
                else if (!smartBusy && !connectionBusy) scope.launch {
                    smartBusy = true
                    smartFailure = null
                    try {
                        val response = loadServers(selected.entitlementId)
                        if (response is ApiResult.Failure) {
                            smartFailure = ConnectionFailures.api(response.error)
                            return@launch
                        }
                        val servers = (response as ApiResult.Success).value
                        val results = servers.map { server -> server.id to onProbe(selected.entitlementId, server.id) }
                        val best = results.mapNotNull { (id, result) -> result.latencyMillis?.let { id to it } }
                            .minByOrNull { it.second }
                        if (best == null) smartFailure = results.firstNotNullOfOrNull { (_, result) ->
                            (result as? LatencyProbeResult.Failed)?.failure
                        } ?: ConnectionFailures.probe("probe.unavailable")
                        else {
                            servers.firstOrNull { it.id == best.first }?.let {
                                onSelectAndConnect(selected.entitlementId, it)
                            }
                        }
                    } finally { smartBusy = false }
                }
            },
        )
        StitchReferenceSelection(
            service=service,server=displayedServer,ping=ping,pingBusy=pingBusy,
            onOpenSubscriptions=onOpenSubscriptions,onOpenServers=onOpenServers,
            onMeasure=if(pingKey!=null && latency!=null) ({latency.measure(pingKey.serviceId,listOf(pingKey.serverId))}) else null,
        )
        reading?.let { Text(latencyReadingTime(it.measuredAtMillis), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        pingFailure?.let { failure ->
            Text(failureMessage(failure), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ConnectionFailureDetails(failure)
        }
        if (switchingSelection) Text(stringResource(R.string.connection_switch_selection_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StitchBrandHeader(premium: Boolean, onOpenStore: () -> Unit, onOpenNotifications: (() -> Unit)?) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    .border(1.dp, StitchGold.copy(alpha = 0.48f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.ganj_logo_official), contentDescription = "نشان گنج VPN",
                        modifier = Modifier.size(28.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("GANJ VPN", style = MaterialTheme.typography.titleLarge.copy(letterSpacing=2.sp), fontWeight = FontWeight.Bold,
                        color = StitchEmeraldGlow, modifier = Modifier.fillMaxWidth())
                    Text("ارتباط امن، کنترل ساده", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                }
            }
            onOpenNotifications?.let { handler -> GanjReferenceNotificationButton(handler) }
            if (!largeText) {
                Spacer(Modifier.width(8.dp))
                StitchHeaderUpgrade(premium, onOpenStore)
            }
        }
        if (largeText) StitchHeaderUpgrade(premium, onOpenStore, Modifier.align(Alignment.End))
    }
}

@Composable
private fun StitchHeaderUpgrade(premium: Boolean, onOpenStore: () -> Unit, modifier: Modifier = Modifier) {
    GanjGlassButton(if(premium) "پریمیوم" else "ارتقا",onOpenStore,modifier=modifier,
        filled=false,accent=StitchGold,contentColor=StitchGoldBright,shapeRadius=8.dp)
}

@Composable
private fun StitchReferenceSelection(
    service: ServiceUiModel?, server: ConnectionServer?, ping: Long?, pingBusy: Boolean,
    onOpenSubscriptions: () -> Unit, onOpenServers: () -> Unit, onMeasure: (() -> Unit)?,
) {
    val identity=remember(server){server?.let{ganjConfigIdentity(it.name,it.countryCode)}}
    val muted=MaterialTheme.colorScheme.onSurfaceVariant
    val large=LocalDensity.current.fontScale>=1.5f
    val shape=RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
        .border(1.dp,MaterialTheme.colorScheme.outlineVariant,shape)) {
        Row(Modifier.fillMaxWidth().testTag("selected-subscription")
            .clickable(role=Role.Button,onClick=onOpenSubscriptions).heightIn(min=52.dp).padding(horizontal=12.dp,vertical=6.dp),
            horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=Alignment.CenterVertically) {
            GanjNavigationIcon(GanjDestination.Servers,StitchGoldBright,Modifier.size(17.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.subscription_selected_label),style=MaterialTheme.typography.labelSmall,color=muted)
                Text(service?.let{subscriptionUsername(it)}?:stringResource(R.string.subscription_choose_first),
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurface)
            }
            Text("تغییر",style=MaterialTheme.typography.labelSmall,color=StitchGoldBright)
        }
        androidx.compose.material3.HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().testTag("selected-config").clickable(role=Role.Button,onClick=onOpenServers)
            .heightIn(min=64.dp).padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            GanjConfigFlagBadge(identity?.countryCode,server!=null,
                Modifier.size(if(large)76.dp else 44.dp,if(large)50.dp else 30.dp).testTag("selected-config-flag"))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(identity?.title?:stringResource(if(service!=null)R.string.config_choose else R.string.subscription_choose_first),
                    style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.onSurface,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                server?.let { Text("${countryName(it.countryCode)} • ${it.protocols.joinToString(" / "){p->p.name}}",
                    style=MaterialTheme.typography.labelSmall,color=muted) }
            }
            if(onMeasure!=null) Box(Modifier.heightIn(min=48.dp).clip(RoundedCornerShape(8.dp))
                .clickable(enabled=!pingBusy,role=Role.Button,onClick=onMeasure).padding(6.dp),contentAlignment=Alignment.Center) {
                Text(if(pingBusy) "…" else persianTechnicalMetric(ping?.toString()?:"—","ms"),
                    style=MaterialTheme.typography.labelSmall,color=StitchGoldBright)
            } else Text("‹",style=MaterialTheme.typography.titleMedium,color=muted)
        }
    }
}

@Composable
private fun StitchSmartConnectCard(activeSubscription: Boolean, busy: Boolean, onClick: () -> Unit) {
    val effects = LocalGanjVisualEffectsPolicy.current
    val dark = referenceDark()
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val sheen = remember { androidx.compose.animation.core.Animatable(-.4f) }
    LaunchedEffect(activeSubscription, effects.reduceMotion) {
        if (activeSubscription && !effects.reduceMotion) {
            sheen.snapTo(-.4f)
            sheen.animateTo(1.4f, androidx.compose.animation.core.tween(1200))
        }
    }
    Box(Modifier.fillMaxWidth().testTag("smart-connect-card").clip(shape)
        .background(Brush.linearGradient(if (dark) listOf(GanjReference.GoldTop, GanjReference.GoldBottom) else listOf(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.surface)))
        .border(1.dp, LocalGanjContentPalette.current.premiumText.copy(alpha=.7f),shape)
        .clickable(enabled=!busy, interactionSource=interaction, indication=null, role=Role.Button,onClick=onClick)) {
        if(!effects.reduceTransparency) androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val x = size.width*sheen.value
            drawRect(Brush.linearGradient(listOf(Color.Transparent,Color.White.copy(alpha=.14f),Color.Transparent),
                start=androidx.compose.ui.geometry.Offset(x-size.width*.2f,0f),end=androidx.compose.ui.geometry.Offset(x+size.width*.2f,size.height)))
        }
        val largeText=LocalDensity.current.fontScale>=1.3f
        val title=if(busy) "در حال بررسی…" else "اتصال هوشمند"
        val detail=if(activeSubscription) "انتخاب بهترین سرور" else "انتخاب اشتراک"
        val titleInk=if(dark)Color(0xFFFFF0C7) else MaterialTheme.colorScheme.onSurface
        val detailInk=if(dark)Color(0xFFE4D0A3) else MaterialTheme.colorScheme.onSurfaceVariant
        if(largeText) Column(Modifier.fillMaxWidth().heightIn(min=55.dp).padding(horizontal=14.dp,vertical=12.dp),
            verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                if(busy) GanjCompactSpinner(accent=StitchGoldBright,size=20.dp)
                else GanjNavigationIcon(GanjDestination.Connect,StitchGoldBright,Modifier.size(20.dp))
                Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold,color=titleInk)
            }
            Text(detail,style=MaterialTheme.typography.labelSmall,color=detailInk)
        } else Row(Modifier.fillMaxWidth().heightIn(min=55.dp).padding(horizontal=14.dp,vertical=12.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            if(busy) GanjCompactSpinner(accent=StitchGoldBright,size=20.dp)
            else GanjNavigationIcon(GanjDestination.Connect,StitchGoldBright,Modifier.size(20.dp))
            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold,color=titleInk)
            Text(detail,style=MaterialTheme.typography.labelSmall,color=detailInk)
        }
    }
}

@Composable
private fun StitchPremiumCard(premium: Boolean, onOpenStore: () -> Unit) {
    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = StitchGold,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 22.dp,
        padding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (premium) "پریمیوم فعال است" else "پریمیوم شوید؛ نامحدود بمانید",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StitchGoldBright,
                )
                Text(
                    text = if (premium) {
                        "به سرورهای پریمیوم و امکانات اشتراک خود دسترسی دارید."
                    } else {
                        "سرورهای بیشتر، اولویت بالاتر و تجربه سریع‌تر را فعال کنید."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(12.dp))
            GanjLiquidAction(
                onClick = onOpenStore,
                accent = StitchGold,
                shapeRadius = 999.dp,
                modifier = Modifier.width(104.dp),
            ) {
                Text(
                    text = if (premium) "مدیریت" else "ارتقا دهید",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF211600),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun connectionTitle(state: GanjConnectionVisualState): String = when (state) {
    GanjConnectionVisualState.Disconnected -> "متصل نیستید"
    GanjConnectionVisualState.Connecting -> "در حال اتصال امن"
    GanjConnectionVisualState.Connected -> "اتصال امن برقرار است"
    GanjConnectionVisualState.Reconnecting -> "در حال اتصال مجدد"
    GanjConnectionVisualState.Failed -> "خطای اتصال"
    GanjConnectionVisualState.Unavailable -> "اشتراک فعال انتخاب نشده"
}

private fun connectionAction(state: GanjConnectionVisualState): String = when (state) {
    GanjConnectionVisualState.Connected -> "قطع اتصال"
    GanjConnectionVisualState.Connecting -> "در حال اتصال…"
    GanjConnectionVisualState.Reconnecting -> "در حال بازیابی…"
    GanjConnectionVisualState.Failed -> "تلاش دوباره"
    GanjConnectionVisualState.Unavailable -> "انتخاب اشتراک"
    GanjConnectionVisualState.Disconnected -> "اتصال"
}

private fun tierPersian(tier: UiTier): String = when (tier) {
    UiTier.FREE -> "رایگان"
    UiTier.PREMIUM -> "پریمیوم"
    UiTier.VIP -> "وی‌آی‌پی"
}

private fun countryName(code: String?): String = when (code?.uppercase()) {
    "DE" -> "آلمان"
    "NL" -> "هلند"
    "US" -> "آمریکا"
    "GB", "UK" -> "بریتانیا"
    "TR" -> "ترکیه"
    "PL" -> "لهستان"
    "FR" -> "فرانسه"
    "CA" -> "کانادا"
    "SG" -> "سنگاپور"
    null -> "سرور جهانی"
    else -> isolateTechnicalLtr(code.uppercase())
}

private fun countryEmoji(code: String?): String = when (code?.uppercase()) {
    "DE" -> "🇩🇪"
    "NL" -> "🇳🇱"
    "US" -> "🇺🇸"
    "GB", "UK" -> "🇬🇧"
    "TR" -> "🇹🇷"
    "PL" -> "🇵🇱"
    "FR" -> "🇫🇷"
    "CA" -> "🇨🇦"
    "SG" -> "🇸🇬"
    else -> "🌐"
}

@Composable
internal fun responsiveHorizontalPadding(): Dp = GanjResponsivePolicy
    .stitchHorizontalPaddingDp(LocalConfiguration.current.screenWidthDp)
    .dp
