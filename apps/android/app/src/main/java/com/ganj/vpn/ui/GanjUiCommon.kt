package com.ganj.vpn.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ganj.vpn.presentation.CheckoutSafeAction
import com.ganj.vpn.presentation.PlanUiModel
import com.ganj.vpn.presentation.UiFailure

@Composable
internal fun Page(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
internal fun AppHeader(
    title: String,
    subtitle: String,
    onRefresh: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (onRefresh != null) {
            GanjGlassSurface(
                role = GanjGlassRole.Clear,
                accent = MaterialTheme.colorScheme.primary,
                shapeRadius = 999.dp,
                padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.clickable(
                    role = Role.Button,
                    onClick = onRefresh,
                ),
            ) {
                Text(
                    text = "REFRESH",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun ContentCard(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GanjGlassSurface(
        role = GanjGlassRole.OpaqueFallback,
        accent = accent,
        modifier = modifier.fillMaxWidth(),
        shapeRadius = 24.dp,
        padding = PaddingValues(18.dp),
        content = content,
    )
}

@Composable
internal fun QuickAction(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GanjGlassSurface(
        role = GanjGlassRole.Regular,
        accent = MaterialTheme.colorScheme.primary,
        shapeRadius = 20.dp,
        padding = PaddingValues(16.dp),
        modifier = modifier.clickable(
            role = Role.Button,
            onClick = onClick,
        ),
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun LoadingCard(text: String) = ContentCard(accent = MaterialTheme.colorScheme.secondary) {
    Text(text, fontWeight = FontWeight.SemiBold)
    Text(
        "Please wait…",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
internal fun EmptyCard(
    title: String,
    subtitle: String,
    onAction: () -> Unit,
) = ContentCard(accent = MaterialTheme.colorScheme.outline) {
    Text(title, fontWeight = FontWeight.Bold)
    Text(
        subtitle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = onAction) { Text("Refresh") }
}

@Composable
internal fun AuthCard(onRetry: () -> Unit) = ContentCard(accent = GanjWarning) {
    Text("Sign in required", fontWeight = FontWeight.Bold)
    Text(
        "Connect your Telegram account, then refresh this page.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = onRetry) { Text("Refresh session") }
}

@Composable
internal fun ErrorCard(
    failure: UiFailure,
    onRetry: () -> Unit,
) = ContentCard(accent = MaterialTheme.colorScheme.error) {
    Text(
        failureMessage(failure),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error,
    )
    failure.requestId?.let {
        Text(
            "Request • ${it.take(8)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
    if (failure.retryable) OutlinedButton(onClick = onRetry) { Text("Try again") }
}

internal fun formatPrice(product: PlanUiModel): String = if (product.amountMinor == 0L) {
    "Free"
} else {
    val major = product.amountMinor / 100
    val minor = product.amountMinor % 100
    "$major.${minor.toString().padStart(2, '0')} ${product.currency}"
}

internal fun formatTraffic(bytes: Long?): String = when {
    bytes == null -> "Unlimited traffic"
    bytes >= 1_000_000_000 -> "${bytes / 1_000_000_000} GB left"
    else -> "${bytes / 1_000_000} MB left"
}

internal fun checkoutActionText(action: CheckoutSafeAction?): String = when (action) {
    is CheckoutSafeAction.LaunchGooglePlay -> "Opening the secure Google Play checkout."
    CheckoutSafeAction.WaitForProvider, null -> "Waiting for Play and backend confirmation."
}

internal fun failureMessage(failure: UiFailure): String = when (failure.messageKey) {
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

internal fun enterpriseMessage(messageKey: String): String = when (messageKey) {
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
