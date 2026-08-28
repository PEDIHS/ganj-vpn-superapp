package com.ganj.vpn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ganj.vpn.R
import com.ganj.vpn.composition.TelegramBotAuthResult

@Composable
internal fun TelegramAccountCard(
    linked: Boolean,
    result: TelegramBotAuthResult?,
    onLogin: () -> Unit,
    onCheck: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ContentCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        accent = if (linked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.telegram_account_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (linked) {
                        stringResource(R.string.telegram_account_linked_body)
                    } else {
                        stringResource(R.string.telegram_account_guest_body)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            GanjStatusPill(
                text = if (linked) {
                    stringResource(R.string.telegram_account_linked)
                } else {
                    stringResource(R.string.telegram_account_guest)
                },
                tone = if (linked) GanjStatusTone.Positive else GanjStatusTone.Neutral,
            )
        }

        when (result) {
            TelegramBotAuthResult.Pending,
            is TelegramBotAuthResult.Launch,
            -> Text(
                stringResource(R.string.telegram_account_waiting),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            TelegramBotAuthResult.Cancelled -> Text(
                stringResource(R.string.telegram_account_cancelled),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )

            is TelegramBotAuthResult.Failed -> Text(
                stringResource(R.string.telegram_account_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )

            else -> Unit
        }

        if (linked) {
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.telegram_account_logout))
            }
        } else if (result is TelegramBotAuthResult.Pending || result is TelegramBotAuthResult.Launch) {
            Button(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.telegram_account_check))
            }
        } else {
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.telegram_account_login))
            }
        }
    }
}
