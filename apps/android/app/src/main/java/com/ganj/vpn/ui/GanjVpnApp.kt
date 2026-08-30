package com.ganj.vpn.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ganj.vpn.R
import com.ganj.vpn.composition.GanjComposition
import com.ganj.vpn.core.controlapi.CurrentAccount
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
import com.ganj.vpn.presentation.ContentState
import com.ganj.vpn.presentation.GanjUiEvent
import com.ganj.vpn.presentation.GanjUiState
import com.ganj.vpn.presentation.PlanUiModel
import com.ganj.vpn.presentation.ServiceUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class GanjAccountSurface {
    PROFILE,
    SETTINGS,
    SUBSCRIPTION_DETAILS,
}

internal sealed interface TelegramServiceSyncFeedback {
    data object Syncing : TelegramServiceSyncFeedback
    data class Success(val serviceCount: Int) : TelegramServiceSyncFeedback
    data object Empty : TelegramServiceSyncFeedback
    data object AuthRequired : TelegramServiceSyncFeedback
    data class Failed(val retryable: Boolean) : TelegramServiceSyncFeedback
}

internal fun telegramServiceSyncFeedback(
    services: ContentState<ServiceUiModel>,
): TelegramServiceSyncFeedback? = when (services) {
    ContentState.Loading -> TelegramServiceSyncFeedback.Syncing
    ContentState.Empty -> TelegramServiceSyncFeedback.Empty
    ContentState.AuthRequired -> TelegramServiceSyncFeedback.AuthRequired
    is ContentState.Ready -> TelegramServiceSyncFeedback.Success(services.items.size)
    is ContentState.Error -> TelegramServiceSyncFeedback.Failed(services.failure.retryable)
}

