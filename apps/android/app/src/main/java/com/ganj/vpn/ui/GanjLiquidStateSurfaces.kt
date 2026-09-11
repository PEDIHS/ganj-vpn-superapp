package com.ganj.vpn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

internal enum class GanjMessageTone { Info, Success, Warning, Error }

@Composable
internal fun GanjInfoDialog(
    title: String,
    body: String,
    actionText: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) = GanjLiquidMessageDialog(GanjMessageTone.Info, title, body, actionText, onAction, onDismiss)

@Composable
internal fun GanjSuccessDialog(
    title: String,
    body: String,
    actionText: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) = GanjLiquidMessageDialog(GanjMessageTone.Success, title, body, actionText, onAction, onDismiss)

@Composable
internal fun GanjWarningDialog(
    title: String,
    body: String,
    actionText: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) = GanjLiquidMessageDialog(GanjMessageTone.Warning, title, body, actionText, onAction, onDismiss)

@Composable
internal fun GanjErrorDialog(
    title: String,
    body: String,
    actionText: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) = GanjLiquidMessageDialog(GanjMessageTone.Error, title, body, actionText, onAction, onDismiss)

@Composable
internal fun GanjLiquidMessageDialog(
    tone: GanjMessageTone,
    title: String,
    body: String,
    actionText: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = when (tone) {
        GanjMessageTone.Info -> MaterialTheme.colorScheme.primary
        GanjMessageTone.Success -> MaterialTheme.colorScheme.tertiary
        GanjMessageTone.Warning -> GanjWarning
        GanjMessageTone.Error -> MaterialTheme.colorScheme.error
    }
    val statusTone = when (tone) {
        GanjMessageTone.Info -> GanjStatusTone.Neutral
        GanjMessageTone.Success -> GanjStatusTone.Positive
        GanjMessageTone.Warning -> GanjStatusTone.Warning
        GanjMessageTone.Error -> GanjStatusTone.Danger
    }

    Dialog(onDismissRequest = onDismiss) {
        GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = accent,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 28.dp,
            padding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GanjStatusPill(text = title, tone = statusTone)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GanjLiquidAction(
                onClick = onAction,
                accent = accent,
                shapeRadius = 999.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = actionText,
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = when (tone) {
                        GanjMessageTone.Warning -> MaterialTheme.colorScheme.onSecondary
                        GanjMessageTone.Error -> MaterialTheme.colorScheme.onError
                        else -> MaterialTheme.colorScheme.onPrimary
                    },
                )
            }
        }
    }
}

@Composable
internal fun GanjFullScreenBlockingState(
    title: String,
    message: String,
    status: String,
    accent: Color,
    statusTone: GanjStatusTone,
    actionText: String,
    onAction: () -> Unit,
    supporting: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = responsiveHorizontalPadding(), vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        GanjGlassSurface(
            role = GanjGlassRole.Prominent,
            accent = accent,
            modifier = Modifier.fillMaxWidth(),
            shapeRadius = 28.dp,
            padding = PaddingValues(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GanjStatusPill(
                text = status,
                tone = statusTone,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            supporting?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            GanjLiquidAction(
                onClick = onAction,
                accent = accent,
                shapeRadius = 999.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = actionText,
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = when (statusTone) {
                        GanjStatusTone.Warning,
                        GanjStatusTone.Premium -> MaterialTheme.colorScheme.onSecondary
                        GanjStatusTone.Danger -> MaterialTheme.colorScheme.onError
                        else -> MaterialTheme.colorScheme.onPrimary
                    },
                )
            }
        }
    }
}
