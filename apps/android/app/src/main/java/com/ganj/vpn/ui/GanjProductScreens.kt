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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        AppHeader("Ganj VPN", "Your privacy dashboard", onRefresh)
        Spacer(Modifier.height(14.dp))
        GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Connection profile",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                if (connected) "VPN connected" else "Choose an active service",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                state.selectedService?.displayName ?: "No active service selected",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenConnect, modifier = Modifier.fillMaxWidth()) {
                Text("Open secure connection")
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAction(
                title = "My services",
                subtitle = "${state.serviceItems.size} active or previous",
                onClick = onOpenServices,
                modifier = Modifier.weight(1f),
            )
            QuickAction(
                title = "Store",
                subtitle = "Choose a subscription",
                onClick = onOpenStore,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("Subscription protected", fontWeight = FontWeight.SemiBold)
        Text(
            "Connection access is issued only from a verified Ganj entitlement and a registered device.",
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
        AppHeader("Smart routing", "Only servers allowed by your service", onRetry)
        Spacer(Modifier.height(10.dp))
        when (val services = state.services) {
            ContentState.Loading -> LoadingCard("Loading eligible services")
            ContentState.Empty -> EmptyCard("No service yet", "Open Store to activate one", onRetry)
            ContentState.AuthRequired -> AuthCard(onRetry)
            is ContentState.Error -> ErrorCard(services.failure, onRetry)
            is ContentState.Ready -> services.items.forEach { service ->
                ContentCard(
                    accent = if (service.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ) {
                    Text(service.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "${service.countryCode ?: "Global"} • ${service.allowedProtocols.joinToString()}",
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
                        Text(if (service.isActive) "Smart connect" else service.status.name)
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
        Text(
            "Server choice and profile issuance are revalidated by the backend for this entitlement.",
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
        AppHeader("GANJ VPN", "Secure. Fast. Yours.", onRetry)
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
            text = service?.displayName ?: "No service selected",
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (service == null) {
            OutlinedButton(
                onClick = onOpenServices,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
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
        Spacer(Modifier.height(12.dp))
        ContentCard(accent = MaterialTheme.colorScheme.primary) {
            Text("Device-bound", fontWeight = FontWeight.Bold)
            Text(
                if (connection is ConnectionUiState.Connected) {
                    "The verified device profile is active only inside the VPN runtime."
                } else {
                    "The app sends only your selected service identity into the connection flow."
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
        AppHeader("Ganj Store", "Backend-verified subscriptions", onRetry)
        Spacer(Modifier.height(10.dp))
        when (val catalog = state.catalog) {
            ContentState.Loading -> LoadingCard("Loading plans")
            ContentState.Empty -> EmptyCard("No plans available", "Try again in a moment", onRetry)
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
            "Service access becomes active only after provider and backend verification.",
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
                    product.tier.name,
                    color = if (premium) GanjGold else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.End) {
                Text(formatPrice(product), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    product.durationDays?.let { "$it days" } ?: "Service plan",
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
            Text(if (selected) "Continue with Google Play" else "Choose with Google Play")
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
            Text("Payment pending", fontWeight = FontWeight.Bold)
            Text(
                checkoutActionText(checkout.action),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is CheckoutUiState.Verified -> ContentCard(accent = MaterialTheme.colorScheme.secondary) {
            Text("Payment verified", fontWeight = FontWeight.Bold)
            Text(
                "Waiting for the activated service to sync",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is CheckoutUiState.Active -> ContentCard(accent = MaterialTheme.colorScheme.primary) {
            Text("Subscription active", fontWeight = FontWeight.Bold)
            Text(
                "Your service is ready in My Services",
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
        AppHeader("My services", "Synced from your Ganj account", onRetry)
        Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.height(4.dp))
            }
        }
        Button(onClick = onBuy, modifier = Modifier.fillMaxWidth()) { Text("Buy another subscription") }
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
                    service.status.name,
                    color = if (service.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                )
            }
            if (selected) {
                Text(
                    "SELECTED",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            "${formatTraffic(service.remainingBytes)} • ${service.deviceLimit} devices allowed",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onSelect,
                enabled = service.isActive,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (selected) "Selected" else "Use plan")
            }
            Button(
                onClick = onConnect,
                enabled = service.isActive && selected,
                modifier = Modifier.weight(1f),
            ) {
                Text("Connect")
            }
        }
    }
}
