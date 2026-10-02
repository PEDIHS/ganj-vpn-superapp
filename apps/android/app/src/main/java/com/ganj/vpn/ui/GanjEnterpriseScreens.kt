package com.ganj.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ganj.vpn.R
import com.ganj.vpn.enterprise.BugCategory
import com.ganj.vpn.enterprise.BugReportInput
import com.ganj.vpn.enterprise.BugReportUiState
import com.ganj.vpn.enterprise.DiagnosticUiState
import com.ganj.vpn.enterprise.ProductAvailabilityUiState

private val EnterpriseGold = Color(0xFFD5A63A)
private val EnterpriseEmerald = Color(0xFF72FCB6)

@Composable
internal fun ProductGateScreen(
    title: String,
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(responsiveHorizontalPadding()),
        contentAlignment = Alignment.Center,
    ) {
        GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = GanjWarning,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 28.dp,
            padding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GanjStatusPill(
                text = stringResource(R.string.enterprise_product_status),
                tone = GanjStatusTone.Warning,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.gate_policy_paused),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            GanjLiquidAction(
                onClick = onRetry,
                accent = GanjWarning,
                shapeRadius = 999.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.gate_check_again),
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSecondary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
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
    val statusText = when (availability) {
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
    }
    val tone = when (availability) {
        ProductAvailabilityUiState.Available -> GanjStatusTone.Positive
        ProductAvailabilityUiState.Loading -> GanjStatusTone.Neutral
        is ProductAvailabilityUiState.OptionalUpdate -> GanjStatusTone.Warning
        else -> GanjStatusTone.Danger
    }

    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = accent,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 22.dp,
        padding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = stringResource(R.string.enterprise_product_status),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GanjStatusPill(text = statusText, tone = tone)
        }
        GanjSecondaryGlassAction(
            text = stringResource(R.string.enterprise_refresh_status),
            onClick = onRefresh,
            accent = accent,
            modifier = Modifier.fillMaxWidth(),
        )
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

    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 22.dp,
        padding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        GanjSectionHeader(
            title = stringResource(R.string.bug_title),
            supporting = stringResource(R.string.bug_privacy_warning),
        )
        when (status) {
            BugReportUiState.Submitting -> GanjStatusPill(
                text = stringResource(R.string.bug_submitting),
                tone = GanjStatusTone.Neutral,
            )
            is BugReportUiState.Submitted -> GanjStatusPill(
                text = stringResource(R.string.bug_submitted, status.publicCode),
                tone = GanjStatusTone.Positive,
            )
            is BugReportUiState.Failed -> GanjStatusPill(
                text = enterpriseMessage(status.messageKey),
                tone = GanjStatusTone.Danger,
            )
            else -> Unit
        }

        GanjEnterpriseTextField(
            value = title,
            onValueChange = { title = it.take(200) },
            enabled = !submitting,
            label = stringResource(R.string.bug_short_title),
            singleLine = true,
        )
        GanjEnterpriseTextField(
            value = description,
            onValueChange = { description = it.take(12_000) },
            enabled = !submitting,
            label = stringResource(R.string.bug_what_happened),
            minLines = 4,
        )

        Text(
            text = stringResource(R.string.bug_category),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.chunked(categoryColumns).forEach { rowCategories ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowCategories.forEach { option ->
                        GanjChoiceChip(
                            text = bugCategoryText(option),
                            selected = category == option,
                            enabled = !submitting,
                            onClick = { category = option },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(categoryColumns - rowCategories.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        GanjConsentRow(
            checked = consent,
            enabled = !submitting,
            text = stringResource(R.string.bug_diagnostics_consent),
            onCheckedChange = { consent = it },
        )

        when (status) {
            BugReportUiState.Idle -> GanjLiquidAction(
                onClick = { onSubmit(BugReportInput(title, description, category, consent)) },
                enabled = title.trim().length >= 3 && description.trim().length >= 3 && consent,
                accent = MaterialTheme.colorScheme.primary,
                shapeRadius = 999.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.bug_send),
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            BugReportUiState.Submitting -> Unit
            BugReportUiState.AuthRequired -> AuthCard(onClear)
            is BugReportUiState.Submitted -> {
                Text(
                    text = stringResource(R.string.bug_status, bugStatusText(status.status)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                GanjSecondaryGlassAction(
                    text = stringResource(R.string.bug_another),
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is BugReportUiState.Failed -> GanjSecondaryGlassAction(
                text = if (status.retryable) {
                    stringResource(R.string.common_retry)
                } else {
                    stringResource(R.string.bug_edit)
                },
                onClick = onClear,
                accent = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
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

    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 22.dp,
        padding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        GanjSectionHeader(
            title = stringResource(R.string.diagnostic_title),
            supporting = stringResource(R.string.diagnostic_privacy_body),
        )
        when (status) {
            DiagnosticUiState.Running -> GanjStatusPill(
                text = stringResource(R.string.diagnostic_running),
                tone = GanjStatusTone.Neutral,
            )
            DiagnosticUiState.Uploading -> GanjStatusPill(
                text = stringResource(R.string.diagnostic_uploading),
                tone = GanjStatusTone.Neutral,
            )
            is DiagnosticUiState.Submitted -> GanjStatusPill(
                text = stringResource(R.string.diagnostic_submitted),
                tone = GanjStatusTone.Positive,
            )
            is DiagnosticUiState.Failed -> GanjStatusPill(
                text = enterpriseMessage(status.messageKey),
                tone = GanjStatusTone.Danger,
            )
            else -> Unit
        }

        GanjConsentRow(
            checked = consent,
            enabled = !busy,
            text = stringResource(R.string.diagnostic_consent),
            onCheckedChange = { consent = it },
        )

        when (status) {
            DiagnosticUiState.Idle -> GanjLiquidAction(
                onClick = { onSubmit(consent) },
                enabled = consent,
                accent = MaterialTheme.colorScheme.primary,
                shapeRadius = 999.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.diagnostic_run),
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            DiagnosticUiState.Running,
            DiagnosticUiState.Uploading,
            -> Unit
            DiagnosticUiState.AuthRequired -> AuthCard(onClear)
            is DiagnosticUiState.Submitted -> {
                GanjInfoChip(
                    text = stringResource(R.string.diagnostic_redaction_policy, status.redactionVersion),
                    accent = MaterialTheme.colorScheme.primary,
                )
                GanjSecondaryGlassAction(
                    text = stringResource(R.string.common_done),
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is DiagnosticUiState.Failed -> GanjSecondaryGlassAction(
                text = if (status.retryable) {
                    stringResource(R.string.common_retry)
                } else {
                    stringResource(R.string.diagnostic_review_consent)
                },
                onClick = onClear,
                accent = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GanjEnterpriseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    label: String,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
            focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.56f),
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
            disabledIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
        ),
    )
}

@Composable
private fun GanjChoiceChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.46f),
            )
            .border(
                1.dp,
                accent.copy(alpha = if (selected) 0.44f else 0.18f),
                shape,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (selected) "✓ $text" else text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) EnterpriseEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GanjConsentRow(
    checked: Boolean,
    enabled: Boolean,
    text: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.40f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), shape)
            .clickable(enabled = enabled, role = Role.Checkbox) { onCheckedChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GanjSecondaryGlassAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.48f))
            .border(1.dp, accent.copy(alpha = 0.26f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (accent == EnterpriseGold) EnterpriseGold else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}
