package com.ganj.vpn.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ganj.vpn.R
import com.ganj.vpn.composition.GanjComposition
import com.ganj.vpn.enterprise.BugReportInput
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
import com.ganj.vpn.presentation.GanjUiEvent
import com.ganj.vpn.presentation.GanjUiState
import com.ganj.vpn.presentation.PlanUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GanjVpnApp(
    composition: GanjComposition,
    telegramLinked: Boolean,
    telegramBusy: Boolean,
    telegramError: Boolean,
    accountRefreshGeneration: Int,
    onTelegramLogin: () -> Unit,
    onTelegramLogout: () -> Unit,
    onLaunchGooglePlay: suspend (CheckoutActionHandle) -> CheckoutEffectResult,
    onLaunchVpn: suspend (ConnectionActionHandle) -> ConnectionEffectResult,
) {
    val controller = remember(composition) { composition.controller }
    val reducer = remember(composition) { composition.reducer }
    val enterpriseController = remember(composition) { composition.enterpriseController }
    val enterpriseReducer = remember(composition) { composition.enterpriseReducer }
    val scope = rememberCoroutineScope()

    var selectedDestination by remember { mutableStateOf(GanjDestination.Connect) }
    var state by remember(composition) { mutableStateOf(composition.restoreUiState()) }
    var enterpriseState by remember(composition) {
        mutableStateOf(composition.restoreEnterpriseState())
    }
    var refreshJob by remember { mutableStateOf<Job?>(null) }
    var checkoutJob by remember { mutableStateOf<Job?>(null) }
    var connectionJob by remember { mutableStateOf<Job?>(null) }
    var enterpriseJob by remember { mutableStateOf<Job?>(null) }

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
            commitEnterprise(
                withContext(Dispatchers.IO) {
                    enterpriseController.refresh(loadingState)
                },
            )
        }
    }

    fun submitBug(input: BugReportInput) {
        enterpriseJob?.cancel()
        commitEnterprise(enterpriseReducer.reduce(enterpriseState, EnterpriseEvent.BugSubmissionStarted))
        val submittingState = enterpriseState
        enterpriseJob = scope.launch {
            commitEnterprise(
                withContext(Dispatchers.IO) {
                    enterpriseController.submitBug(submittingState, input)
                },
            )
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
            commit(
                withContext(Dispatchers.IO) {
                    controller.refresh(loadingState)
                },
            )
        }
    }

    fun checkout(plan: PlanUiModel) {
        checkoutJob?.cancel()
        commit(reducer.reduce(state, GanjUiEvent.CheckoutRequested(plan.id)))
        val pendingState = state
        checkoutJob = scope.launch {
            commit(
                withContext(Dispatchers.IO) {
                    controller.checkout(pendingState, plan.id)
                },
            )
        }
    }

    fun requestProfile(entitlementId: String) {
        connectionJob?.cancel()
        commit(reducer.reduce(state, GanjUiEvent.ConnectionRequested(entitlementId)))
        val pendingState = state
        connectionJob = scope.launch {
            commit(
                withContext(Dispatchers.IO) {
                    controller.prepareConnection(pendingState, entitlementId)
                },
            )
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

    LaunchedEffect(accountRefreshGeneration) {
        if (accountRefreshGeneration > 0) {
            refresh()
            refreshEnterprise()
        }
    }

    DisposableEffect(composition) {
        val registration = composition.observePlayPurchases { event ->
            scope.launch {
                commit(controller.onPlayPurchaseEvent(state, event))
            }
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

    GanjLiquidCanvas {
        when (val availability = enterpriseState.availability) {
            is ProductAvailabilityUiState.ForcedUpdate -> ProductGateScreen(
                title = stringResource(R.string.gate_security_update_title),
                message = stringResource(R.string.gate_security_update_body, availability.latestVersion),
                onRetry = ::refreshEnterprise,
            )

            is ProductAvailabilityUiState.Maintenance -> ProductGateScreen(
                title = stringResource(R.string.gate_maintenance_title),
                message = availability.message ?: stringResource(R.string.gate_maintenance_default),
                onRetry = ::refreshEnterprise,
            )

            else -> Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    GanjLiquidBottomNavigation(
                        selectedDestination = selectedDestination,
                        onDestinationSelected = { selectedDestination = it },
                    )
                },
            ) { padding ->
                GanjDestinationTransition(
                    destination = selectedDestination,
                    modifier = Modifier.padding(padding),
                ) { destination ->
                    when (destination) {
                        GanjDestination.Home -> HomeScreen(
                            state = state,
                            onOpenConnect = { selectedDestination = GanjDestination.Connect },
                            onOpenStore = { selectedDestination = GanjDestination.Store },
                            onOpenServices = { selectedDestination = GanjDestination.Account },
                            onRefresh = ::refresh,
                        )

                        GanjDestination.Servers -> SmartRoutingScreen(
                            state = state,
                            onSelectService = {
                                commit(reducer.reduce(state, GanjUiEvent.SelectService(it)))
                            },
                            onConnect = {
                                requestProfile(it)
                                selectedDestination = GanjDestination.Connect
                            },
                            onRetry = ::refresh,
                        )

                        GanjDestination.Connect -> ConnectionDashboard(
                            state = state,
                            onConnect = ::requestProfile,
                            onClear = ::disconnectTunnel,
                            onOpenServices = { selectedDestination = GanjDestination.Account },
                            onRetry = ::refresh,
                        )

                        GanjDestination.Store -> StoreScreen(
                            state = state,
                            onSelect = {
                                commit(reducer.reduce(state, GanjUiEvent.SelectPlan(it)))
                            },
                            onPurchase = ::checkout,
                            onRetry = ::refresh,
                        )

                        GanjDestination.Account -> Box(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            MyServicesScreen(
                                state = state,
                                enterpriseState = enterpriseState,
                                onSelectService = {
                                    commit(reducer.reduce(state, GanjUiEvent.SelectService(it)))
                                },
                                onConnect = {
                                    state.selectedEntitlementId?.let(::requestProfile)
                                    selectedDestination = GanjDestination.Connect
                                },
                                onBuy = { selectedDestination = GanjDestination.Store },
                                onRetry = ::refresh,
                                onEnterpriseRefresh = ::refreshEnterprise,
                                onSubmitBug = ::submitBug,
                                onSubmitDiagnostics = ::submitDiagnostics,
                                onClearBug = {
                                    commitEnterprise(
                                        enterpriseReducer.reduce(
                                            enterpriseState,
                                            EnterpriseEvent.ClearBugResult,
                                        ),
                                    )
                                },
                                onClearDiagnostic = {
                                    commitEnterprise(
                                        enterpriseReducer.reduce(
                                            enterpriseState,
                                            EnterpriseEvent.ClearDiagnosticResult,
                                        ),
                                    )
                                },
                                modifier = Modifier.padding(top = 220.dp),
                            )
                            TelegramAccountCard(
                                linked = telegramLinked,
                                busy = telegramBusy,
                                error = telegramError,
                                onLogin = onTelegramLogin,
                                onLogout = onTelegramLogout,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
