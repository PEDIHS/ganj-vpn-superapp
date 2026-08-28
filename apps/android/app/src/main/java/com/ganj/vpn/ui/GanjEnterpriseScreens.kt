package com.ganj.vpn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                "Connections and purchases are paused until the signed product policy allows them.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Check again") }
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
        Text("Product status", fontWeight = FontWeight.Bold)
        Text(
            when (availability) {
                ProductAvailabilityUiState.Loading -> "Checking signed runtime policy…"
                ProductAvailabilityUiState.Available -> "Operational"
                is ProductAvailabilityUiState.OptionalUpdate ->
                    "Version ${availability.latestVersion} is available."
                is ProductAvailabilityUiState.ForcedUpdate -> "A security update is required."
                is ProductAvailabilityUiState.Maintenance -> availability.message ?: "Maintenance in progress."
                ProductAvailabilityUiState.AuthRequired -> "Sign in to resolve device policy."
                is ProductAvailabilityUiState.Failed -> "Runtime policy is unavailable; optional features are off."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onRefresh) { Text("Refresh product status") }
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

    ContentCard(accent = MaterialTheme.colorScheme.secondary) {
        Text("Report a problem", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Do not paste VPN configurations, credentials, links, or private message content.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(200) },
            enabled = !submitting,
            label = { Text("Short title") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(12_000) },
            enabled = !submitting,
            label = { Text("What happened?") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Category", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(BugCategory.CONNECTION, BugCategory.PURCHASE, BugCategory.ACCOUNT).forEach { option ->
                OutlinedButton(
                    onClick = { category = option },
                    enabled = !submitting,
                ) {
                    Text(if (category == option) "✓ ${option.name}" else option.name, fontSize = 10.sp)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !submitting)
            Text(
                "I approve sending coarse device, network type, connection state and error code.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
        }
        when (status) {
            BugReportUiState.Idle -> Button(
                onClick = { onSubmit(BugReportInput(title, description, category, consent)) },
                enabled = title.trim().length >= 3 && description.trim().length >= 3 && consent,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send privacy-safe report") }
            BugReportUiState.Submitting -> LoadingCard("Submitting report")
            BugReportUiState.AuthRequired -> AuthCard(onClear)
            is BugReportUiState.Submitted -> {
                Text(
                    "Report ${status.publicCode} submitted",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Status: ${status.status.name}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                OutlinedButton(onClick = onClear) { Text("Report another issue") }
            }
            is BugReportUiState.Failed -> {
                Text(
                    enterpriseMessage(status.messageKey),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(onClick = onClear) { Text(if (status.retryable) "Try again" else "Edit report") }
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
        Text("Privacy-safe diagnostics", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Tests only result classes, latency, packet loss, VPN state and device integrity. " +
                "No hostname, destination, IP address, DNS query, payload, credential or configuration is collected.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !busy)
            Text(
                "I approve running and uploading this minimized diagnostic report.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
        }
        when (status) {
            DiagnosticUiState.Idle -> Button(
                onClick = { onSubmit(consent) },
                enabled = consent,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Run diagnostics") }
            DiagnosticUiState.Running -> LoadingCard("Running allowlisted tests")
            DiagnosticUiState.Uploading -> LoadingCard("Uploading redacted results")
            DiagnosticUiState.AuthRequired -> AuthCard(onClear)
            is DiagnosticUiState.Submitted -> {
                Text(
                    "Diagnostic report submitted",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Redaction policy: ${status.redactionVersion}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                OutlinedButton(onClick = onClear) { Text("Done") }
            }
            is DiagnosticUiState.Failed -> {
                Text(
                    enterpriseMessage(status.messageKey),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(onClick = onClear) { Text(if (status.retryable) "Retry" else "Review consent") }
            }
        }
    }
}
