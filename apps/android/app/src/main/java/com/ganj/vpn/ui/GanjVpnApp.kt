package com.ganj.vpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ganj.vpn.composition.GanjComposition
import com.ganj.vpn.presentation.CheckoutActionHandle
import com.ganj.vpn.presentation.CheckoutEffectResult
import com.ganj.vpn.presentation.CheckoutSafeAction
import com.ganj.vpn.presentation.CheckoutUiState
import com.ganj.vpn.presentation.ConnectionUiState
import com.ganj.vpn.presentation.ContentState
import com.ganj.vpn.presentation.GanjUiEvent
import com.ganj.vpn.presentation.GanjUiState
import com.ganj.vpn.presentation.PlanUiModel
import com.ganj.vpn.presentation.ServiceUiModel
import com.ganj.vpn.presentation.UiFailure
import com.ganj.vpn.presentation.UiTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DeepNavy = Color(0xFF06111F)
private val SurfaceNavy = Color(0xFF0C1C30)
private val IosBlue = Color(0xFF0A84FF)
private val Emerald = Color(0xFF30D158)
private val Gold = Color(0xFFFFC857)
private val Danger = Color(0xFFFF5A67)
private val Muted = Color(0xFF92A4B8)

private val GanjDarkScheme = darkColorScheme(
    primary = IosBlue,
    secondary = Emerald,
    background = DeepNavy,
    surface = SurfaceNavy,
    onBackground = Color.White,
    onSurface = Color.White,
)

private enum class AppTab(val title: String) {
    HOME("Home"),
    SERVERS("Servers"),
    CONNECT("Connect"),
    STORE("Store"),
    ACCOUNT("Account"),
}

