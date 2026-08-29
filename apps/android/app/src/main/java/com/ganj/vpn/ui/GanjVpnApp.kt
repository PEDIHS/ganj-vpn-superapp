package com.ganj.vpn.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
            is ProductAvailabilityUiState.ForcedUpdate -> StitchProductGateScreen(
                kind = StitchProductGateKind.SecurityUpdate,
                title = stringResource(R.string.gate_security_update_title),
                message = stringResource(R.string.gate_security_update_body, availability.latestVersion),
                onRetry = ::refreshEnterprise,
            )

            is ProductAvailabilityUiState.Maintenance -> StitchProductGateScreen(
                kind = StitchProductGateKind.Maintenance,
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
                        GanjDestination.Home -> StitchHomeScreen(
                            state = state,
                            onOpenConnect = { selectedDestination = GanjDestination.Connect },
                            onOpenStore = { selectedDestination = GanjDestination.Store },
                            onOpenServers = { selectedDestination = GanjDestination.Servers },
                            onOpenProfile = { selectedDestination = GanjDestination.Account },
                        )

                        GanjDestination.Servers -> StitchServersScreen(
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

                        GanjDestination.Connect -> StitchConnectionScreen(
                            state = state,
                            onConnect = ::requestProfile,
                            onDisconnect = ::disconnectTunnel,
                            onOpenServers = { selectedDestination = GanjDestination.Servers },
                            onOpenStore = { selectedDestination = GanjDestination.Store },
                            onRetry = ::refresh,
                        )

                        GanjDestination.Store -> StitchStoreScreen(
                            state = state,
                            onSelect = {
                                commit(reducer.reduce(state, GanjUiEvent.SelectPlan(it)))
                            },
                            onPurchase = ::checkout,
                            onRetry = ::refresh,
                        )

                        GanjDestination.Account -> StitchProfileScreen(
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
                        )
                    }
                }
            }
        }
    }
}
