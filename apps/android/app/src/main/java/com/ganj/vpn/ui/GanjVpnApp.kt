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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ganj.vpn.composition.GanjComposition
import com.ganj.vpn.enterprise.BugCategory
import com.ganj.vpn.enterprise.BugReportInput
import com.ganj.vpn.enterprise.BugReportUiState
import com.ganj.vpn.enterprise.DiagnosticUiState
import com.ganj.vpn.enterprise.EnterpriseEvent
import com.ganj.vpn.enterprise.EnterpriseUiState
import com.ganj.vpn.enterprise.ProductAvailabilityUiState
import com.ganj.vpn.presentation.CheckoutActionHandle
import com.ganj.vpn.presentation.CheckoutEffectResult
import com.ganj.vpn.presentation.CheckoutSafeAction
import com.ganj.vpn.presentation.CheckoutUiState
import com.ganj.vpn.presentation.ConnectionActionHandle
import com.ganj.vpn.presentation.ConnectionEffectResult
import com.ganj.vpn.presentation.ConnectionSafeAction
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

private val DeepNavy = Color(0xFF0A1019)
private val IosBlue = GanjBlue
private val Emerald = GanjGreen
private val Gold = GanjAmber
private val Danger = GanjRed
private val Muted = Color(0xFF6C7583)

private enum class AppTab(val title: String) {
    HOME("خانه"),
    SERVERS("سرورها"),
    CONNECT("اتصال"),
    STORE("فروشگاه"),
    ACCOUNT("حساب"),
}

