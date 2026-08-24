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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.ganj.vpn.core.subscription.ConnectionDenial
import com.ganj.vpn.core.subscription.ConnectionUiState
import com.ganj.vpn.core.subscription.PurchaseFailure
import com.ganj.vpn.core.subscription.PurchaseStatus
import com.ganj.vpn.core.subscription.SubscriptionProduct
import com.ganj.vpn.core.subscription.SubscriptionStoreAction
import com.ganj.vpn.core.subscription.SubscriptionStoreReducer
import com.ganj.vpn.core.subscription.SubscriptionStoreState
import com.ganj.vpn.core.subscription.SubscriptionTier
import com.ganj.vpn.core.subscription.UserService
import com.ganj.vpn.core.subscription.UserServiceStatus

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
fun GanjVpnApp() {
    MaterialTheme(colorScheme = GanjDarkScheme) {
        val reducer = remember { SubscriptionStoreReducer() }
        val appStartedAt = remember { System.currentTimeMillis() }
        var selectedTab by remember { mutableStateOf(AppTab.CONNECT) }
        var state by remember { mutableStateOf(mockSubscriptionState(appStartedAt)) }

        fun dispatch(action: SubscriptionStoreAction) {
            state = reducer.reduce(state, action)
        }

        Scaffold(
            containerColor = DeepNavy,
            bottomBar = {
                GanjBottomBar(selected = selectedTab, onSelected = { selectedTab = it })
            },
        ) { padding ->
            when (selectedTab) {
                AppTab.HOME -> HomeScreen(
                    state = state,
                    onOpenConnect = { selectedTab = AppTab.CONNECT },
                    onOpenStore = { selectedTab = AppTab.STORE },
                    onOpenServices = { selectedTab = AppTab.ACCOUNT },
                    modifier = Modifier.padding(padding),
                )

                AppTab.SERVERS -> ServersScreen(
                    state = state,
                    onConnect = { serverId ->
                        dispatch(
                            SubscriptionStoreAction.RequestConnection(
                                serverId = serverId,
                                nowEpochMillis = System.currentTimeMillis(),
                            ),
                        )
                        if (state.connection is ConnectionUiState.Requested) {
                            selectedTab = AppTab.CONNECT
                        }
                    },
                    modifier = Modifier.padding(padding),
                )

                AppTab.CONNECT -> ConnectionDashboard(
                    state = state,
                    onToggleConnection = {
                        if (state.connection is ConnectionUiState.Connected) {
                            dispatch(SubscriptionStoreAction.Disconnect)
                        } else {
                            val pendingServerId = (state.connection as? ConnectionUiState.Requested)
                                ?.command
                                ?.serverId
                            val serverId = pendingServerId
                                ?: state.selectedService?.allowedServerIds?.firstOrNull()
                            if (serverId != null) {
                                dispatch(
                                    SubscriptionStoreAction.RequestConnection(
                                        serverId = serverId,
                                        nowEpochMillis = System.currentTimeMillis(),
                                    ),
                                )
                                dispatch(SubscriptionStoreAction.ConnectionEstablished)
                            }
                        }
                    },
                    onOpenServices = { selectedTab = AppTab.ACCOUNT },
                    modifier = Modifier.padding(padding),
                )

                AppTab.STORE -> StoreScreen(
                    state = state,
                    onSelect = { dispatch(SubscriptionStoreAction.SelectProduct(it)) },
                    onPurchase = { product ->
                        dispatch(SubscriptionStoreAction.BeginCheckout(product.id))
                        dispatch(SubscriptionStoreAction.VerifyPurchase)
                        dispatch(
                            SubscriptionStoreAction.CompletePurchase(
                                mockPurchasedService(product, System.currentTimeMillis()),
                            ),
                        )
                    },
                    modifier = Modifier.padding(padding),
                )

                AppTab.ACCOUNT -> MyServicesScreen(
                    state = state,
                    onSelectService = { dispatch(SubscriptionStoreAction.SelectService(it)) },
                    onConnect = { selectedTab = AppTab.CONNECT },
                    onBuy = { selectedTab = AppTab.STORE },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: SubscriptionStoreState,
    onOpenConnect: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenServices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Page(modifier) {
        AppHeader("Welcome back", "Your privacy dashboard")
        Spacer(Modifier.height(24.dp))
        GlassCard(accent = Emerald) {
            Text("Protection status", color = Muted, fontSize = 12.sp)
            Text(
                if (state.connection is ConnectionUiState.Connected) "Protected" else "Ready to connect",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.connection is ConnectionUiState.Connected) Emerald else Color.White,
            )
            Text(state.selectedService?.displayName ?: "Choose a service to continue", color = Muted)
            Button(onClick = onOpenConnect, modifier = Modifier.fillMaxWidth()) {
                Text("Open secure connection")
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAction("My services", "${state.services.size} plans", onOpenServices, Modifier.weight(1f))
            QuickAction("Store", "Upgrade plan", onOpenStore, Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        Text("Private by design", fontWeight = FontWeight.SemiBold)
        Text(
            "Ganj VPN activates encrypted profiles from your subscription. Manual config import is not available.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ServersScreen(
    state: SubscriptionStoreState,
    onConnect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Page(modifier) {
        AppHeader("Servers", state.selectedService?.displayName ?: "No active service")
        Spacer(Modifier.height(20.dp))
        mockServers.forEach { server ->
            val included = server.id in (state.selectedService?.allowedServerIds ?: emptySet())
            GlassCard(accent = if (included) IosBlue else Muted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("${server.country} • ${server.city}", fontWeight = FontWeight.SemiBold)
                        Text("${server.pingMs} ms  •  ${server.loadPercent}% load", color = Muted, fontSize = 12.sp)
                    }
                    Text(
                        if (included) "CONNECT" else if (server.vipOnly) "VIP" else "LOCKED",
                        color = if (included) Emerald else Gold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = if (included) Modifier.clickable { onConnect(server.id) } else Modifier,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Text(
            "Server access is determined by the selected subscription and verified again by the backend.",
            color = Muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ConnectionDashboard(
    state: SubscriptionStoreState,
    onToggleConnection: () -> Unit,
    onOpenServices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = state.connection is ConnectionUiState.Connected
    val connectionColor by animateColorAsState(
        targetValue = if (connected) Emerald else IosBlue,
        label = "connectionColor",
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (connected) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
        label = "buttonScale",
    )
    val service = state.selectedService
    val blocked = state.connection as? ConnectionUiState.Blocked

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
            AppHeader("GANJ VPN", "Secure. Fast. Yours.")
            Spacer(Modifier.height(46.dp))
            Text(
                text = if (connected) "Protected" else "Ready to connect",
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
                    .clickable(enabled = service != null, onClick = onToggleConnection),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (connected) "ON" else "GO", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text(if (connected) "Tap to disconnect" else "Use selected service", fontSize = 11.sp)
                }
            }
            if (service == null) {
                OutlinedButton(onClick = onOpenServices, modifier = Modifier.padding(top = 18.dp)) {
                    Text("Choose my service")
                }
            }
            if (blocked != null) {
                Text(
                    denialMessage(blocked.reason),
                    color = Danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
            Spacer(Modifier.height(36.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x12FFFFFF), RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(24.dp))
                    .padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Metric("PING", if (connected) "42 ms" else "—")
                Metric("DOWNLOAD", if (connected) "84 Mbps" else "—")
                Metric("UPLOAD", if (connected) "21 Mbps" else "—")
            }
            Text(
                "Profiles are issued securely from your Ganj subscription. Manual import is disabled.",
                color = Muted,
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
    }
}

@Composable
private fun StoreScreen(
    state: SubscriptionStoreState,
    onSelect: (String) -> Unit,
    onPurchase: (SubscriptionProduct) -> Unit,
    modifier: Modifier = Modifier,
) {
    Page(modifier) {
        AppHeader("Ganj Store", "Choose the plan that fits you")
        Spacer(Modifier.height(20.dp))
        state.products.forEach { product ->
            val selected = state.selectedProductId == product.id
            GlassCard(
                accent = if (product.tier == SubscriptionTier.VIP) Gold else IosBlue,
                modifier = Modifier.clickable { onSelect(product.id) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(product.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(product.tier.name, color = if (product.tier == SubscriptionTier.VIP) Gold else Muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatPrice(product), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("per month", color = Muted, fontSize = 11.sp)
                    }
                }
                product.benefits.forEach { benefit ->
                    Text("✓  $benefit", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Button(
                    onClick = { onPurchase(product) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (product.tier == SubscriptionTier.VIP) Gold else IosBlue,
                        contentColor = if (product.tier == SubscriptionTier.VIP) DeepNavy else Color.White,
                    ),
                ) {
                    Text(if (selected) "Continue with ${product.title}" else "Choose ${product.title}")
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        if (state.purchaseStatus == PurchaseStatus.SUCCEEDED) {
            Text(
                "Subscription activated",
                color = Emerald,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (state.purchaseStatus == PurchaseStatus.FAILED) {
            Text(
                purchaseFailureMessage(state.purchaseFailure),
                color = Danger,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "Mock checkout represents wallet, Telegram payment, or gateway verification. Access is activated only after server confirmation.",
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun MyServicesScreen(
    state: SubscriptionStoreState,
    onSelectService: (String) -> Unit,
    onConnect: () -> Unit,
    onBuy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Page(modifier) {
        AppHeader("My services", "Synced with your Ganj account")
        Spacer(Modifier.height(20.dp))
        state.services.forEach { service ->
            val selected = state.selectedServiceId == service.id
            ServiceCard(
                service = service,
                selected = selected,
                onSelect = { onSelectService(service.id) },
                onConnect = onConnect,
            )
            Spacer(Modifier.height(14.dp))
        }
        Button(onClick = onBuy, modifier = Modifier.fillMaxWidth()) {
            Text("Buy another subscription")
        }
        Spacer(Modifier.height(24.dp))
        GlassCard(accent = Muted) {
            Text("Account", fontWeight = FontWeight.Bold)
            Text("Telegram linked • @ganj_demo", color = Muted, fontSize = 13.sp)
            Text("1 of 3 devices active", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ServiceCard(
    service: UserService,
    selected: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
) {
    val active = service.status == UserServiceStatus.ACTIVE
    GlassCard(
        accent = when {
            !active -> Danger
            service.tier == SubscriptionTier.VIP -> Gold
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
                Text(service.status.name, color = if (active) Emerald else Danger, fontSize = 12.sp)
            }
            if (selected) Text("SELECTED", color = Emerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "${formatTraffic(service.remainingBytes)} • ${service.activeDevices}/${service.maxDevices} devices",
            color = Muted,
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onSelect, enabled = active, modifier = Modifier.weight(1f)) {
                Text(if (selected) "Selected" else "Use plan")
            }
            Button(onClick = onConnect, enabled = active && selected, modifier = Modifier.weight(1f)) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun Page(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        content = content,
    )
}

@Composable
private fun AppHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(999.dp)) {
            Text("GANJ", modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp), fontSize = 11.sp)
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
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 0.8.sp)
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
                if (!central) {
                    Text(
                        text = tab.title,
                        fontSize = 9.sp,
                        color = if (isSelected) Color.White else Muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun formatPrice(product: SubscriptionProduct): String =
    if (product.price.amountMinor == 0L) "Free" else {
        val major = product.price.amountMinor / 100
        val minor = product.price.amountMinor % 100
        "$${major}.${minor.toString().padStart(2, '0')}"
    }

private fun formatTraffic(bytes: Long?): String = when {
    bytes == null -> "Unlimited traffic"
    bytes >= 1_000_000_000 -> "${bytes / 1_000_000_000} GB left"
    else -> "${bytes / 1_000_000} MB left"
}

private fun denialMessage(reason: ConnectionDenial): String = when (reason) {
    ConnectionDenial.NOT_AUTHENTICATED -> "Sign in before connecting."
    ConnectionDenial.WRONG_OWNER -> "This service belongs to another account."
    ConnectionDenial.SERVICE_NOT_ACTIVE -> "Select an active service before connecting."
    ConnectionDenial.SUBSCRIPTION_EXPIRED -> "Your subscription has expired."
    ConnectionDenial.TRAFFIC_EXHAUSTED -> "Your service traffic is exhausted."
    ConnectionDenial.DEVICE_LIMIT_REACHED -> "The service device limit has been reached."
    ConnectionDenial.SERVER_NOT_INCLUDED -> "This server is not included in your plan."
}

private fun purchaseFailureMessage(failure: PurchaseFailure?): String = when (failure) {
    PurchaseFailure.PRODUCT_UNAVAILABLE -> "This plan is currently unavailable."
    PurchaseFailure.VERIFICATION_REJECTED -> "Purchase verification failed."
    PurchaseFailure.PAYMENT_FAILED -> "Payment was not completed."
    PurchaseFailure.NETWORK_ERROR -> "Check your connection and try again."
    null -> "The purchase could not be completed."
}
