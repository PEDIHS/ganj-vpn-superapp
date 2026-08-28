package com.ganj.vpn.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
private fun shouldStackScreenActions(): Boolean = GanjResponsivePolicy.shouldStackPrimaryActions(
    widthDp = LocalConfiguration.current.screenWidthDp,
    fontScale = LocalDensity.current.fontScale,
)

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
    val serviceReady = state.selectedService?.isActive == true
    val stackActions = shouldStackScreenActions()
    Page(modifier) {
        AppHeader(
            stringResource(R.string.home_title),
            stringResource(R.string.home_subtitle),
            onRefresh,
        )
        Spacer(Modifier.height(8.dp))
        GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (connected || serviceReady) {
                GanjStatusPill(
                    text = stringResource(if (connected) R.string.visual_protected else R.string.visual_ready),
                    tone = GanjStatusTone.Positive,
                )
            }
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
            state.selectedService?.let { service ->
                if (stackActions) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GanjInfoChip(text = tierText(service.tier))
                        GanjInfoChip(
                            text = stringResource(R.string.account_devices_allowed, service.deviceLimit),
                            accent = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GanjInfoChip(text = tierText(service.tier))
                        GanjInfoChip(
                            text = stringResource(R.string.account_devices_allowed, service.deviceLimit),
                            accent = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Button(onClick = onOpenConnect, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_open_secure_connection))
            }
        }

        GanjSectionHeader(
            title = stringResource(R.string.visual_service_security),
            supporting = stringResource(R.string.visual_service_security_body),
        )
        if (stackActions) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickAction(
                    title = stringResource(R.string.home_my_services),
                    subtitle = stringResource(R.string.home_service_count, state.serviceItems.size),
                    onClick = onOpenServices,
                    modifier = Modifier.fillMaxWidth(),
                )
                QuickAction(
                    title = stringResource(R.string.home_store),
                    subtitle = stringResource(R.string.home_choose_subscription),
                    onClick = onOpenStore,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
        }
        ContentCard(accent = MaterialTheme.colorScheme.primary) {
            GanjStatusPill(
                text = stringResource(R.string.visual_verified),
                tone = GanjStatusTone.Positive,
            )
            Text(stringResource(R.string.home_subscription_protected), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.home_subscription_protected_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
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
        GanjSectionHeader(
            title = stringResource(R.string.visual_available_services),
            supporting = stringResource(R.string.visual_available_services_body),
        )
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
                    accent = when {
                        !service.isActive -> MaterialTheme.colorScheme.outline
                        service.tier == UiTier.VIP -> GanjGold
                        else -> MaterialTheme.colorScheme.primary
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                service.displayName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                service.countryCode ?: stringResource(R.string.common_global),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        GanjStatusPill(
                            text = if (service.isActive) {
                                stringResource(R.string.visual_ready)
                            } else {
                                serviceStatusText(service.status)
                            },
                            tone = if (service.isActive) GanjStatusTone.Positive else GanjStatusTone.Neutral,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GanjInfoChip(
                            text = stringResource(R.string.visual_protocols, service.allowedProtocols.size),
                            accent = MaterialTheme.colorScheme.primary,
                        )
                        GanjInfoChip(
                            text = tierText(service.tier),
                            accent = if (service.tier == UiTier.VIP) GanjGold else MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (service.allowedProtocols.isNotEmpty()) {
                        Text(
                            service.allowedProtocols.joinToString(separator = " • "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
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
        Spacer(Modifier.height(8.dp))
        service?.let {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                GanjStatusPill(
                    text = stringResource(R.string.visual_selected_service),
                    tone = if (it.tier == UiTier.VIP) GanjStatusTone.Premium else GanjStatusTone.Positive,
                )
            }
        }
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
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        service?.let {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                GanjInfoChip(text = tierText(it.tier))
            }
        }
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
        ContentCard(accent = MaterialTheme.colorScheme.primary) {
            GanjStatusPill(
                text = stringResource(R.string.connect_device_bound),
                tone = GanjStatusTone.Positive,
            )
            Text(
                if (connection is ConnectionUiState.Connected) {
                    stringResource(R.string.connect_device_bound_active)
                } else {
                    stringResource(R.string.connect_device_bound_idle)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
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
        GanjSectionHeader(
            title = stringResource(R.string.visual_store_plans),
            supporting = stringResource(R.string.visual_store_plans_body),
        )
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
        GanjStatusPill(
            text = stringResource(R.string.visual_verified),
            tone = GanjStatusTone.Positive,
        )
        Text(
            stringResource(R.string.store_verification_notice),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
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
    val stackActions = shouldStackScreenActions()
    ContentCard(
        accent = if (premium) GanjGold else MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { onSelect(product.id) },
    ) {
        if (stackActions) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GanjStatusPill(
                    text = if (premium) stringResource(R.string.visual_signature) else tierText(product.tier),
                    tone = if (premium) GanjStatusTone.Premium else GanjStatusTone.Positive,
                )
                if (selected) {
                    GanjStatusPill(
                        text = stringResource(R.string.common_selected),
                        tone = GanjStatusTone.Positive,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GanjStatusPill(
                    text = if (premium) stringResource(R.string.visual_signature) else tierText(product.tier),
                    tone = if (premium) GanjStatusTone.Premium else GanjStatusTone.Positive,
                )
                if (selected) {
                    GanjStatusPill(
                        text = stringResource(R.string.common_selected),
                        tone = GanjStatusTone.Positive,
                    )
                }
            }
        }
        if (stackActions) {
            PlanIdentity(product = product, premium = premium)
            PlanPrice(product = product, horizontalAlignment = Alignment.Start)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlanIdentity(product = product, premium = premium, modifier = Modifier.weight(1f))
                PlanPrice(product = product, horizontalAlignment = Alignment.End)
            }
        }
        product.benefits.take(6).forEach { benefit ->
            GanjFeatureLine(
                text = benefit,
                accent = if (premium) GanjGold else MaterialTheme.colorScheme.primary,
            )
        }
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
private fun PlanIdentity(
    product: PlanUiModel,
    premium: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            product.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            tierText(product.tier),
            color = if (premium) GanjGold else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PlanPrice(
    product: PlanUiModel,
    horizontalAlignment: Alignment.Horizontal,
) {
    Column(horizontalAlignment = horizontalAlignment) {
        Text(
            formatPrice(product),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            product.durationDays?.let { stringResource(R.string.plan_days, it) }
                ?: stringResource(R.string.plan_service),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
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
            GanjStatusPill(
                text = stringResource(R.string.checkout_pending_title),
                tone = GanjStatusTone.Warning,
            )
            Text(
                checkoutActionText(checkout.action),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is CheckoutUiState.Verified -> ContentCard(accent = MaterialTheme.colorScheme.secondary) {
            GanjStatusPill(
                text = stringResource(R.string.checkout_verified_title),
                tone = GanjStatusTone.Positive,
            )
            Text(
                stringResource(R.string.checkout_verified_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is CheckoutUiState.Active -> ContentCard(accent = MaterialTheme.colorScheme.primary) {
            GanjStatusPill(
                text = stringResource(R.string.checkout_active_title),
                tone = GanjStatusTone.Positive,
            )
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
        GanjSectionHeader(
            title = stringResource(R.string.visual_my_services),
            supporting = stringResource(R.string.visual_my_services_body),
        )
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
        Spacer(Modifier.height(6.dp))
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
    val stackActions = shouldStackScreenActions()
    ContentCard(
        accent = when {
            !service.isActive -> MaterialTheme.colorScheme.error
            service.tier == UiTier.VIP -> GanjGold
            else -> MaterialTheme.colorScheme.primary
        },
    ) {
        if (stackActions) {
            ServiceIdentity(service = service)
            if (selected) {
                GanjStatusPill(
                    text = stringResource(R.string.visual_selected_service),
                    tone = GanjStatusTone.Positive,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ServiceIdentity(service = service, modifier = Modifier.weight(1f))
                if (selected) {
                    GanjStatusPill(
                        text = stringResource(R.string.visual_selected_service),
                        tone = GanjStatusTone.Positive,
                    )
                }
            }
        }
        if (stackActions) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GanjStatusPill(
                    text = if (service.isActive) {
                        stringResource(R.string.visual_active_service)
                    } else {
                        serviceStatusText(service.status)
                    },
                    tone = if (service.isActive) GanjStatusTone.Positive else GanjStatusTone.Danger,
                )
                if (service.tier == UiTier.VIP) {
                    GanjStatusPill(
                        text = stringResource(R.string.visual_signature),
                        tone = GanjStatusTone.Premium,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GanjInfoChip(text = formatTraffic(service.remainingBytes))
                GanjInfoChip(
                    text = stringResource(R.string.account_devices_allowed, service.deviceLimit),
                    accent = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GanjStatusPill(
                    text = if (service.isActive) {
                        stringResource(R.string.visual_active_service)
                    } else {
                        serviceStatusText(service.status)
                    },
                    tone = if (service.isActive) GanjStatusTone.Positive else GanjStatusTone.Danger,
                )
                if (service.tier == UiTier.VIP) {
                    GanjStatusPill(
                        text = stringResource(R.string.visual_signature),
                        tone = GanjStatusTone.Premium,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GanjInfoChip(text = formatTraffic(service.remainingBytes))
                GanjInfoChip(
                    text = stringResource(R.string.account_devices_allowed, service.deviceLimit),
                    accent = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (stackActions) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onSelect,
                    enabled = service.isActive,
                    modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.common_connect))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
}

@Composable
private fun ServiceIdentity(
    service: ServiceUiModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            service.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            tierText(service.tier),
            color = if (service.tier == UiTier.VIP) GanjGold else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