@Composable
fun GanjVpnApp(
    composition: GanjComposition,
    onLaunchGooglePlay: suspend (CheckoutActionHandle) -> CheckoutEffectResult,
    onLaunchVpn: suspend (ConnectionActionHandle) -> ConnectionEffectResult,
) {
    GanjTheme {
        val controller = remember(composition) { composition.controller }
        val reducer = remember(composition) { composition.reducer }
        val enterpriseController = remember(composition) { composition.enterpriseController }
        val enterpriseReducer = remember(composition) { composition.enterpriseReducer }
        val scope = rememberCoroutineScope()
        var selectedTab by remember { mutableStateOf(AppTab.CONNECT) }
        var state by remember(composition) { mutableStateOf(composition.restoreUiState()) }
        var refreshJob by remember { mutableStateOf<Job?>(null) }
        var checkoutJob by remember { mutableStateOf<Job?>(null) }
        var connectionJob by remember { mutableStateOf<Job?>(null) }
        var enterpriseJob by remember { mutableStateOf<Job?>(null) }
        var enterpriseState by remember(composition) {
            mutableStateOf(composition.restoreEnterpriseState())
        }

        fun commit(next: GanjUiState) {
            composition.retainUiState(next)
            state = next
        }

        fun commitEnterprise(next: EnterpriseUiState) {
            composition.retainEnterpriseState(next)
            enterpriseState = next
        }

        fun refreshEnterprise() {
            enterpriseJob?.cancel()
            commitEnterprise(enterpriseReducer.reduce(enterpriseState, EnterpriseEvent.RefreshRequested))
            val loadingState = enterpriseState
            enterpriseJob = scope.launch {
                commitEnterprise(withContext(Dispatchers.IO) { enterpriseController.refresh(loadingState) })
            }
        }

        fun submitBug(input: BugReportInput) {
            enterpriseJob?.cancel()
            commitEnterprise(enterpriseReducer.reduce(enterpriseState, EnterpriseEvent.BugSubmissionStarted))
            val submittingState = enterpriseState
            enterpriseJob = scope.launch {
                commitEnterprise(withContext(Dispatchers.IO) { enterpriseController.submitBug(submittingState, input) })
            }
        }

        fun submitDiagnostics(explicitConsent: Boolean) {
            enterpriseJob?.cancel()
            commitEnterprise(enterpriseReducer.reduce(enterpriseState, EnterpriseEvent.DiagnosticStarted))
            val runningState = enterpriseState
            enterpriseJob = scope.launch {
                commitEnterprise(
                    withContext(Dispatchers.IO) {
                        enterpriseController.submitDiagnostics(runningState, explicitConsent)
                    },
                )
            }
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

        fun disconnectTunnel() {
            connectionJob?.cancel()
            connectionJob = scope.launch {
                commit(controller.onDisconnectResult(state, composition.disconnectVpn()))
            }
        }

        LaunchedEffect(composition) {
            refresh()
            refreshEnterprise()
        }

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

        val readyConnection = state.connection as? ConnectionUiState.ProfileReady
        val tunnelAction = readyConnection?.action as? ConnectionSafeAction.StartTunnel
        LaunchedEffect(tunnelAction?.handle) {
            val handle = tunnelAction?.handle ?: return@LaunchedEffect
            val entitlementId = readyConnection?.entitlementId ?: return@LaunchedEffect
            val result = onLaunchVpn(handle)
            commit(controller.onConnectionEffectResult(state, entitlementId, result))
        }

        when (val availability = enterpriseState.availability) {
            is ProductAvailabilityUiState.ForcedUpdate -> ProductGateScreen(
                title = "Security update required",
                message = "Install version ${availability.latestVersion} to continue safely.",
                onRetry = ::refreshEnterprise,
            )
            is ProductAvailabilityUiState.Maintenance -> ProductGateScreen(
                title = "Scheduled maintenance",
                message = availability.message ?: "Ganj VPN is temporarily unavailable.",
                onRetry = ::refreshEnterprise,
            )
            else -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
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
                    onClear = ::disconnectTunnel,
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
                    enterpriseState = enterpriseState,
                    onSelectService = { commit(reducer.reduce(state, GanjUiEvent.SelectService(it))) },
                    onConnect = {
                        state.selectedEntitlementId?.let(::requestProfile)
                        selectedTab = AppTab.CONNECT
                    },
                    onBuy = { selectedTab = AppTab.STORE },
                    onRetry = ::refresh,
                    onEnterpriseRefresh = ::refreshEnterprise,
                    onSubmitBug = ::submitBug,
                    onSubmitDiagnostics = ::submitDiagnostics,
                    onClearBug = {
                        commitEnterprise(enterpriseReducer.reduce(enterpriseState, EnterpriseEvent.ClearBugResult))
                    },
                    onClearDiagnostic = {
                        commitEnterprise(
                            enterpriseReducer.reduce(enterpriseState, EnterpriseEvent.ClearDiagnosticResult),
                        )
                    },
                    modifier = Modifier.padding(padding),
                )
            }
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
    val ready = state.connection is ConnectionUiState.Connected
    Page(modifier) {
        AppHeader("Ganj VPN", "Your privacy dashboard", onRefresh)
        Spacer(Modifier.height(24.dp))
        GlassCard(accent = if (ready) Emerald else IosBlue) {
            Text("Connection profile", color = Muted, fontSize = 12.sp)
            Text(
                if (ready) "VPN connected" else "Choose an active service",
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
    val connected = connection is ConnectionUiState.Connected
    val requesting = connection is ConnectionUiState.Requesting || connection is ConnectionUiState.ProfileReady
    val connectionColor by animateColorAsState(
        targetValue = when {
            connected -> Emerald
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
                    colors = listOf(connectionColor.copy(alpha = 0.18f), MaterialTheme.colorScheme.background),
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
                    connected -> "Connected securely"
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
                        onClick = { service?.entitlementId?.let { if (connected) onClear() else onConnect(it) } },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (requesting) "…" else if (connected) "ON" else "GO", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(if (connected) "Disconnect securely" else "Use selected service", fontSize = 11.sp)
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
                    if (connected) "The verified device profile is active only inside the VPN runtime."
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
    enterpriseState: EnterpriseUiState,
    onSelectService: (String) -> Unit,
    onConnect: () -> Unit,
    onBuy: () -> Unit,
    onRetry: () -> Unit,
    onEnterpriseRefresh: () -> Unit,
    onSubmitBug: (BugReportInput) -> Unit,
    onSubmitDiagnostics: (Boolean) -> Unit,
    onClearBug: () -> Unit,
    onClearDiagnostic: () -> Unit,
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
        Spacer(Modifier.height(24.dp))
        EnterpriseStatusCard(enterpriseState.availability, onEnterpriseRefresh)
        if (enterpriseState.features.bugReportsEnabled) {
            Spacer(Modifier.height(14.dp))
            BugReportPanel(enterpriseState.bugReport, onSubmitBug, onClearBug)
        }
        if (enterpriseState.features.diagnosticsEnabled) {
            Spacer(Modifier.height(14.dp))
            DiagnosticPanel(enterpriseState.diagnostic, onSubmitDiagnostics, onClearDiagnostic)
        }
    }
}

@Composable
private fun ProductGateScreen(
    title: String,
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(accent = Gold) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(message, color = Muted, textAlign = TextAlign.Center)
            Text(
                "Connections and purchases are paused until the signed product policy allows them.",
                color = Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Check again") }
        }
    }
}

@Composable
private fun EnterpriseStatusCard(
    availability: ProductAvailabilityUiState,
    onRefresh: () -> Unit,
) {
    val accent = when (availability) {
        ProductAvailabilityUiState.Available -> Emerald
        ProductAvailabilityUiState.Loading -> IosBlue
        is ProductAvailabilityUiState.OptionalUpdate -> Gold
        else -> Danger
    }
    GlassCard(accent = accent) {
        Text("Product status", fontWeight = FontWeight.Bold)
        Text(
            when (availability) {
                ProductAvailabilityUiState.Loading -> "Checking signed runtime policy…"
                ProductAvailabilityUiState.Available -> "Operational"
                is ProductAvailabilityUiState.OptionalUpdate ->
                    "Version ${availability.latestVersion} is available."
                is ProductAvailabilityUiState.ForcedUpdate -> "A security update is required."
                is ProductAvailabilityUiState.Maintenance -> availability.message ?: "Maintenance in progress."
                ProductAvailabilityUiState.AuthRequired -> "Sign in to resolve device policy."
                is ProductAvailabilityUiState.Failed -> "Runtime policy is unavailable; optional features are off."
            },
            color = Muted,
            fontSize = 12.sp,
        )
        OutlinedButton(onClick = onRefresh) { Text("Refresh product status") }
    }
}

@Composable
private fun BugReportPanel(
    status: BugReportUiState,
    onSubmit: (BugReportInput) -> Unit,
    onClear: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(BugCategory.CONNECTION) }
    var consent by remember { mutableStateOf(false) }
    val submitting = status == BugReportUiState.Submitting

    GlassCard(accent = IosBlue) {
        Text("Report a problem", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Do not paste VPN configurations, credentials, links, or private message content.",
            color = Muted,
            fontSize = 11.sp,
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(200) },
            enabled = !submitting,
            label = { Text("Short title") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(12_000) },
            enabled = !submitting,
            label = { Text("What happened?") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Category", color = Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(BugCategory.CONNECTION, BugCategory.PURCHASE, BugCategory.ACCOUNT).forEach { option ->
                OutlinedButton(
                    onClick = { category = option },
                    enabled = !submitting,
                ) { Text(if (category == option) "✓ ${option.name}" else option.name, fontSize = 10.sp) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !submitting)
            Text(
                "I approve sending coarse device, network type, connection state and error code.",
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
        }
        when (status) {
            BugReportUiState.Idle -> Button(
                onClick = {
                    onSubmit(BugReportInput(title, description, category, consent))
                },
                enabled = title.trim().length >= 3 && description.trim().length >= 3 && consent,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send privacy-safe report") }
            BugReportUiState.Submitting -> LoadingCard("Submitting report")
            BugReportUiState.AuthRequired -> AuthCard(onClear)
            is BugReportUiState.Submitted -> {
                Text("Report ${status.publicCode} submitted", color = Emerald, fontWeight = FontWeight.Bold)
                Text("Status: ${status.status.name}", color = Muted, fontSize = 12.sp)
                OutlinedButton(onClick = onClear) { Text("Report another issue") }
            }
            is BugReportUiState.Failed -> {
                Text(enterpriseMessage(status.messageKey), color = Danger, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onClear) { Text(if (status.retryable) "Try again" else "Edit report") }
            }
        }
    }
}

@Composable
private fun DiagnosticPanel(
    status: DiagnosticUiState,
    onSubmit: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    var consent by remember { mutableStateOf(false) }
    val busy = status == DiagnosticUiState.Running || status == DiagnosticUiState.Uploading
    GlassCard(accent = Emerald) {
        Text("Privacy-safe diagnostics", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Tests only result classes, latency, packet loss, VPN state and device integrity. " +
                "No hostname, destination, IP address, DNS query, payload, credential or configuration is collected.",
            color = Muted,
            fontSize = 11.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !busy)
            Text(
                "I approve running and uploading this minimized diagnostic report.",
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
        }
        when (status) {
            DiagnosticUiState.Idle -> Button(
                onClick = { onSubmit(consent) },
                enabled = consent,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Run diagnostics") }
            DiagnosticUiState.Running -> LoadingCard("Running allowlisted tests")
            DiagnosticUiState.Uploading -> LoadingCard("Uploading redacted results")
            DiagnosticUiState.AuthRequired -> AuthCard(onClear)
            is DiagnosticUiState.Submitted -> {
                Text("Diagnostic report submitted", color = Emerald, fontWeight = FontWeight.Bold)
                Text("Redaction policy: ${status.redactionVersion}", color = Muted, fontSize = 12.sp)
                OutlinedButton(onClick = onClear) { Text("Done") }
            }
            is DiagnosticUiState.Failed -> {
                Text(enterpriseMessage(status.messageKey), color = Danger, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onClear) { Text(if (status.retryable) "Retry" else "Review consent") }
            }
        }
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(999.dp),
            modifier = if (onRefresh != null) {
                Modifier
                    .minimumInteractiveComponentSize()
                    .semantics {
                        role = Role.Button
                        contentDescription = "تازه‌سازی صفحه"
                    }
                    .clickable(onClick = onRefresh)
            } else {
                Modifier.semantics { contentDescription = "گنج" }
            },
        ) {
            Text(
                if (onRefresh != null) "تازه‌سازی" else "گنج",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                fontSize = 11.sp,
            )
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
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
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
            .minimumInteractiveComponentSize()
            .semantics(mergeDescendants = true) { role = Role.Button }
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
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
            .background(MaterialTheme.colorScheme.surface)
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
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            else -> Color.Transparent
                        },
                        shape = CircleShape,
                    )
                    .semantics(mergeDescendants = true) {
                        role = Role.Tab
                        selected = isSelected
                        contentDescription = UiAccessibilityPolicy.destinationDescription(tab.title, isSelected)
                    }
                    .clickable { onSelected(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (central) "G" else tab.title.take(1),
                    fontWeight = FontWeight.Bold,
                    color = when {
                        central -> Color.White
                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (!central) {
                    Text(
                        tab.title,
                        fontSize = 9.sp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
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
    "connection.permission_denied" -> "Android VPN permission is required to connect."
    "connection.action_expired", "connection.profile_consumed", "connection.profile_expired" ->
        "The secure connection request expired. Try again."
    "connection.device_crypto_unavailable" -> "Secure device keys are not ready on this device."
    "connection.profile_authentication_failed", "connection.profile_rejected" ->
        "The encrypted server profile could not be verified."
    "connection.protocol_unsupported" -> "This server protocol is not supported on this version."
    "connection.tunnel_start_failed" -> "The VPN tunnel could not start. Try another network."
    "connection.disconnect_failed" -> "The VPN could not disconnect cleanly. Try again."
    "billing.provider_unavailable" -> "This payment method is not available yet."
    else -> "The request could not be completed safely."
}

private fun enterpriseMessage(messageKey: String): String = when (messageKey) {
    "privacy.consent_required" -> "Review and approve the privacy summary first."
    "bug_report.invalid_or_sensitive" -> "Remove configuration, credential, or invalid content."
    "bug_report.disabled" -> "Bug reporting is temporarily unavailable."
    "diagnostic.disabled" -> "Diagnostics are temporarily unavailable."
    "diagnostic.context_unavailable" -> "Device context is not ready. Try again later."
    "diagnostic.collection_failed" -> "The diagnostic checks could not complete safely."
    "diagnostic.invalid" -> "The diagnostic result did not pass local validation."
    "network.unavailable" -> "Check your internet connection."
    "request.rate_limited" -> "Too many attempts. Please wait and retry."
    "server.unavailable" -> "The service is temporarily unavailable."
    else -> "This enterprise feature is unavailable right now."
}
