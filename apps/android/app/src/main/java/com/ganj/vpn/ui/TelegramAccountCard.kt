package com.ganj.vpn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ganj.vpn.R

@Composable
internal fun TelegramAccountCard(
    linked: Boolean,
    busy: Boolean,
    error: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ContentCard(
        accent = if (linked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        modifier = modifier.padding(horizontal = 16.dp),
    ) {
        GanjStatusPill(
            text = stringResource(
                if (linked) R.string.auth_telegram_linked_badge else R.string.auth_telegram_guest_badge,
            ),
            tone = if (linked) GanjStatusTone.Positive else GanjStatusTone.Neutral,
        )
        Text(
            text = stringResource(R.string.auth_telegram_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(
                if (linked) R.string.auth_telegram_linked_body else R.string.auth_telegram_guest_body,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        if (error) {
            Text(
                text = stringResource(R.string.auth_telegram_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (linked) {
                OutlinedButton(
                    onClick = onLogout,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (busy) R.string.auth_telegram_working else R.string.auth_telegram_logout,
                        ),
                    )
                }
            } else {
                Button(
                    onClick = onLogin,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (busy) R.string.auth_telegram_working else R.string.auth_telegram_connect,
                        ),
                    )
                }
            }
        }
    }
}
