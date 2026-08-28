package com.ganj.vpn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ganj.vpn.R
import com.ganj.vpn.enterprise.BugCategory
import com.ganj.vpn.enterprise.BugReportInput
import com.ganj.vpn.enterprise.BugReportUiState
import com.ganj.vpn.enterprise.DiagnosticUiState
import com.ganj.vpn.enterprise.ProductAvailabilityUiState

@Composable
internal fun ProductGateScreen(
    title: String,
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = GanjWarning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.gate_policy_paused),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.gate_check_again))
            }
        }
    }
}

@Composable
internal fun EnterpriseStatusCard(
    availability: ProductAvailabilityUiState,
    onRefresh: () -> Unit,
) {
    val accent = when (availability) {
        ProductAvailabilityUiState.Available -> MaterialTheme.colorScheme.primary
        ProductAvailabilityUiState.Loading -> MaterialTheme.colorScheme.secondary
        is ProductAvailabilityUiState.OptionalUpdate -> GanjWarning
        else -> MaterialTheme.colorScheme.error
    }
    ContentCard(accent = accent) {
        Text(stringResource(R.string.enterprise_product_status), fontWeight = FontWeight.Bold)
        Text(
            when (availability) {
                ProductAvailabilityUiState.Loading -> stringResource(R.string.enterprise_policy_checking)
                ProductAvailabilityUiState.Available -> stringResource(R.string.enterprise_operational)
                is ProductAvailabilityUiState.OptionalUpdate ->
                    stringResource(R.string.enterprise_optional_update, availability.latestVersion)
                is ProductAvailabilityUiState.ForcedUpdate ->
                    stringResource(R.string.enterprise_security_update_required)
                is ProductAvailabilityUiState.Maintenance ->
                    availability.message ?: stringResource(R.string.enterprise_maintenance)
                ProductAvailabilityUiState.AuthRequired -> stringResource(R.string.enterprise_sign_in_policy)
                is ProductAvailabilityUiState.Failed -> stringResource(R.string.enterprise_policy_unavailable)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onRefresh) {
            Text(stringResource(R.string.enterprise_refresh_status))
        }
    }
}

@Composable
internal fun BugReportPanel(
    status: BugReportUiState,
    onSubmit: (BugReportInput) -> Unit,
    onClear: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(BugCategory.CONNECTION) }
    var consent by remember { mutableStateOf(false) }
    val submitting = status == BugReportUiState.Submitting
    val categories = remember { BugCategory.entries.toList() }
    val categoryColumns = GanjResponsivePolicy.categoryColumns(
        widthDp = LocalConfiguration.current.screenWidthDp,
        fontScale = LocalDensity.current.fontScale,
    )

    ContentCard(accent = MaterialTheme.colorScheme.secondary) {
        Text(stringResource(R.string.bug_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            stringResource(R.string.bug_privacy_warning),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(200) },
            enabled = !submitting,
            label = { Text(stringResource(R.string.bug_short_title)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(12_000) },
            enabled = !submitting,
            label = { Text(stringResource(R.string.bug_what_happened)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.bug_category),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.chunked(categoryColumns).forEach { rowCategories ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowCategories.forEach { option ->
                        val optionText = bugCategoryText(option)
                        OutlinedButton(
                            onClick = { category = option },
                            enabled = !submitting,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                if (category == option) "✓ $optionText" else optionText,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = if (categoryColumns == 1) 2 else 1,
                            )
                        }
                    }
                    repeat(categoryColumns - rowCategories.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !submitting)
            Text(
                stringResource(R.string.bug_diagnostics_consent),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
        when (status) {
            BugReportUiState.Idle -> Button(
                onClick = { onSubmit(BugReportInput(title, description, category, consent)) },
                enabled = title.trim().length >= 3 && description.trim().length >= 3 && consent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.bug_send))
            }
            BugReportUiState.Submitting -> LoadingCard(stringResource(R.string.bug_submitting))
            BugReportUiState.AuthRequired -> AuthCard(onClear)
            is BugReportUiState.Submitted -> {
                Text(
                    stringResource(R.string.bug_submitted, status.publicCode),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.bug_status, bugStatusText(status.status)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onClear) {
                    Text(stringResource(R.string.bug_another))
                }
            }
            is BugReportUiState.Failed -> {
                Text(
                    enterpriseMessage(status.messageKey),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(onClick = onClear) {
                    Text(
                        if (status.retryable) {
                            stringResource(R.string.common_retry)
                        } else {
                            stringResource(R.string.bug_edit)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun DiagnosticPanel(
    status: DiagnosticUiState,
    onSubmit: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    var consent by remember { mutableStateOf(false) }
    val busy = status == DiagnosticUiState.Running || status == DiagnosticUiState.Uploading

    ContentCard(accent = MaterialTheme.colorScheme.primary) {
        Text(stringResource(R.string.diagnostic_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            stringResource(R.string.diagnostic_privacy_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !busy)
            Text(
                stringResource(R.string.diagnostic_consent),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
        when (status) {
            DiagnosticUiState.Idle -> Button(
                onClick = { onSubmit(consent) },
                enabled = consent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.diagnostic_run))
            }
            DiagnosticUiState.Running -> LoadingCard(stringResource(R.string.diagnostic_running))
            DiagnosticUiState.Uploading -> LoadingCard(stringResource(R.string.diagnostic_uploading))
            DiagnosticUiState.AuthRequired -> AuthCard(onClear)
            is DiagnosticUiState.Submitted -> {
                Text(
                    stringResource(R.string.diagnostic_submitted),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.diagnostic_redaction_policy, status.redactionVersion),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onClear) {
                    Text(stringResource(R.string.common_done))
                }
            }
            is DiagnosticUiState.Failed -> {
                Text(
                    enterpriseMessage(status.messageKey),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(onClick = onClear) {
                    Text(
                        if (status.retryable) {
                            stringResource(R.string.common_retry)
                        } else {
                            stringResource(R.string.diagnostic_review_consent)
                        },
                    )
                }
            }
        }
    }
}