@Composable
fun GanjVpnApp(
    composition: GanjComposition,
    telegramLinked: Boolean,
    telegramBusy: Boolean,
    telegramWaiting: Boolean,
    telegramErrorCode: String?,
    currentAccount: CurrentAccount?,
    accountIdentityLoading: Boolean,
    accountIdentityErrorCode: String?,
    accountRefreshGeneration: Int,
    userPreferences: GanjUserPreferences,
    onTelegramLogin: () -> Unit,
    onTelegramCancel: () -> Unit,
    onTelegramFallback: () -> Unit,
    onTelegramLogout: () -> Unit,
    onRetryAccountIdentity: () -> Unit,
    onThemePreferenceChanged: (GanjThemePreference) -> Unit,
    onReduceMotionChanged: (Boolean) -> Unit,
    onReduceTransparencyChanged: (Boolean) -> Unit,
    onOnboardingCompleted: () -> Unit,
    onRestartOnboarding: () -> Unit,
    onLaunchGooglePlay: suspend (CheckoutActionHandle) -> CheckoutEffectResult,
    onLaunchVpn: suspend (ConnectionActionHandle) -> ConnectionEffectResult,
) {
    val controller = remember(composition) { composition.controller }
    val reducer = remember(composition) { composition.reducer }
    val enterpriseController = remember(composition) { composition.enterpriseController }
    val enterpriseReducer = remember(composition) { composition.enterpriseReducer }
    val scope = rememberCoroutineScope()

    var selectedDestination by remember { mutableStateOf(GanjDestination.Connect) }
    var accountSurface by remember { mutableStateOf(GanjAccountSurface.PROFILE) }
    var purchaseConfirmationPlan by remember { mutableStateOf<PlanUiModel?>(null) }
    var accountSyncFeedback by remember { mutableStateOf<TelegramServiceSyncFeedback?>(null) }
    var state by remember(composition) { mutableStateOf(composition.restoreUiState()) }
    var enterpriseState by remember(composition) {
        mutableStateOf(composition.restoreEnterpriseState())
    }
    var refreshJob by remember { mutableStateOf<Job?>(null) }
    var checkoutJob by remember { mutableStateOf<Job?>(null) }
    var connectionJob by remember { mutableStateOf<Job?>(null) }
    var enterpriseJob by remember { mutableStateOf<Job?>(null) }

    BackHandler(
        enabled = userPreferences.onboardingCompleted &&
            selectedDestination == GanjDestination.Account &&
            accountSurface != GanjAccountSurface.PROFILE,
    ) {
        accountSurface = GanjAccountSurface.PROFILE
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

    fun refreshLinkedAccount() {
        refreshJob?.cancel()
        commit(reducer.reduce(state, GanjUiEvent.RefreshRequested))
        val loadingState = state
        accountSyncFeedback = if (telegramLinked) TelegramServiceSyncFeedback.Syncing else null
        refreshJob = scope.launch {
            val refreshed = withContext(Dispatchers.IO) {
                controller.refresh(loadingState)
            }
            commit(refreshed)
            accountSyncFeedback = if (telegramLinked) {
                telegramServiceSyncFeedback(refreshed.services)
            } else {
                null
            }
        }
        refreshEnterprise()
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
            refreshLinkedAccount()
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

            else -> if (!userPreferences.onboardingCompleted) {
                StitchOnboardingScreen(
                    onComplete = onOnboardingCompleted,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        GanjLiquidBottomNavigation(
                            selectedDestination = selectedDestination,
                            onDestinationSelected = { destination ->
                                selectedDestination = destination
                                if (destination == GanjDestination.Account) {
                                    accountSurface = GanjAccountSurface.PROFILE
                                }
                            },
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
                                onOpenProfile = {
                                    accountSurface = GanjAccountSurface.PROFILE
                                    selectedDestination = GanjDestination.Account
                                },
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
                                onPurchase = { plan -> purchaseConfirmationPlan = plan },
                                onRetry = ::refresh,
                            )

                            GanjDestination.Account -> when (accountSurface) {
                                GanjAccountSurface.SETTINGS -> StitchSettingsScreen(
                                    preferences = userPreferences,
                                    onThemeChanged = onThemePreferenceChanged,
                                    onReduceMotionChanged = onReduceMotionChanged,
                                    onReduceTransparencyChanged = onReduceTransparencyChanged,
                                    onRestartOnboarding = onRestartOnboarding,
                                    onBack = { accountSurface = GanjAccountSurface.PROFILE },
                                    modifier = Modifier.fillMaxSize(),
                                )

                                GanjAccountSurface.SUBSCRIPTION_DETAILS -> {
                                    val service = state.selectedService
                                    if (service == null) {
                                        accountSurface = GanjAccountSurface.PROFILE
                                    } else {
                                        StitchSubscriptionDetailsScreen(
                                            service = service,
                                            onBack = { accountSurface = GanjAccountSurface.PROFILE },
                                            onConnect = {
                                                requestProfile(service.entitlementId)
                                                selectedDestination = GanjDestination.Connect
                                            },
                                            onOpenStore = { selectedDestination = GanjDestination.Store },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }

                                GanjAccountSurface.PROFILE -> Column(
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    StitchTelegramAccountCard(
                                        linked = telegramLinked,
                                        busy = telegramBusy,
                                        waitingForApproval = telegramWaiting,
                                        errorCode = telegramErrorCode,
                                        currentAccount = currentAccount,
                                        accountIdentityLoading = accountIdentityLoading,
                                        accountIdentityErrorCode = accountIdentityErrorCode,
                                        syncFeedback = accountSyncFeedback,
                                        onLogin = onTelegramLogin,
                                        onCancelApproval = onTelegramCancel,
                                        onFallbackLogin = onTelegramFallback,
                                        onRetryIdentity = onRetryAccountIdentity,
                                        onRetrySync = ::refreshLinkedAccount,
                                        onLogout = onTelegramLogout,
                                        modifier = Modifier.padding(
                                            horizontal = responsiveHorizontalPadding(),
                                            vertical = 12.dp,
                                        ),
                                    )
                                    StitchSettingsEntry(
                                        onClick = { accountSurface = GanjAccountSurface.SETTINGS },
                                        modifier = Modifier.padding(
                                            horizontal = responsiveHorizontalPadding(),
                                            vertical = 2.dp,
                                        ),
                                    )
                                    state.selectedService?.let { service ->
                                        StitchSubscriptionDetailsEntry(
                                            service = service,
                                            onClick = {
                                                accountSurface = GanjAccountSurface.SUBSCRIPTION_DETAILS
                                            },
                                            modifier = Modifier.padding(
                                                horizontal = responsiveHorizontalPadding(),
                                                vertical = 2.dp,
                                            ),
                                        )
                                    }
                                    StitchProfileScreen(
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
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    purchaseConfirmationPlan?.let { plan ->
        GanjLiquidConfirmDialog(
            title = "تأیید خرید ${plan.title}",
            body = "پس از تأیید، پرداخت امن برای این پلن شروع می‌شود. مبلغ و شرایط نهایی پیش از پرداخت در Provider نمایش داده خواهد شد.",
            confirmText = "ادامه به پرداخت",
            dismissText = "انصراف",
            onConfirm = {
                purchaseConfirmationPlan = null
                checkout(plan)
            },
            onDismiss = { purchaseConfirmationPlan = null },
        )
    }
}
