package com.ganj.vpn.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ganj.vpn.R

internal enum class StitchProductGateKind {
    SecurityUpdate,
    Maintenance,
}

@Composable
internal fun StitchProductGateScreen(
    kind: StitchProductGateKind,
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = when (kind) {
        StitchProductGateKind.SecurityUpdate -> GanjGold
        StitchProductGateKind.Maintenance -> MaterialTheme.colorScheme.secondary
    }
    val status = when (kind) {
        StitchProductGateKind.SecurityUpdate -> "بروزرسانی لازم است"
        StitchProductGateKind.Maintenance -> "سرویس موقتاً متوقف است"
    }
    val tone = when (kind) {
        StitchProductGateKind.SecurityUpdate -> GanjStatusTone.Premium
        StitchProductGateKind.Maintenance -> GanjStatusTone.Warning
    }

    GanjFullScreenBlockingState(
        title = title,
        message = message,
        status = status,
        accent = accent,
        statusTone = tone,
        actionText = stringResource(R.string.gate_check_again),
        onAction = onRetry,
        supporting = stringResource(R.string.gate_policy_paused),
        modifier = modifier,
    )
}
