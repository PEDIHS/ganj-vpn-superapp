package com.ganj.vpn.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun AccountBackAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GanjBackPill(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = "بازگشت به صفحه قبل" },
    )
}