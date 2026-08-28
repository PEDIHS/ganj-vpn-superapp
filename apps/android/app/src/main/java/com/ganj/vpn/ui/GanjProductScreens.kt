package com.ganj.vpn.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ganj.vpn.R
import com.ganj.vpn.enterprise.BugReportInput
import com.ganj.vpn.enterprise.EnterpriseUiState
import com.ganj.vpn.presentation.CheckoutUiState
import com.ganj.vpn.presentation.ConnectionUiState
import com.ganj.vpn.presentation.ContentState
import com.ganj.vpn.presentation.GanjUiState
import com.ganj.vpn.presentation.PlanUiModel
import com.ganj.vpn.presentation.ServiceUiModel
import com.ganj.vpn.presentation.UiTier

@Composable
internal fun HomeScreen(
    state: GanjUiState,
    onOpenConnect: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenServices: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = state.connection is ConnectionUiState.Connected
    Page(modifier) {
        AppHeader(
            stringResource(R.string.home_title),
            stringResource(R.string.home_subtitle),
            onRefresh,
        )
        Spacer(Modifier.height(14.dp))
        GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.home_connection_profile),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                if (connected) {
                    stringResource(R.string.home_vpn_connected)
                } else {
                    stringResource(R.string.home_choose_active_service)
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                state.selectedService?.displayName ?: stringResource(R.string.home_no_active_service),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenConnect, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_open_secure_connection))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAction(
                title = stringResource(R.string.home_my_services),
                subtitle = stringResource(R.string.home_service_count, state.serviceItems.size),
                onClick = onOpenServices,
                modifier = Modifier.weight(1f),
            )
            QuickAction(
                title = stringResource(R.string.home_store),
                subtitle = stringResource(R.string.home_choose_subscription),
                onClick = onOpenStore,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.home_subscription_protected), fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.home_subscription_protected_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun SmartRoutingScreen(
    state: GanjUiState,
    onSelectService: (String) -> Unit,
    onConnect: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Page(modifier) {
        AppHeader(
            stringResource(R.string.servers_title),
            stringResource(R.string.servers_subtitle),
            onRetry,
        )
        Spacer(Modifier.height(10.dp))
        when (val services = state.services) {
            ContentState.Loading -> LoadingCard(stringResource(R.string.servers_loading))
            ContentState.Empty -> EmptyCard(
                stringResource(R.string.servers_empty_title),
                stringResource(R.string.servers_empty_body),
                onRetry,
            )
            ContentState.AuthRequired -> AuthCard(onRetry)
            is ContentState.Error -> ErrorCard(services.failure, onRetry)
            is ContentState.Ready -> services.items.forEach { service ->
                ContentCard(
                    accent = if (service.isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                ) {
                    Text(service.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "${service.countryCode ?: stringResource(R.string.common_global)} • ${service.allowedProtocols.joinToString()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        enabled = service.isActive,
                        onClick = {
                            onSelectService(service.entitlementId)
                            onConnect(service.entitlementId)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (service.isActive) {
                                stringResource(R.string.servers_smart_connect)
                            } else {
                                serviceStatusText(service.status)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
        Text(
            stringResource(R.string.servers_backend_verified),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun ConnectionDashboard(
    state: GanjUiState,
    onConnect: (String) -> Unit,
    onClear: () -> Unit,
    onOpenServices: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connection = state.connection
    val service = state.selectedService
    val visualState = when (connection) {
        is ConnectionUiState.Connected -> GanjConnectionVisualState.Connected
        is ConnectionUiState.Requesting,
        is ConnectionUiState.ProfileReady,
        -> GanjConnectionVisualState.Connecting
        is ConnectionUiState.Failed -> GanjConnectionVisualState.Failed
        ConnectionUiState.AuthRequired -> GanjConnectionVisualState.Unavailable
        else -> if (service?.isActive == true) {
            GanjConnectionVisualState.Disconnected
        } else {
            GanjConnectionVisualState.Unavailable
        }
    }

    Page(modifier) {
        AppHeader(
            stringResource(R.string.connect_title),
            stringResource(R.string.connect_subtitle),
            onRetry,
        )
        Spacer(Modifier.height(22.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            GanjLiquidConnectControl(
                state = visualState,
                onClick = {
                    when {
                        connection is ConnectionUiState.Connected -> onClear()
                        service?.isActive == true -> onConnect(service.entitlementId)
                    }
                },
            )
        }
        Text(
            text = service?.displayName ?: stringResource(R.string.connect_no_service),
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (service == null) {
            OutlinedButton(
                onClick = onOpenServices,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.connect_choose_service))
            }
        }
        when (connection) {
            ConnectionUiState.AuthRequired -> AuthCard(onRetry)
            is ConnectionUiState.Failed -> ErrorCard(connection.failure) {
                service?.entitlementId?.let(onConnect) ?: onRetry()
            }
            else -> Unit
        }
        Spacer(Modifier.height(12.dp))
        ContentCard(accent = MaterialTheme.colorScheme.primary) {
            Text(stringResource(R.string.connect_device_bound), fontWeight = FontWeight.Bold)
            Text(
                if (connection is ConnectionUiState.Connected) {
                    stringResource(R.string.connect_device_bound_active)
                } else {
                    stringResource(R.string.connect_device_bound_idle)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun StoreScreen(
    state: GanjUiState,
    onSelect: (String) -> Unit,
    onPurchase: (PlanUiModel) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Page(modifier) {
        AppHeader(
            stringResource(R.string.store_title),
            stringResource(R.string.store_subtitle),
            onRetry,
        )
        Spacer(Modifier.height(10.dp))
        when (val catalog = state.catalog) {
            ContentState.Loading -> LoadingCard(stringResource(R.string.store_loading))
            ContentState.Empty -> EmptyCard(
                stringResource(R.string.store_empty_title),
                stringResource(R.string.store_empty_body),
                onRetry,
            )
            ContentState.AuthRequired -> AuthCard(onRetry)
            is ContentState.Error -> ErrorCard(catalog.failure, onRetry)
            is ContentState.Ready -> catalog.items.forEach { product ->
                val selected = state.selectedPlanId == product.id
                PlanCard(product, selected, onSelect, onPurchase)
                Spacer(Modifier.height(4.dp))
            }
        }
        CheckoutStatus(
            checkout = state.checkout,
            onRetry = { state.selectedPlan?.let(onPurchase) ?: onRetry() },
        )
        Text(
            stringResource(R.string.store_verification_notice),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
    }
}

@Composable
internal fun PlanCard(
    product: PlanUiModel,
    selected: Boolean,
    onSelect: (String) -> Unit,
    onPurchase: (PlanUiModel) -> Unit,
) {
    val premium = product.tier == UiTier.VIP
    ContentCard(
        accent = if (premium) GanjGold else MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { onSelect(product.id) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column {
                Text(product.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    tierText(product.tier),
                    color = if (premium) GanjGold else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.End) {
                Text(formatPrice(product), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    product.durationDays?.let { stringResource(R.string.plan_days, it) }
                        ?: stringResource(R.string.plan_service),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        }
        product.benefits.take(6).forEach { Text("✓  $it", fontSize = 13.sp) }
        Button(
            onClick = { onPurchase(product) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                if (selected) {
                    stringResource(R.string.plan_continue_google_play)
                } else {
                    stringResource(R.string.plan_choose_google_play)
                },
            )
        }
    }
}

@Composable
internal fun CheckoutStatus(
    checkout: CheckoutUiState,
    onRetry: () -> Unit,
) {
    when (checkout) {
        CheckoutUiState.Idle -> Unit
        CheckoutUiState.AuthRequired -> AuthCard(onRetry)
        is CheckoutUiState.Pending -> ContentCard(accent = GanjWarning) {
            Text(stringResource(R.string.checkout_pending_title), fontWeight = FontWeight.Bold)
            Text(
                checkoutActionText(checkout.action),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is CheckoutUiState.Verified -> ContentCard(accent = MaterialTheme.colorScheme.secondary) {
            Text(stringResource(R.string.checkout_verified_title), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.checkout_verified_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is CheckoutUiState.Active -> ContentCard(accent = MaterialTheme.colorScheme.primary) {
            Text(stringResource(R.string.checkout_active_title), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.checkout_active_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is CheckoutUiState.Failed -> ErrorCard(checkout.failure, onRetry)
    }
}

@Composable
internal fun MyServicesScreen(
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
        AppHeader(
            stringResource(R.string.account_title),
            stringResource(R.string.account_subtitle),
            onRetry,
        )
        Spacer(Modifier.height(10.dp))
        when (val services = state.services) {
            ContentState.Loading -> LoadingCard(stringResource(R.string.account_loading))
            ContentState.Empty -> EmptyCard(
                stringResource(R.string.account_empty_title),
                stringResource(R.string.account_empty_body),
                onBuy,
            )
            ContentState.AuthRequired -> AuthCard(onRetry)
            is ContentState.Error -> ErrorCard(services.failure, onRetry)
            is ContentState.Ready -> services.items.forEach { service ->
                ServiceCard(
                    service = service,
                    selected = state.selectedEntitlementId == service.entitlementId,
                    onSelect = { onSelectService(service.entitlementId) },
                    onConnect = onConnect,
                )
                Spacer(Modifier.height(4.dp))
            }
        }
        Button(onClick = onBuy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.account_buy_another))
        }
        Spacer(Modifier.height(14.dp))
        EnterpriseStatusCard(enterpriseState.availability, onEnterpriseRefresh)
        if (enterpriseState.features.bugReportsEnabled) {
            BugReportPanel(enterpriseState.bugReport, onSubmitBug, onClearBug)
        }
        if (enterpriseState.features.diagnosticsEnabled) {
            DiagnosticPanel(enterpriseState.diagnostic, onSubmitDiagnostics, onClearDiagnostic)
        }
    }
}

@Composable
internal fun ServiceCard(
    service: ServiceUiModel,
    selected: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
) {
    ContentCard(
        accent = when {
            !service.isActive -> MaterialTheme.colorScheme.error
            service.tier == UiTier.VIP -> GanjGold
            else -> MaterialTheme.colorScheme.primary
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column {
                Text(service.displayName, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    serviceStatusText(service.status),
                    color = if (service.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                )
            }
            if (selected) {
                Text(
                    stringResource(R.string.account_selected_badge),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            "${formatTraffic(service.remainingBytes)} • ${stringResource(R.string.account_devices_allowed, service.deviceLimit)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onSelect,
                enabled = service.isActive,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (selected) {
                        stringResource(R.string.common_selected)
                    } else {
                        stringResource(R.string.account_use_plan)
                    },
                )
            }
            Button(
                onClick = onConnect,
                enabled = service.isActive && selected,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.common_connect))
            }
        }
    }
}
