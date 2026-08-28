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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ganj.vpn.R
import com.ganj.vpn.enterprise.BugCategory
import com.ganj.vpn.enterprise.BugStatus
import com.ganj.vpn.presentation.CheckoutSafeAction
import com.ganj.vpn.presentation.PlanUiModel
import com.ganj.vpn.presentation.ServiceUiStatus
import com.ganj.vpn.presentation.UiFailure
import com.ganj.vpn.presentation.UiTier

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
                    text = stringResource(R.string.common_refresh),
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
        stringResource(R.string.common_wait),
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
    OutlinedButton(onClick = onAction) { Text(stringResource(R.string.common_refresh)) }
}

@Composable
internal fun AuthCard(onRetry: () -> Unit) = ContentCard(accent = GanjWarning) {
    Text(stringResource(R.string.auth_required_title), fontWeight = FontWeight.Bold)
    Text(
        stringResource(R.string.auth_required_body),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.auth_refresh_session)) }
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
            stringResource(R.string.common_request_code, it.take(8)),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
    if (failure.retryable) {
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
    }
}

@Composable
internal fun formatPrice(product: PlanUiModel): String = if (product.amountMinor == 0L) {
    stringResource(R.string.plan_free)
} else {
    val major = product.amountMinor / 100
    val minor = product.amountMinor % 100
    "$major.${minor.toString().padStart(2, '0')} ${product.currency}"
}

@Composable
internal fun formatTraffic(bytes: Long?): String = when {
    bytes == null -> stringResource(R.string.traffic_unlimited)
    bytes >= 1_000_000_000 -> stringResource(R.string.traffic_gb_left, bytes / 1_000_000_000)
    else -> stringResource(R.string.traffic_mb_left, bytes / 1_000_000)
}

@Composable
internal fun checkoutActionText(action: CheckoutSafeAction?): String = when (action) {
    is CheckoutSafeAction.LaunchGooglePlay -> stringResource(R.string.checkout_opening_play)
    CheckoutSafeAction.WaitForProvider, null -> stringResource(R.string.checkout_waiting_provider)
}

@Composable
internal fun serviceStatusText(status: ServiceUiStatus): String = when (status) {
    ServiceUiStatus.PENDING -> stringResource(R.string.service_status_pending)
    ServiceUiStatus.ACTIVE -> stringResource(R.string.service_status_active)
    ServiceUiStatus.DISABLED -> stringResource(R.string.service_status_disabled)
    ServiceUiStatus.EXPIRED -> stringResource(R.string.service_status_expired)
    ServiceUiStatus.REVOKED -> stringResource(R.string.service_status_revoked)
}

@Composable
internal fun tierText(tier: UiTier): String = when (tier) {
    UiTier.FREE -> stringResource(R.string.tier_free)
    UiTier.PREMIUM -> stringResource(R.string.tier_premium)
    UiTier.VIP -> stringResource(R.string.tier_vip)
}

@Composable
internal fun bugCategoryText(category: BugCategory): String = when (category) {
    BugCategory.CONNECTION -> stringResource(R.string.bug_category_connection)
    BugCategory.PURCHASE -> stringResource(R.string.bug_category_purchase)
    BugCategory.ACCOUNT -> stringResource(R.string.bug_category_account)
    BugCategory.UI -> stringResource(R.string.bug_category_ui)
    BugCategory.PERFORMANCE -> stringResource(R.string.bug_category_performance)
    BugCategory.SECURITY -> stringResource(R.string.bug_category_security)
    BugCategory.OTHER -> stringResource(R.string.bug_category_other)
}

@Composable
internal fun bugStatusText(status: BugStatus): String = when (status) {
    BugStatus.NEW -> stringResource(R.string.bug_status_new)
    BugStatus.INVESTIGATING -> stringResource(R.string.bug_status_investigating)
    BugStatus.ASSIGNED -> stringResource(R.string.bug_status_assigned)
    BugStatus.FIXING -> stringResource(R.string.bug_status_fixing)
    BugStatus.TESTING -> stringResource(R.string.bug_status_testing)
    BugStatus.RELEASED -> stringResource(R.string.bug_status_released)
    BugStatus.CLOSED -> stringResource(R.string.bug_status_closed)
}

@Composable
internal fun failureMessage(failure: UiFailure): String = when (failure.messageKey) {
    "auth.required" -> stringResource(R.string.failure_auth_required)
    "entitlement.denied" -> stringResource(R.string.failure_entitlement_denied)
    "request.conflict" -> stringResource(R.string.failure_request_conflict)
    "request.rate_limited" -> stringResource(R.string.failure_rate_limited)
    "network.unavailable" -> stringResource(R.string.failure_network_unavailable)
    "server.unavailable" -> stringResource(R.string.failure_server_unavailable)
    "connection.context_unavailable" -> stringResource(R.string.failure_connection_context)
    "connection.service_inactive" -> stringResource(R.string.failure_service_inactive)
    "connection.permission_denied" -> stringResource(R.string.failure_vpn_permission)
    "connection.action_expired", "connection.profile_consumed", "connection.profile_expired" ->
        stringResource(R.string.failure_connection_expired)
    "connection.device_crypto_unavailable" -> stringResource(R.string.failure_device_crypto)
    "connection.profile_authentication_failed", "connection.profile_rejected" ->
        stringResource(R.string.failure_profile_auth)
    "connection.protocol_unsupported" -> stringResource(R.string.failure_protocol_unsupported)
    "connection.tunnel_start_failed" -> stringResource(R.string.failure_tunnel_start)
    "connection.disconnect_failed" -> stringResource(R.string.failure_disconnect)
    "billing.provider_unavailable" -> stringResource(R.string.failure_billing_provider)
    else -> stringResource(R.string.failure_generic)
}

@Composable
internal fun enterpriseMessage(messageKey: String): String = when (messageKey) {
    "privacy.consent_required" -> stringResource(R.string.enterprise_failure_consent)
    "bug_report.invalid_or_sensitive" -> stringResource(R.string.enterprise_failure_bug_sensitive)
    "bug_report.disabled" -> stringResource(R.string.enterprise_failure_bug_disabled)
    "diagnostic.disabled" -> stringResource(R.string.enterprise_failure_diagnostic_disabled)
    "diagnostic.context_unavailable" -> stringResource(R.string.enterprise_failure_context)
    "diagnostic.collection_failed" -> stringResource(R.string.enterprise_failure_collection)
    "diagnostic.invalid" -> stringResource(R.string.enterprise_failure_invalid)
    "network.unavailable" -> stringResource(R.string.failure_network_unavailable)
    "request.rate_limited" -> stringResource(R.string.failure_rate_limited)
    "server.unavailable" -> stringResource(R.string.failure_server_unavailable)
    else -> stringResource(R.string.enterprise_failure_generic)
}
