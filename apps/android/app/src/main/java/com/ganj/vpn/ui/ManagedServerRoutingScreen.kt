package com.ganj.vpn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ganj.vpn.R
import com.ganj.vpn.presentation.ContentState
import com.ganj.vpn.presentation.GanjUiState
import com.ganj.vpn.presentation.ServerUiModel
import com.ganj.vpn.presentation.ServerUiStatus
import com.ganj.vpn.presentation.UiTier

/**
 * Safe server browser backed by /servers. It renders only control-plane metadata; reusable VPN
 * configuration material is structurally absent from ServerUiModel.
 */
@Composable
internal fun ManagedServerRoutingScreen(
    state: GanjUiState,
    onSelectServer: (String) -> Unit,
    onConnect: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.servers_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.servers_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(R.string.common_refresh))
            }
        }

        when (val servers = state.servers) {
            ContentState.Loading -> Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.servers_loading))
            }
            ContentState.Empty -> EmptyServerState(onRetry)
            ContentState.AuthRequired -> EmptyServerState(onRetry)
            is ContentState.Error -> EmptyServerState(onRetry)
            is ContentState.Ready -> servers.items.forEach { server ->
                ManagedServerCard(
                    server = server,
                    selected = state.selectedServerId == server.id,
                    compatibleEntitlementId = state.compatibleServiceFor(server)?.entitlementId,
                    onSelectServer = onSelectServer,
                    onConnect = onConnect,
                )
            }
        }

        Text(
            text = stringResource(R.string.servers_backend_verified),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ManagedServerCard(
    server: ServerUiModel,
    selected: Boolean,
    compatibleEntitlementId: String?,
    onSelectServer: (String) -> Unit,
    onConnect: (String) -> Unit,
) {
    val enabled = server.isSelectable && compatibleEntitlementId != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = listOfNotNull(server.city, server.countryCode).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = server.tier.label(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                text = server.protocols.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = buildString {
                    append(server.latencyHintMs?.let { "$it ms" } ?: "—")
                    append(" • ")
                    append(server.loadPercent)
                    append('%')
                    if (server.favorite) append(" • ★")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                enabled = enabled,
                onClick = {
                    val entitlementId = compatibleEntitlementId ?: return@Button
                    onSelectServer(server.id)
                    onConnect(entitlementId)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (selected && enabled) {
                        stringResource(R.string.servers_smart_connect)
                    } else if (server.status == ServerUiStatus.MAINTENANCE) {
                        stringResource(R.string.failure_server_unavailable)
                    } else {
                        stringResource(R.string.common_connect)
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyServerState(onRetry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.servers_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.servers_empty_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(R.string.common_retry))
            }
        }
    }
}

private fun UiTier.label(): String = when (this) {
    UiTier.FREE -> "FREE"
    UiTier.PREMIUM -> "PREMIUM"
    UiTier.VIP -> "VIP"
}