@Composable
fun GanjVpnApp(
    composition: GanjComposition,
    onLaunchGooglePlay: suspend (CheckoutActionHandle) -> CheckoutEffectResult,
) {
    MaterialTheme(colorScheme = GanjDarkScheme) {
        val controller = remember(composition) { composition.controller }
        val reducer = remember(composition) { composition.reducer }
        val scope = rememberCoroutineScope()
        var selectedTab by remember { mutableStateOf(AppTab.CONNECT) }
        var state by remember(composition) { mutableStateOf(composition.restoreUiState()) }
        var refreshJob by remember { mutableStateOf<Job?>(null) }
        var checkoutJob by remember { mutableStateOf<Job?>(null) }
        var connectionJob by remember { mutableStateOf<Job?>(null) }

        fun commit(next: GanjUiState) {
            composition.retainUiState(next)
            state = next
        }

        fun refresh() {
            refreshJob?.cancel()
            commit(reducer.reduce(state, GanjUiEvent.RefreshRequested))
            val loadingState = state
            refreshJob = scope.launch {
                commit(withContext(Dispatchers.IO) { controller.refresh(loadingState) })
            }
        }

        fun checkout(plan: PlanUiModel) {
            checkoutJob?.cancel()
            commit(reducer.reduce(state, GanjUiEvent.CheckoutRequested(plan.id)))
            val pendingState = state
            checkoutJob = scope.launch {
                commit(withContext(Dispatchers.IO) { controller.checkout(pendingState, plan.id) })
            }
        }

        fun requestProfile(entitlementId: String) {
            connectionJob?.cancel()
            commit(reducer.reduce(state, GanjUiEvent.ConnectionRequested(entitlementId)))
            val pendingState = state
            connectionJob = scope.launch {
                commit(withContext(Dispatchers.IO) { controller.prepareConnection(pendingState, entitlementId) })
            }
        }

        LaunchedEffect(composition) { refresh() }

        DisposableEffect(composition) {
            val registration = composition.observePlayPurchases { event ->
                scope.launch { commit(controller.onPlayPurchaseEvent(state, event)) }
            }
            onDispose { registration.close() }
        }

        val pendingCheckout = state.checkout as? CheckoutUiState.Pending
        val playAction = pendingCheckout?.action as? CheckoutSafeAction.LaunchGooglePlay
        LaunchedEffect(playAction?.handle) {
            val handle = playAction?.handle ?: return@LaunchedEffect
            val planId = pendingCheckout?.planId ?: return@LaunchedEffect
            val result = onLaunchGooglePlay(handle)
            commit(controller.onCheckoutEffectResult(state, planId, result))
        }

        Scaffold(
            containerColor = DeepNavy,
            bottomBar = { GanjBottomBar(selected = selectedTab, onSelected = { selectedTab = it }) },
        ) { padding ->
            when (selectedTab) {
                AppTab.HOME -> HomeScreen(
                    state = state,
                    onOpenConnect = { selectedTab = AppTab.CONNECT },
                    onOpenStore = { selectedTab = AppTab.STORE },
                    onOpenServices = { selectedTab = AppTab.ACCOUNT },
                    onRefresh = ::refresh,
                    modifier = Modifier.padding(padding),
                )
                AppTab.SERVERS -> SmartRoutingScreen(
                    state = state,
                    onSelectService = { commit(reducer.reduce(state, GanjUiEvent.SelectService(it))) },
                    onConnect = {
                        requestProfile(it)
                        selectedTab = AppTab.CONNECT
                    },
                    onRetry = ::refresh,
                    modifier = Modifier.padding(padding),
                )
                AppTab.CONNECT -> ConnectionDashboard(
                    state = state,
                    onConnect = ::requestProfile,
                    onClear = { commit(reducer.reduce(state, GanjUiEvent.ClearConnection)) },
                    onOpenServices = { selectedTab = AppTab.ACCOUNT },
                    onRetry = ::refresh,
                    modifier = Modifier.padding(padding),
                )
                AppTab.STORE -> StoreScreen(
                    state = state,
                    onSelect = { commit(reducer.reduce(state, GanjUiEvent.SelectPlan(it))) },
                    onPurchase = ::checkout,
                    onRetry = ::refresh,
                    modifier = Modifier.padding(padding),
                )
                AppTab.ACCOUNT -> MyServicesScreen(
                    state = state,
                    onSelectService = { commit(reducer.reduce(state, GanjUiEvent.SelectService(it))) },
                    onConnect = {
                        state.selectedEntitlementId?.let(::requestProfile)
                        selectedTab = AppTab.CONNECT
                    },
                    onBuy = { selectedTab = AppTab.STORE },
                    onRetry = ::refresh,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: GanjUiState,
    onOpenConnect: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenServices: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = state.connection is ConnectionUiState.ProfileReady
    Page(modifier) {
        AppHeader("Ganj VPN", "Your privacy dashboard", onRefresh)
        Spacer(Modifier.height(24.dp))
        GlassCard(accent = if (ready) Emerald else IosBlue) {
            Text("Connection profile", color = Muted, fontSize = 12.sp)
            Text(
                if (ready) "Ready for VPN core" else "Choose an active service",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = if (ready) Emerald else Color.White,
            )
            Text(state.selectedService?.displayName ?: "No active service selected", color = Muted)
            Button(onClick = onOpenConnect, modifier = Modifier.fillMaxWidth()) {
                Text("Open secure connection")
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAction("My services", "${state.serviceItems.size} active or previous", onOpenServices, Modifier.weight(1f))
            QuickAction("Store", "Choose a subscription", onOpenStore, Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        Text("Subscription protected", fontWeight = FontWeight.SemiBold)
        Text(
            "Connection access is issued only from a verified Ganj entitlement and a registered device.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SmartRoutingScreen(
    state: GanjUiState,
    onSelectService: (String) -> Unit,
    onConnect: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Page(modifier) {
        AppHeader("Smart routing", "Only servers allowed by your service", onRetry)
        Spacer(Modifier.height(20.dp))
        when (val services = state.services) {
            ContentState.Loading -> LoadingCard("Loading eligible services")
            ContentState.Empty -> EmptyCard("No service yet", "Open Store to activate one", onRetry)
            ContentState.AuthRequired -> AuthCard(onRetry)
            is ContentState.Error -> ErrorCard(services.failure, onRetry)
            is ContentState.Ready -> services.items.forEach { service ->
                GlassCard(accent = if (service.isActive) IosBlue else Muted) {
                    Text(service.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "${service.countryCode ?: "Global"} • ${service.allowedProtocols.joinToString()}",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    Button(
                        enabled = service.isActive,
                        onClick = {
                            onSelectService(service.entitlementId)
                            onConnect(service.entitlementId)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (service.isActive) "Smart connect" else service.status.name)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        Text(
            "Server choice and profile issuance are revalidated by the backend for this entitlement.",
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun ConnectionDashboard(
    state: GanjUiState,
    onConnect: (String) -> Unit,
    onClear: () -> Unit,
    onOpenServices: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connection = state.connection
    val ready = connection is ConnectionUiState.ProfileReady
    val requesting = connection is ConnectionUiState.Requesting
    val connectionColor by animateColorAsState(
        targetValue = when {
            ready -> Emerald
            requesting -> Gold
            else -> IosBlue
        },
        label = "connectionColor",
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (requesting) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
        label = "buttonScale",
    )
    val service = state.selectedService

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(connectionColor.copy(alpha = 0.18f), DeepNavy),
                    radius = 980f,
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(260.dp)
                .blur(64.dp)
                .background(connectionColor.copy(alpha = 0.18f), CircleShape),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppHeader("GANJ VPN", "Secure. Fast. Yours.", onRetry)
            Spacer(Modifier.height(46.dp))
            Text(
                text = when {
                    ready -> "Profile verified"
                    requesting -> "Preparing securely"
                    else -> "Ready to connect"
                },
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(service?.displayName ?: "No service selected", color = Muted)
            Spacer(Modifier.height(34.dp))
            Box(
                modifier = Modifier
                    .scale(buttonScale)
                    .size(180.dp)
                    .background(Color(0x18FFFFFF), CircleShape)
                    .border(1.dp, Color(0x3DFFFFFF), CircleShape)
                    .padding(16.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(connectionColor.copy(alpha = 0.95f), connectionColor.copy(alpha = 0.62f)),
                        ),
                        CircleShape,
                    )
                    .clickable(
                        enabled = service?.isActive == true && !requesting,
                        onClick = { service?.entitlementId?.let { if (ready) onClear() else onConnect(it) } },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (requesting) "…" else if (ready) "OK" else "GO", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(if (ready) "Clear prepared profile" else "Use selected service", fontSize = 11.sp)
                }
            }
            if (service == null) {
                OutlinedButton(onClick = onOpenServices, modifier = Modifier.padding(top = 18.dp)) {
                    Text("Choose my service")
                }
            }
            when (connection) {
                ConnectionUiState.AuthRequired -> AuthCard(onRetry)
                is ConnectionUiState.Failed -> ErrorCard(connection.failure) {
                    service?.entitlementId?.let(onConnect) ?: onRetry()
                }
                else -> Unit
            }
            Spacer(Modifier.height(36.dp))
            GlassCard(accent = connectionColor) {
                Text("Device-bound", fontWeight = FontWeight.Bold)
                Text(
                    if (ready) "A short-lived encrypted profile is held outside presentation state."
                    else "The app sends only your selected service identity into the connection flow.",
                    color = Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StoreScreen(
    state: GanjUiState,
    onSelect: (String) -> Unit,
    onPurchase: (PlanUiModel) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Page(modifier) {
        AppHeader("Ganj Store", "Backend-verified subscriptions", onRetry)
        Spacer(Modifier.height(20.dp))
        when (val catalog = state.catalog) {
            ContentState.Loading -> LoadingCard("Loading plans")
            ContentState.Empty -> EmptyCard("No plans available", "Try again in a moment", onRetry)
            ContentState.AuthRequired -> AuthCard(onRetry)
            is ContentState.Error -> ErrorCard(catalog.failure, onRetry)
            is ContentState.Ready -> catalog.items.forEach { product ->
                val selected = state.selectedPlanId == product.id
                PlanCard(product, selected, onSelect, onPurchase)
                Spacer(Modifier.height(14.dp))
            }
        }
        CheckoutStatus(
            checkout = state.checkout,
            onRetry = { state.selectedPlan?.let(onPurchase) ?: onRetry() },
        )
        Text(
            "Service access becomes active only after provider and backend verification.",
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun PlanCard(
    product: PlanUiModel,
    selected: Boolean,
    onSelect: (String) -> Unit,
    onPurchase: (PlanUiModel) -> Unit,
) {
    GlassCard(
        accent = if (product.tier == UiTier.VIP) Gold else IosBlue,
        modifier = Modifier.clickable { onSelect(product.id) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(product.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(product.tier.name, color = if (product.tier == UiTier.VIP) Gold else Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatPrice(product), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(product.durationDays?.let { "$it days" } ?: "Service plan", color = Muted, fontSize = 11.sp)
            }
        }
        product.benefits.take(6).forEach { Text("✓  $it", fontSize = 13.sp) }
        Button(
            onClick = { onPurchase(product) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (product.tier == UiTier.VIP) Gold else IosBlue,
                contentColor = if (product.tier == UiTier.VIP) DeepNavy else Color.White,
            ),
        ) {
            Text(if (selected) "Continue with Google Play" else "Choose with Google Play")
        }
    }
}

@Composable
private fun CheckoutStatus(checkout: CheckoutUiState, onRetry: () -> Unit) {
    when (checkout) {
        CheckoutUiState.Idle -> Unit
        CheckoutUiState.AuthRequired -> AuthCard(onRetry)
        is CheckoutUiState.Pending -> GlassCard(accent = Gold) {
            Text("Payment pending", fontWeight = FontWeight.Bold)
            Text(checkoutActionText(checkout.action), color = Muted, fontSize = 12.sp)
        }
        is CheckoutUiState.Verified -> GlassCard(accent = IosBlue) {
            Text("Payment verified", fontWeight = FontWeight.Bold)
            Text("Waiting for the activated service to sync", color = Muted, fontSize = 12.sp)
        }
        is CheckoutUiState.Active -> GlassCard(accent = Emerald) {
            Text("Subscription active", fontWeight = FontWeight.Bold)
            Text("Your service is ready in My Services", color = Muted, fontSize = 12.sp)
        }
        is CheckoutUiState.Failed -> ErrorCard(checkout.failure, onRetry)
    }
}

@Composable
private fun MyServicesScreen(
    state: GanjUiState,
    onSelectService: (String) -> Unit,
    onConnect: () -> Unit,
    onBuy: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Page(modifier) {
        AppHeader("My services", "Synced from your Ganj account", onRetry)
        Spacer(Modifier.height(20.dp))
        when (val services = state.services) {
            ContentState.Loading -> LoadingCard("Loading services")
            ContentState.Empty -> EmptyCard("No subscription yet", "Choose a plan to get started", onBuy)
            ContentState.AuthRequired -> AuthCard(onRetry)
            is ContentState.Error -> ErrorCard(services.failure, onRetry)
            is ContentState.Ready -> services.items.forEach { service ->
                ServiceCard(
                    service = service,
                    selected = state.selectedEntitlementId == service.entitlementId,
                    onSelect = { onSelectService(service.entitlementId) },
                    onConnect = onConnect,
                )
                Spacer(Modifier.height(14.dp))
            }
        }
        Button(onClick = onBuy, modifier = Modifier.fillMaxWidth()) { Text("Buy another subscription") }
    }
}

@Composable
private fun ServiceCard(
    service: ServiceUiModel,
    selected: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
) {
    GlassCard(
        accent = when {
            !service.isActive -> Danger
            service.tier == UiTier.VIP -> Gold
            else -> Emerald
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(service.displayName, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(service.status.name, color = if (service.isActive) Emerald else Danger, fontSize = 12.sp)
            }
            if (selected) Text("SELECTED", color = Emerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "${formatTraffic(service.remainingBytes)} • ${service.deviceLimit} devices allowed",
            color = Muted,
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onSelect, enabled = service.isActive, modifier = Modifier.weight(1f)) {
                Text(if (selected) "Selected" else "Use plan")
            }
            Button(onClick = onConnect, enabled = service.isActive && selected, modifier = Modifier.weight(1f)) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun LoadingCard(text: String) = GlassCard(accent = IosBlue) {
    Text(text, fontWeight = FontWeight.SemiBold)
    Text("Please wait…", color = Muted, fontSize = 12.sp)
}

@Composable
private fun EmptyCard(title: String, subtitle: String, onAction: () -> Unit) = GlassCard(accent = Muted) {
    Text(title, fontWeight = FontWeight.Bold)
    Text(subtitle, color = Muted, fontSize = 12.sp)
    OutlinedButton(onClick = onAction) { Text("Refresh") }
}

@Composable
private fun AuthCard(onRetry: () -> Unit) = GlassCard(accent = Gold) {
    Text("Sign in required", fontWeight = FontWeight.Bold)
    Text("Connect your Telegram account, then refresh this page.", color = Muted, fontSize = 12.sp)
    OutlinedButton(onClick = onRetry) { Text("Refresh session") }
}

@Composable
private fun ErrorCard(failure: UiFailure, onRetry: () -> Unit) = GlassCard(accent = Danger) {
    Text(failureMessage(failure), fontWeight = FontWeight.Bold, color = Danger)
    failure.requestId?.let { Text("Request • ${it.take(8)}", color = Muted, fontSize = 10.sp) }
    if (failure.retryable) OutlinedButton(onClick = onRetry) { Text("Try again") }
}

@Composable
private fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        content = content,
    )
}

@Composable
private fun AppHeader(title: String, subtitle: String, onRefresh: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Surface(
            color = Color(0x22FFFFFF),
            shape = RoundedCornerShape(999.dp),
            modifier = if (onRefresh != null) Modifier.clickable(onClick = onRefresh) else Modifier,
        ) {
            Text(if (onRefresh != null) "REFRESH" else "GANJ", modifier = Modifier.padding(12.dp), fontSize = 10.sp)
        }
    }
}

@Composable
private fun GlassCard(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x14FFFFFF), RoundedCornerShape(24.dp))
            .border(1.dp, accent.copy(alpha = 0.36f), RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun QuickAction(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color(0x14FFFFFF), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun GanjBottomBar(selected: AppTab, onSelected: (AppTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF20A1727))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val central = tab == AppTab.CONNECT
            Column(
                modifier = Modifier
                    .size(if (central) 64.dp else 58.dp)
                    .background(
                        color = when {
                            central -> IosBlue
                            isSelected -> Color(0x22FFFFFF)
                            else -> Color.Transparent
                        },
                        shape = CircleShape,
                    )
                    .clickable { onSelected(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (central) "G" else tab.title.take(1),
                    fontWeight = FontWeight.Bold,
                    color = if (central || isSelected) Color.White else Muted,
                )
                if (!central) Text(tab.title, fontSize = 9.sp, color = if (isSelected) Color.White else Muted)
            }
        }
    }
}

private fun formatPrice(product: PlanUiModel): String = if (product.amountMinor == 0L) {
    "Free"
} else {
    val major = product.amountMinor / 100
    val minor = product.amountMinor % 100
    "$major.${minor.toString().padStart(2, '0')} ${product.currency}"
}

private fun formatTraffic(bytes: Long?): String = when {
    bytes == null -> "Unlimited traffic"
    bytes >= 1_000_000_000 -> "${bytes / 1_000_000_000} GB left"
    else -> "${bytes / 1_000_000} MB left"
}

private fun checkoutActionText(action: CheckoutSafeAction?): String = when (action) {
    is CheckoutSafeAction.LaunchGooglePlay -> "Opening the secure Google Play checkout."
    CheckoutSafeAction.WaitForProvider, null -> "Waiting for Play and backend confirmation."
}

private fun failureMessage(failure: UiFailure): String = when (failure.messageKey) {
    "auth.required" -> "Sign in to continue."
    "entitlement.denied" -> "This action is not included in your service."
    "request.conflict" -> "This request is already being processed."
    "request.rate_limited" -> "Too many attempts. Please wait and retry."
    "network.unavailable" -> "Check your internet connection."
    "server.unavailable" -> "The service is temporarily unavailable."
    "connection.context_unavailable" -> "Secure device context is not ready yet."
    "connection.service_inactive" -> "Choose an active service."
    "billing.provider_unavailable" -> "This payment method is not available yet."
    else -> "The request could not be completed safely."
}
